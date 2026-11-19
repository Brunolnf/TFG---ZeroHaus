"""
Pago con retención (bloque E del plan): parámetros y máquina de estados.

Stripe Connect con «separate charges and transfers»: el cliente paga a la
plataforma, el dinero queda retenido en el saldo de Stripe de ZeroHaus y se
transfiere al profesional al liberarse. Primero, lo que no depende de Stripe ni
de Firestore, para poder probarlo entero:

  leer_parametros_pago  Los 4 parámetros de Remote Config, con caché y, ante
                        cualquier duda, valores seguros (pagos apagados).
  calcular_importes     Importe, comisión y neto del profesional.
  nuevo_pago            Documento inicial de /pagos/{id}, en `solicitado`.
  aplicar_transicion    Cambio de estado según TRANSICIONES; lo demás se rechaza.

Después, las funciones (main.py las importa para que Firebase las encuentre):

  Callables      stripe_crear_enlace_cuenta, stripe_estado_cuenta (alta del
                 profesional en Connect); pagos_solicitar, pagos_aceptar,
                 pagos_reintentar, pagos_cancelar, pagos_terminar,
                 pagos_confirmar, pagos_disputar y pagos_resolver (admin).
  HTTP           stripe_webhook: eventos de Stripe con la firma verificada.
  Programada     pagos_liberar_vencidos: cada hora, libera los plazos vencidos.

Secretos (Secret Manager), solo de modo test:
  firebase functions:secrets:set STRIPE_SECRET_KEY              (sk_test_…)
  firebase functions:secrets:set STRIPE_WEBHOOK_SECRET          (whsec_… de la plataforma)
  firebase functions:secrets:set STRIPE_WEBHOOK_SECRET_CONNECT  (whsec_… de Connect)

Todo el dinero va en céntimos (int) y en euros: con float, 5000 céntimos al
1,13 % dan 56,4999… en vez de 56,5 y se pierde el céntimo del redondeo.
"""
import copy
import hashlib
import json
import math
import re
import threading
import time
from dataclasses import dataclass
from fractions import Fraction
from types import MappingProxyType
from typing import Callable, Optional

import stripe
from firebase_admin import firestore
from firebase_functions import https_fn, scheduler_fn
from firebase_functions.params import SecretParam
from google.auth import default as default_creds
from google.auth.transport.requests import AuthorizedSession
from google.cloud.firestore_v1 import FieldFilter

# ══ Parámetros de Remote Config ══
# Los aplica el servidor; la app solo los muestra. Se lee la plantilla de la app
# (la misma API que el precio de la luz en main.py) y no la de servidor de
# firebase_admin.remote_config, que es otra plantilla (namespace firebase-server)
# y obligaría a mantener los valores en dos sitios. No se importa main: main
# importará este módulo para registrar las funciones de pago.
PROJECT_ID = "zerohaus-2a865"
REMOTE_CONFIG_URL = f"https://firebaseremoteconfig.googleapis.com/v1/projects/{PROJECT_ID}/remoteConfig"
REMOTE_CONFIG_SCOPE = "https://www.googleapis.com/auth/firebase.remoteconfig"
# Se lee dentro de una llamada de la app: mejor fallar pronto (y dejar los
# pagos apagados) que tenerla esperando los 30 s de la tarea del precio de la luz
REMOTE_CONFIG_TIMEOUT_S = 10

CACHE_PARAMETROS_S = 5 * 60
# Un fallo o un valor no válido se guarda menos: al arreglarlo en la consola los
# pagos vuelven en un minuto y, mientras, no se consulta la API en cada llamada
CACHE_PARAMETROS_FALLO_S = 60

COMISION_PCT_RANGO = (0, 30)
LIBERACION_DIAS_RANGO = (1, 30)
IMPORTE_MAX_EUR_RANGO = (10, 10_000)

# Mínimo fijo, no configurable: por debajo de 10 € la comisión de Stripe se
# come buena parte del pago
IMPORTE_MIN_CENT = 1000
MONEDA = "eur"
DESCRIPCION_MAX = 500
MOTIVO_DISPUTA_MAX = 500
DIA_MS = 24 * 60 * 60 * 1000


class ErrorPago(ValueError):
    """Operación de pago rechazada. `codigo` es estable (lo usarán las
    funciones y la app para elegir el mensaje); el detalle es para el registro."""

    def __init__(self, codigo: str, detalle: str = ""):
        super().__init__(f"{codigo}: {detalle}" if detalle else codigo)
        self.codigo = codigo


# Todos los códigos de ErrorPago: llegan a la app en `details.motivo` y la app
# los compara con los suyos. Un test comprueba que no se usa ninguno más.
CODIGOS_ERROR = frozenset({
    # Máquina de estados e importes (T-E2)
    "pagos_deshabilitados", "importe_no_entero", "importe_bajo_minimo", "importe_sobre_maximo",
    "transicion_no_permitida", "actor_no_permitido", "uid_no_autorizado", "pago_no_valido",
    "fecha_no_valida", "motivo_no_valido", "descripcion_no_valida", "id_no_valido",
    "partes_no_validas", "plazo_no_vencido",
    # Funciones con Stripe y Firestore (T-E3 a T-E5)
    "no_es_profesional", "chat_no_valido", "bloqueo", "cuenta_no_activa", "limite_solicitudes",
    "sin_cuenta", "pago_no_encontrado", "cobro_no_valido", "cobro_en_curso", "operacion_en_curso",
    "decision_no_valida", "version_no_valida", "stripe_no_configurado",
})


def _es_entero(valor) -> bool:
    # bool es un int en Python: True no puede colarse como 1 céntimo o 1 día
    return isinstance(valor, int) and not isinstance(valor, bool)


@dataclass(frozen=True)
class ParametrosPago:
    """Parámetros del pago tal como los aplica el servidor. Inmutable: la misma
    instancia se comparte entre peticiones a través de la caché. Se valida al
    crearla, también a mano, para que no exista una con valores fuera de rango."""
    pagos_habilitados: bool
    pago_comision_pct: int | float
    pago_liberacion_dias: int
    pago_importe_max_eur: int

    def __post_init__(self):
        if not isinstance(self.pagos_habilitados, bool):
            raise ValueError(f"pagos_habilitados={self.pagos_habilitados!r} no es booleano")
        pct = self.pago_comision_pct
        if (not isinstance(pct, (int, float)) or isinstance(pct, bool) or not math.isfinite(pct)
                or not COMISION_PCT_RANGO[0] <= pct <= COMISION_PCT_RANGO[1]
                or (Fraction(str(pct)) * 100).denominator != 1):
            raise ValueError(f"pago_comision_pct={pct!r}: entre 0 y 30, con 2 decimales como mucho")
        dias = self.pago_liberacion_dias
        if not _es_entero(dias) or not LIBERACION_DIAS_RANGO[0] <= dias <= LIBERACION_DIAS_RANGO[1]:
            raise ValueError(f"pago_liberacion_dias={dias!r}: entero entre 1 y 30")
        maximo = self.pago_importe_max_eur
        if not _es_entero(maximo) or not IMPORTE_MAX_EUR_RANGO[0] <= maximo <= IMPORTE_MAX_EUR_RANGO[1]:
            raise ValueError(f"pago_importe_max_eur={maximo!r}: entero entre 10 y 10000")

    @property
    def importe_max_cent(self) -> int:
        return self.pago_importe_max_eur * 100


# Los valores de remoteconfig.template.json, con los pagos apagados: es lo que
# se aplica si Remote Config no responde o trae algo que no es válido
PARAMETROS_SEGUROS = ParametrosPago(
    pagos_habilitados=False, pago_comision_pct=10, pago_liberacion_dias=7, pago_importe_max_eur=3000,
)

_NOMBRES_PARAMETROS = ("pagos_habilitados", "pago_comision_pct", "pago_liberacion_dias", "pago_importe_max_eur")
# Solo dígitos ASCII y punto decimal: nada de signo, exponente, nan, inf, «_» ni
# dígitos de otros alfabetos, que float(), int() o Fraction() sí aceptarían
_NUMERO = re.compile(r"[0-9]{1,6}(\.[0-9]{1,6})?")


class _ParametrosNoValidos(Exception):
    """La plantilla trae un parámetro de pago que no se puede aplicar."""


def _valores_por_defecto(plantilla) -> dict:
    """{nombre: texto} de los parámetros de pago, sueltos o en grupos.

    Solo cuenta el valor por defecto: las condiciones (idioma, versión de la
    app…) dependen del móvil y el servidor no tiene ese contexto.
    """
    if not isinstance(plantilla, dict):
        raise _ParametrosNoValidos("la plantilla no es un objeto")
    grupos = plantilla.get("parameterGroups") or {}
    if not isinstance(grupos, dict):
        raise _ParametrosNoValidos("parameterGroups no es un objeto")
    fuentes = [plantilla.get("parameters") or {}]
    for grupo in grupos.values():
        if not isinstance(grupo, dict):
            raise _ParametrosNoValidos("grupo de parámetros no válido")
        fuentes.append(grupo.get("parameters") or {})
    valores = {}
    for parametros in fuentes:
        if not isinstance(parametros, dict):
            raise _ParametrosNoValidos("parameters no es un objeto")
        for nombre in _NOMBRES_PARAMETROS:
            if nombre not in parametros:
                continue
            parametro = parametros[nombre]
            if not isinstance(parametro, dict):
                raise _ParametrosNoValidos(f"{nombre} no es un objeto")
            defecto = parametro.get("defaultValue")
            # Sin valor por defecto o «usar el de la app»: el servidor no tiene
            # valor propio y se queda con el suyo (los pagos, apagados)
            if defecto is None or (isinstance(defecto, dict) and defecto.get("useInAppDefault") is True):
                continue
            if not isinstance(defecto, dict) or not isinstance(defecto.get("value"), str):
                raise _ParametrosNoValidos(f"{nombre} sin valor de texto")
            valores[nombre] = defecto["value"].strip()
    return valores


def _numero(nombre: str, texto: str) -> Fraction:
    if not _NUMERO.fullmatch(texto):
        raise _ParametrosNoValidos(f"{nombre}={texto!r} no es un número")
    return Fraction(texto)


def _entero(nombre: str, texto: str) -> int:
    valor = _numero(nombre, texto)
    if valor.denominator != 1:
        raise _ParametrosNoValidos(f"{nombre}={texto!r} no es entero")
    return int(valor)


def parametros_desde_plantilla(plantilla) -> ParametrosPago:
    """Parámetros de pago de una plantilla de Remote Config (la de la API REST).
    Un parámetro ausente toma su valor por defecto; uno no válido lanza error."""
    valores = _valores_por_defecto(plantilla)
    s = PARAMETROS_SEGUROS
    habilitados = valores.get("pagos_habilitados", "false")
    # Solo lo que guarda la consola para un BOOLEAN; «TRUE», «1» o «sí» no encienden nada
    if habilitados not in ("true", "false"):
        raise _ParametrosNoValidos(f"pagos_habilitados={habilitados!r} no es true ni false")
    if "pago_comision_pct" in valores:
        pct = _numero("pago_comision_pct", valores["pago_comision_pct"])
        comision = int(pct) if pct.denominator == 1 else float(pct)
    else:
        comision = s.pago_comision_pct
    try:
        return ParametrosPago(
            pagos_habilitados=habilitados == "true",
            pago_comision_pct=comision,
            pago_liberacion_dias=(_entero("pago_liberacion_dias", valores["pago_liberacion_dias"])
                                  if "pago_liberacion_dias" in valores else s.pago_liberacion_dias),
            pago_importe_max_eur=(_entero("pago_importe_max_eur", valores["pago_importe_max_eur"])
                                  if "pago_importe_max_eur" in valores else s.pago_importe_max_eur),
        )
    except ValueError as e:
        raise _ParametrosNoValidos(str(e)) from e


