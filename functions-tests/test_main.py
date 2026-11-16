"""Tests de la lógica pura de functions/main.py (sin Firebase ni Gemini).

Uso, desde la raíz del repositorio y con las dependencias de functions/
instaladas:
    python -m unittest discover -s functions-tests -v

Están fuera de functions/ para que no se suban con el despliegue.
"""
import calendar
import hashlib
import hmac
import inspect
import json
import os
import sys
import time
import unittest
from types import SimpleNamespace
from unittest import mock

# main.py declara los secretos SMTP; para importarlo basta con valores ficticios
os.environ.setdefault("SMTP_USUARIO", "tests@example.com")
os.environ.setdefault("SMTP_CLAVE", "tests")
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "functions"))

import main  # noqa: E402
from google.api_core.exceptions import NotFound  # noqa: E402


def factura(**cambios):
    """Lo que devolvería Gemini para una factura normal: 320 kWh en 31 días, 72,50 €."""
    base = {"esFacturaLuz": True, "consumoKwh": 320, "dias": 31, "importeTotal": 72.5, "potenciaKw": 4.6}
    return {**base, **cambios}


class ValidarFacturaTest(unittest.TestCase):

    def test_factura_normal_se_pasa_a_datos_anuales(self):
        r = main._validar_factura(factura())
        self.assertEqual(r["consumoAnualKwh"], round(320 * 365 / 31, 1))
        self.assertAlmostEqual(r["precioMedio"], 72.5 / 320, places=4)
        self.assertEqual(r["potenciaKw"], 4.6)
        self.assertEqual(r["dias"], 31)

    def test_lo_que_no_es_una_factura_de_luz_se_rechaza(self):
        with self.assertRaises(main._FacturaNoValida):
            main._validar_factura(factura(esFacturaLuz=False))

    def test_datos_imposibles_se_rechazan(self):
        casos = [
            factura(consumoKwh=0),
            factura(importeTotal=0),
            factura(dias=0),
            factura(dias=500),
            factura(importeTotal=7250),   # 22,6 €/kWh: número mal leído
            factura(importeTotal=5),      # 0,016 €/kWh: imposible con impuestos
            factura(consumoKwh="mucho"),
        ]
        for caso in casos:
            with self.subTest(caso=caso), self.assertRaises(main._FacturaNoValida):
                main._validar_factura(caso)

    def test_potencia_absurda_o_ausente_queda_en_cero(self):
        self.assertEqual(main._validar_factura(factura(potenciaKw=0))["potenciaKw"], 0.0)
        self.assertEqual(main._validar_factura(factura(potenciaKw=900))["potenciaKw"], 0.0)


class PromptIATest(unittest.TestCase):

    INFORME = {
        "etiqueta": "D", "consumoEstimado": 12000, "consumoPorM2": 150,
        "emisiones": 3000, "costeAnual": 1500, "recomendaciones": [],
    }
    VIVIENDA = {
        "superficie": 90, "provincia": "Madrid", "potenciaContratadaKw": 5.75,
        "nombre": "Casa de mi abuela", "direccion": "Calle Falsa 123", "uid": "abc123",
    }

    def test_no_envia_datos_personales(self):
        prompt = main._prompt_ia(self.INFORME, self.VIVIENDA, "Español")
        for dato in ("Casa de mi abuela", "Calle Falsa", "abc123"):
            self.assertNotIn(dato, prompt)

    def test_incluye_la_factura_solo_si_existe(self):
        sin = main._prompt_ia(self.INFORME, self.VIVIENDA, "Español")
        self.assertNotIn("consumo_luz_real_factura_kwh_anio", sin)
        con = main._prompt_ia({**self.INFORME, "consumoLuzFactura": 3768, "consumoLuzEstimado": 3100,
                               "precioLuz": 0.2266}, self.VIVIENDA, "Español")
        self.assertIn("consumo_luz_real_factura_kwh_anio", con)
        self.assertIn("potenciaContratadaKw", con)

    def test_pide_responder_en_el_idioma_de_la_app(self):
        self.assertIn("«Deutsch»", main._prompt_ia(self.INFORME, self.VIVIENDA, "Deutsch"))


