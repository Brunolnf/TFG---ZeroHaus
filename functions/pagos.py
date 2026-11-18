"""
Pago con retención (bloque E del plan): parámetros y máquina de estados.

Stripe Connect con «separate charges and transfers»: el cliente paga a la
plataforma, el dinero queda retenido en el saldo de Stripe de ZeroHaus y se
transfiere al profesional al liberarse. Aquí va lo que no depende de Stripe ni
de Firestore, para poder probarlo entero:

  leer_parametros_pago  Los 4 parámetros de Remote Config, con caché y, ante
                        cualquier duda, valores seguros (pagos apagados).
  calcular_importes     Importe, comisión y neto del profesional.
  nuevo_pago            Documento inicial de /pagos/{id}, en `solicitado`.
  aplicar_transicion    Cambio de estado según TRANSICIONES; lo demás se rechaza.

Todo el dinero va en céntimos (int) y en euros: con float, 5000 céntimos al
1,13 % dan 56,4999… en vez de 56,5 y se pierde el céntimo del redondeo.
"""
import copy
import math
import re
import threading
import time
from dataclasses import dataclass
from fractions import Fraction
from types import MappingProxyType
from typing import Callable, Optional

from google.auth import default as default_creds
from google.auth.transport.requests import AuthorizedSession

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