def _descargar_plantilla() -> dict:
    """Plantilla en vigor de Remote Config, con la cuenta de servicio de las funciones."""
    creds, _ = default_creds(scopes=[REMOTE_CONFIG_SCOPE])
    r = AuthorizedSession(creds).get(REMOTE_CONFIG_URL, timeout=REMOTE_CONFIG_TIMEOUT_S)
    r.raise_for_status()
    return r.json()


_cache_lock = threading.Lock()
_cache = {"valor": None, "caduca": 0.0}


def borrar_cache_parametros() -> None:
    """Olvida los parámetros guardados (tests, o forzar la relectura)."""
    with _cache_lock:
        _cache.update(valor=None, caduca=0.0)


def leer_parametros_pago(leer_plantilla: Optional[Callable[[], object]] = None,
                         reloj: Optional[Callable[[], float]] = None) -> ParametrosPago:
    """Parámetros de pago en vigor, con caché en memoria de 5 minutos.

    Si la lectura falla o algún valor no es válido, PARAMETROS_SEGUROS: pagos
    apagados ante la duda. Un valor raro no se arregla por separado: con una
    comisión mal puesta no se debe cobrar con la de por defecto sin que nadie
    se entere. El candado evita que varias peticiones a la vez (80 por
    instancia) lancen cada una su lectura.
    """
    leer_plantilla = leer_plantilla or _descargar_plantilla
    reloj = reloj or time.monotonic
    with _cache_lock:
        ahora = reloj()
        if _cache["valor"] is not None and ahora < _cache["caduca"]:
            return _cache["valor"]
        try:
            valor = parametros_desde_plantilla(leer_plantilla())
            duracion = CACHE_PARAMETROS_S
        except Exception as e:
            print(f"[PAGOS] Parámetros de Remote Config no válidos o no disponibles, pagos apagados: {e}")
            valor, duracion = PARAMETROS_SEGUROS, CACHE_PARAMETROS_FALLO_S
        _cache.update(valor=valor, caduca=ahora + duracion)
        return valor


# ══ Importes ══

@dataclass(frozen=True)
class Importes:
    importe_cent: int
    comision_cent: int
    neto_profesional_cent: int


def calcular_importes(importe_cent, params: ParametrosPago) -> Importes:
    """Comisión de ZeroHaus y neto del profesional, en céntimos.

    La comisión se redondea a la mitad hacia arriba (1005 céntimos al 10 % son
    101). Se calcula con Fraction, exacta: con float, 5000 céntimos al 1,13 %
    darían 56,4999… y la mitad no subiría.
    """
    if not params.pagos_habilitados:
        raise ErrorPago("pagos_deshabilitados")
    if not _es_entero(importe_cent):
        raise ErrorPago("importe_no_entero", f"{importe_cent!r}")
    if importe_cent < IMPORTE_MIN_CENT:
        raise ErrorPago("importe_bajo_minimo", f"{importe_cent} < {IMPORTE_MIN_CENT}")
    if importe_cent > params.importe_max_cent:
        raise ErrorPago("importe_sobre_maximo", f"{importe_cent} > {params.importe_max_cent}")
    # str(): el porcentaje tal como se escribió (1.13), no su aproximación binaria
    exacta = Fraction(importe_cent) * Fraction(str(params.pago_comision_pct)) / 100
    comision = math.floor(exacta + Fraction(1, 2))
    return Importes(importe_cent, comision, importe_cent - comision)


# ══ Máquina de estados ══
# Solo el servidor cambia el estado de un pago (las reglas deniegan escribir
# en /pagos), siempre con aplicar_transicion y dentro de una transacción.

CLIENTE, PROFESIONAL, ADMIN, SISTEMA = "cliente", "profesional", "admin", "sistema"
# sistema: el webhook de Stripe o la tarea programada, sin usuario detrás
ACTORES = frozenset({CLIENTE, PROFESIONAL, ADMIN, SISTEMA})

ESTADOS = frozenset({
    "solicitado", "pendiente_pago", "retenido", "terminado", "liberado",
    "cancelado", "fallido", "en_disputa", "reembolsado", "contracargo",
})
# De aquí no se sale, salvo un contracargo después de liberar: el banco puede
# devolver el cargo al titular de la tarjeta semanas después
ESTADOS_FINALES = frozenset({"cancelado", "liberado", "reembolsado", "contracargo"})

# (estado de origen, evento) → (estado de destino, actores permitidos).
# Lo que no está aquí se rechaza.
TRANSICIONES = MappingProxyType({
    # El profesional pide el pago; al aceptarlo el cliente se crea el cobro
    ("solicitado", "aceptar"): ("pendiente_pago", frozenset({CLIENTE})),
    ("solicitado", "cancelar"): ("cancelado", frozenset({CLIENTE, PROFESIONAL})),
    # Resultado del cobro, por webhook de Stripe
    ("pendiente_pago", "cobro_ok"): ("retenido", frozenset({SISTEMA})),
    ("pendiente_pago", "cobro_fallido"): ("fallido", frozenset({SISTEMA})),
    # sistema: el cobro caduca sin pagarse
    ("pendiente_pago", "cancelar"): ("cancelado", frozenset({CLIENTE, PROFESIONAL, SISTEMA})),
    ("fallido", "reintentar"): ("pendiente_pago", frozenset({CLIENTE})),
    ("fallido", "cancelar"): ("cancelado", frozenset({CLIENTE, PROFESIONAL})),
    # Con el dinero retenido ya no se cancela: o se termina o se disputa
    ("retenido", "terminar"): ("terminado", frozenset({PROFESIONAL})),
    ("retenido", "disputar"): ("en_disputa", frozenset({CLIENTE})),
    ("terminado", "confirmar"): ("liberado", frozenset({CLIENTE})),
    ("terminado", "vencer_plazo"): ("liberado", frozenset({SISTEMA})),
    ("terminado", "disputar"): ("en_disputa", frozenset({CLIENTE})),
    ("en_disputa", "resolver_liberar"): ("liberado", frozenset({ADMIN})),
    ("en_disputa", "resolver_reembolsar"): ("reembolsado", frozenset({ADMIN})),
    # Aviso del banco: puede llegar en cualquier momento después de cobrar
    ("retenido", "contracargo"): ("contracargo", frozenset({SISTEMA})),
    ("terminado", "contracargo"): ("contracargo", frozenset({SISTEMA})),
    ("en_disputa", "contracargo"): ("contracargo", frozenset({SISTEMA})),
    ("liberado", "contracargo"): ("contracargo", frozenset({SISTEMA})),
})
EVENTOS = frozenset(evento for _, evento in TRANSICIONES)

# Campo del pago con el uid de cada parte
_CAMPO_PARTE = {CLIENTE: "cliente", PROFESIONAL: "profesional"}
# Ids de Firestore y uids de Auth: sin «/», que partiría la ruta del documento
_ID = re.compile(r"[A-Za-z0-9_-]{1,128}")


def _comprobar_fecha(ahora_ms) -> None:
    if not _es_entero(ahora_ms) or ahora_ms <= 0:
        raise ErrorPago("fecha_no_valida", f"{ahora_ms!r}")


def _texto(valor, maximo: int, codigo: str) -> str:
    """Texto obligatorio, sin espacios a los lados y con tope de caracteres."""
    if not isinstance(valor, str) or not valor.strip() or len(valor.strip()) > maximo:
        raise ErrorPago(codigo, f"obligatorio y de {maximo} caracteres como mucho")
    return valor.strip()


def _comprobar_uid(pago: dict, actor: str, uid_actor) -> None:
    """El uid tiene que ser el de la parte que dice actuar.

    Del administrador solo se exige el uid (para el historial): su claim lo
    comprueba quien llama, con _verificar_admin. El sistema no lleva uid, así
    que una llamada con el uid de un usuario nunca pasa por webhook o tarea.
    """
    if actor == SISTEMA:
        if uid_actor is not None:
            raise ErrorPago("uid_no_autorizado", "el sistema no actúa con uid")
        return
    if not isinstance(uid_actor, str) or not uid_actor:
        raise ErrorPago("uid_no_autorizado", "falta el uid")
    if actor == ADMIN:
        return
    parte = pago.get(_CAMPO_PARTE[actor])
    if not isinstance(parte, str) or not parte or uid_actor != parte:
        raise ErrorPago("uid_no_autorizado", f"{uid_actor} no es el {actor} de este pago")


def aplicar_transicion(pago: dict, evento: str, actor: str, uid_actor, ahora_ms: int,
                       *, motivo: Optional[str] = None) -> dict:
    """Pago tras `evento`, sin modificar el de entrada (lo devuelve copiado).

    Rechaza con ErrorPago lo que no está en TRANSICIONES, un actor no permitido
    y un cliente o profesional que no sea el de este pago. `motivo` solo se usa
    al disputar, y ahí es obligatorio.
    """
    if not isinstance(pago, dict) or not isinstance(pago.get("historial"), list):
        raise ErrorPago("pago_no_valido", "el pago no es un objeto con historial")
    _comprobar_fecha(ahora_ms)
    origen = pago.get("estado")
    regla = None
    # Solo textos: una lista como clave de dict lanzaría TypeError, no ErrorPago
    if isinstance(origen, str) and isinstance(evento, str):
        regla = TRANSICIONES.get((origen, evento))
    if regla is None:
        raise ErrorPago("transicion_no_permitida", f"{origen!r} --{evento!r}-->")
    destino, actores = regla
    if not isinstance(actor, str) or actor not in actores:
        raise ErrorPago("actor_no_permitido", f"{actor!r} no puede {evento} en {origen}")
    _comprobar_uid(pago, actor, uid_actor)

    nuevo = copy.deepcopy(pago)
    if evento == "terminar":
        # Los días guardados al crear el pago, no los de Remote Config de hoy:
        # el cliente aceptó ese plazo
        dias = pago.get("liberacionDias")
        if not _es_entero(dias) or not LIBERACION_DIAS_RANGO[0] <= dias <= LIBERACION_DIAS_RANGO[1]:
            raise ErrorPago("pago_no_valido", f"liberacionDias={dias!r}")
        nuevo["terminadoEn"] = ahora_ms
        nuevo["liberarEn"] = ahora_ms + dias * DIA_MS
    elif evento == "disputar":
        nuevo["disputa"] = {"motivo": _texto(motivo, MOTIVO_DISPUTA_MAX, "motivo_no_valido"), "fecha": ahora_ms}
        # En disputa no hay liberación automática: decide el administrador
        nuevo["liberarEn"] = None
    elif evento == "vencer_plazo":
        # La tarea programada no puede liberar antes de tiempo aunque se equivoque de consulta
        liberar_en = pago.get("liberarEn")
        if not _es_entero(liberar_en):
            raise ErrorPago("pago_no_valido", f"liberarEn={liberar_en!r}")
        if ahora_ms < liberar_en:
            raise ErrorPago("plazo_no_vencido", f"faltan {liberar_en - ahora_ms} ms")

    uid = None if actor == SISTEMA else uid_actor
    nuevo["historial"].append({"estado": destino, "evento": evento, "actor": actor, "uid": uid, "fecha": ahora_ms})
    nuevo["estado"] = destino
    nuevo["actualizado"] = ahora_ms
    return nuevo