class LimpiarRespuestaIATest(unittest.TestCase):

    def test_recorta_y_normaliza(self):
        r = main._limpiar_respuesta_ia({
            "resumen": "x" * 2000,
            "consejos": [{"titulo": "t" * 500, "detalle": "d", "prioridad": "urgente", "coste": "gratis"}] * 8
                        + ["no es un consejo"],
            "habitos": ["h1", "h2", "h3", "h4"],
        })
        self.assertEqual(len(r["resumen"]), 800)
        self.assertEqual(len(r["consejos"]), 5)
        self.assertEqual(len(r["consejos"][0]["titulo"]), 120)
        self.assertEqual(r["consejos"][0]["prioridad"], "media")
        self.assertEqual(r["consejos"][0]["coste"], "medio")
        self.assertEqual(r["habitos"], ["h1", "h2", "h3"])

    def test_respuesta_vacia_no_rompe(self):
        self.assertEqual(main._limpiar_respuesta_ia({}), {"resumen": "", "consejos": [], "habitos": []})


class ResumenMensajeTest(unittest.TestCase):

    def test_texto_tal_cual(self):
        self.assertEqual(main._resumen_mensaje({"tipo": "texto", "texto": "Hola"}, "Español"), "Hola")

    def test_adjuntos_en_el_idioma_de_quien_lo_recibe(self):
        self.assertEqual(main._resumen_mensaje({"tipo": "imagen"}, "English"), "📷 Photo")
        self.assertEqual(main._resumen_mensaje({"tipo": "imagen", "texto": "La caldera"}, "Deutsch"), "📷 La caldera")
        self.assertEqual(main._resumen_mensaje({"tipo": "archivo", "mediaNombre": "presupuesto.pdf"}, "Polski"), "📎 presupuesto.pdf")
        self.assertEqual(main._resumen_mensaje({"tipo": "archivo"}, "Français"), "📎 Fichier")

    def test_idioma_desconocido_usa_espanol(self):
        self.assertEqual(main._resumen_mensaje({"tipo": "imagen"}, "Klingon"), "📷 Foto")

    def test_los_textos_traducidos_cubren_los_14_idiomas(self):
        for tabla in (main._ADJUNTOS, main._TEXTOS_SUSCRIPCION, main._AVISOS_EMAIL):
            self.assertEqual(set(tabla), set(main._TEXTOS_EMAIL))


class _DocFalso:
    def __init__(self, datos): self._datos = datos
    def get(self): return self
    def to_dict(self): return self._datos


class _DBFalsa:
    """Lo justo de Firestore para leer /usuarios/{uid}."""
    def __init__(self, usuarios): self._usuarios = usuarios
    def collection(self, _nombre): return self
    def document(self, uid): return _DocFalso(self._usuarios.get(uid))


class NombreRealTest(unittest.TestCase):

    def test_usa_el_nombre_del_perfil_y_no_el_del_mensaje(self):
        db = _DBFalsa({"u1": {"nombre": "Ana López"}})
        self.assertEqual(main._nombre_real(db, "u1", "ZeroHaus Soporte"), "Ana López")

    def test_sin_perfil_usa_el_respaldo_recortado(self):
        self.assertEqual(main._nombre_real(_DBFalsa({}), "u2", "x" * 100), "x" * 60)
        self.assertEqual(main._nombre_real(_DBFalsa({}), "", ""), "ZeroHaus")


class _Ref:
    def __init__(self, cambios, doc_id): self._cambios, self._id = cambios, doc_id
    def update(self, datos): self._cambios[self._id] = datos


class _Snap:
    def __init__(self, doc_id, datos, cambios):
        self.id, self._datos, self.reference = doc_id, datos, _Ref(cambios, doc_id)
    def to_dict(self): return self._datos


class _DBColecciones:
    """Lo justo de Firestore para recorrer colecciones y actualizar documentos."""
    def __init__(self, colecciones):
        self._colecciones, self.cambios = colecciones, {}
    def collection(self, nombre):
        docs = self._colecciones.get(nombre, {})
        cambios = self.cambios
        class _Col:
            def stream(self_):
                return [_Snap(i, d, cambios) for i, d in docs.items()]
        return _Col()


