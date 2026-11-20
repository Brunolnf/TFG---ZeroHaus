"""Tests del pago con retención (functions/pagos.py): parámetros de Remote
Config y máquina de estados, sin Stripe ni Firestore.

Uso, desde la raíz del repositorio y con las dependencias de functions/
instaladas:
    python -m unittest discover -s functions-tests -v
"""
import copy
import dataclasses
import json
import os
import sys
import unittest
from decimal import Decimal
from unittest import mock

# main.py declara los secretos SMTP; para importarlo basta con valores ficticios
os.environ.setdefault("SMTP_USUARIO", "tests@example.com")
os.environ.setdefault("SMTP_CLAVE", "tests")
RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, os.path.join(RAIZ, "functions"))

import main  # noqa: E402
import pagos  # noqa: E402

RUTA_PLANTILLA = os.path.join(RAIZ, "remoteconfig.template.json")
DIA_MS = 24 * 60 * 60 * 1000
AHORA = 1_790_000_000_000

# Copia literal de la tabla del plan (bloque E, T-E2): si alguien cambia
# pagos.TRANSICIONES sin cambiar el plan, este test lo para.
TABLA_ACORDADA = {
    ("solicitado", "aceptar"): ("pendiente_pago", {"cliente"}),
    ("solicitado", "cancelar"): ("cancelado", {"cliente", "profesional"}),
    ("pendiente_pago", "cobro_ok"): ("retenido", {"sistema"}),
    ("pendiente_pago", "cobro_fallido"): ("fallido", {"sistema"}),
    ("pendiente_pago", "cancelar"): ("cancelado", {"cliente", "profesional", "sistema"}),
    ("fallido", "reintentar"): ("pendiente_pago", {"cliente"}),
    ("fallido", "cancelar"): ("cancelado", {"cliente", "profesional"}),
    ("retenido", "terminar"): ("terminado", {"profesional"}),
    ("retenido", "disputar"): ("en_disputa", {"cliente"}),
    ("terminado", "confirmar"): ("liberado", {"cliente"}),
    ("terminado", "vencer_plazo"): ("liberado", {"sistema"}),
    ("terminado", "disputar"): ("en_disputa", {"cliente"}),
    ("en_disputa", "resolver_liberar"): ("liberado", {"admin"}),
    ("en_disputa", "resolver_reembolsar"): ("reembolsado", {"admin"}),
    ("retenido", "contracargo"): ("contracargo", {"sistema"}),
    ("terminado", "contracargo"): ("contracargo", {"sistema"}),
    ("en_disputa", "contracargo"): ("contracargo", {"sistema"}),
    ("liberado", "contracargo"): ("contracargo", {"sistema"}),
}
ESTADOS = {"solicitado", "pendiente_pago", "retenido", "terminado", "liberado",
           "cancelado", "fallido", "en_disputa", "reembolsado", "contracargo"}
EVENTOS = {"aceptar", "cancelar", "cobro_ok", "cobro_fallido", "reintentar", "terminar",
           "disputar", "confirmar", "vencer_plazo", "resolver_liberar", "resolver_reembolsar",
           "contracargo"}
ACTORES = ("cliente", "profesional", "admin", "sistema")
# uid con el que actúa cada actor sobre el pago de pago_en(): el suyo
UID = {"cliente": "cli", "profesional": "pro", "admin": "adm", "sistema": None}

PARAMETROS_PAGO = {
    "pagos_habilitados": ("BOOLEAN", "false"),
    "pago_comision_pct": ("NUMBER", "10"),
    "pago_liberacion_dias": ("NUMBER", "7"),
    "pago_importe_max_eur": ("NUMBER", "3000"),
}


def params(**cambios):
    """Parámetros válidos con los pagos encendidos."""
    base = dict(pagos_habilitados=True, pago_comision_pct=10,
                pago_liberacion_dias=7, pago_importe_max_eur=3000)
    return pagos.ParametrosPago(**{**base, **cambios})


def plantilla_con(**valores):
    """Plantilla de Remote Config con esos valores por defecto (texto, como los da la API)."""
    return {"parameters": {n: {"defaultValue": {"value": v}, "valueType": "NUMBER"}
                           for n, v in valores.items()}}


def leer_plantilla_archivo():
    with open(RUTA_PLANTILLA, encoding="utf-8") as f:
        return json.load(f)


def pago_nuevo(**cambios):
    datos = dict(pago_id="pago1", cliente="cli", profesional="pro", chat_id="chat1",
                 descripcion="Cambiar la caldera", importe_cent=1005, params=params(),
                 ahora_ms=AHORA - 10 * DIA_MS)
    return pagos.nuevo_pago(**{**datos, **cambios})


def pago_en(estado, **cambios):
    """Pago de 10,05 € en `estado`, con lo que ese estado tendría guardado."""
    p = pago_nuevo()
    p["estado"] = estado
    if estado in ("terminado", "liberado", "contracargo", "reembolsado"):
        p["terminadoEn"] = AHORA - 8 * DIA_MS
        p["liberarEn"] = AHORA - DIA_MS          # plazo ya vencido
    p.update(cambios)
    return p