def nuevo_pago(*, pago_id: str, cliente: str, profesional: str, chat_id: str, descripcion: str,
               importe_cent: int, params: ParametrosPago, ahora_ms: int) -> dict:
    """Documento inicial de /pagos/{pago_id}: el profesional pide `importe_cent`
    al cliente de un chat. Las reglas y la app se escriben contra este esquema.

    Guarda la comisión y los días de liberación de ahora: si Remote Config
    cambia después, el pago sigue con lo que el cliente vio al aceptarlo.
    """
    importes = calcular_importes(importe_cent, params)
    for nombre, valor in (("pago_id", pago_id), ("cliente", cliente),
                          ("profesional", profesional), ("chat_id", chat_id)):
        if not isinstance(valor, str) or not _ID.fullmatch(valor):
            raise ErrorPago("id_no_valido", nombre)
    if cliente == profesional:
        raise ErrorPago("partes_no_validas", "el cliente y el profesional son la misma cuenta")
    descripcion = _texto(descripcion, DESCRIPCION_MAX, "descripcion_no_valida")
    _comprobar_fecha(ahora_ms)
    return {
        "id": pago_id,
        "cliente": cliente,
        "profesional": profesional,
        "chatId": chat_id,
        "descripcion": descripcion,
        "importeCent": importes.importe_cent,
        "comisionCent": importes.comision_cent,
        "netoCent": importes.neto_profesional_cent,
        "moneda": MONEDA,
        "comisionPct": params.pago_comision_pct,
        "liberacionDias": params.pago_liberacion_dias,
        "estado": "solicitado",
        "historial": [{"estado": "solicitado", "evento": "solicitar", "actor": PROFESIONAL,
                       "uid": profesional, "fecha": ahora_ms}],
        "creado": ahora_ms,
        "actualizado": ahora_ms,
        "stripePaymentIntentId": None,
        "stripeTransferId": None,
        "stripeRefundId": None,
        "terminadoEn": None,
        "liberarEn": None,
        "disputa": None,
    }


# ════════════════════════════════════════════════════════════════════════
# Stripe y Firestore (T-E3 a T-E5)
# ════════════════════════════════════════════════════════════════════════

REGION = "europe-west1"            # la de main.py (lo comprueba un test)
MAX_INSTANCIAS_PAGOS = 3           # pocas: cada llamada puede mover dinero
MAX_INSTANCIAS_WEBHOOK = 5

STRIPE_SECRET_KEY = SecretParam("STRIPE_SECRET_KEY")
STRIPE_WEBHOOK_SECRET = SecretParam("STRIPE_WEBHOOK_SECRET")
STRIPE_WEBHOOK_SECRET_CONNECT = SecretParam("STRIPE_WEBHOOK_SECRET_CONNECT")

# Versión de la API fijada (la de stripe==16.1.0). Sin fijarla, las llamadas
# irían con la de la cuenta, que cambia si alguien la actualiza en el panel, y
# con ella la forma de los objetos que se leen aquí. Los dos endpoints del
# webhook se crean en Stripe con esta misma versión.
STRIPE_API_VERSION = "2026-10-28.endive"
stripe.api_version = STRIPE_API_VERSION
# Todo lo que crea algo en Stripe lleva clave de idempotencia: repetir la
# petición tras un corte de red no lo duplica
stripe.max_network_retries = 2

# Solo modo test (decisión del plan): una clave que no sea de test se rechaza
# antes de llamar a Stripe. Si se despliega por error con una clave live, no se
# cobra, transfiere ni reembolsa dinero real: las funciones responden
# FAILED_PRECONDITION («stripe_no_configurado») y lo dejan en el registro.
PREFIJOS_CLAVE_TEST = ("sk_test_", "rk_test_")

# Adonde vuelve el profesional al terminar (o abandonar) el alta en Stripe.
# Solo le dice que vuelva a la app; el estado llega con account.updated.
URL_RETORNO_STRIPE = "https://zerohaus-2a865.web.app/stripe/retorno"

TIPOS_PROFESIONAL = ("Técnico", "Empresa")      # /usuarios.tipoUsuario
PAGOS_SOLICITUDES_MAX_DIA = 10                  # por profesional: frena el spam de solicitudes
# Con el dinero en camino o retenido: con uno de estos, la cuenta no se borra
ESTADOS_CON_DINERO = frozenset({"pendiente_pago", "retenido", "terminado", "en_disputa"})
# Estados del PaymentIntent en los que el cliente todavía puede pagarlo
_COBRO_PAGABLE = frozenset({"requires_payment_method", "requires_confirmation", "requires_action"})

LIBERAR_LOTE = 20                 # pagos por consulta de la tarea programada
LIBERAR_MAX_EJECUCION = 100       # y como mucho por ejecución (cada hora)
# Una transferencia o un reembolso a medias se da por abandonado pasado este
# tiempo: una llamada a Stripe tarda segundos y la función se corta a los 9 min
OPERACION_CADUCA_MS = 15 * 60 * 1000

_VERSION_STRIPE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}(\.[a-z]+)?")
_SEUDONIMO = re.compile(r"[0-9a-f]{64}")
# Un Long de Kotlin llega así por el SDK de Firebase Functions (protobuf en JSON)
_TIPO_INT64 = "type.googleapis.com/google.protobuf.Int64Value"
_INT64 = re.compile(r"-?[0-9]{1,19}")


def entero_de_la_app(valor):
    """importeCent tal como lo manda la app: un entero JSON o, si el SDK lo
    envía como Long, {"@type": Int64Value, "value": "123"}, que se pasa a int.
    Cualquier otra cosa se devuelve igual, para que calcular_importes la
    rechace (importe_no_entero): ni decimales, ni exponentes, ni espacios."""
    if (isinstance(valor, dict) and set(valor) == {"@type", "value"} and valor["@type"] == _TIPO_INT64
            and isinstance(valor["value"], str) and _INT64.fullmatch(valor["value"])):
        return int(valor["value"])
    return valor


def _ahora_ms() -> int:
    return int(time.time() * 1000)


def _hoy_utc() -> str:
    return time.strftime("%Y%m%d", time.gmtime(_ahora_ms() / 1000))


# ── Ayudas compartidas con main.py ──
# pagos.py no importa main: Firebase carga main.py sin meterlo en sys.modules,
# así que un `import main` desde aquí lo ejecutaría otra vez entero. main.py
# llama a configurar() con lo que comparte (sesión, administrador y avisos).
_AYUDAS: dict = {}


def configurar(**ayudas) -> None:
    """exigir_email_verificado, verificar_admin, preferencias, crear_notificacion y enviar_push."""
    _AYUDAS.update(ayudas)


def _ayuda(nombre: str):
    if nombre not in _AYUDAS:
        raise RuntimeError(f"pagos.configurar() no ha recibido «{nombre}»")
    return _AYUDAS[nombre]


# ── Stripe ──

def comprobar_clave_stripe(clave) -> str:
    """La clave secreta si es de modo test; si no, ErrorPago y nada llega a Stripe."""
    if not isinstance(clave, str) or not clave.startswith(PREFIJOS_CLAVE_TEST):
        # Sin la clave en el registro, ni siquiera un trozo
        print("[PAGOS] ERROR: STRIPE_SECRET_KEY no es de modo test (sk_test_ o rk_test_); no se llama a Stripe")
        raise ErrorPago("stripe_no_configurado", "solo se admiten claves de modo test")
    return clave


def _a_dict(objeto) -> dict:
    """Los objetos de Stripe no son dict: se pasan a dict para leerlos igual que los eventos."""
    return objeto.to_dict() if hasattr(objeto, "to_dict") else dict(objeto)


class _StripeAPI:
    """Lo único que se llama de Stripe, con dicts de entrada y salida. Las claves
    de idempotencia las pone quien llama: son parte de la lógica (una por
    movimiento de dinero, no por intento). Los tests lo cambian por uno simulado."""

    def crear_cuenta(self, uid: str, clave: str) -> dict:
        return _a_dict(stripe.Account.create(
            type="express", country="ES",
            capabilities={"transfers": {"requested": True}},
            metadata={"uid": uid}, idempotency_key=clave,
        ))

    def crear_enlace_cuenta(self, account_id: str) -> str:
        enlace = stripe.AccountLink.create(
            account=account_id, refresh_url=URL_RETORNO_STRIPE,
            return_url=URL_RETORNO_STRIPE, type="account_onboarding",
        )
        return _a_dict(enlace)["url"]

    def leer_cuenta(self, account_id: str) -> dict:
        return _a_dict(stripe.Account.retrieve(account_id))

    def crear_cliente(self, uid: str, clave: str) -> dict:
        return _a_dict(stripe.Customer.create(metadata={"uid": uid}, idempotency_key=clave))

    def crear_clave_efimera(self, customer_id: str, version: str) -> str:
        return _a_dict(stripe.EphemeralKey.create(customer=customer_id, stripe_version=version))["secret"]

    def crear_cobro(self, pago: dict, customer_id: str, clave: str) -> dict:
        return _a_dict(stripe.PaymentIntent.create(
            amount=pago["importeCent"], currency=MONEDA, customer=customer_id,
            transfer_group=pago["id"], metadata={"pagoId": pago["id"]},
            automatic_payment_methods={"enabled": True}, idempotency_key=clave,
        ))

    def leer_cobro(self, cobro_id: str) -> dict:
        return _a_dict(stripe.PaymentIntent.retrieve(cobro_id))

    def cancelar_cobro(self, cobro_id: str, clave: str) -> dict:
        return _a_dict(stripe.PaymentIntent.cancel(cobro_id, idempotency_key=clave))

    def buscar_transferencia(self, pago_id: str) -> Optional[dict]:
        for t in stripe.Transfer.list(transfer_group=pago_id, limit=10).data:
            t = _a_dict(t)
            if not t.get("reversed"):
                return t
        return None

    def crear_transferencia(self, pago: dict, account_id: str, cargo_id: str, clave: str) -> dict:
        return _a_dict(stripe.Transfer.create(
            amount=pago["netoCent"], currency=MONEDA, destination=account_id,
            # Con el cargo de este pago: Stripe no la cubre con saldo de otros cobros
            source_transaction=cargo_id, transfer_group=pago["id"],
            metadata={"pagoId": pago["id"]}, idempotency_key=clave,
        ))

    def buscar_reembolso(self, cobro_id: str) -> Optional[dict]:
        for r in stripe.Refund.list(payment_intent=cobro_id, limit=10).data:
            r = _a_dict(r)
            if r.get("status") in ("pending", "requires_action", "succeeded"):
                return r
        return None

    def crear_reembolso(self, pago: dict, clave: str) -> dict:
        return _a_dict(stripe.Refund.create(
            payment_intent=pago["stripePaymentIntentId"], amount=pago["importeCent"],
            metadata={"pagoId": pago["id"]}, idempotency_key=clave,
        ))


