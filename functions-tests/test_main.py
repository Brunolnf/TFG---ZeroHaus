"""Tests de la lógica pura de functions/main.py (sin Firebase ni Gemini).

Uso, desde la raíz del repositorio y con las dependencias de functions/
instaladas:
    python -m unittest discover -s functions-tests -v

Están fuera de functions/ para que no se suban con el despliegue.
"""
import json
import os
import sys
import unittest

# main.py declara los secretos SMTP; para importarlo basta con valores ficticios
os.environ.setdefault("SMTP_USUARIO", "tests@example.com")
os.environ.setdefault("SMTP_CLAVE", "tests")
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "functions"))

import main  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