def aplicar(pago, evento, actor, uid="por_defecto", ahora=AHORA, motivo="El trabajo no se hizo"):
    uid = UID[actor] if uid == "por_defecto" else uid
    return pagos.aplicar_transicion(pago, evento, actor, uid, ahora, motivo=motivo)


# ══ T-E1: parámetros de Remote Config ══

class PlantillaRemoteConfigTest(unittest.TestCase):

    def test_la_plantilla_trae_los_4_parametros_con_tipo_valor_y_descripcion(self):
        parametros = leer_plantilla_archivo()["parameters"]
        for nombre, (tipo, valor) in PARAMETROS_PAGO.items():
            with self.subTest(parametro=nombre):
                self.assertEqual(parametros[nombre]["valueType"], tipo)
                self.assertEqual(parametros[nombre]["defaultValue"], {"value": valor})
                descripcion = parametros[nombre]["description"]
                self.assertTrue(descripcion.strip())
                # Remote Config no acepta descripciones de más de 256 caracteres
                self.assertLessEqual(len(descripcion), 256)

    def test_los_valores_de_la_plantilla_son_los_seguros_del_servidor(self):
        leidos = pagos.parametros_desde_plantilla(leer_plantilla_archivo())
        self.assertEqual(leidos, pagos.PARAMETROS_SEGUROS)
        self.assertFalse(leidos.pagos_habilitados)

    def test_la_tarea_del_precio_de_la_luz_no_pisa_ni_borra_los_parametros_de_pago(self):
        # Plantilla en vigor con los pagos encendidos: la tarea diaria la baja
        # entera, cambia el precio y la vuelve a subir.
        en_vigor = leer_plantilla_archivo()
        en_vigor["parameters"]["pagos_habilitados"]["defaultValue"] = {"value": "true"}
        en_vigor["parameters"]["pago_comision_pct"]["defaultValue"] = {"value": "12.5"}
        en_vigor["parameterGroups"] = {"otros": {"parameters": {
            "aviso": {"defaultValue": {"value": "hola"}, "valueType": "STRING"}}}}
        en_vigor["conditions"] = [{"name": "android", "expression": "device.os == 'android'"}]
        enviadas = []

        class Respuesta:
            def __init__(self, cuerpo=None): self._cuerpo, self.headers = cuerpo, {"ETag": "etag-1"}
            def raise_for_status(self): pass
            def json(self): return copy.deepcopy(self._cuerpo)

        class Sesion:
            def __init__(self, _creds): pass
            def get(self, url, timeout): return Respuesta(en_vigor)
            def put(self, url, data, headers, timeout):
                enviadas.append((json.loads(data), headers))
                return Respuesta()

        with mock.patch.object(main, "default_creds", lambda scopes: (None, None)), \
                mock.patch.object(main, "AuthorizedSession", Sesion):
            self.assertTrue(main._publicar_precio_luz(0.2, 600))

        (subida, cabeceras), = enviadas
        self.assertEqual(cabeceras["If-Match"], "etag-1")
        self.assertEqual(subida["parameters"]["precio_electricidad"]["defaultValue"]["value"], "0.2000")
        for nombre in PARAMETROS_PAGO:
            self.assertEqual(subida["parameters"][nombre], en_vigor["parameters"][nombre])
        self.assertEqual(subida["parameterGroups"], en_vigor["parameterGroups"])
        self.assertEqual(subida["conditions"], en_vigor["conditions"])
        self.assertEqual(pagos.parametros_desde_plantilla(subida), params(pago_comision_pct=12.5))

    def test_un_despliegue_completo_no_publica_la_plantilla(self):
        # Con el bloque remoteconfig, `firebase deploy` sustituiría la plantilla
        # en vigor por el archivo y apagaría los pagos en producción
        with open(os.path.join(RAIZ, "firebase.json"), encoding="utf-8") as f:
            self.assertNotIn("remoteconfig", json.load(f))