def _stripe() -> _StripeAPI:
    """Stripe listo para usar. La clave se comprueba en cada uso: la de Secret
    Manager solo existe dentro de la función, no al importar el módulo."""
    stripe.api_key = comprobar_clave_stripe(STRIPE_SECRET_KEY.value)
    stripe.api_version = STRIPE_API_VERSION
    return _StripeAPI()


def datos_cuenta_stripe(cuenta: dict) -> dict:
    """Estado de una cuenta Connect, con los campos de /cuentas_stripe.

    activa: alta terminada, la capacidad `transfers` activa y los pagos al
    banco habilitados. No se exige charges_enabled: con «separate charges and
    transfers» cobra la plataforma, y una cuenta que solo pide `transfers` puede
    tenerlo en false aunque esté lista para recibir el dinero.
    """
    detalles = cuenta.get("details_submitted") is True
    payouts = cuenta.get("payouts_enabled") is True
    transfers = (cuenta.get("capabilities") or {}).get("transfers")
    desactivada = bool((cuenta.get("requirements") or {}).get("disabled_reason"))
    if not detalles:
        estado = "pendiente"
    elif payouts and transfers == "active" and not desactivada:
        estado = "activa"
    else:
        estado = "restringida"
    return {
        "estado": estado,
        "chargesEnabled": cuenta.get("charges_enabled") is True,
        "payoutsEnabled": payouts,
        "detailsSubmitted": detalles,
    }


# ── Lecturas y comprobaciones ──

def _datos(db, coleccion: str, doc_id: str) -> dict:
    snap = db.collection(coleccion).document(doc_id).get()
    return (snap.to_dict() or {}) if snap.exists else {}


def _comprobar_id(valor, nombre: str) -> str:
    if not isinstance(valor, str) or not _ID.fullmatch(valor):
        raise ErrorPago("id_no_valido", nombre)
    return valor


def _leer_pago(db, pago_id) -> dict:
    _comprobar_id(pago_id, "pagoId")
    snap = db.collection("pagos").document(pago_id).get()
    if not snap.exists:
        raise ErrorPago("pago_no_encontrado")
    return snap.to_dict()


def _exigir_profesional(db, uid: str) -> None:
    if _datos(db, "usuarios", uid).get("tipoUsuario") not in TIPOS_PROFESIONAL:
        raise ErrorPago("no_es_profesional")


def _hay_bloqueo(db, a: str, b: str) -> bool:
    """Bloqueo en cualquier sentido. A diferencia del de los avisos de chat, si
    Firestore falla no se sigue: sin saberlo, no se pide ni se cobra dinero."""
    bloqueos = db.collection("bloqueos")
    return bloqueos.document(f"{a}_{b}").get().exists or bloqueos.document(f"{b}_{a}").get().exists


def _parte(pago: dict, uid: str) -> str:
    """Papel de `uid` en el pago: cliente o profesional."""
    if uid and uid == pago.get("cliente"):
        return CLIENTE
    if uid and uid == pago.get("profesional"):
        return PROFESIONAL
    raise ErrorPago("uid_no_autorizado", "no es parte de este pago")


def _cuenta_profesional_activa(db, profesional: str) -> str:
    cuenta = _datos(db, "cuentas_stripe", profesional)
    if cuenta.get("estado") != "activa" or not cuenta.get("accountId"):
        raise ErrorPago("cuenta_no_activa")
    return cuenta["accountId"]


def _cobro_no_cuadra(pago: dict, cobro: dict, cobrado: bool = False) -> str:
    """Por qué un PaymentIntent no es el de este pago ("" si cuadra). Lo que
    llega de Stripe se compara con lo que guardó el servidor, nunca al revés."""
    if not cobro.get("id") or cobro.get("id") != pago.get("stripePaymentIntentId"):
        return "no es el PaymentIntent del pago"
    if (cobro.get("metadata") or {}).get("pagoId") != pago.get("id"):
        return "metadata.pagoId distinto"
    if cobro.get("currency") != MONEDA:
        return f"moneda {cobro.get('currency')!r}"
    if cobro.get("amount") != pago.get("importeCent"):
        return f"importe {cobro.get('amount')!r} en vez de {pago.get('importeCent')}"
    if cobrado and cobro.get("amount_received") != pago.get("importeCent"):
        return f"cobrado {cobro.get('amount_received')!r} en vez de {pago.get('importeCent')}"
    return ""


def _cargo_cobrado(api, pago: dict) -> str:
    """Id del cargo del cobro, si está cobrado y cuadra con el pago."""
    cobro = api.leer_cobro(pago["stripePaymentIntentId"])
    motivo = _cobro_no_cuadra(pago, cobro, cobrado=True)
    if motivo or cobro.get("status") != "succeeded":
        raise ErrorPago("cobro_no_valido", motivo or f"estado {cobro.get('status')}")
    cargo = cobro.get("latest_charge")
    cargo = cargo.get("id") if isinstance(cargo, dict) else cargo
    if not isinstance(cargo, str) or not cargo:
        raise ErrorPago("cobro_no_valido", "sin cargo")
    return cargo


# ── Cambios de estado ──
# Cada cambio va en una transacción que relee el pago y escribe solo lo que
# devuelve aplicar_transicion. Las llamadas a Stripe, fuera de la transacción.

def _transicion(db, pago_id: str, evento: str, actor: str, uid_actor, *, motivo=None,
                cambios: Optional[dict] = None, operacion: Optional[tuple] = None) -> dict:
    """Aplica `evento` y avisa a la otra parte. Con `operacion` (nombre,
    renovado visto) comprueba además que no haya empezado otra operación con
    dinero desde que se miró, y borra la caducada."""
    ref = db.collection("pagos").document(pago_id)
    op_ref = db.collection("operaciones_pagos").document(pago_id)
    ahora = _ahora_ms()

    @firestore.transactional
    def _aplicar(txn):
        snap = ref.get(transaction=txn)
        op_snap = op_ref.get(transaction=txn) if operacion else None
        if not snap.exists:
            raise ErrorPago("pago_no_encontrado")
        op = op_snap.to_dict() if op_snap is not None and op_snap.exists else None
        if operacion and _operacion_cambiada(op, *operacion):
            raise ErrorPago("operacion_en_curso")
        nuevo = aplicar_transicion(snap.to_dict(), evento, actor, uid_actor, ahora, motivo=motivo)
        nuevo.update(cambios or {})
        txn.set(ref, nuevo)
        if op is not None:
            txn.delete(op_ref)      # caducada y sin dinero movido (_comprobar_operacion)
        return nuevo

    nuevo = _aplicar(db.transaction())
    _avisar(db, nuevo, evento, actor)
    return nuevo


def _comprobar_operacion(db, pago: dict, operacion: str, api=None):
    """Antes de transferir, reembolsar o disputar: que no haya otra operación
    con dinero en marcha sobre el pago (/operaciones_pagos/{id}). Devuelve el
    `renovado` de la que había, o None, para que la transacción compruebe que
    no ha cambiado.

    Sin esto, un cliente que disputa mientras la tarea programada transfiere
    dejaría el pago en disputa con el dinero ya en manos del profesional. Una
    operación distinta y reciente lo impide; una caducada solo se sustituye si
    en Stripe no llegó a moverse el dinero.
    """
    op = _datos(db, "operaciones_pagos", pago["id"])
    if not op or op.get("operacion") == operacion:
        return op.get("renovado") if op else None
    if _ahora_ms() - int(op.get("renovado") or 0) < OPERACION_CADUCA_MS:
        raise ErrorPago("operacion_en_curso", str(op.get("operacion")))
    api = api or _stripe()
    if op.get("operacion") == "transferencia":
        movido = api.buscar_transferencia(pago["id"])
    else:
        movido = api.buscar_reembolso(pago.get("stripePaymentIntentId") or "")
    if movido:
        raise ErrorPago("operacion_en_curso", f"{op.get('operacion')} ya hecha en Stripe")
    return op.get("renovado")


def _operacion_cambiada(op: Optional[dict], operacion: str, visto) -> bool:
    """True si, desde _comprobar_operacion, ha empezado o se ha renovado otra operación."""
    if not op or op.get("operacion") == operacion:
        return False
    return op.get("renovado") != visto


def _reservar_operacion(db, pago_id: str, evento: str, actor: str, uid_actor, operacion: str, visto) -> None:
    """Apunta que va a moverse dinero, en la misma transacción que comprueba que
    la transición sigue siendo válida. `renovado` cambia siempre: una disputa
    que vio la reserva anterior caducada ya no puede seguir."""
    ref = db.collection("pagos").document(pago_id)
    op_ref = db.collection("operaciones_pagos").document(pago_id)
    ahora = _ahora_ms()

    @firestore.transactional
    def _reservar(txn):
        snap = ref.get(transaction=txn)
        op_snap = op_ref.get(transaction=txn)
        if not snap.exists:
            raise ErrorPago("pago_no_encontrado")
        aplicar_transicion(snap.to_dict(), evento, actor, uid_actor, ahora)
        op = op_snap.to_dict() if op_snap.exists else None
        if _operacion_cambiada(op, operacion, visto):
            raise ErrorPago("operacion_en_curso")
        misma = op is not None and op.get("operacion") == operacion
        txn.set(op_ref, {
            "pagoId": pago_id, "operacion": operacion, "evento": evento,
            "creado": op.get("creado", ahora) if misma else ahora, "renovado": ahora,
        })

    _reservar(db.transaction())


def _cerrar_operacion(db, pago_id: str, evento: str, actor: str, uid_actor, campo: str, id_stripe: str) -> dict:
    """Tras mover el dinero: aplica la transición y guarda el id de Stripe.

    Si otra llamada ya lo cerró con el mismo movimiento, no cambia nada. Si el
    estado cambió mientras tanto (un contracargo), guarda igualmente el id, para
    que conste a dónde fue el dinero, y después lanza el error.
    """
    ref = db.collection("pagos").document(pago_id)
    op_ref = db.collection("operaciones_pagos").document(pago_id)
    ahora = _ahora_ms()

    @firestore.transactional
    def _cerrar(txn):
        pago = ref.get(transaction=txn).to_dict()
        try:
            nuevo = aplicar_transicion(pago, evento, actor, uid_actor, ahora)
        except ErrorPago as e:
            if pago.get(campo) == id_stripe:
                return pago, "ya_cerrado"
            pago[campo] = pago.get(campo) or id_stripe
            txn.set(ref, pago)
            txn.set(op_ref, {"hecha": True, "stripeId": id_stripe}, merge=True)
            return pago, e.codigo
        nuevo[campo] = id_stripe
        txn.set(ref, nuevo)
        txn.set(op_ref, {"hecha": True, "stripeId": id_stripe}, merge=True)
        return nuevo, ""

    pago, problema = _cerrar(db.transaction())
    if problema == "ya_cerrado":
        return pago
    if problema:
        print(f"[PAGOS] ERROR: {campo}={id_stripe} hecho en Stripe pero el pago {pago_id} "
              f"está en {pago.get('estado')}: revisarlo a mano")
        raise ErrorPago(problema)
    _avisar(db, pago, evento, actor)
    return pago