class RecalcularRatingsTest(unittest.TestCase):

    def test_corrige_notas_que_no_salen_de_resenas(self):
        db = _DBColecciones({
            "resenas": {"a": {"tecnicoId": "t1", "puntuacion": 5}, "b": {"tecnicoId": "t1", "puntuacion": 4}},
            "tecnicos": {
                "t1": {"rating": 4.5, "opiniones": 2},    # ya correcto: no se toca
                "t2": {"rating": 4.8, "opiniones": 12},   # perfil de pruebas sin reseñas
            },
        })
        self.assertEqual(main._recalcular_todos_los_ratings(db), 1)
        self.assertEqual(db.cambios, {"t2": {"rating": 0.0, "opiniones": 0}})


class EnviarPushTest(unittest.TestCase):

    def setUp(self):
        self.enviados = []
        self.fallar_fid = False
        self._original = main.messaging.send

        def enviar(msg):
            if msg.fid and self.fallar_fid:
                raise main.messaging.UnregisteredError("FID dado de baja")
            self.enviados.append("fid" if msg.fid else "token")
        main.messaging.send = enviar

    def tearDown(self):
        main.messaging.send = self._original

    def test_envia_al_fid_si_lo_hay(self):
        main._enviar_push({"fid": "f1", "token": "t1"}, "Hola", "Texto")
        self.assertEqual(self.enviados, ["fid"])

    def test_si_el_fid_falla_usa_el_token_antiguo(self):
        self.fallar_fid = True
        main._enviar_push({"fid": "f1", "token": "t1"}, "Hola", "Texto")
        self.assertEqual(self.enviados, ["token"])

    def test_apps_antiguas_solo_con_token(self):
        main._enviar_push({"fid": None, "token": "t1"}, "Hola", "Texto")
        self.assertEqual(self.enviados, ["token"])

    def test_sin_destino_no_envia_nada(self):
        main._enviar_push({"fid": None, "token": None}, "Hola", "Texto")
        main._enviar_push(None, "Hola", "Texto")
        self.assertEqual(self.enviados, [])


class ConfiguracionTest(unittest.TestCase):

    def test_ningun_modelo_por_defecto_esta_retirado(self):
        # gemini-2.0-flash se apagó en junio de 2026 y 2.5 el 20/10/2026
        if os.environ.get("GEMINI_MODEL"):
            self.skipTest("GEMINI_MODEL definido en el entorno")
        for modelo in main.GEMINI_MODELOS:
            self.assertFalse(modelo.startswith(("gemini-2.0", "gemini-2.5")), modelo)

    def test_los_esquemas_de_gemini_son_json_valido(self):
        for esquema in (main._ESQUEMA_IA, main._ESQUEMA_FACTURA):
            self.assertEqual(json.loads(json.dumps(esquema)), esquema)
            self.assertEqual(esquema["type"], "OBJECT")


def respuesta_ree(pvpc, spot=(50.0,)):
    """Respuesta de REData con el PVPC (id 1001) y el precio spot (id 600)."""
    def indicador(id_, valores):
        return {"id": id_, "attributes": {"values": [
            {"value": v, "datetime": "2026-10-01T00:00:00.000+02:00"} for v in valores
        ]}}
    return {"included": [indicador("600", spot), indicador("1001", pvpc)]}