class LeerParametrosPagoTest(unittest.TestCase):

    def setUp(self):
        pagos.borrar_cache_parametros()
        self.addCleanup(pagos.borrar_cache_parametros)
        self.reloj = [1000.0]

    def leer(self, plantilla):
        with mock.patch("builtins.print"):
            return pagos.leer_parametros_pago(leer_plantilla=lambda: plantilla,
                                              reloj=lambda: self.reloj[0])

    def test_lee_los_valores_validos(self):
        leidos = self.leer(plantilla_con(pagos_habilitados="true", pago_comision_pct="12.5",
                                         pago_liberacion_dias="14", pago_importe_max_eur="5000"))
        self.assertEqual(leidos, params(pago_comision_pct=12.5, pago_liberacion_dias=14,
                                        pago_importe_max_eur=5000))
        self.assertIsInstance(leidos.pago_liberacion_dias, int)
        self.assertIsInstance(leidos.pago_importe_max_eur, int)
        self.assertEqual(leidos.importe_max_cent, 500_000)

    def test_es_un_dataclass_inmutable(self):
        leidos = self.leer(plantilla_con(pagos_habilitados="true"))
        with self.assertRaises(dataclasses.FrozenInstanceError):
            leidos.pagos_habilitados = False
        with self.assertRaises(dataclasses.FrozenInstanceError):
            pagos.PARAMETROS_SEGUROS.pagos_habilitados = True

    def test_los_limites_de_cada_rango_son_validos(self):
        casos = [
            ("pago_comision_pct", "0", 0), ("pago_comision_pct", "30", 30),
            ("pago_comision_pct", "1.13", 1.13), ("pago_comision_pct", "7.0", 7),
            ("pago_liberacion_dias", "1", 1), ("pago_liberacion_dias", "30", 30),
            ("pago_importe_max_eur", "10", 10), ("pago_importe_max_eur", "10000", 10000),
        ]
        for nombre, texto, valor in casos:
            with self.subTest(parametro=nombre, valor=texto):
                pagos.borrar_cache_parametros()
                leidos = self.leer(plantilla_con(pagos_habilitados="true", **{nombre: texto}))
                self.assertTrue(leidos.pagos_habilitados)
                self.assertEqual(getattr(leidos, nombre), valor)

    def test_un_parametro_ausente_usa_su_valor_por_defecto(self):
        self.assertEqual(self.leer({}), pagos.PARAMETROS_SEGUROS)
        pagos.borrar_cache_parametros()
        self.assertEqual(self.leer(plantilla_con(pagos_habilitados="true")), params())
        pagos.borrar_cache_parametros()
        # «Usar el valor predeterminado de la app»: el servidor no tiene valor
        sin_valor = plantilla_con(pagos_habilitados="true")
        sin_valor["parameters"]["pago_comision_pct"] = {"defaultValue": {"useInAppDefault": True}}
        self.assertEqual(self.leer(sin_valor), params())

    def test_tambien_lee_los_parametros_agrupados_en_la_consola(self):
        plantilla = {"parameterGroups": {"pagos": {
            "parameters": plantilla_con(pagos_habilitados="true", pago_liberacion_dias="3")["parameters"]}}}
        self.assertEqual(self.leer(plantilla), params(pago_liberacion_dias=3))

    def test_las_condiciones_no_cambian_lo_que_aplica_el_servidor(self):
        plantilla = plantilla_con(pagos_habilitados="false")
        plantilla["parameters"]["pagos_habilitados"]["conditionalValues"] = {"beta": {"value": "true"}}
        self.assertFalse(self.leer(plantilla).pagos_habilitados)

    def test_un_valor_no_valido_apaga_los_pagos_y_deja_los_valores_por_defecto(self):
        malos = {
            "pagos_habilitados": ["yes", "1", "TRUE", "verdadero", ""],
            "pago_comision_pct": ["-1", "30.01", "31", "abc", "", "nan", "inf", "1e1", "10.123",
                                  "10,5", "0x10", "1_0", " ", "+5", "١٠"],
            "pago_liberacion_dias": ["0", "31", "7.5", "-7", "siete", "1e1"],
            # Dígitos de otros alfabetos: int() y Fraction() los aceptarían
            "pago_importe_max_eur": ["9", "10001", "3000.5", "-3000", "∞", "３０００"],
        }
        for nombre, valores in malos.items():
            for valor in valores:
                with self.subTest(parametro=nombre, valor=valor):
                    pagos.borrar_cache_parametros()
                    buenos = dict(pagos_habilitados="true", pago_comision_pct="15",
                                  pago_liberacion_dias="3", pago_importe_max_eur="500")
                    leidos = self.leer(plantilla_con(**{**buenos, nombre: valor}))
                    self.assertEqual(leidos, pagos.PARAMETROS_SEGUROS)

    def test_una_plantilla_con_forma_inesperada_apaga_los_pagos(self):
        encendidos = plantilla_con(pagos_habilitados="true")["parameters"]
        casos = [
            None, [], "plantilla", {"parameters": ["no es un objeto"]},
            {"parameters": {**encendidos, "pago_comision_pct": "10"}},
            {"parameters": {**encendidos, "pago_comision_pct": {"defaultValue": "10"}}},
            {"parameters": {**encendidos, "pago_comision_pct": {"defaultValue": {"value": 10}}}},
            {"parameters": encendidos, "parameterGroups": ["no es un objeto"]},
            {"parameters": encendidos, "parameterGroups": {"g": {"parameters": "x"}}},
        ]
        for plantilla in casos:
            with self.subTest(plantilla=plantilla):
                pagos.borrar_cache_parametros()
                self.assertEqual(self.leer(plantilla), pagos.PARAMETROS_SEGUROS)

    def test_si_la_lectura_falla_los_pagos_quedan_apagados(self):
        for error in (OSError("sin red"), ValueError("JSON roto"), RuntimeError("403")):
            with self.subTest(error=error):
                pagos.borrar_cache_parametros()

                def falla():
                    raise error
                with mock.patch("builtins.print") as log:
                    leidos = pagos.leer_parametros_pago(leer_plantilla=falla, reloj=lambda: 0.0)
                self.assertEqual(leidos, pagos.PARAMETROS_SEGUROS)
                self.assertIn("[PAGOS]", log.call_args[0][0])

    def test_cache_de_5_minutos(self):
        lecturas = []
        respuestas = [plantilla_con(pagos_habilitados="true"),
                      plantilla_con(pagos_habilitados="true", pago_comision_pct="20")]

        def lector():
            lecturas.append(self.reloj[0])
            return respuestas[len(lecturas) - 1]

        def leer():
            return pagos.leer_parametros_pago(leer_plantilla=lector, reloj=lambda: self.reloj[0])

        primera = leer()
        self.assertEqual(primera.pago_comision_pct, 10)
        self.reloj[0] += 5 * 60 - 0.5
        self.assertIs(leer(), primera)
        self.assertEqual(len(lecturas), 1)
        self.reloj[0] += 0.5                      # justo a los 5 minutos se vuelve a leer
        self.assertEqual(leer().pago_comision_pct, 20)
        self.assertEqual(len(lecturas), 2)

    def test_un_fallo_se_guarda_solo_un_minuto(self):
        lecturas = []

        def lector():
            lecturas.append(self.reloj[0])
            if len(lecturas) == 1:
                raise OSError("Remote Config no responde")
            return plantilla_con(pagos_habilitados="true")

        def leer():
            with mock.patch("builtins.print"):
                return pagos.leer_parametros_pago(leer_plantilla=lector, reloj=lambda: self.reloj[0])

        self.assertFalse(leer().pagos_habilitados)
        self.reloj[0] += 59
        self.assertFalse(leer().pagos_habilitados)   # sin consultar la API en cada llamada
        self.assertEqual(len(lecturas), 1)
        self.reloj[0] += 1
        self.assertTrue(leer().pagos_habilitados)
        self.assertEqual(len(lecturas), 2)

    def test_por_defecto_lee_la_plantilla_del_proyecto_con_la_api_de_remote_config(self):
        pedidas = []

        class Sesion:
            def __init__(self, creds): self.creds = creds
            def get(self, url, timeout):
                pedidas.append((url, timeout))
                return SimpleRespuesta()

        class SimpleRespuesta:
            def raise_for_status(self): pass
            def json(self): return plantilla_con(pagos_habilitados="true")

        def creds(scopes):
            self.assertEqual(scopes, ["https://www.googleapis.com/auth/firebase.remoteconfig"])
            return object(), "zerohaus-2a865"

        with mock.patch.object(pagos, "default_creds", creds), \
                mock.patch.object(pagos, "AuthorizedSession", Sesion):
            self.assertTrue(pagos.leer_parametros_pago().pagos_habilitados)
        (url, timeout), = pedidas
        self.assertEqual(url, main.REMOTE_CONFIG_URL)
        self.assertLessEqual(timeout, 10)

    def test_los_parametros_se_validan_tambien_al_crearlos_a_mano(self):
        malos = [
            dict(pagos_habilitados="true"), dict(pago_comision_pct=31), dict(pago_comision_pct=-1),
            dict(pago_comision_pct=True), dict(pago_comision_pct=float("nan")),
            dict(pago_comision_pct=10.123), dict(pago_liberacion_dias=0), dict(pago_liberacion_dias=7.0),
            dict(pago_importe_max_eur=9), dict(pago_importe_max_eur=10001), dict(pago_importe_max_eur="3000"),
        ]
        for cambios in malos:
            with self.subTest(cambios=cambios), self.assertRaises(ValueError):
                params(**cambios)