def _mover_dinero(db, pago_id: str, evento: str, actor: str, uid_actor, operacion: str) -> dict:
    """Libera (transferencia al profesional) o reembolsa (al cliente) un pago,
    sin poder duplicar el dinero aunque se repita o falle a medias:
      1. comprueba la transición y reserva la operación en una transacción;
      2. en Stripe, busca si ya se hizo y, si no, la hace con una clave de
         idempotencia fija por pago (confirmar y vencer el plazo dan la misma);
      3. aplica la transición y guarda el id de Stripe en otra transacción.
    Repetirlo tras un fallo encuentra la reserva y el movimiento ya hechos.
    """
    pago = _leer_pago(db, pago_id)
    aplicar_transicion(pago, evento, actor, uid_actor, _ahora_ms())     # antes de tocar Stripe
    if not pago.get("stripePaymentIntentId"):
        raise ErrorPago("cobro_no_valido", "el pago no tiene cobro")
    account_id = None
    if operacion == "transferencia":
        account_id = _datos(db, "cuentas_stripe", pago["profesional"]).get("accountId")
        if not account_id:
            raise ErrorPago("cuenta_no_activa", "el profesional no tiene cuenta de Stripe")
    api = _stripe()
    visto = _comprobar_operacion(db, pago, operacion, api)
    _reservar_operacion(db, pago_id, evento, actor, uid_actor, operacion, visto)
    if operacion == "transferencia":
        hecha = api.buscar_transferencia(pago_id)
        if hecha is None:
            hecha = api.crear_transferencia(pago, account_id, _cargo_cobrado(api, pago),
                                            f"{pago_id}:transferencia:1")
        return _cerrar_operacion(db, pago_id, evento, actor, uid_actor, "stripeTransferId", hecha["id"])
    hecho = api.buscar_reembolso(pago["stripePaymentIntentId"])
    if hecho is None:
        _cargo_cobrado(api, pago)
        hecho = api.crear_reembolso(pago, f"{pago_id}:reembolso:1")
    return _cerrar_operacion(db, pago_id, evento, actor, uid_actor, "stripeRefundId", hecho["id"])


# ── T-E3: alta del profesional en Connect ──

def _crear_enlace_cuenta(db, uid: str, datos: dict) -> dict:
    _exigir_profesional(db, uid)
    account_id = _datos(db, "cuentas_stripe", uid).get("accountId")
    api = _stripe()
    if not account_id:
        # Clave fija por usuario: dos toques seguidos no crean dos cuentas
        account_id = _guardar_cuenta_nueva(db, uid, api.crear_cuenta(uid, f"cuenta:{uid}")["id"])
    return {"url": api.crear_enlace_cuenta(account_id)}


def _guardar_cuenta_nueva(db, uid: str, account_id: str) -> str:
    ref = db.collection("cuentas_stripe").document(uid)
    ahora = _ahora_ms()

    @firestore.transactional
    def _guardar(txn):
        snap = ref.get(transaction=txn)
        actual = (snap.to_dict() or {}) if snap.exists else {}
        if actual.get("accountId"):
            if actual["accountId"] != account_id:
                print(f"[PAGOS] {uid} ya tenía la cuenta {actual['accountId']}; {account_id} queda sin usar")
            return actual["accountId"]
        txn.set(ref, {
            "uid": uid, "accountId": account_id, "estado": "pendiente", "chargesEnabled": False,
            "payoutsEnabled": False, "detailsSubmitted": False, "creado": ahora, "actualizado": ahora,
        }, merge=True)
        return account_id

    return _guardar(db.transaction())