class PrecioLuzTest(unittest.TestCase):

    def test_solo_lee_el_pvpc_y_descarta_valores_raros(self):
        r = respuesta_ree([120.0, None, "x", 130.5, True, float("nan"), float("inf")])
        self.assertEqual(main._valores_pvpc(r), [120.0, 130.5])
        self.assertEqual(main._valores_pvpc({}), [])

    def test_los_precios_negativos_o_cero_cuentan_en_la_media(self):
        # El PVPC baja de cero en horas solares de fin de semana (p. ej. 12/10/2026)
        r = respuesta_ree([120.0, -12.35, 0, 130.5])
        self.assertEqual(main._valores_pvpc(r), [120.0, -12.35, 0.0, 130.5])

    def test_precio_con_impuestos_en_eur_kwh(self):
        precio = main._precio_luz_desde_pvpc([120.0] * main.PRECIO_LUZ_MIN_HORAS)
        esperado = round(0.12 * (1 + main.IMPUESTO_ELECTRICIDAD) * (1 + main.IVA_LUZ), 4)
        self.assertEqual(precio, esperado)
        self.assertAlmostEqual(precio, 0.1526, places=4)

    def test_con_pocos_datos_o_precio_absurdo_no_se_actualiza(self):
        with self.assertRaises(main._PrecioLuzNoValido):
            main._precio_luz_desde_pvpc([120.0] * 24)
        with self.assertRaises(main._PrecioLuzNoValido):
            main._precio_luz_desde_pvpc([5000.0] * main.PRECIO_LUZ_MIN_HORAS)   # 6,4 €/kWh

    def test_las_semanas_cubren_los_dias_hasta_ayer_sin_solaparse(self):
        from datetime import date, timedelta
        hoy = date(2026, 10, 14)
        tramos = main._tramos_precio_luz(hoy)
        self.assertEqual(tramos[-1][1], hoy - timedelta(days=1))
        self.assertEqual(tramos[0][0], hoy - timedelta(days=main.PRECIO_LUZ_DIAS))
        dias = sum((fin - inicio).days + 1 for inicio, fin in tramos)
        self.assertEqual(dias, main.PRECIO_LUZ_DIAS)
        for (_, fin), (inicio, _) in zip(tramos, tramos[1:]):
            self.assertEqual(inicio, fin + timedelta(days=1))

    def test_solo_cambia_el_precio_de_la_luz_y_si_merece_la_pena(self):
        plantilla = {"parameters": {
            "precio_electricidad": {"defaultValue": {"value": "0.17"}, "valueType": "NUMBER", "description": "luz"},
            "precio_gas": {"defaultValue": {"value": "0.08"}, "valueType": "NUMBER"},
        }}
        self.assertFalse(main._aplicar_precio_luz(plantilla, 0.172))      # menos de medio céntimo
        self.assertEqual(plantilla["parameters"]["precio_electricidad"]["defaultValue"]["value"], "0.17")

        self.assertTrue(main._aplicar_precio_luz(plantilla, 0.1526))
        luz = plantilla["parameters"]["precio_electricidad"]
        self.assertEqual(luz["defaultValue"]["value"], "0.1526")
        self.assertEqual(luz["description"], "luz")
        self.assertEqual(plantilla["parameters"]["precio_gas"]["defaultValue"]["value"], "0.08")

    def test_crea_el_parametro_si_no_existe_o_usaba_el_valor_de_la_app(self):
        vacia = {}
        self.assertTrue(main._aplicar_precio_luz(vacia, 0.15))
        self.assertEqual(vacia["parameters"]["precio_electricidad"]["defaultValue"]["value"], "0.1500")
        con_defecto_de_app = {"parameters": {"precio_electricidad": {"defaultValue": {"useInAppDefault": True}}}}
        self.assertTrue(main._aplicar_precio_luz(con_defecto_de_app, 0.15))


class AutoresDelChatTest(unittest.TestCase):

    def test_solo_los_participantes_que_han_escrito(self):
        mensajes = [{"emisorUid": "cliente"}, {"emisorUid": "cliente"}, {"emisorUid": "intruso"}]
        self.assertEqual(main._autores_del_chat(mensajes, ["cliente", "pro"]), ["cliente"])
        mensajes.append({"emisorUid": "pro"})
        self.assertEqual(main._autores_del_chat(mensajes, ["cliente", "pro"]), ["cliente", "pro"])
        self.assertEqual(main._autores_del_chat([], ["cliente", "pro"]), [])


class _Peticion:
    """CallableRequest mínima: solo lo que miran las comprobaciones de sesión."""

    def __init__(self, uid=None, verificado=False):
        self.auth = None if uid is None else type("Auth", (), {
            "uid": uid, "token": {"email_verified": verificado},
        })()