# ══ T-E2: importes ══

class ImportesTest(unittest.TestCase):

    def test_el_minimo_es_fijo_10_euros(self):
        self.assertEqual(pagos.IMPORTE_MIN_CENT, 1000)

    def test_comision_redondeada_a_la_mitad_hacia_arriba(self):
        casos = [
            (1005, 10, 101), (1004, 10, 100), (1015, 10, 102), (1000, 10, 100),
            (300_000, 10, 30_000), (1000, 0, 0), (1000, 30, 300),
            (1000, 12.5, 125), (1004, 12.5, 126), (1001, 12.5, 125),
            # 5000 × 1,13 % = 56,5 justos; con float sale 56,4999… y se perdería el céntimo
            (5000, 1.13, 57), (11_000, 0.35, 39),
        ]
        for importe, pct, comision in casos:
            with self.subTest(importe=importe, pct=pct):
                r = pagos.calcular_importes(importe, params(pago_comision_pct=pct))
                self.assertEqual(r.importe_cent, importe)
                self.assertEqual(r.comision_cent, comision)
                self.assertEqual(r.neto_profesional_cent, importe - comision)
                for valor in (r.importe_cent, r.comision_cent, r.neto_profesional_cent):
                    self.assertIs(type(valor), int)

    def test_limites_del_importe(self):
        self.assertEqual(pagos.calcular_importes(1000, params()).importe_cent, 1000)
        self.assertEqual(pagos.calcular_importes(300_000, params()).importe_cent, 300_000)
        self.assertEqual(pagos.calcular_importes(1000, params(pago_importe_max_eur=10)).importe_cent, 1000)
        casos = [
            (999, params(), "importe_bajo_minimo"), (0, params(), "importe_bajo_minimo"),
            (-5000, params(), "importe_bajo_minimo"),
            (300_001, params(), "importe_sobre_maximo"),
            (1001, params(pago_importe_max_eur=10), "importe_sobre_maximo"),
        ]
        for importe, p, codigo in casos:
            with self.subTest(importe=importe), self.assertRaises(pagos.ErrorPago) as error:
                pagos.calcular_importes(importe, p)
            self.assertEqual(error.exception.codigo, codigo)

    def test_solo_importes_enteros_en_centimos(self):
        for importe in (1000.0, 1500.5, "1000", True, None, Decimal("1000"), [1000]):
            with self.subTest(importe=importe), self.assertRaises(pagos.ErrorPago) as error:
                pagos.calcular_importes(importe, params())
            self.assertEqual(error.exception.codigo, "importe_no_entero")

    def test_con_los_pagos_apagados_se_rechaza_todo(self):
        for p in (params(pagos_habilitados=False), pagos.PARAMETROS_SEGUROS):
            with self.subTest(params=p), self.assertRaises(pagos.ErrorPago) as error:
                pagos.calcular_importes(5000, p)
            self.assertEqual(error.exception.codigo, "pagos_deshabilitados")

    def test_el_error_es_un_value_error_con_codigo(self):
        with self.assertRaises(ValueError) as error:
            pagos.calcular_importes(1, params())
        self.assertEqual(error.exception.codigo, "importe_bajo_minimo")