def _estado_cuenta(db, uid: str, datos: dict) -> dict:
    account_id = _datos(db, "cuentas_stripe", uid).get("accountId")
    if not account_id:
        raise ErrorPago("sin_cuenta")
    estado = datos_cuenta_stripe(_stripe().leer_cuenta(account_id))
    ahora = _ahora_ms()
    # estadoStripeEn: un account.updated anterior a esta lectura ya no la pisa
    db.collection("cuentas_stripe").document(uid).set(
        {**estado, "actualizado": ahora, "estadoStripeEn": ahora // 1000}, merge=True)
    return estado


# ── T-E4: solicitar y pagar ──

def _solicitar(db, uid: str, datos: dict) -> dict:
    params = leer_parametros_pago()
    chat_id = _comprobar_id(datos.get("chatId"), "chatId")
    _exigir_profesional(db, uid)
    participantes = _datos(db, "chats", chat_id).get("participantes")
    otros = [p for p in participantes if p != uid] if isinstance(participantes, list) else []
    if not isinstance(participantes, list) or len(participantes) != 2 or uid not in participantes or len(otros) != 1:
        raise ErrorPago("chat_no_valido")
    cliente = otros[0]
    if _hay_bloqueo(db, uid, cliente):
        raise ErrorPago("bloqueo")
    _cuenta_profesional_activa(db, uid)
    ref = db.collection("pagos").document()
    pago = nuevo_pago(pago_id=ref.id, cliente=cliente, profesional=uid, chat_id=chat_id,
                      descripcion=datos.get("descripcion"), importe_cent=entero_de_la_app(datos.get("importeCent")),
                      params=params, ahora_ms=_ahora_ms())
    limite_ref = db.collection("limites_pagos").document(uid)
    hoy = _hoy_utc()

    @firestore.transactional
    def _crear(txn):
        snap = limite_ref.get(transaction=txn)
        d = (snap.to_dict() or {}) if snap.exists else {}
        usadas = int(d.get("solicitudes") or 0) if d.get("dia") == hoy else 0
        if usadas >= PAGOS_SOLICITUDES_MAX_DIA:
            raise ErrorPago("limite_solicitudes")
        txn.set(limite_ref, {"uid": uid, "dia": hoy, "solicitudes": usadas + 1})
        txn.set(ref, pago)

    _crear(db.transaction())
    _avisar(db, pago, "solicitar", PROFESIONAL)
    return {"pagoId": ref.id}


def _customer(db, api, uid: str) -> str:
    """Customer de Stripe del cliente: uno por cuenta, en /cuentas_stripe/{uid}."""
    customer_id = _datos(db, "cuentas_stripe", uid).get("customerId")
    if customer_id:
        return customer_id
    customer_id = api.crear_cliente(uid, f"cliente:{uid}")["id"]
    db.collection("cuentas_stripe").document(uid).set({"uid": uid, "customerId": customer_id}, merge=True)
    return customer_id


def _cobro_para(api, pago: dict, customer_id: str) -> tuple:
    """(PaymentIntent, recién creado). Al reintentar se usa el del pago si aún
    se puede pagar: con dos cobros vivos, el cliente podría pagar los dos."""
    cobro_id = pago.get("stripePaymentIntentId")
    if cobro_id:
        cobro = api.leer_cobro(cobro_id)
        if cobro.get("status") in ("succeeded", "processing"):
            raise ErrorPago("cobro_en_curso")
        if cobro.get("status") in _COBRO_PAGABLE:
            return cobro, False
    # Uno por cada entrada en pendiente_pago: si la llamada se repite antes de
    # guardar la transición, la clave sale igual y Stripe devuelve el mismo
    intento = 1 + sum(1 for h in pago.get("historial") or [] if h.get("evento") in ("aceptar", "reintentar"))
    return api.crear_cobro(pago, customer_id, f"{pago['id']}:cobro:{intento}"), True


def _guardar_cobro(db, api, pago_id: str, evento: str, uid: str, cobro_id: str, recien_creado: bool) -> None:
    try:
        _transicion(db, pago_id, evento, CLIENTE, uid, cambios={"stripePaymentIntentId": cobro_id})
    except ErrorPago:
        actual = _leer_pago(db, pago_id)
        if actual.get("estado") == "pendiente_pago" and actual.get("stripePaymentIntentId") == cobro_id:
            return          # otra llamada a la vez lo guardó con el mismo cobro
        if recien_creado:
            # El pago cambió mientras tanto (p. ej. se canceló): el cobro no se
            # llega a entregar a la app, pero se anula para que no quede vivo
            try:
                api.cancelar_cobro(cobro_id, f"{pago_id}:cancelar:{cobro_id}")
            except stripe.StripeError as e:
                print(f"[PAGOS] No se pudo anular el cobro sobrante {cobro_id}: {e}")
        raise


def _preparar_cobro(db, uid: str, datos: dict, evento: str) -> dict:
    """pagos_aceptar (desde solicitado) y pagos_reintentar (desde fallido)."""
    pago = _leer_pago(db, datos.get("pagoId"))
    version = datos.get("stripeVersion")
    version = STRIPE_API_VERSION if version is None else version
    if not isinstance(version, str) or not _VERSION_STRIPE.fullmatch(version):
        raise ErrorPago("version_no_valida")
    if uid != pago.get("cliente"):
        raise ErrorPago("uid_no_autorizado", "solo el cliente del pago")
    api = _stripe()
    if pago.get("estado") == "pendiente_pago" and pago.get("stripePaymentIntentId"):
        # La respuesta anterior no llegó a la app: el mismo cobro, sin crear otro
        cobro = api.leer_cobro(pago["stripePaymentIntentId"])
        customer_id = _customer(db, api, uid)
    else:
        aplicar_transicion(pago, evento, CLIENTE, uid, _ahora_ms())     # antes de crear nada en Stripe
        if _hay_bloqueo(db, pago["cliente"], pago["profesional"]):
            raise ErrorPago("bloqueo")
        _cuenta_profesional_activa(db, pago["profesional"])
        customer_id = _customer(db, api, uid)
        cobro, recien_creado = _cobro_para(api, pago, customer_id)
        _guardar_cobro(db, api, pago["id"], evento, uid, cobro["id"], recien_creado)
    if cobro.get("status") == "canceled":
        raise ErrorPago("cobro_no_valido", "cobro cancelado")
    return {
        "paymentIntentClientSecret": cobro["client_secret"],
        "customerId": customer_id,
        "ephemeralKeySecret": api.crear_clave_efimera(customer_id, version),
    }


def _aceptar(db, uid: str, datos: dict) -> dict:
    return _preparar_cobro(db, uid, datos, "aceptar")


def _reintentar(db, uid: str, datos: dict) -> dict:
    return _preparar_cobro(db, uid, datos, "reintentar")


def _cancelar(db, uid: str, datos: dict) -> dict:
    pago = _leer_pago(db, datos.get("pagoId"))
    actor = _parte(pago, uid)
    aplicar_transicion(pago, "cancelar", actor, uid, _ahora_ms())
    cobro_id = pago.get("stripePaymentIntentId")
    if cobro_id:
        # Primero en Stripe: un cobro anulado ya no se puede pagar. Si ya está
        # pagado (o pagándose) no se cancela: el webhook lo pasará a retenido
        api = _stripe()
        cobro = api.leer_cobro(cobro_id)
        if cobro.get("status") in ("succeeded", "processing"):
            raise ErrorPago("cobro_en_curso")
        if cobro.get("status") != "canceled":
            api.cancelar_cobro(cobro_id, f"{pago['id']}:cancelar:{cobro_id}")
    return {"estado": _transicion(db, pago["id"], "cancelar", actor, uid)["estado"]}


# ── T-E5: terminar, confirmar, disputar y resolver ──

def _terminar(db, uid: str, datos: dict) -> dict:
    pago_id = _comprobar_id(datos.get("pagoId"), "pagoId")
    return {"estado": _transicion(db, pago_id, "terminar", PROFESIONAL, uid)["estado"]}


def _confirmar(db, uid: str, datos: dict) -> dict:
    pago_id = _comprobar_id(datos.get("pagoId"), "pagoId")
    return {"estado": _mover_dinero(db, pago_id, "confirmar", CLIENTE, uid, "transferencia")["estado"]}


def _disputar(db, uid: str, datos: dict) -> dict:
    pago = _leer_pago(db, datos.get("pagoId"))
    motivo = datos.get("motivo")
    aplicar_transicion(pago, "disputar", CLIENTE, uid, _ahora_ms(), motivo=motivo)
    visto = _comprobar_operacion(db, pago, "disputa")
    nuevo = _transicion(db, pago["id"], "disputar", CLIENTE, uid, motivo=motivo, operacion=("disputa", visto))
    return {"estado": nuevo["estado"]}


def _resolver(db, uid: str, datos: dict) -> dict:
    decision = datos.get("decision")
    if decision not in ("liberar", "reembolsar"):
        raise ErrorPago("decision_no_valida")
    pago_id = _comprobar_id(datos.get("pagoId"), "pagoId")
    if decision == "liberar":
        nuevo = _mover_dinero(db, pago_id, "resolver_liberar", ADMIN, uid, "transferencia")
    else:
        nuevo = _mover_dinero(db, pago_id, "resolver_reembolsar", ADMIN, uid, "reembolso")
    print(f"[ADMIN] {uid} resolvió la disputa del pago {pago_id}: {decision}")
    return {"estado": nuevo["estado"]}


def liberar_vencidos(db) -> dict:
    """Pagos en `terminado` con el plazo vencido, por lotes y con tope por
    ejecución. Un fallo en uno no para los demás: vuelve a probarse en la
    siguiente ejecución, y la transferencia no se duplica (_mover_dinero)."""
    consulta = (db.collection("pagos")
                .where(filter=FieldFilter("estado", "==", "terminado"))
                .where(filter=FieldFilter("liberarEn", "<=", _ahora_ms()))
                .order_by("liberarEn")
                .limit(LIBERAR_LOTE))
    resultado = {"liberados": 0, "fallidos": 0}
    ultimo, vistos = None, 0
    while vistos < LIBERAR_MAX_EJECUCION:
        lote = list((consulta if ultimo is None else consulta.start_after(ultimo)).stream())
        for snap in lote[:LIBERAR_MAX_EJECUCION - vistos]:
            vistos += 1
            try:
                _mover_dinero(db, snap.id, "vencer_plazo", SISTEMA, None, "transferencia")
                resultado["liberados"] += 1
            except Exception as e:
                resultado["fallidos"] += 1
                print(f"[PAGOS] No se pudo liberar {snap.id}: {e}")
                if isinstance(e, ErrorPago) and e.codigo == "stripe_no_configurado":
                    return resultado        # sin clave válida fallarían todos
        if len(lote) < LIBERAR_LOTE:
            break
        ultimo = lote[-1]
    return resultado


# ── Webhook de Stripe ──

def _verificar_evento(payload: bytes, firma) -> tuple:
    """(evento, origen) si la firma vale con el secreto de la plataforma o con
    el de Connect; (None, None) si no vale con ninguno o el cuerpo no es JSON."""
    secretos = (("plataforma", STRIPE_WEBHOOK_SECRET.value), ("connect", STRIPE_WEBHOOK_SECRET_CONNECT.value))
    for origen, secreto in secretos:
        if not secreto:
            continue
        try:
            stripe.Webhook.construct_event(payload, firma, secreto)
        except stripe.SignatureVerificationError:
            continue
        except ValueError:
            return None, None
        try:
            evento = json.loads(payload)
        except ValueError:
            return None, None
        return (evento, origen) if isinstance(evento, dict) else (None, None)
    return None, None


def _evento_cuenta(db, txn, cuenta: dict, evento: dict) -> tuple:
    """account.updated → estado de /cuentas_stripe/{uid}."""
    uid = (cuenta.get("metadata") or {}).get("uid")
    if not isinstance(uid, str) or not _ID.fullmatch(uid) or evento.get("account") not in (None, cuenta.get("id")):
        print(f"[PAGOS] account.updated de {cuenta.get('id')} sin uid válido: descartado")
        return "descartado", [], None
    ref = db.collection("cuentas_stripe").document(uid)
    snap = ref.get(transaction=txn)
    actual = (snap.to_dict() or {}) if snap.exists else {}
    if actual.get("accountId") != cuenta.get("id"):
        print(f"[PAGOS] account.updated de {cuenta.get('id')} no es la cuenta de {uid}: descartado")
        return "descartado", [], None
    creado = int(evento.get("created") or 0)
    # Stripe no garantiza el orden de entrega: uno antiguo no pisa uno nuevo
    if creado < int(actual.get("estadoStripeEn") or 0):
        return "antiguo", [], None
    datos = {**datos_cuenta_stripe(cuenta), "estadoStripeEn": creado, "actualizado": _ahora_ms()}
    return "aplicado", [(ref, datos, True)], None


def _fallo_antiguo(pago: dict, evento: dict) -> bool:
    """Un fallo de cobro de antes de la última vez que el pago entró en
    pendiente_pago (llega tarde, después de reintentar): no lo devuelve a fallido."""
    creado_ms = int(evento.get("created") or 0) * 1000
    entradas = [h.get("fecha") or 0 for h in pago.get("historial") or [] if h.get("estado") == "pendiente_pago"]
    return bool(entradas) and creado_ms < max(entradas)


def _evento_cobro(db, txn, cobro: dict, evento: dict) -> tuple:
    """payment_intent.succeeded → cobro_ok; payment_intent.payment_failed → cobro_fallido.
    Solo si el PaymentIntent es el del pago y cuadran importe y moneda."""
    exito = evento.get("type") == "payment_intent.succeeded"
    pago_id = (cobro.get("metadata") or {}).get("pagoId")
    if not isinstance(pago_id, str) or not _ID.fullmatch(pago_id):
        print(f"[PAGOS] {evento.get('type')} de {cobro.get('id')} sin pagoId: descartado")
        return "descartado", [], None
    ref = db.collection("pagos").document(pago_id)
    snap = ref.get(transaction=txn)
    if not snap.exists:
        print(f"[PAGOS] {evento.get('type')} para el pago {pago_id}, que no existe: descartado")
        return "descartado", [], None
    pago = snap.to_dict()
    motivo = _cobro_no_cuadra(pago, cobro, cobrado=exito)
    if motivo:
        print(f"[PAGOS] {evento.get('type')} del pago {pago_id} no cuadra ({motivo}): no se cambia nada")
        return "descartado", [], None
    ahora = _ahora_ms()
    try:
        if exito:
            if pago.get("estado") == "fallido":
                # Tras un fallo, la pantalla de pago deja probar otra tarjeta sin
                # salir: es el cliente reintentando, aunque llegue por el webhook
                pago = aplicar_transicion(pago, "reintentar", CLIENTE, pago.get("cliente"), ahora)
            nuevo = aplicar_transicion(pago, "cobro_ok", SISTEMA, None, ahora)
        else:
            if _fallo_antiguo(pago, evento):
                return "antiguo", [], None
            nuevo = aplicar_transicion(pago, "cobro_fallido", SISTEMA, None, ahora)
    except ErrorPago as e:
        aviso = " ERROR: hay dinero cobrado, revisarlo" if exito else ""
        print(f"[PAGOS] {evento.get('type')} del pago {pago_id} en {pago.get('estado')} ({e.codigo}): no se aplica.{aviso}")
        return "no_aplicable", [], None
    evento_pago = "cobro_ok" if exito else "cobro_fallido"
    return "aplicado", [(ref, nuevo, False)], (nuevo, evento_pago, SISTEMA)


def _evento_contracargo(db, txn, disputa: dict, evento: dict) -> tuple:
    """charge.dispute.created → contracargo."""
    cobro_id = disputa.get("payment_intent")
    if not isinstance(cobro_id, str) or not cobro_id:
        print(f"[PAGOS] Contracargo {disputa.get('id')} sin PaymentIntent: descartado")
        return "descartado", [], None
    encontrados = list(db.collection("pagos")
                       .where(filter=FieldFilter("stripePaymentIntentId", "==", cobro_id)).limit(2).stream())
    if len(encontrados) != 1:
        print(f"[PAGOS] Contracargo {disputa.get('id')}: {len(encontrados)} pagos con {cobro_id}: descartado")
        return "descartado", [], None
    ref = encontrados[0].reference
    op_ref = db.collection("operaciones_pagos").document(ref.id)
    pago = ref.get(transaction=txn).to_dict()
    op = op_ref.get(transaction=txn)
    try:
        nuevo = aplicar_transicion(pago, "contracargo", SISTEMA, None, _ahora_ms())
    except ErrorPago as e:
        print(f"[PAGOS] Contracargo del pago {ref.id} en {pago.get('estado')} ({e.codigo}): no se aplica")
        return "no_aplicable", [], None
    if pago.get("stripeTransferId") or (op.exists and (op.to_dict() or {}).get("operacion") == "transferencia"):
        print(f"[PAGOS] Contracargo del pago {ref.id} con el dinero ya transferido (o transfiriéndose) "
              f"al profesional: {pago.get('stripeTransferId') or 'en curso'}")
    return "aplicado", [(ref, nuevo, False)], (nuevo, "contracargo", SISTEMA)


_MANEJADORES = {
    "account.updated": _evento_cuenta,
    "payment_intent.succeeded": _evento_cobro,
    "payment_intent.payment_failed": _evento_cobro,
    "charge.dispute.created": _evento_contracargo,
}


def procesar_evento(db, evento: dict, origen: str) -> str:
    """Aplica un evento de Stripe ya verificado. El id va a /eventos_stripe en la
    misma transacción que el efecto: un evento repetido no hace nada."""
    tipo, evento_id = evento.get("type"), evento.get("id")
    manejador = _MANEJADORES.get(tipo)
    if manejador is None:
        return "ignorado"
    if evento.get("livemode") is not False:
        print(f"[PAGOS] Evento live {evento_id} ignorado: solo se trabaja en modo test")
        return "ignorado_live"
    if not isinstance(evento_id, str) or not _ID.fullmatch(evento_id):
        return "ignorado"
    # Cobros y contracargos son de la plataforma; un evento de una cuenta
    # conectada (lleva `account`) no puede mover un pago
    if tipo != "account.updated" and (origen != "plataforma" or evento.get("account")):
        print(f"[PAGOS] {tipo} {evento_id} de una cuenta conectada: ignorado")
        return "ignorado"
    objeto = (evento.get("data") or {}).get("object") or {}
    evento_ref = db.collection("eventos_stripe").document(evento_id)
    aviso = {}

    @firestore.transactional
    def _procesar(txn):
        if evento_ref.get(transaction=txn).exists:
            return "duplicado"
        resultado, escrituras, avisar = manejador(db, txn, objeto, evento)
        for ref, datos, merge in escrituras:
            txn.set(ref, datos, merge=merge)
        if escrituras:
            txn.set(evento_ref, {"tipo": tipo, "procesado": _ahora_ms()})
        aviso["pago"] = avisar
        return resultado

    resultado = _procesar(db.transaction())
    if aviso.get("pago"):
        _avisar(db, *aviso["pago"])
    return resultado


def _respuesta(codigo: int, resultado: str) -> https_fn.Response:
    return https_fn.Response(json.dumps({"resultado": resultado}), status=codigo, mimetype="application/json")


# ── Borrado de cuenta ──

def pagos_activos(db, uid: str) -> list:
    """Ids de los pagos de `uid` (como cliente o profesional) con dinero en camino o retenido."""
    activos = []
    for campo in ("cliente", "profesional"):
        for snap in db.collection("pagos").where(filter=FieldFilter(campo, "==", uid)).stream():
            if (snap.to_dict() or {}).get("estado") in ESTADOS_CON_DINERO:
                activos.append(snap.id)
    return activos


def seudonimo(uid: str) -> str:
    """SHA-256 del uid, el mismo cálculo que _cuenta_ofuscada en main.py."""
    return hashlib.sha256(uid.encode("utf-8")).hexdigest()


def seudonimizar_pagos(db, uid: str) -> int:
    """Al borrar una cuenta, sus /pagos no se borran: son registros contables
    que hay que conservar. Se cambia su uid por un seudónimo (también en el
    historial), así que ya no se pueden leer con esa cuenta ni ligar a ella sin
    el uid. El plazo de conservación (y cuándo borrarlos) lo tiene que
    confirmar el usuario: plan, §4. Devuelve cuántos ha cambiado."""
    alias = seudonimo(uid)
    total = 0
    for campo in ("cliente", "profesional"):
        for snap in db.collection("pagos").where(filter=FieldFilter(campo, "==", uid)).stream():
            pago = snap.to_dict() or {}
            pago[campo] = alias
            pago["historial"] = [{**h, "uid": alias} if isinstance(h, dict) and h.get("uid") == uid else h
                                 for h in pago.get("historial") or []]
            snap.reference.set(pago)
            total += 1
    return total


# ── Avisos a las partes ──

_ESTADOS_AVISO = ("solicitado", "pendiente_pago", "retenido", "fallido", "cancelado",
                  "terminado", "liberado", "en_disputa", "reembolsado", "contracargo")
# (título, textos por estado en el orden de _ESTADOS_AVISO), en los 14 idiomas
_AVISOS_PAGO = {
    "Español": ("Pagos", (
        "Tienes una nueva solicitud de pago. Revísala en el chat.",
        "El cliente ha aceptado tu solicitud de pago.",
        "Pago completado: ZeroHaus lo retiene hasta que termine el trabajo.",
        "El cobro no se ha completado.",
        "Se ha cancelado una solicitud de pago.",
        "El trabajo está marcado como terminado. Confírmalo o abre una disputa antes de que se libere el pago.",
        "El pago se ha liberado al profesional.",
        "Se ha abierto una disputa sobre un pago. ZeroHaus la revisará.",
        "El pago se ha reembolsado al cliente.",
        "El banco ha devuelto el cargo de un pago. ZeroHaus lo revisará.")),
    "English": ("Payments", (
        "You have a new payment request. Check it in the chat.",
        "The client has accepted your payment request.",
        "Payment completed: ZeroHaus holds it until the job is finished.",
        "The charge could not be completed.",
        "A payment request has been cancelled.",
        "The job has been marked as finished. Confirm it or open a dispute before the payment is released.",
        "The payment has been released to the professional.",
        "A dispute has been opened on a payment. ZeroHaus will review it.",
        "The payment has been refunded to the client.",
        "The bank has reversed a payment charge. ZeroHaus will review it.")),
    "Català": ("Pagaments", (
        "Tens una nova sol·licitud de pagament. Revisa-la al xat.",
        "El client ha acceptat la teva sol·licitud de pagament.",
        "Pagament completat: ZeroHaus el reté fins que s'acabi la feina.",
        "El cobrament no s'ha completat.",
        "S'ha cancel·lat una sol·licitud de pagament.",
        "La feina està marcada com a acabada. Confirma-ho o obre una disputa abans que s'alliberi el pagament.",
        "El pagament s'ha alliberat al professional.",
        "S'ha obert una disputa sobre un pagament. ZeroHaus la revisarà.",
        "El pagament s'ha reemborsat al client.",
        "El banc ha retornat el càrrec d'un pagament. ZeroHaus el revisarà.")),
    "Euskara": ("Ordainketak", (
        "Ordainketa-eskaera berri bat duzu. Begiratu txatean.",
        "Bezeroak zure ordainketa-eskaera onartu du.",
        "Ordainketa eginda: ZeroHausek gordeko du lana amaitu arte.",
        "Kobrantza ezin izan da osatu.",
        "Ordainketa-eskaera bat bertan behera utzi da.",
        "Lana amaitutzat markatu da. Berretsi edo ireki eztabaida bat ordainketa askatu aurretik.",
        "Ordainketa profesionalari askatu zaio.",
        "Ordainketa baten gaineko eztabaida bat ireki da. ZeroHausek berrikusiko du.",
        "Ordainketa bezeroari itzuli zaio.",
        "Bankuak ordainketa baten kargua itzuli du. ZeroHausek berrikusiko du.")),
    "Galego": ("Pagamentos", (
        "Tes unha nova solicitude de pagamento. Revísaa no chat.",
        "O cliente aceptou a túa solicitude de pagamento.",
        "Pagamento completado: ZeroHaus retéñao ata que remate o traballo.",
        "O cobro non se completou.",
        "Cancelouse unha solicitude de pagamento.",
        "O traballo está marcado como rematado. Confírmao ou abre unha disputa antes de que se libere o pagamento.",
        "O pagamento liberouse ao profesional.",
        "Abriuse unha disputa sobre un pagamento. ZeroHaus revisaraa.",
        "O pagamento reembolsouse ao cliente.",
        "O banco devolveu o cargo dun pagamento. ZeroHaus revisarao.")),
    "Português": ("Pagamentos", (
        "Tem um novo pedido de pagamento. Veja-o no chat.",
        "O cliente aceitou o seu pedido de pagamento.",
        "Pagamento concluído: o ZeroHaus retém-no até o trabalho terminar.",
        "A cobrança não foi concluída.",
        "Um pedido de pagamento foi cancelado.",
        "O trabalho foi marcado como concluído. Confirme-o ou abra uma disputa antes de o pagamento ser libertado.",
        "O pagamento foi libertado ao profissional.",
        "Foi aberta uma disputa sobre um pagamento. O ZeroHaus vai analisá-la.",
        "O pagamento foi reembolsado ao cliente.",
        "O banco estornou a cobrança de um pagamento. O ZeroHaus vai analisá-la.")),
    "Français": ("Paiements", (
        "Vous avez une nouvelle demande de paiement. Consultez-la dans le chat.",
        "Le client a accepté votre demande de paiement.",
        "Paiement effectué : ZeroHaus le conserve jusqu'à la fin du travail.",
        "Le paiement n'a pas pu aboutir.",
        "Une demande de paiement a été annulée.",
        "Le travail a été marqué comme terminé. Confirmez-le ou ouvrez un litige avant le versement du paiement.",
        "Le paiement a été versé au professionnel.",
        "Un litige a été ouvert sur un paiement. ZeroHaus va l'examiner.",
        "Le paiement a été remboursé au client.",
        "La banque a contesté le débit d'un paiement. ZeroHaus va l'examiner.")),
    "Deutsch": ("Zahlungen", (
        "Du hast eine neue Zahlungsanfrage. Sieh sie dir im Chat an.",
        "Der Kunde hat deine Zahlungsanfrage angenommen.",
        "Zahlung abgeschlossen: ZeroHaus hält sie zurück, bis der Auftrag erledigt ist.",
        "Die Abbuchung konnte nicht abgeschlossen werden.",
        "Eine Zahlungsanfrage wurde storniert.",
        "Der Auftrag wurde als erledigt markiert. Bestätige ihn oder eröffne einen Streitfall, bevor die Zahlung freigegeben wird.",
        "Die Zahlung wurde an die Fachkraft freigegeben.",
        "Zu einer Zahlung wurde ein Streitfall eröffnet. ZeroHaus prüft ihn.",
        "Die Zahlung wurde dem Kunden erstattet.",
        "Die Bank hat die Abbuchung einer Zahlung zurückgebucht. ZeroHaus prüft das.")),
    "Italiano": ("Pagamenti", (
        "Hai una nuova richiesta di pagamento. Controllala nella chat.",
        "Il cliente ha accettato la tua richiesta di pagamento.",
        "Pagamento completato: ZeroHaus lo trattiene fino alla fine del lavoro.",
        "L'addebito non è andato a buon fine.",
        "Una richiesta di pagamento è stata annullata.",
        "Il lavoro è stato segnato come terminato. Confermalo o apri una contestazione prima che il pagamento venga sbloccato.",
        "Il pagamento è stato sbloccato al professionista.",
        "È stata aperta una contestazione su un pagamento. ZeroHaus la esaminerà.",
        "Il pagamento è stato rimborsato al cliente.",
        "La banca ha stornato l'addebito di un pagamento. ZeroHaus lo esaminerà.")),
    "العربية": ("المدفوعات", (
        "لديك طلب دفع جديد. راجعه في المحادثة.",
        "قبل العميل طلب الدفع الخاص بك.",
        "اكتمل الدفع: تحتفظ به ZeroHaus حتى انتهاء العمل.",
        "تعذّر إتمام عملية الدفع.",
        "تم إلغاء طلب دفع.",
        "تم وضع علامة على العمل بأنه منتهٍ. أكّده أو افتح نزاعاً قبل تحرير الدفعة.",
        "تم تحرير الدفعة للمهني.",
        "تم فتح نزاع بشأن دفعة. ستراجعه ZeroHaus.",
        "تم استرداد الدفعة للعميل.",
        "ألغى البنك خصم إحدى الدفعات. ستراجعه ZeroHaus.")),
    "中文": ("付款", (
        "您有一条新的付款请求，请在聊天中查看。",
        "客户已接受您的付款请求。",
        "付款已完成：ZeroHaus 将暂存该款项，直到工作完成。",
        "扣款未能完成。",
        "一条付款请求已取消。",
        "工作已标记为完成。请在付款发放前确认或提出争议。",
        "款项已发放给专业人员。",
        "有一笔付款被提出争议，ZeroHaus 将进行审核。",
        "款项已退还给客户。",
        "银行已撤回一笔付款的扣款，ZeroHaus 将进行审核。")),
    "Română": ("Plăți", (
        "Ai o nouă cerere de plată. Verific-o în chat.",
        "Clientul a acceptat cererea ta de plată.",
        "Plată finalizată: ZeroHaus o reține până la terminarea lucrării.",
        "Plata nu a putut fi finalizată.",
        "O cerere de plată a fost anulată.",
        "Lucrarea a fost marcată ca terminată. Confirm-o sau deschide o dispută înainte ca plata să fie eliberată.",
        "Plata a fost eliberată profesionistului.",
        "S-a deschis o dispută privind o plată. ZeroHaus o va analiza.",
        "Plata a fost rambursată clientului.",
        "Banca a anulat debitarea unei plăți. ZeroHaus o va analiza.")),
    "Nederlands": ("Betalingen", (
        "Je hebt een nieuw betaalverzoek. Bekijk het in de chat.",
        "De klant heeft je betaalverzoek geaccepteerd.",
        "Betaling voltooid: ZeroHaus houdt het bedrag vast tot het werk klaar is.",
        "De afschrijving is niet gelukt.",
        "Een betaalverzoek is geannuleerd.",
        "Het werk is als klaar gemarkeerd. Bevestig het of open een geschil voordat de betaling wordt vrijgegeven.",
        "De betaling is vrijgegeven aan de vakman.",
        "Er is een geschil geopend over een betaling. ZeroHaus gaat het beoordelen.",
        "De betaling is terugbetaald aan de klant.",
        "De bank heeft de afschrijving van een betaling teruggedraaid. ZeroHaus gaat het beoordelen.")),
    "Polski": ("Płatności", (
        "Masz nową prośbę o płatność. Sprawdź ją na czacie.",
        "Klient zaakceptował Twoją prośbę o płatność.",
        "Płatność zakończona: ZeroHaus przechowuje ją do zakończenia pracy.",
        "Nie udało się pobrać płatności.",
        "Prośba o płatność została anulowana.",
        "Praca została oznaczona jako zakończona. Potwierdź to lub otwórz spór, zanim płatność zostanie zwolniona.",
        "Płatność została przekazana fachowcowi.",
        "Otwarto spór dotyczący płatności. ZeroHaus go rozpatrzy.",
        "Płatność została zwrócona klientowi.",
        "Bank cofnął obciążenie z tytułu płatności. ZeroHaus to rozpatrzy.")),
}


def _texto_aviso(idioma: str, estado: str) -> tuple:
    titulo, textos = _AVISOS_PAGO.get(idioma) or _AVISOS_PAGO["Español"]
    return titulo, textos[_ESTADOS_AVISO.index(estado)]


def _avisar(db, pago: dict, evento: str, actor: str) -> None:
    """Push y notificación en la app a las partes del pago, salvo a quien lo ha
    hecho (con el sistema o el administrador, a las dos). Nunca hace fallar la
    operación: el dinero ya se ha movido y el aviso es lo de menos."""
    try:
        quien = pago.get(_CAMPO_PARTE[actor]) if actor in _CAMPO_PARTE else None
        orden = len(pago.get("historial") or [])
        for campo in ("cliente", "profesional"):
            uid = pago.get(campo)
            # Un seudónimo es una cuenta ya borrada: no hay a quién avisar
            if not isinstance(uid, str) or not uid or uid == quien or _SEUDONIMO.fullmatch(uid):
                continue
            try:
                pref = _ayuda("preferencias")(db, uid)
                titulo, detalle = _texto_aviso(pref["idioma"], pago["estado"])
                # Id fijo por cambio de estado: un reintento no la duplica
                _ayuda("crear_notificacion")(db, uid, titulo, detalle, "pago", f"pago_{pago['id']}_{orden}_{uid}")
                if pref["push"]:
                    _ayuda("enviar_push")(pref["destino"], titulo, detalle,
                                          {"tipo": "pago", "pagoId": pago["id"]}, pref["sonido"])
            except Exception as e:
                print(f"[PAGOS] No se pudo avisar a {uid} del pago {pago.get('id')}: {e}")
    except Exception as e:
        print(f"[PAGOS] No se pudo avisar del pago {pago.get('id')}: {e}")


# ── Funciones de Firebase ──

_F = https_fn.FunctionsErrorCode
_CODIGOS_HTTPS = {
    **dict.fromkeys(("importe_no_entero", "importe_bajo_minimo", "importe_sobre_maximo", "descripcion_no_valida",
                     "motivo_no_valido", "id_no_valido", "partes_no_validas", "decision_no_valida",
                     "version_no_valida"), _F.INVALID_ARGUMENT),
    **dict.fromkeys(("uid_no_autorizado", "actor_no_permitido", "no_es_profesional"), _F.PERMISSION_DENIED),
    **dict.fromkeys(("pago_no_encontrado", "chat_no_valido", "sin_cuenta"), _F.NOT_FOUND),
    "limite_solicitudes": _F.RESOURCE_EXHAUSTED,
}   # el resto (pagos_deshabilitados, transicion_no_permitida, bloqueo…): FAILED_PRECONDITION


def _error_https(e: ErrorPago) -> https_fn.HttpsError:
    return https_fn.HttpsError(_CODIGOS_HTTPS.get(e.codigo, _F.FAILED_PRECONDITION),
                               f"No se puede completar la operación de pago ({e.codigo}).", {"motivo": e.codigo})


def _ejecutar(req, accion, *, nueva: bool = False, admin: bool = False) -> dict:
    """Lo común de las callables: sesión con el email verificado (o
    administrador), interruptor de pagos y errores como HttpsError con
    `details.motivo`.

    `nueva`: abre un flujo de dinero nuevo (alta, solicitar, aceptar,
    reintentar) y se corta con los pagos apagados. Lo demás sigue funcionando
    con los pagos que ya existen, para que no se quede dinero atascado."""
    try:
        if admin:
            _ayuda("verificar_admin")(req)
            uid = req.auth.uid
        else:
            uid = _ayuda("exigir_email_verificado")(req)
        if nueva and not leer_parametros_pago().pagos_habilitados:
            raise ErrorPago("pagos_deshabilitados")
        datos = req.data if isinstance(req.data, dict) else {}
        return accion(firestore.client(), uid, datos)
    except ErrorPago as e:
        raise _error_https(e) from None
    except stripe.StripeError as e:
        print(f"[PAGOS] Stripe falló en {accion.__name__}: {type(e).__name__}: {e}")
        raise https_fn.HttpsError(_F.UNAVAILABLE, "El servicio de pagos no responde. Inténtalo más tarde.",
                                  {"motivo": "stripe"}) from None


def _opciones(con_stripe: bool = True) -> dict:
    # La clave de Stripe solo la reciben las funciones que llaman a Stripe
    return dict(region=REGION, enforce_app_check=True, max_instances=MAX_INSTANCIAS_PAGOS,
                secrets=[STRIPE_SECRET_KEY] if con_stripe else None)


@https_fn.on_call(**_opciones())
def stripe_crear_enlace_cuenta(req: https_fn.CallableRequest) -> dict:
    """Alta del profesional en Stripe Connect (Express), o su continuación. → {url}"""
    return _ejecutar(req, _crear_enlace_cuenta, nueva=True)


@https_fn.on_call(**_opciones())
def stripe_estado_cuenta(req: https_fn.CallableRequest) -> dict:
    """Estado de la cuenta Connect, refrescado desde Stripe.
    → {estado, chargesEnabled, payoutsEnabled, detailsSubmitted}"""
    return _ejecutar(req, _estado_cuenta)


@https_fn.on_call(**_opciones(con_stripe=False))
def pagos_solicitar(req: https_fn.CallableRequest) -> dict:
    """El profesional pide un pago al otro participante de un chat.
    Args: chatId, importeCent, descripcion. → {pagoId}"""
    return _ejecutar(req, _solicitar, nueva=True)


@https_fn.on_call(**_opciones())
def pagos_aceptar(req: https_fn.CallableRequest) -> dict:
    """El cliente acepta la solicitud y recibe lo que necesita PaymentSheet.
    Args: pagoId, stripeVersion (opcional). → {paymentIntentClientSecret, customerId, ephemeralKeySecret}"""
    return _ejecutar(req, _aceptar, nueva=True)


@https_fn.on_call(**_opciones())
def pagos_reintentar(req: https_fn.CallableRequest) -> dict:
    """Como pagos_aceptar, tras un cobro fallido."""
    return _ejecutar(req, _reintentar, nueva=True)


@https_fn.on_call(**_opciones())
def pagos_cancelar(req: https_fn.CallableRequest) -> dict:
    """Cancela un pago antes de cobrarlo (cliente o profesional). Args: pagoId. → {estado}"""
    return _ejecutar(req, _cancelar)


@https_fn.on_call(**_opciones(con_stripe=False))
def pagos_terminar(req: https_fn.CallableRequest) -> dict:
    """El profesional marca el trabajo como terminado; empieza el plazo de liberación. → {estado}"""
    return _ejecutar(req, _terminar)


@https_fn.on_call(**_opciones())
def pagos_confirmar(req: https_fn.CallableRequest) -> dict:
    """El cliente confirma el trabajo y el dinero se transfiere al profesional. → {estado}"""
    return _ejecutar(req, _confirmar)


@https_fn.on_call(**_opciones())
def pagos_disputar(req: https_fn.CallableRequest) -> dict:
    """El cliente abre una disputa. Args: pagoId, motivo. → {estado}"""
    return _ejecutar(req, _disputar)


@https_fn.on_call(**_opciones())
def pagos_resolver(req: https_fn.CallableRequest) -> dict:
    """El administrador resuelve una disputa. Args: pagoId, decision ("liberar" o "reembolsar"). → {estado}"""
    return _ejecutar(req, _resolver, admin=True)


@https_fn.on_request(region=REGION, max_instances=MAX_INSTANCIAS_WEBHOOK,
                     secrets=[STRIPE_WEBHOOK_SECRET, STRIPE_WEBHOOK_SECRET_CONNECT])
def stripe_webhook(req: https_fn.Request) -> https_fn.Response:
    """Eventos de Stripe (endpoint de la plataforma y de Connect). 400 si la
    firma no vale; 500 si falla al aplicarlo, para que Stripe lo reintente; 200
    en lo demás, también en los eventos que no se manejan o repetidos."""
    if req.method != "POST":
        return _respuesta(405, "solo POST")
    evento, origen = _verificar_evento(req.get_data(), req.headers.get("Stripe-Signature"))
    if evento is None:
        print("[PAGOS] Webhook con la firma no válida: rechazado")
        return _respuesta(400, "firma no válida")
    try:
        resultado = procesar_evento(firestore.client(), evento, origen)
    except Exception as e:
        print(f"[PAGOS] Error al procesar {evento.get('type')} {evento.get('id')}: {e}")
        return _respuesta(500, "error")
    print(f"[PAGOS] {evento.get('type')} {evento.get('id')}: {resultado}")
    return _respuesta(200, resultado)


@scheduler_fn.on_schedule(
    schedule="every 1 hours",
    timezone=scheduler_fn.Timezone("Europe/Madrid"),
    region=REGION,
    timeout_sec=540,
    max_instances=1,              # dos a la vez solo se pisarían (sin duplicar dinero)
    secrets=[STRIPE_SECRET_KEY],
)
def pagos_liberar_vencidos(event: scheduler_fn.ScheduledEvent) -> None:
    """Libera los pagos terminados cuyo plazo ha vencido sin disputa."""
    print(f"[PAGOS] Liberación de plazos vencidos: {liberar_vencidos(firestore.client())}")