class EmailVerificadoTest(unittest.TestCase):

    def codigo(self, peticion):
        with self.assertRaises(main.https_fn.HttpsError) as error:
            main._exigir_email_verificado(peticion)
        return error.exception.code

    def test_con_el_email_verificado_devuelve_el_uid(self):
        self.assertEqual(main._exigir_email_verificado(_Peticion("u1", verificado=True)), "u1")

    def test_sin_sesion_o_sin_verificar_no_pasa(self):
        self.assertEqual(self.codigo(_Peticion()), main.https_fn.FunctionsErrorCode.UNAUTHENTICATED)
        self.assertEqual(self.codigo(_Peticion("u1")), main.https_fn.FunctionsErrorCode.PERMISSION_DENIED)


# ── Firestore y Auth en memoria, para probar los flujos con transacciones ──

class _SnapMem:
    def __init__(self, ref, datos):
        self.reference, self.id, self._datos = ref, ref.id, datos
        self.exists = datos is not None
    def to_dict(self): return None if self._datos is None else dict(self._datos)


def _sin_borrados(datos):
    return {k: v for k, v in datos.items() if v is not main.firestore.DELETE_FIELD}


class _RefMem:
    def __init__(self, db, ruta):
        self._db, self.ruta, self.id = db, ruta, ruta.rsplit("/", 1)[-1]
    def get(self, transaction=None): return _SnapMem(self, self._db.docs.get(self.ruta))
    def set(self, datos, merge=False):
        base = dict(self._db.docs.get(self.ruta) or {}) if merge else {}
        base.update(datos)
        self._db.docs[self.ruta] = _sin_borrados(base)
    def update(self, datos):
        if self.ruta not in self._db.docs:
            raise NotFound(f"No existe {self.ruta}")
        self._db.docs[self.ruta] = _sin_borrados({**self._db.docs[self.ruta], **datos})


_OPERADORES = {"==": lambda a, b: a == b, ">": lambda a, b: a > b, "<": lambda a, b: a < b}


class _ConsultaMem:
    """Colección de primer nivel con filtros where(filter=FieldFilter(...))."""
    def __init__(self, db, nombre, filtros=()):
        self._db, self._nombre, self._filtros = db, nombre, list(filtros)
    def document(self, doc_id): return _RefMem(self._db, f"{self._nombre}/{doc_id}")
    def where(self, filter): return _ConsultaMem(self._db, self._nombre, self._filtros + [filter])
    def stream(self):
        prefijo = f"{self._nombre}/"
        for ruta, datos in list(self._db.docs.items()):
            if not ruta.startswith(prefijo) or "/" in ruta[len(prefijo):]:
                continue
            if all(f.field_path in datos and _OPERADORES[f.op_string](datos[f.field_path], f.value)
                   for f in self._filtros):
                yield _RefMem(self._db, ruta).get()


class _FirestoreMem:
    def __init__(self): self.docs = {}
    def collection(self, nombre): return _ConsultaMem(self, nombre)
    def transaction(self):
        return SimpleNamespace(set=lambda ref, datos, merge=False: ref.set(datos, merge=merge),
                               update=lambda ref, datos: ref.update(datos))


class _ConServiciosFalsos(unittest.TestCase):
    """Firestore y Auth en memoria y el correo capturado (nada sale a la red)."""

    def setUp(self):
        self.db = _FirestoreMem()
        self.usuarios = {}         # uid → SimpleNamespace(email, email_verified)
        self.correos = []          # destinatarios de los avisos enviados
        self.codigos = []          # (destino, código) de los emails de verificación
        self.update_user = mock.Mock()
        self.auth_caido = False     # True: Auth responde con un error inesperado
        for parche in (
            mock.patch.object(main.firestore, "transactional", lambda f: f),
            mock.patch.object(main.firestore, "client", lambda: self.db),
            mock.patch.object(main.auth, "get_user", self._get_user),
            mock.patch.object(main.auth, "update_user", self.update_user),
            mock.patch.object(main, "_enviar_email", lambda destino, *a, **k: self.correos.append(destino)),
            mock.patch.object(main, "_enviar_email_codigo", lambda destino, codigo, idioma: self.codigos.append((destino, codigo))),
        ):
            parche.start()
            self.addCleanup(parche.stop)

    def _get_user(self, uid):
        if self.auth_caido:
            raise RuntimeError("Auth no responde")
        if uid not in self.usuarios:
            raise main.auth.UserNotFoundError(f"No existe {uid}")
        return self.usuarios[uid]

    def usuario(self, uid, email, verificado):
        self.usuarios[uid] = SimpleNamespace(uid=uid, email=email, email_verified=verificado)

    @staticmethod
    def hoy():
        return time.strftime("%Y%m%d", time.gmtime())