# ══ T-E2: documento inicial ══

class NuevoPagoTest(unittest.TestCase):

    def test_esquema_exacto_en_solicitado(self):
        p = pago_nuevo(ahora_ms=AHORA)
        self.assertEqual(set(p), {
            "id", "cliente", "profesional", "chatId", "descripcion", "importeCent", "comisionCent",
            "netoCent", "moneda", "comisionPct", "liberacionDias", "estado", "historial", "creado",
            "actualizado", "stripePaymentIntentId", "stripeTransferId", "stripeRefundId",
            "terminadoEn", "liberarEn", "disputa",
        })
        self.assertEqual(p, {
            "id": "pago1", "cliente": "cli", "profesional": "pro", "chatId": "chat1",
            "descripcion": "Cambiar la caldera",
            "importeCent": 1005, "comisionCent": 101, "netoCent": 904, "moneda": "eur",
            "comisionPct": 10, "liberacionDias": 7,
            "estado": "solicitado",
            "historial": [{"estado": "solicitado", "evento": "solicitar", "actor": "profesional",
                           "uid": "pro", "fecha": AHORA}],
            "creado": AHORA, "actualizado": AHORA,
            "stripePaymentIntentId": None, "stripeTransferId": None, "stripeRefundId": None,
            "terminadoEn": None, "liberarEn": None, "disputa": None,
        })

    def test_guarda_la_comision_y_los_dias_del_momento_de_crearlo(self):
        p = pago_nuevo(importe_cent=2000, params=params(pago_comision_pct=15, pago_liberacion_dias=3))
        self.assertEqual((p["comisionPct"], p["liberacionDias"]), (15, 3))
        self.assertEqual((p["comisionCent"], p["netoCent"]), (300, 1700))

    def test_la_descripcion_se_recorta_y_tiene_tope(self):
        self.assertEqual(pago_nuevo(descripcion="  Caldera  ")["descripcion"], "Caldera")
        self.assertEqual(len(pago_nuevo(descripcion="d" * 500)["descripcion"]), 500)
        for descripcion in ("", "   ", "d" * 501, None, 5, ["x"]):
            with self.subTest(descripcion=descripcion), self.assertRaises(pagos.ErrorPago) as error:
                pago_nuevo(descripcion=descripcion)
            self.assertEqual(error.exception.codigo, "descripcion_no_valida")

    def test_datos_no_validos_se_rechazan(self):
        casos = [
            (dict(profesional="cli"), "partes_no_validas"),
            (dict(cliente=""), "id_no_valido"), (dict(profesional=None), "id_no_valido"),
            (dict(chat_id=""), "id_no_valido"), (dict(pago_id="a/b"), "id_no_valido"),
            (dict(pago_id="x" * 129), "id_no_valido"), (dict(cliente=7), "id_no_valido"),
            (dict(ahora_ms=1.5), "fecha_no_valida"), (dict(ahora_ms=None), "fecha_no_valida"),
            (dict(importe_cent=300_001), "importe_sobre_maximo"),
            (dict(importe_cent=999), "importe_bajo_minimo"),
            (dict(params=pagos.PARAMETROS_SEGUROS), "pagos_deshabilitados"),
        ]
        for cambios, codigo in casos:
            with self.subTest(cambios=cambios), self.assertRaises(pagos.ErrorPago) as error:
                pago_nuevo(**cambios)
            self.assertEqual(error.exception.codigo, codigo)