class CupoCorreosTest(_ConServiciosFalsos):
    """Los avisos por email no pueden dejar sin correo a los códigos de verificación."""

    def aviso(self, uid, chat="c1"):
        main._enviar_aviso_email(self.db, uid, "Español", "mensaje", clave_limite=f"chat_{chat}",
                                 nombre="Ana", texto="hola")

    def test_con_el_cupo_de_avisos_gastado_los_codigos_siguen_saliendo(self):
        self.usuario("dest", "dest@x.es", True)
        self.db.docs[f"limites_correo/{self.hoy()}"] = {"enviados": 150, "avisos": 150}
        self.aviso("dest")
        self.assertEqual(self.correos, [])
        self.assertTrue(main._reservar_cupo_correo(self.db))

    def test_con_el_tope_total_gastado_no_sale_nada(self):
        self.usuario("dest", "dest@x.es", True)
        self.db.docs[f"limites_correo/{self.hoy()}"] = {"enviados": 300, "avisos": 10}
        self.aviso("dest")
        self.assertEqual(self.correos, [])
        self.assertFalse(main._reservar_cupo_correo(self.db))

    def test_un_aviso_cuenta_en_el_total_y_en_los_avisos(self):
        self.usuario("dest", "dest@x.es", True)
        self.aviso("dest")
        self.assertEqual(self.correos, ["dest@x.es"])
        self.assertEqual(self.db.docs[f"limites_correo/{self.hoy()}"], {"enviados": 1, "avisos": 1})

    def test_como_mucho_20_avisos_al_dia_a_la_misma_persona(self):
        self.usuario("dest", "dest@x.es", True)
        for i in range(21):
            self.aviso("dest", chat=f"c{i}")   # chats distintos: no los frena la espera de 30 min
        self.assertEqual(len(self.correos), 20)
        self.usuario("otra", "otra@x.es", True)
        self.aviso("otra")
        self.assertEqual(len(self.correos), 21)

    def test_el_tope_por_persona_se_reinicia_cada_dia(self):
        self.usuario("dest", "dest@x.es", True)
        self.db.docs["avisos_email/dest"] = {"dia": "20000101", "enviadosHoy": 20}
        self.aviso("dest")
        self.assertEqual(self.correos, ["dest@x.es"])

    def test_los_cupos_cambian_de_dia_a_medianoche_utc(self):
        reloj = [calendar.timegm((2026, 11, 16, 23, 59, 0)) * 1000]   # 23:59 UTC
        self.usuario("dest", "dest@x.es", True)
        self.db.docs["limites_correo/20261116"] = {"enviados": 150, "avisos": 150}
        self.db.docs["avisos_email/dest"] = {"dia": "20261116", "enviadosHoy": 3}
        with mock.patch.object(main, "_ahora_ms", lambda: reloj[0]):
            self.aviso("dest")
            self.assertEqual(self.correos, [])
            reloj[0] += 2 * 60 * 1000   # 00:01 UTC del día siguiente
            self.aviso("dest", chat="c2")
        self.assertEqual(self.correos, ["dest@x.es"])
        self.assertEqual(self.db.docs["limites_correo/20261117"], {"enviados": 1, "avisos": 1})
        self.assertEqual(self.db.docs["avisos_email/dest"]["dia"], "20261117")
        self.assertEqual(self.db.docs["avisos_email/dest"]["enviadosHoy"], 1)

    def test_un_chat_avisa_como_mucho_una_vez_cada_30_minutos(self):
        self.usuario("dest", "dest@x.es", True)
        self.aviso("dest", chat="c1")
        self.aviso("dest", chat="c1")
        self.assertEqual(self.correos, ["dest@x.es"])

    def test_con_el_tope_de_la_persona_gastado_no_se_anota_el_chat(self):
        self.usuario("dest", "dest@x.es", True)
        self.db.docs["avisos_email/dest"] = {"dia": self.hoy(), "enviadosHoy": 20}
        self.aviso("dest", chat="c9")
        self.assertEqual(self.correos, [])
        self.assertNotIn("chat_c9", self.db.docs["avisos_email/dest"])

    def test_al_anotar_un_chat_se_borran_las_esperas_ya_vencidas(self):
        self.usuario("dest", "dest@x.es", True)
        ahora = main._ahora_ms()
        self.db.docs["avisos_email/dest"] = {
            "chat_viejo": ahora - 31 * 60 * 1000, "chat_reciente": ahora - 5 * 60 * 1000,
        }
        self.aviso("dest", chat="nuevo")
        self.assertEqual(self.correos, ["dest@x.es"])
        claves = {k for k in self.db.docs["avisos_email/dest"] if k.startswith("chat_")}
        self.assertEqual(claves, {"chat_reciente", "chat_nuevo"})