# ══ T-E2: máquina de estados ══

class TablaTransicionesTest(unittest.TestCase):

    def test_la_tabla_es_exactamente_la_acordada(self):
        self.assertEqual({k: (d, set(a)) for k, (d, a) in pagos.TRANSICIONES.items()}, TABLA_ACORDADA)
        self.assertEqual(set(pagos.ESTADOS), ESTADOS)
        self.assertEqual(set(pagos.EVENTOS), EVENTOS)
        self.assertEqual(set(pagos.ACTORES), set(ACTORES))
        self.assertEqual(set(pagos.ESTADOS_FINALES), {"cancelado", "liberado", "reembolsado", "contracargo"})

    def test_la_tabla_no_se_puede_cambiar_en_marcha(self):
        with self.assertRaises(TypeError):
            pagos.TRANSICIONES[("liberado", "reintentar")] = ("pendiente_pago", frozenset({"cliente"}))
        destino, actores = pagos.TRANSICIONES[("solicitado", "aceptar")]
        with self.assertRaises(AttributeError):
            actores.add("profesional")

    def test_de_un_estado_final_solo_se_sale_de_liberado_a_contracargo(self):
        salidas = {k for k in pagos.TRANSICIONES if k[0] in pagos.ESTADOS_FINALES}
        self.assertEqual(salidas, {("liberado", "contracargo")})

    def test_cada_transicion_permitida_funciona_con_cada_actor_permitido(self):
        for (origen, evento), (destino, actores) in TABLA_ACORDADA.items():
            for actor in actores:
                with self.subTest(origen=origen, evento=evento, actor=actor):
                    pago = pago_en(origen)
                    antes = copy.deepcopy(pago)
                    nuevo = aplicar(pago, evento, actor)
                    self.assertEqual(pago, antes)                # el de entrada no cambia
                    self.assertEqual(nuevo["estado"], destino)
                    self.assertEqual(nuevo["actualizado"], AHORA)
                    self.assertEqual(nuevo["historial"][:-1], antes["historial"])
                    self.assertEqual(nuevo["historial"][-1], {
                        "estado": destino, "evento": evento, "actor": actor,
                        "uid": UID[actor], "fecha": AHORA,
                    })
                    self.assertEqual(set(nuevo), set(antes))     # el esquema no cambia

    def test_toda_combinacion_fuera_de_la_tabla_se_rechaza(self):
        rechazadas = 0
        for origen in sorted(ESTADOS):
            for evento in sorted(EVENTOS):
                for actor in ACTORES:
                    regla = TABLA_ACORDADA.get((origen, evento))
                    if regla is not None and actor in regla[1]:
                        continue
                    codigo = "transicion_no_permitida" if regla is None else "actor_no_permitido"
                    with self.subTest(origen=origen, evento=evento, actor=actor):
                        pago = pago_en(origen)
                        antes = copy.deepcopy(pago)
                        with self.assertRaises(pagos.ErrorPago) as error:
                            aplicar(pago, evento, actor)
                        self.assertEqual(error.exception.codigo, codigo)
                        self.assertEqual(pago, antes)
                    rechazadas += 1
        permitidas = sum(len(actores) for _, actores in TABLA_ACORDADA.values())
        self.assertEqual(rechazadas + permitidas, len(ESTADOS) * len(EVENTOS) * len(ACTORES))

    def test_estados_eventos_y_actores_desconocidos(self):
        casos = [
            (pago_en("inventado"), "aceptar", "cliente", "transicion_no_permitida"),
            (pago_en("solicitado"), "pagar", "cliente", "transicion_no_permitida"),
            (pago_en("solicitado"), "aceptar", "hacker", "actor_no_permitido"),
            (pago_en("solicitado"), "aceptar", "Cliente", "actor_no_permitido"),
            ({**pago_en("solicitado"), "estado": ["solicitado"]}, "aceptar", "cliente", "transicion_no_permitida"),
            (pago_en("solicitado"), ["aceptar"], "cliente", "transicion_no_permitida"),
            (pago_en("solicitado"), "aceptar", ["cliente"], "actor_no_permitido"),
        ]
        for pago, evento, actor, codigo in casos:
            with self.subTest(evento=evento, actor=actor), self.assertRaises(pagos.ErrorPago) as error:
                pagos.aplicar_transicion(pago, evento, actor, "cli", AHORA)
            self.assertEqual(error.exception.codigo, codigo)

    def test_un_pago_que_no_es_un_objeto_se_rechaza(self):
        for pago in (None, [], "pago"):
            with self.subTest(pago=pago), self.assertRaises(pagos.ErrorPago) as error:
                pagos.aplicar_transicion(pago, "aceptar", "cliente", "cli", AHORA)
            self.assertEqual(error.exception.codigo, "pago_no_valido")
        with self.assertRaises(pagos.ErrorPago) as error:
            aplicar(pago_en("solicitado", historial="no es una lista"), "aceptar", "cliente")
        self.assertEqual(error.exception.codigo, "pago_no_valido")


class PartesDelPagoTest(unittest.TestCase):

    def test_solo_el_cliente_y_el_profesional_de_ese_pago(self):
        for (origen, evento), (_, actores) in TABLA_ACORDADA.items():
            for actor in actores & {"cliente", "profesional"}:
                otro = "pro" if actor == "cliente" else "cli"
                for uid in ("intruso", otro, "adm", "", None, 7):
                    with self.subTest(origen=origen, evento=evento, actor=actor, uid=uid), \
                            self.assertRaises(pagos.ErrorPago) as error:
                        aplicar(pago_en(origen), evento, actor, uid=uid)
                    self.assertEqual(error.exception.codigo, "uid_no_autorizado")

    def test_un_pago_sin_partes_no_deja_actuar_a_nadie(self):
        for campo, actor in (("cliente", "cliente"), ("profesional", "profesional")):
            with self.subTest(campo=campo), self.assertRaises(pagos.ErrorPago) as error:
                aplicar(pago_en("solicitado", **{campo: ""}), "cancelar", actor, uid="")
            self.assertEqual(error.exception.codigo, "uid_no_autorizado")

    def test_el_administrador_necesita_uid_y_el_sistema_no_lleva(self):
        disputa = pago_en("en_disputa")
        for uid in ("", None, 7):
            with self.subTest(uid=uid), self.assertRaises(pagos.ErrorPago) as error:
                aplicar(disputa, "resolver_reembolsar", "admin", uid=uid)
            self.assertEqual(error.exception.codigo, "uid_no_autorizado")
        # Un uid de usuario no puede hacerse pasar por el webhook o la tarea programada
        with self.assertRaises(pagos.ErrorPago) as error:
            aplicar(pago_en("pendiente_pago"), "cobro_ok", "sistema", uid="cli")
        self.assertEqual(error.exception.codigo, "uid_no_autorizado")

    def test_la_fecha_tiene_que_ser_un_entero_en_ms(self):
        for ahora in (None, 1.5, "1790000000000", True, 0, -1):
            with self.subTest(ahora=ahora), self.assertRaises(pagos.ErrorPago) as error:
                aplicar(pago_en("solicitado"), "aceptar", "cliente", ahora=ahora)
            self.assertEqual(error.exception.codigo, "fecha_no_valida")


class EfectosDeLasTransicionesTest(unittest.TestCase):

    def test_terminar_fija_la_liberacion_con_los_dias_guardados_en_el_pago(self):
        pago = pago_en("retenido", liberacionDias=3)
        # Remote Config dice ahora otra cosa: no debe consultarse
        with mock.patch.object(pagos, "leer_parametros_pago", side_effect=AssertionError("no")):
            nuevo = aplicar(pago, "terminar", "profesional")
        self.assertEqual(nuevo["terminadoEn"], AHORA)
        self.assertEqual(nuevo["liberarEn"], AHORA + 3 * DIA_MS)
        self.assertIsNone(pago["terminadoEn"])

    def test_dias_de_liberacion_corruptos_en_el_pago_se_rechazan(self):
        for dias in (None, 0, 31, "7", 7.0, True):
            with self.subTest(dias=dias), self.assertRaises(pagos.ErrorPago) as error:
                aplicar(pago_en("retenido", liberacionDias=dias), "terminar", "profesional")
            self.assertEqual(error.exception.codigo, "pago_no_valido")

    def test_disputar_guarda_el_motivo_y_para_la_liberacion_automatica(self):
        nuevo = aplicar(pago_en("terminado"), "disputar", "cliente", motivo="  No terminó la obra  ")
        self.assertEqual(nuevo["disputa"], {"motivo": "No terminó la obra", "fecha": AHORA})
        self.assertIsNone(nuevo["liberarEn"])
        self.assertEqual(nuevo["terminadoEn"], AHORA - 8 * DIA_MS)
        nuevo = aplicar(pago_en("retenido"), "disputar", "cliente", motivo="m" * 500)
        self.assertEqual(nuevo["disputa"]["motivo"], "m" * 500)

    def test_disputar_sin_motivo_valido_se_rechaza(self):
        for motivo in (None, "", "   ", "m" * 501, 5, ["motivo"]):
            with self.subTest(motivo=motivo), self.assertRaises(pagos.ErrorPago) as error:
                aplicar(pago_en("retenido"), "disputar", "cliente", motivo=motivo)
            self.assertEqual(error.exception.codigo, "motivo_no_valido")

    def test_vencer_plazo_solo_cuando_ha_vencido(self):
        self.assertEqual(aplicar(pago_en("terminado", liberarEn=AHORA), "vencer_plazo", "sistema")["estado"],
                         "liberado")
        with self.assertRaises(pagos.ErrorPago) as error:
            aplicar(pago_en("terminado", liberarEn=AHORA + 1), "vencer_plazo", "sistema")
        self.assertEqual(error.exception.codigo, "plazo_no_vencido")
        for liberar_en in (None, "mañana", 1.5):
            with self.subTest(liberarEn=liberar_en), self.assertRaises(pagos.ErrorPago) as error:
                aplicar(pago_en("terminado", liberarEn=liberar_en), "vencer_plazo", "sistema")
            self.assertEqual(error.exception.codigo, "pago_no_valido")

    def test_el_pago_nuevo_no_comparte_listas_ni_objetos_con_el_de_entrada(self):
        pago = pago_en("terminado", disputa=None)
        nuevo = aplicar(pago, "disputar", "cliente")
        nuevo["historial"][0]["evento"] = "cambiado"
        self.assertEqual(pago["historial"][0]["evento"], "solicitar")
        self.assertEqual(len(pago["historial"]), 1)