def llamar(funcion, quien, admin=False, **datos):
    """Ejecuta una callable (sin el envoltorio HTTP) como el usuario `quien`."""
    peticion = SimpleNamespace(auth=SimpleNamespace(uid=quien, token={"admin": admin}), data=datos)
    return inspect.unwrap(funcion)(peticion)


class CodigoVerificacionTest(_ConServiciosFalsos):
    """El código solo verifica el email al que se envió."""

    def pedir_codigo(self, uid):
        self.assertTrue(llamar(main.enviar_codigo_verificacion, uid, idioma="Español")["enviado"])
        return self.codigos[-1][1]

    def verificar(self, uid, codigo):
        return llamar(main.verificar_codigo_email, uid, codigo=codigo)

    def test_el_codigo_verifica_la_cuenta_con_el_mismo_email(self):
        self.usuario("u1", "Ana@X.es", False)
        codigo = self.pedir_codigo("u1")
        self.assertEqual(self.db.docs["verificaciones_email/u1"]["email"], "ana@x.es")
        self.usuarios["u1"].email = "ana@x.es"   # mayúsculas aparte, es el mismo
        self.assertEqual(self.verificar("u1", codigo), {"verificado": True})
        self.update_user.assert_called_once_with("u1", email_verified=True)

    def test_si_el_email_de_la_cuenta_cambia_el_codigo_ya_no_vale(self):
        self.usuario("u1", "a@x.es", False)
        codigo = self.pedir_codigo("u1")
        self.usuarios["u1"].email = "b@x.es"
        with self.assertRaises(main.https_fn.HttpsError) as error:
            self.verificar("u1", codigo)
        self.assertEqual(error.exception.code, main.https_fn.FunctionsErrorCode.FAILED_PRECONDITION)
        self.update_user.assert_not_called()

    def test_si_el_email_cambia_mientras_se_verifica_se_deshace(self):
        self.usuario("u1", "a@x.es", False)
        self.db.docs["tecnicos/u1"] = {"uid": "u1", "emailVerificado": False}
        codigo = self.pedir_codigo("u1")

        def cambio_simultaneo(uid, **cambios):   # el email cambia justo al marcarlo
            if cambios.get("email_verified"):
                self.usuarios[uid].email = "b@x.es"
        self.update_user.side_effect = cambio_simultaneo
        with self.assertRaises(main.https_fn.HttpsError) as error:
            self.verificar("u1", codigo)
        self.assertEqual(error.exception.code, main.https_fn.FunctionsErrorCode.FAILED_PRECONDITION)
        self.assertEqual(error.exception.details, {"motivo": "caducado"})
        self.assertEqual(self.update_user.call_args_list,
                         [mock.call("u1", email_verified=True), mock.call("u1", email_verified=False)])
        self.assertIs(self.db.docs["tecnicos/u1"]["emailVerificado"], False)

    def test_un_codigo_emitido_sin_email_guardado_caduca_sin_gastar_intentos(self):
        # Códigos pedidos antes de ligar el código al email: la huella no lo incluía
        self.usuario("u1", "a@x.es", False)
        huella = hmac.new(main.SMTP_CLAVE.value.encode(), b"u1:123456", hashlib.sha256).hexdigest()
        self.db.docs["verificaciones_email/u1"] = {
            "huella": huella, "expira": main._ahora_ms() + 60_000, "intentos": 0,
        }
        with self.assertRaises(main.https_fn.HttpsError) as error:
            self.verificar("u1", "123456")
        self.assertEqual(error.exception.details, {"motivo": "caducado"})
        self.assertEqual(self.db.docs["verificaciones_email/u1"]["intentos"], 0)
        self.update_user.assert_not_called()


class PerfilEmailVerificadoTest(_ConServiciosFalsos):
    """`emailVerificado` de /tecnicos (oculta del directorio los perfiles de
    cuentas sin verificar): solo lo escribe el servidor."""

    def crear_perfil(self, uid, **datos):
        """Simula el alta de /tecnicos/{uid} y el trigger que la sigue."""
        self.db.docs[f"tecnicos/{uid}"] = {"id": uid, "uid": uid, "nombre": "Pro", **datos}
        evento = SimpleNamespace(data=_RefMem(self.db, f"tecnicos/{uid}").get(), params={"tecnicoId": uid})
        inspect.unwrap(main.on_tecnico_creado)(evento)
        return self.db.docs[f"tecnicos/{uid}"]

    def test_el_perfil_de_una_cuenta_sin_verificar_queda_oculto(self):
        self.usuario("p1", "p1@x.es", False)
        self.assertIs(self.crear_perfil("p1")["emailVerificado"], False)
        self.assertIs(self.crear_perfil("p1", emailVerificado=False)["emailVerificado"], False)

    def test_el_perfil_de_una_cuenta_verificada_queda_visible(self):
        self.usuario("p1", "p1@x.es", True)
        self.assertIs(self.crear_perfil("p1")["emailVerificado"], True)
        self.assertIs(self.crear_perfil("p1", emailVerificado=False)["emailVerificado"], True)

    def test_el_perfil_de_una_cuenta_que_no_existe_queda_oculto(self):
        self.assertIs(self.crear_perfil("fantasma")["emailVerificado"], False)

    def test_no_pisa_el_true_que_ya_puso_la_verificacion(self):
        self.usuario("p1", "p1@x.es", False)   # Auth leído antes de verificar
        self.assertIs(self.crear_perfil("p1", emailVerificado=True)["emailVerificado"], True)

    def test_al_verificar_el_codigo_el_perfil_se_hace_visible(self):
        self.usuario("p1", "p1@x.es", False)
        self.db.docs["tecnicos/p1"] = {"uid": "p1", "nombre": "Pro", "emailVerificado": False}
        llamar(main.enviar_codigo_verificacion, "p1", idioma="Español")
        self.assertEqual(llamar(main.verificar_codigo_email, "p1", codigo=self.codigos[-1][1]), {"verificado": True})
        self.assertEqual(self.db.docs["tecnicos/p1"], {"uid": "p1", "nombre": "Pro", "emailVerificado": True})

    def test_verificar_sin_perfil_profesional_no_falla_ni_lo_crea(self):
        self.usuario("c1", "c1@x.es", False)
        llamar(main.enviar_codigo_verificacion, "c1", idioma="Español")
        self.assertEqual(llamar(main.verificar_codigo_email, "c1", codigo=self.codigos[-1][1]), {"verificado": True})
        self.assertNotIn("tecnicos/c1", self.db.docs)

    def test_el_administrador_marca_el_email_y_el_perfil_se_hace_visible(self):
        self.usuario("p1", "p1@x.es", False)
        self.db.docs["tecnicos/p1"] = {"uid": "p1", "emailVerificado": False}
        self.assertEqual(llamar(main.marcar_email_verificado, "admin", admin=True, uid="p1"), {"ok": True})
        self.update_user.assert_called_once_with("p1", email_verified=True)
        self.assertIs(self.db.docs["tecnicos/p1"]["emailVerificado"], True)
        # Sin perfil profesional tampoco falla
        self.usuario("c1", "c1@x.es", False)
        self.assertEqual(llamar(main.marcar_email_verificado, "admin", admin=True, uid="c1"), {"ok": True})
        self.assertNotIn("tecnicos/c1", self.db.docs)


if __name__ == "__main__":
    unittest.main()