class EscenariosTest(unittest.TestCase):
    """Escenarios obligatorios del plan que no dependen de Stripe."""

    def recorrer(self, pago, pasos):
        ahora = AHORA
        for evento, actor in pasos:
            ahora += DIA_MS
            pago = aplicar(pago, evento, actor, ahora=ahora)
        return pago, ahora

    def eventos(self, pago):
        return [h["evento"] for h in pago["historial"]]

    def test_pago_retencion_y_liberacion_por_confirmacion(self):
        pago, _ = self.recorrer(pago_nuevo(), [
            ("aceptar", "cliente"), ("cobro_ok", "sistema"), ("terminar", "profesional"),
            ("confirmar", "cliente"),
        ])
        self.assertEqual(pago["estado"], "liberado")
        self.assertEqual(self.eventos(pago), ["solicitar", "aceptar", "cobro_ok", "terminar", "confirmar"])
        self.assertEqual((pago["importeCent"], pago["comisionCent"], pago["netoCent"]), (1005, 101, 904))

    def test_liberacion_automatica_al_vencer_el_plazo(self):
        pago, ahora = self.recorrer(pago_nuevo(), [
            ("aceptar", "cliente"), ("cobro_ok", "sistema"), ("terminar", "profesional"),
        ])
        self.assertEqual(pago["liberarEn"], ahora + 7 * DIA_MS)
        with self.assertRaises(pagos.ErrorPago):
            aplicar(pago, "vencer_plazo", "sistema", ahora=pago["liberarEn"] - 1)
        pago = aplicar(pago, "vencer_plazo", "sistema", ahora=pago["liberarEn"])
        self.assertEqual(pago["estado"], "liberado")

    def test_disputa_y_reembolso(self):
        pago, _ = self.recorrer(pago_nuevo(), [
            ("aceptar", "cliente"), ("cobro_ok", "sistema"), ("terminar", "profesional"),
            ("disputar", "cliente"), ("resolver_reembolsar", "admin"),
        ])
        self.assertEqual(pago["estado"], "reembolsado")
        self.assertEqual(pago["disputa"]["motivo"], "El trabajo no se hizo")
        self.assertEqual(pago["historial"][-1]["uid"], "adm")

    def test_cancelacion_antes_de_pagar(self):
        for pasos in ([("cancelar", "profesional")],
                      [("aceptar", "cliente"), ("cancelar", "cliente")],
                      [("aceptar", "cliente"), ("cobro_fallido", "sistema"), ("cancelar", "cliente")]):
            with self.subTest(pasos=pasos):
                pago, _ = self.recorrer(pago_nuevo(), pasos)
                self.assertEqual(pago["estado"], "cancelado")
                with self.assertRaises(pagos.ErrorPago):
                    aplicar(pago, "aceptar", "cliente")

    def test_cobro_fallido_y_reintento(self):
        pago, _ = self.recorrer(pago_nuevo(), [
            ("aceptar", "cliente"), ("cobro_fallido", "sistema"), ("reintentar", "cliente"),
            ("cobro_ok", "sistema"),
        ])
        self.assertEqual(pago["estado"], "retenido")

    def test_contracargo_tras_liberar(self):
        pago, _ = self.recorrer(pago_nuevo(), [
            ("aceptar", "cliente"), ("cobro_ok", "sistema"), ("terminar", "profesional"),
            ("confirmar", "cliente"), ("contracargo", "sistema"),
        ])
        self.assertEqual(pago["estado"], "contracargo")

    def test_importe_por_encima_del_maximo_rechazado(self):
        with self.assertRaises(pagos.ErrorPago) as error:
            pago_nuevo(importe_cent=300_001)
        self.assertEqual(error.exception.codigo, "importe_sobre_maximo")


if __name__ == "__main__":
    unittest.main()
