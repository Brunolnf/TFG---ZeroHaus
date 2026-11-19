"""Tests del pago con retención con Stripe y Firestore simulados (T-E3 a T-E5).

Nada sale a la red: Stripe es _StripeFalso (o el SDK con sus métodos
sustituidos) y Firestore un diccionario con transacciones que, como las de
verdad, solo escriben si la función termina sin error.

Uso: python -m unittest discover -s functions-tests -v
"""
import copy
import hashlib
import hmac
import inspect
import json
import operator
import os
import sys
import time
import unittest
from types import SimpleNamespace
from unittest import mock

os.environ.setdefault("SMTP_USUARIO", "tests@example.com")
os.environ.setdefault("SMTP_CLAVE", "tests")
RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, os.path.join(RAIZ, "functions"))

import main  # noqa: E402
import pagos  # noqa: E402
import stripe  # noqa: E402

F = main.https_fn.FunctionsErrorCode
DIA_MS = 24 * 60 * 60 * 1000
AHORA = 1_790_000_000_000
SECRETO_PLATAFORMA = "whsec_plataforma_de_prueba"
SECRETO_CONNECT = "whsec_connect_de_prueba"
FUNCIONES = ("stripe_crear_enlace_cuenta", "stripe_estado_cuenta", "stripe_webhook", "pagos_solicitar",
             "pagos_aceptar", "pagos_reintentar", "pagos_cancelar", "pagos_terminar", "pagos_confirmar",
             "pagos_disputar", "pagos_resolver", "pagos_liberar_vencidos")


# ── Firestore en memoria ──

class _Snap:
    def __init__(self, ref, datos):
        self.reference, self.id = ref, ref.id
        self._datos = copy.deepcopy(datos)
        self.exists = datos is not None

    def to_dict(self):
        return copy.deepcopy(self._datos)


class _Ref:
    def __init__(self, db, ruta):
        self._db, self.ruta, self.id = db, ruta, ruta.rsplit("/", 1)[-1]

    def get(self, transaction=None):
        if transaction is not None:
            transaction.leer()
        return _Snap(self, self._db.docs.get(self.ruta))

    def set(self, datos, merge=False):
        self._db.escribir([("set", self.ruta, copy.deepcopy(datos), merge)])

    def update(self, datos):
        self._db.escribir([("update", self.ruta, copy.deepcopy(datos), False)])

    def delete(self):
        self._db.escribir([("delete", self.ruta, None, False)])

    def collection(self, nombre):
        return _Consulta(self._db, f"{self.ruta}/{nombre}")


_OPS = {"==": operator.eq, "<=": operator.le, "<": operator.lt, ">=": operator.ge, ">": operator.gt,
        "array_contains": lambda a, b: isinstance(a, list) and b in a}


def _cumple(datos, f):
    if f.field_path not in datos:
        return False
    valor = datos[f.field_path]
    # Como Firestore: las desigualdades solo comparan valores del mismo tipo
    if f.op_string in ("<=", "<", ">=", ">") and (valor is None or type(valor) is not type(f.value)):
        return False
    return _OPS[f.op_string](valor, f.value)


class _Consulta:
    def __init__(self, db, ruta, filtros=(), orden=None, limite=None, despues=None):
        self._db, self._ruta = db, ruta
        self._filtros, self._orden, self._limite, self._despues = list(filtros), orden, limite, despues

    def _con(self, **cambios):
        actual = dict(filtros=self._filtros, orden=self._orden, limite=self._limite, despues=self._despues)
        return _Consulta(self._db, self._ruta, **{**actual, **cambios})

    def document(self, doc_id=None):
        if doc_id is None:
            self._db.contador += 1
            doc_id = f"auto{self._db.contador:05d}"
        return _Ref(self._db, f"{self._ruta}/{doc_id}")

    def where(self, filter):
        return self._con(filtros=self._filtros + [filter])

    def order_by(self, campo, direction="ASCENDING"):
        return self._con(orden=campo)

    def limit(self, n):
        return self._con(limite=n)

    def start_after(self, snap):
        return self._con(despues=snap)

    def stream(self):
        prefijo = f"{self._ruta}/"
        encontrados = [_Ref(self._db, ruta).get() for ruta, datos in sorted(self._db.docs.items())
                       if ruta.startswith(prefijo) and "/" not in ruta[len(prefijo):]
                       and all(_cumple(datos, f) for f in self._filtros)]
        if self._orden:
            encontrados = sorted((s for s in encontrados if self._orden in s.to_dict()),
                                 key=lambda s: (s.to_dict()[self._orden], s.id))
            if self._despues is not None:
                clave = (self._despues.to_dict()[self._orden], self._despues.id)
                encontrados = [s for s in encontrados if (s.to_dict()[self._orden], s.id) > clave]
        return iter(encontrados if self._limite is None else encontrados[:self._limite])


class _Txn:
    def __init__(self, db):
        self._db, self._escrituras = db, []

    def leer(self):
        if self._escrituras:
            raise AssertionError("Firestore exige leer antes de escribir en una transacción")

    def set(self, ref, datos, merge=False):
        self._escrituras.append(("set", ref.ruta, copy.deepcopy(datos), merge))

    def update(self, ref, datos):
        self._escrituras.append(("update", ref.ruta, copy.deepcopy(datos), False))

    def delete(self, ref):
        self._escrituras.append(("delete", ref.ruta, None, False))

    def confirmar(self):
        self._db.escribir(self._escrituras)


def _transaccional(funcion):
    """firestore.transactional: si la función falla, no se escribe nada."""
    def ejecutar(txn):
        resultado = funcion(txn)
        txn.confirmar()
        return resultado
    return ejecutar


def _fusionar(base, cambios):
    resultado = dict(base)
    for clave, valor in cambios.items():
        if isinstance(valor, dict) and isinstance(resultado.get(clave), dict):
            resultado[clave] = _fusionar(resultado[clave], valor)
        else:
            resultado[clave] = valor
    return resultado


class _FirestoreFalso:
    def __init__(self):
        self.docs = {}
        self.contador = 0
        self.fallar_en = {}     # ruta → cuántas escrituras más fallan (Firestore no responde)

    def collection(self, nombre):
        return _Consulta(self, nombre)

    def transaction(self):
        return _Txn(self)

    def batch(self):
        return SimpleNamespace(delete=lambda ref: ref.delete(), commit=lambda: None)

    def escribir(self, escrituras):
        for _, ruta, _, _ in escrituras:
            if self.fallar_en.get(ruta):
                self.fallar_en[ruta] -= 1
                raise RuntimeError(f"Firestore no responde al escribir {ruta}")
        for tipo, ruta, datos, merge in escrituras:
            if tipo == "delete":
                self.docs.pop(ruta, None)
            elif tipo == "update":
                if ruta not in self.docs:
                    raise main.NotFound(ruta)
                self.docs[ruta] = {**self.docs[ruta], **datos}
            else:
                self.docs[ruta] = _fusionar(self.docs.get(ruta) or {}, datos) if merge else datos


# ── Stripe simulado ──

class _StripeFalso:
    """Mismo contrato que pagos._StripeAPI. Como Stripe, una clave de
    idempotencia repetida devuelve el objeto que se creó la primera vez."""

    def __init__(self):
        self.llamadas = []        # (método, clave de idempotencia)
        self.cuentas, self.clientes, self.cobros = {}, {}, {}
        self.transferencias, self.reembolsos, self.versiones = [], [], []
        self._por_clave = {}
        self._n = 0
        self.caido = False

    def _id(self, prefijo):
        self._n += 1
        return f"{prefijo}_{self._n}"

    def _llamada(self, metodo, clave=None):
        self.llamadas.append((metodo, clave))
        if self.caido:
            raise stripe.APIConnectionError("Stripe no responde")

    def _idempotente(self, clave, crear):
        if clave not in self._por_clave:
            self._por_clave[clave] = crear()
        return copy.deepcopy(self._por_clave[clave])

    def creaciones(self, metodo):
        return [clave for m, clave in self.llamadas if m == metodo]

    def crear_cuenta(self, uid, clave):
        self._llamada("crear_cuenta", clave)

        def crear():
            cuenta = {"id": self._id("acct"), "metadata": {"uid": uid}, "details_submitted": False,
                      "charges_enabled": False, "payouts_enabled": False, "capabilities": {"transfers": "inactive"}}
            self.cuentas[cuenta["id"]] = cuenta
            return cuenta
        return self._idempotente(clave, crear)

    def crear_enlace_cuenta(self, account_id):
        self._llamada("crear_enlace_cuenta")
        assert account_id in self.cuentas
        return f"https://connect.stripe.com/setup/e/{account_id}"

    def leer_cuenta(self, account_id):
        self._llamada("leer_cuenta")
        return copy.deepcopy(self.cuentas[account_id])

    def crear_cliente(self, uid, clave):
        self._llamada("crear_cliente", clave)

        def crear():
            cliente = {"id": self._id("cus"), "metadata": {"uid": uid}}
            self.clientes[cliente["id"]] = cliente
            return cliente
        return self._idempotente(clave, crear)

    def crear_clave_efimera(self, customer_id, version):
        self._llamada("crear_clave_efimera")
        assert customer_id in self.clientes
        self.versiones.append(version)
        return f"{self._id('ek_test')}_secret"

    def crear_cobro(self, pago, customer_id, clave):
        self._llamada("crear_cobro", clave)

        def crear():
            cobro_id = self._id("pi")
            cobro = {"id": cobro_id, "object": "payment_intent", "amount": pago["importeCent"], "currency": "eur",
                     "customer": customer_id, "metadata": {"pagoId": pago["id"]}, "transfer_group": pago["id"],
                     "status": "requires_payment_method", "client_secret": f"{cobro_id}_secret_test",
                     "amount_received": 0, "latest_charge": None}
            self.cobros[cobro_id] = cobro
            return cobro
        return self._idempotente(clave, crear)

    def leer_cobro(self, cobro_id):
        self._llamada("leer_cobro")
        if cobro_id not in self.cobros:
            raise stripe.InvalidRequestError(f"No such payment_intent: {cobro_id}", "id")
        return copy.deepcopy(self.cobros[cobro_id])

    def cancelar_cobro(self, cobro_id, clave):
        self._llamada("cancelar_cobro", clave)
        cobro = self.cobros[cobro_id]
        if cobro["status"] in ("succeeded", "processing"):
            raise stripe.InvalidRequestError("No se puede cancelar un cobro hecho", None)
        cobro["status"] = "canceled"
        return copy.deepcopy(cobro)

    def buscar_transferencia(self, pago_id):
        self._llamada("buscar_transferencia")
        return next((copy.deepcopy(t) for t in self.transferencias if t["transfer_group"] == pago_id), None)

    def crear_transferencia(self, pago, account_id, cargo_id, clave):
        self._llamada("crear_transferencia", clave)
        assert any(c.get("latest_charge") == cargo_id and c["status"] == "succeeded" for c in self.cobros.values())

        def crear():
            t = {"id": self._id("tr"), "amount": pago["netoCent"], "currency": "eur", "destination": account_id,
                 "source_transaction": cargo_id, "transfer_group": pago["id"], "reversed": False}
            self.transferencias.append(t)
            return t
        return self._idempotente(clave, crear)

    def buscar_reembolso(self, cobro_id):
        self._llamada("buscar_reembolso")
        return next((copy.deepcopy(r) for r in self.reembolsos if r["payment_intent"] == cobro_id), None)

    def crear_reembolso(self, pago, clave):
        self._llamada("crear_reembolso", clave)

        def crear():
            r = {"id": self._id("re"), "payment_intent": pago["stripePaymentIntentId"],
                 "amount": pago["importeCent"], "status": "succeeded"}
            self.reembolsos.append(r)
            return r
        return self._idempotente(clave, crear)

    # Lo que hace el cliente en la pantalla de pago
    def pagar(self, cobro_id):
        cobro = self.cobros[cobro_id]
        cobro.update(status="succeeded", amount_received=cobro["amount"], latest_charge=self._id("ch"))

    def rechazar(self, cobro_id):
        self.cobros[cobro_id].update(status="requires_payment_method",
                                     last_payment_error={"code": "card_declined"})


# ── Ayudas ──

def plantilla(habilitados=True):
    return {"parameters": {"pagos_habilitados": {"defaultValue": {"value": "true" if habilitados else "false"}}}}


def llamar(funcion, uid, admin=False, verificado=True, **datos):
    """Ejecuta una callable (sin el envoltorio HTTP) como `uid`."""
    peticion = SimpleNamespace(auth=SimpleNamespace(uid=uid, token={"email_verified": verificado, "admin": admin}),
                               data=datos)
    return inspect.unwrap(funcion)(peticion)


_n_eventos = [0]


def evento(tipo, objeto, *, evento_id=None, creado=None, livemode=False, account=None):
    _n_eventos[0] += 1
    e = {"id": evento_id or f"evt_{_n_eventos[0]}", "object": "event", "type": tipo, "livemode": livemode,
         "created": creado if creado is not None else AHORA // 1000, "api_version": pagos.STRIPE_API_VERSION,
         "data": {"object": copy.deepcopy(objeto)}}
    if account:
        e["account"] = account
    return e


def firma_de(cuerpo: bytes, secreto=SECRETO_PLATAFORMA) -> str:
    """Cabecera Stripe-Signature, calculada como la calcula Stripe."""
    t = int(time.time())     # la firma se comprueba contra el reloj real (5 min de margen)
    firma = hmac.new(secreto.encode("utf-8"), f"{t}.".encode("utf-8") + cuerpo, hashlib.sha256).hexdigest()
    return f"t={t},v1={firma}"


def firmar(datos, secreto=SECRETO_PLATAFORMA):
    cuerpo = json.dumps(datos).encode("utf-8")
    return cuerpo, firma_de(cuerpo, secreto)


class _PeticionHttp:
    def __init__(self, cuerpo, firma=None, metodo="POST"):
        self.method, self._cuerpo = metodo, cuerpo
        self.headers = {} if firma is None else {"Stripe-Signature": firma}

    def get_data(self):
        return self._cuerpo


def cuenta_stripe(estado="activa", **cambios):
    return {"uid": "pro", "accountId": "acct_pro", "estado": estado, "chargesEnabled": False,
            "payoutsEnabled": estado == "activa", "detailsSubmitted": estado != "pendiente", **cambios}


class _Base(unittest.TestCase):
    """Firestore y Stripe simulados, pagos encendidos, un profesional con la
    cuenta Connect activa (`pro`), un cliente (`cli`) y un chat entre los dos."""

    def setUp(self):
        self.db = _FirestoreFalso()
        self.stripe = _StripeFalso()
        self.reloj = [AHORA]
        self.plantilla = plantilla()
        self.pushes = []
        pagos.borrar_cache_parametros()
        self.addCleanup(pagos.borrar_cache_parametros)
        parches = [
            mock.patch.object(pagos.firestore, "client", lambda: self.db),
            mock.patch.object(pagos.firestore, "transactional", _transaccional),
            mock.patch.object(pagos, "_stripe", lambda: self.stripe),
            mock.patch.object(pagos, "_descargar_plantilla", lambda: self.plantilla),
            mock.patch.object(pagos, "_ahora_ms", lambda: self.reloj[0]),
            mock.patch.dict(pagos._AYUDAS, {"enviar_push": self._push}),
            mock.patch.dict(os.environ, {"STRIPE_WEBHOOK_SECRET": SECRETO_PLATAFORMA,
                                         "STRIPE_WEBHOOK_SECRET_CONNECT": SECRETO_CONNECT}),
        ]
        parches.append(mock.patch("builtins.print"))
        for parche in parches:
            self.log = parche.start()
            self.addCleanup(parche.stop)
        self.db.docs.update({
            "usuarios/pro": {"tipoUsuario": "Técnico", "nombre": "Pro"},
            "usuarios/cli": {"tipoUsuario": "Propietario", "nombre": "Cli"},
            "usuarios/otro": {"tipoUsuario": "Empresa", "nombre": "Otro"},
            "chats/chat1": {"participantes": ["cli", "pro"]},
            "cuentas_stripe/pro": cuenta_stripe(),
            "ajustes/cli": {"fidFCM": "fid-cli", "idioma": "Español"},
            "ajustes/pro": {"fidFCM": "fid-pro", "idioma": "English"},
        })
        self.stripe.cuentas["acct_pro"] = {"id": "acct_pro", "metadata": {"uid": "pro"}, "details_submitted": True,
                                           "charges_enabled": False, "payouts_enabled": True,
                                           "capabilities": {"transfers": "active"}}

    def _push(self, destino, titulo, cuerpo, data=None, sonido=True):
        self.pushes.append((destino.get("fid"), titulo, cuerpo, data))

    def apagar_pagos(self):
        self.plantilla = plantilla(False)
        pagos.borrar_cache_parametros()

    def logs(self):
        return " ".join(str(c) for c in self.log.call_args_list)

    def error(self, funcion, uid, **datos):
        with self.assertRaises(main.https_fn.HttpsError) as error:
            llamar(funcion, uid, **datos)
        return error.exception

    def assertError(self, funcion, uid, codigo_esperado, motivo_esperado, **datos):
        e = self.error(funcion, uid, **datos)
        self.assertEqual((e.code, (e.details or {}).get("motivo")), (codigo_esperado, motivo_esperado))

    def pago(self, pago_id):
        return self.db.docs[f"pagos/{pago_id}"]

    def eventos_de(self, pago_id):
        return [h["evento"] for h in self.pago(pago_id)["historial"]]

    def webhook(self, datos, secreto=SECRETO_PLATAFORMA):
        cuerpo, firma = firmar(datos, secreto)
        respuesta = inspect.unwrap(pagos.stripe_webhook)(_PeticionHttp(cuerpo, firma))
        return respuesta.status_code, json.loads(respuesta.get_data())["resultado"]

    # Pasos del flujo
    def solicitar(self, importe=1005, chat="chat1"):
        return llamar(pagos.pagos_solicitar, "pro", chatId=chat, importeCent=importe,
                      descripcion="Cambiar la caldera")["pagoId"]

    def aceptar(self, pago_id, **datos):
        return llamar(pagos.pagos_aceptar, "cli", pagoId=pago_id, **datos)

    def evento_cobro(self, pago_id, tipo="payment_intent.succeeded", **cambios):
        cobro = {**self.stripe.cobros[self.pago(pago_id)["stripePaymentIntentId"]], **cambios}
        return evento(tipo, cobro)

    def cobrar(self, pago_id):
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        return self.webhook(self.evento_cobro(pago_id))

    def hasta_retenido(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.assertEqual(self.cobrar(pago_id), (200, "aplicado"))
        self.assertEqual(self.pago(pago_id)["estado"], "retenido")
        return pago_id

    def hasta_terminado(self):
        pago_id = self.hasta_retenido()
        self.assertEqual(llamar(pagos.pagos_terminar, "pro", pagoId=pago_id), {"estado": "terminado"})
        return pago_id


# ══ Configuración ══

class ConfiguracionTest(unittest.TestCase):

    def test_misma_region_que_main_y_todas_las_funciones_registradas_en_main(self):
        self.assertEqual(pagos.REGION, main.REGION)
        for nombre in FUNCIONES:
            with self.subTest(funcion=nombre):
                self.assertIs(getattr(main, nombre), getattr(pagos, nombre))
                self.assertTrue(hasattr(getattr(main, nombre), "__firebase_endpoint__"))

    def test_pocas_instancias_y_cada_funcion_solo_con_sus_secretos(self):
        con_clave = {"stripe_crear_enlace_cuenta", "stripe_estado_cuenta", "pagos_aceptar", "pagos_reintentar",
                     "pagos_cancelar", "pagos_confirmar", "pagos_disputar", "pagos_resolver", "pagos_liberar_vencidos"}
        for nombre in FUNCIONES:
            with self.subTest(funcion=nombre):
                endpoint = getattr(pagos, nombre).__firebase_endpoint__
                self.assertLessEqual(endpoint.maxInstances, 5)
                self.assertEqual(endpoint.region, [main.REGION])
                secretos = {s["key"] for s in endpoint.secretEnvironmentVariables or []}
                if nombre == "stripe_webhook":
                    self.assertEqual(secretos, {"STRIPE_WEBHOOK_SECRET", "STRIPE_WEBHOOK_SECRET_CONNECT"})
                else:
                    self.assertEqual(secretos, {"STRIPE_SECRET_KEY"} if nombre in con_clave else set())

    def test_sdk_y_version_de_la_api_fijados(self):
        with open(os.path.join(RAIZ, "functions", "requirements.txt"), encoding="utf-8") as f:
            self.assertIn("stripe==16.1.0", f.read().split())
        from stripe._api_version import _ApiVersion
        self.assertEqual(pagos.STRIPE_API_VERSION, _ApiVersion.CURRENT)

    def test_codigos_de_error_publicados_son_todos_los_que_se_usan(self):
        import re
        with open(os.path.join(RAIZ, "functions", "pagos.py"), encoding="utf-8") as f:
            fuente = f.read()
        usados = set(re.findall(r'ErrorPago\("([a-z_]+)"', fuente))
        usados |= set(re.findall(r'_texto\([^)]*"([a-z_]+)"\)', fuente))     # los que valida _texto
        self.assertEqual(usados, set(pagos.CODIGOS_ERROR))
        # Cada código llega a la app con un código HTTPS concreto
        for codigo in pagos.CODIGOS_ERROR:
            self.assertEqual(pagos._error_https(pagos.ErrorPago(codigo)).details, {"motivo": codigo})

    def test_la_tarea_programada_tiene_su_indice(self):
        with open(os.path.join(RAIZ, "firestore.indexes.json"), encoding="utf-8") as f:
            indices = json.load(f)["indexes"]
        self.assertIn({"collectionGroup": "pagos", "queryScope": "COLLECTION", "fields": [
            {"fieldPath": "estado", "order": "ASCENDING"}, {"fieldPath": "liberarEn", "order": "ASCENDING"}]},
            indices)


class ClaveStripeTest(unittest.TestCase):

    def test_solo_claves_de_modo_test(self):
        for clave in ("sk_test_123", "rk_test_123"):
            self.assertEqual(pagos.comprobar_clave_stripe(clave), clave)
        for clave in ("sk_live_123", "rk_live_123", "pk_test_123", "whsec_123", "", None, " sk_test_1"):
            with self.subTest(clave=clave), mock.patch("builtins.print") as log, \
                    self.assertRaises(pagos.ErrorPago) as error:
                pagos.comprobar_clave_stripe(clave)
            self.assertEqual(error.exception.codigo, "stripe_no_configurado")
            if clave:
                self.assertNotIn(clave, str(log.call_args_list))   # la clave no va al registro

    def test_stripe_queda_con_la_clave_y_la_version_fijada(self):
        with mock.patch.dict(os.environ, {"STRIPE_SECRET_KEY": "sk_test_abc"}), \
                mock.patch.object(stripe, "api_key", None), mock.patch.object(stripe, "api_version", "otra"):
            self.assertIsInstance(pagos._stripe(), pagos._StripeAPI)
            self.assertEqual((stripe.api_key, stripe.api_version), ("sk_test_abc", pagos.STRIPE_API_VERSION))

    def test_con_una_clave_live_no_se_llama_a_stripe(self):
        db = _FirestoreFalso()
        db.docs.update({"usuarios/pro": {"tipoUsuario": "Técnico"}})
        prohibido = mock.Mock(side_effect=AssertionError("no se puede llamar a Stripe"))
        with mock.patch.dict(os.environ, {"STRIPE_SECRET_KEY": "sk_live_de_verdad"}), \
                mock.patch.object(stripe, "api_key", None), \
                mock.patch.object(pagos.firestore, "client", lambda: db), \
                mock.patch.object(pagos, "_descargar_plantilla", lambda: plantilla()), \
                mock.patch.object(stripe.Account, "create", prohibido), \
                mock.patch.object(stripe.AccountLink, "create", prohibido), \
                mock.patch("builtins.print"):
            pagos.borrar_cache_parametros()
            with self.assertRaises(main.https_fn.HttpsError) as error:
                llamar(pagos.stripe_crear_enlace_cuenta, "pro")
            pagos.borrar_cache_parametros()
            self.assertIsNone(stripe.api_key)
        self.assertEqual((error.exception.code, error.exception.details),
                         (F.FAILED_PRECONDITION, {"motivo": "stripe_no_configurado"}))
        prohibido.assert_not_called()


class AdaptadorStripeTest(unittest.TestCase):
    """_StripeAPI pasa al SDK lo que toca, con la clave de idempotencia de quien llama."""

    PAGO = {"id": "pago1", "importeCent": 1005, "netoCent": 904, "stripePaymentIntentId": "pi_1"}

    def llamar_sdk(self, objetivo, metodo, devuelve, llamada):
        sdk = mock.Mock(return_value=SimpleNamespace(to_dict=lambda: devuelve))
        with mock.patch.object(objetivo, metodo, sdk):
            resultado = llamada(pagos._StripeAPI())
        return resultado, sdk.call_args

    def test_cobro(self):
        r, args = self.llamar_sdk(stripe.PaymentIntent, "create", {"id": "pi_1"},
                                  lambda api: api.crear_cobro(self.PAGO, "cus_1", "pago1:cobro:1"))
        self.assertEqual(r, {"id": "pi_1"})
        self.assertEqual(args.kwargs, {
            "amount": 1005, "currency": "eur", "customer": "cus_1", "transfer_group": "pago1",
            "metadata": {"pagoId": "pago1"}, "automatic_payment_methods": {"enabled": True},
            "idempotency_key": "pago1:cobro:1"})

    def test_transferencia_con_el_cargo_del_pago(self):
        _, args = self.llamar_sdk(stripe.Transfer, "create", {"id": "tr_1"},
                                  lambda api: api.crear_transferencia(self.PAGO, "acct_1", "ch_1", "pago1:transferencia:1"))
        self.assertEqual(args.kwargs, {
            "amount": 904, "currency": "eur", "destination": "acct_1", "source_transaction": "ch_1",
            "transfer_group": "pago1", "metadata": {"pagoId": "pago1"}, "idempotency_key": "pago1:transferencia:1"})

    def test_reembolso_del_importe_completo(self):
        _, args = self.llamar_sdk(stripe.Refund, "create", {"id": "re_1"},
                                  lambda api: api.crear_reembolso(self.PAGO, "pago1:reembolso:1"))
        self.assertEqual(args.kwargs, {"payment_intent": "pi_1", "amount": 1005, "metadata": {"pagoId": "pago1"},
                                       "idempotency_key": "pago1:reembolso:1"})

    def test_cuenta_express_en_espana_con_transfers(self):
        _, args = self.llamar_sdk(stripe.Account, "create", {"id": "acct_1"},
                                  lambda api: api.crear_cuenta("u1", "cuenta:u1"))
        self.assertEqual(args.kwargs, {"type": "express", "country": "ES",
                                       "capabilities": {"transfers": {"requested": True}},
                                       "metadata": {"uid": "u1"}, "idempotency_key": "cuenta:u1"})

    def test_enlace_de_alta_vuelve_a_la_pagina_de_retorno(self):
        url, args = self.llamar_sdk(stripe.AccountLink, "create", {"url": "https://connect.stripe.com/x"},
                                    lambda api: api.crear_enlace_cuenta("acct_1"))
        self.assertEqual(url, "https://connect.stripe.com/x")
        self.assertEqual(args.kwargs, {"account": "acct_1", "refresh_url": pagos.URL_RETORNO_STRIPE,
                                       "return_url": pagos.URL_RETORNO_STRIPE, "type": "account_onboarding"})

    def test_clave_efimera_con_la_version_pedida(self):
        secreto, args = self.llamar_sdk(stripe.EphemeralKey, "create", {"secret": "ek_1"},
                                        lambda api: api.crear_clave_efimera("cus_1", "2020-08-27"))
        self.assertEqual((secreto, args.kwargs), ("ek_1", {"customer": "cus_1", "stripe_version": "2020-08-27"}))

    def test_cliente_y_cancelacion(self):
        _, args = self.llamar_sdk(stripe.Customer, "create", {"id": "cus_1"},
                                  lambda api: api.crear_cliente("u1", "cliente:u1"))
        self.assertEqual(args.kwargs, {"metadata": {"uid": "u1"}, "idempotency_key": "cliente:u1"})
        _, args = self.llamar_sdk(stripe.PaymentIntent, "cancel", {"id": "pi_1"},
                                  lambda api: api.cancelar_cobro("pi_1", "pago1:cancelar:pi_1"))
        self.assertEqual((args.args, args.kwargs), (("pi_1",), {"idempotency_key": "pago1:cancelar:pi_1"}))

    def test_busquedas_ignoran_lo_revertido_o_fallido(self):
        def lista(*objetos):
            return mock.Mock(return_value=SimpleNamespace(data=[SimpleNamespace(to_dict=lambda o=o: o) for o in objetos]))
        with mock.patch.object(stripe.Transfer, "list", lista({"id": "tr_1", "reversed": True}, {"id": "tr_2"})) as l:
            self.assertEqual(pagos._StripeAPI().buscar_transferencia("pago1")["id"], "tr_2")
            self.assertEqual(l.call_args.kwargs["transfer_group"], "pago1")
        with mock.patch.object(stripe.Refund, "list", lista({"id": "re_1", "status": "failed"})):
            self.assertIsNone(pagos._StripeAPI().buscar_reembolso("pi_1"))


# ══ T-E3: alta en Connect ══

class AltaConnectTest(_Base):

    def test_crea_la_cuenta_express_una_sola_vez_y_devuelve_el_enlace(self):
        self.db.docs["usuarios/pro2"] = {"tipoUsuario": "Empresa"}
        url = llamar(pagos.stripe_crear_enlace_cuenta, "pro2")["url"]
        cuenta = self.db.docs["cuentas_stripe/pro2"]
        self.assertEqual(url, f"https://connect.stripe.com/setup/e/{cuenta['accountId']}")
        self.assertEqual((cuenta["uid"], cuenta["estado"], cuenta["chargesEnabled"]), ("pro2", "pendiente", False))
        self.assertEqual(self.stripe.cuentas[cuenta["accountId"]]["metadata"], {"uid": "pro2"})
        llamar(pagos.stripe_crear_enlace_cuenta, "pro2")      # vuelve a por el enlace
        self.assertEqual(self.stripe.creaciones("crear_cuenta"), ["cuenta:pro2"])
        self.assertEqual(self.db.docs["cuentas_stripe/pro2"]["accountId"], cuenta["accountId"])

    def test_solo_un_profesional(self):
        self.assertError(pagos.stripe_crear_enlace_cuenta, "cli", F.PERMISSION_DENIED, "no_es_profesional")
        self.assertEqual(self.stripe.llamadas, [])

    def test_estado_refrescado_desde_stripe(self):
        self.db.docs["cuentas_stripe/pro"] = cuenta_stripe("pendiente")
        self.assertEqual(llamar(pagos.stripe_estado_cuenta, "pro"), {
            "estado": "activa", "chargesEnabled": False, "payoutsEnabled": True, "detailsSubmitted": True})
        self.assertEqual(self.db.docs["cuentas_stripe/pro"]["estado"], "activa")
        self.assertError(pagos.stripe_estado_cuenta, "cli", F.NOT_FOUND, "sin_cuenta")

    def test_estado_segun_alta_capacidad_y_pagos_al_banco(self):
        base = {"details_submitted": True, "payouts_enabled": True, "charges_enabled": False,
                "capabilities": {"transfers": "active"}}
        casos = [
            ({**base, "details_submitted": False}, "pendiente"),
            (base, "activa"),
            ({**base, "charges_enabled": True}, "activa"),
            ({**base, "payouts_enabled": False}, "restringida"),
            ({**base, "capabilities": {"transfers": "pending"}}, "restringida"),
            ({**base, "capabilities": None}, "restringida"),
            ({**base, "requirements": {"disabled_reason": "requirements.past_due"}}, "restringida"),
        ]
        for cuenta, estado in casos:
            with self.subTest(cuenta=cuenta):
                self.assertEqual(pagos.datos_cuenta_stripe(cuenta)["estado"], estado)

    def test_account_updated_actualiza_la_cuenta(self):
        self.db.docs["cuentas_stripe/pro"] = cuenta_stripe("pendiente")
        cuenta = self.stripe.cuentas["acct_pro"]
        self.assertEqual(self.webhook(evento("account.updated", cuenta, account="acct_pro"), SECRETO_CONNECT),
                         (200, "aplicado"))
        self.assertEqual(self.db.docs["cuentas_stripe/pro"]["estado"], "activa")

    def test_account_updated_ajeno_o_antiguo_no_cambia_nada(self):
        self.db.docs["cuentas_stripe/pro"] = cuenta_stripe("pendiente", estadoStripeEn=AHORA // 1000)
        antes = copy.deepcopy(self.db.docs)
        cuenta = self.stripe.cuentas["acct_pro"]
        casos = [
            (evento("account.updated", {**cuenta, "id": "acct_otra"}), "descartado"),
            (evento("account.updated", {**cuenta, "metadata": {"uid": "cli"}}), "descartado"),
            (evento("account.updated", {**cuenta, "metadata": {}}), "descartado"),
            (evento("account.updated", cuenta, creado=AHORA // 1000 - 60), "antiguo"),
        ]
        for datos, resultado in casos:
            with self.subTest(resultado=resultado):
                self.assertEqual(self.webhook(datos, SECRETO_CONNECT), (200, resultado))
        self.assertEqual(self.db.docs, antes)


# ══ Webhook ══

class WebhookTest(_Base):

    def test_firma_no_valida_400_y_no_se_escribe_nada(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        antes = copy.deepcopy(self.db.docs)
        datos = self.evento_cobro(pago_id)
        cuerpo, firma = firmar(datos)
        manipulado = cuerpo.replace(b'"amount": 1005', b'"amount": 5')
        peticiones = [
            _PeticionHttp(*firmar(datos, "whsec_otro")),
            _PeticionHttp(manipulado, firma),
            _PeticionHttp(cuerpo, None),
            _PeticionHttp(cuerpo, "t=1,v1=00"),
            _PeticionHttp(cuerpo, firmar(datos)[1].replace("t=", "t=1")),    # caducada
            _PeticionHttp(b"no es json", firma_de(b"no es json")),            # firma buena, cuerpo roto
        ]
        for peticion in peticiones:
            with self.subTest(cuerpo=peticion.get_data()[:30]):
                self.assertEqual(inspect.unwrap(pagos.stripe_webhook)(peticion).status_code, 400)
        self.assertEqual(inspect.unwrap(pagos.stripe_webhook)(_PeticionHttp(cuerpo, firma, "GET")).status_code, 405)
        self.assertEqual(self.db.docs, antes)

    def test_un_evento_que_no_se_maneja_responde_200_sin_hacer_nada(self):
        antes = copy.deepcopy(self.db.docs)
        self.assertEqual(self.webhook(evento("customer.created", {"id": "cus_1"})), (200, "ignorado"))
        self.assertEqual(self.db.docs, antes)

    def test_un_evento_live_se_ignora(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        datos = {**self.evento_cobro(pago_id), "livemode": True}
        self.assertEqual(self.webhook(datos), (200, "ignorado_live"))
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")

    def test_webhook_duplicado_no_cambia_nada(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        datos = self.evento_cobro(pago_id)
        self.assertEqual(self.webhook(datos), (200, "aplicado"))
        despues = copy.deepcopy(self.db.docs)
        pushes = list(self.pushes)
        self.reloj[0] += 1000
        self.assertEqual(self.webhook(datos), (200, "duplicado"))
        self.assertEqual(self.db.docs, despues)
        self.assertEqual(self.pushes, pushes)
        self.assertEqual(self.eventos_de(pago_id), ["solicitar", "aceptar", "cobro_ok"])
        self.assertEqual(self.db.docs[f"eventos_stripe/{datos['id']}"]["tipo"], "payment_intent.succeeded")

    def test_un_cobro_que_no_cuadra_no_cambia_nada(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        antes = copy.deepcopy(self.db.docs)
        casos = {
            "importe": dict(amount=999_00),
            "cobrado de menos": dict(amount_received=1000),
            "moneda": dict(currency="usd"),
            "otro pago en metadata": dict(metadata={"pagoId": "otro"}),
            "otro PaymentIntent": dict(id="pi_de_otro"),
        }
        for caso, cambios in casos.items():
            with self.subTest(caso=caso):
                self.assertEqual(self.webhook(self.evento_cobro(pago_id, **cambios))[0], 200)
        self.assertEqual(self.db.docs, antes)
        self.assertIn("no cuadra", self.logs())

    def test_un_cobro_que_llega_por_connect_o_de_una_cuenta_conectada_se_ignora(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        self.assertEqual(self.webhook(self.evento_cobro(pago_id), SECRETO_CONNECT), (200, "ignorado"))
        self.assertEqual(self.webhook({**self.evento_cobro(pago_id), "account": "acct_x"}), (200, "ignorado"))
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")

    def test_si_falla_al_aplicarlo_responde_500_y_el_reintento_lo_aplica(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        datos = self.evento_cobro(pago_id)
        self.db.fallar_en[f"pagos/{pago_id}"] = 1
        self.assertEqual(self.webhook(datos), (500, "error"))
        self.assertNotIn(f"eventos_stripe/{datos['id']}", self.db.docs)
        self.assertEqual(self.webhook(datos), (200, "aplicado"))
        self.assertEqual(self.pago(pago_id)["estado"], "retenido")

    def test_cobro_fallido_y_otra_tarjeta_en_la_misma_pantalla(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        cobro_id = self.pago(pago_id)["stripePaymentIntentId"]
        self.stripe.rechazar(cobro_id)
        self.assertEqual(self.webhook(self.evento_cobro(pago_id, "payment_intent.payment_failed")), (200, "aplicado"))
        self.assertEqual(self.pago(pago_id)["estado"], "fallido")
        self.stripe.pagar(cobro_id)
        self.assertEqual(self.webhook(self.evento_cobro(pago_id)), (200, "aplicado"))
        self.assertEqual(self.pago(pago_id)["estado"], "retenido")
        self.assertEqual(self.eventos_de(pago_id)[-3:], ["cobro_fallido", "reintentar", "cobro_ok"])

    def test_un_fallo_que_llega_tarde_no_devuelve_a_fallido(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        cobro_id = self.pago(pago_id)["stripePaymentIntentId"]
        self.stripe.rechazar(cobro_id)
        self.webhook(self.evento_cobro(pago_id, "payment_intent.payment_failed"))
        fallo_viejo = self.evento_cobro(pago_id, "payment_intent.payment_failed")    # otro id, mismo instante
        self.reloj[0] += 60_000
        llamar(pagos.pagos_reintentar, "cli", pagoId=pago_id)
        self.assertEqual(self.webhook(fallo_viejo), (200, "antiguo"))
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")


# ══ T-E4: solicitar y pagar ══

class SolicitarTest(_Base):

    def test_crea_el_pago_en_solicitado_y_avisa_al_cliente(self):
        pago_id = self.solicitar()
        pago = self.pago(pago_id)
        self.assertEqual((pago["id"], pago["cliente"], pago["profesional"], pago["chatId"], pago["estado"]),
                         (pago_id, "cli", "pro", "chat1", "solicitado"))
        self.assertEqual((pago["importeCent"], pago["comisionCent"], pago["netoCent"]), (1005, 101, 904))
        self.assertEqual(self.db.docs["limites_pagos/pro"]["solicitudes"], 1)
        self.assertEqual(self.stripe.llamadas, [])
        self.assertEqual([p[0] for p in self.pushes], ["fid-cli"])
        notificacion = self.db.docs[f"notificaciones/pago_{pago_id}_1_cli"]
        self.assertEqual((notificacion["uid"], notificacion["tipo"], notificacion["titulo"]), ("cli", "pago", "Pagos"))

    def test_importe_por_encima_del_maximo_rechazado(self):
        self.assertError(pagos.pagos_solicitar, "pro", F.INVALID_ARGUMENT, "importe_sobre_maximo",
                         chatId="chat1", importeCent=300_001, descripcion="Obra")
        self.assertFalse(any(r.startswith("pagos/") for r in self.db.docs))
        self.assertNotIn("limites_pagos/pro", self.db.docs)

    def test_importe_como_entero_json_o_como_long_del_sdk(self):
        largo = "type.googleapis.com/google.protobuf.Int64Value"
        for importe in (2500, {"@type": largo, "value": "2500"}):
            with self.subTest(importe=importe):
                pago_id = llamar(pagos.pagos_solicitar, "pro", chatId="chat1", importeCent=importe,
                                 descripcion="Obra")["pagoId"]
                self.assertIs(type(self.pago(pago_id)["importeCent"]), int)
                self.assertEqual(self.pago(pago_id)["importeCent"], 2500)
        no_enteros = [
            2500.0, "2500", True, None, [2500],
            {"@type": largo, "value": "2500.0"}, {"@type": largo, "value": "25e2"},
            {"@type": largo, "value": 2500}, {"@type": largo, "value": " 2500"}, {"@type": largo, "value": ""},
            {"@type": "type.googleapis.com/google.protobuf.DoubleValue", "value": "2500"},
            {"@type": largo, "value": "2500", "otro": 1}, {"value": "2500"},
        ]
        for importe in no_enteros:
            with self.subTest(importe=importe):
                self.assertError(pagos.pagos_solicitar, "pro", F.INVALID_ARGUMENT, "importe_no_entero",
                                 chatId="chat1", importeCent=importe, descripcion="Obra")
        self.assertError(pagos.pagos_solicitar, "pro", F.INVALID_ARGUMENT, "importe_sobre_maximo",
                         chatId="chat1", importeCent={"@type": largo, "value": "300001"}, descripcion="Obra")
        self.assertError(pagos.pagos_solicitar, "pro", F.INVALID_ARGUMENT, "importe_bajo_minimo",
                         chatId="chat1", importeCent={"@type": largo, "value": "-2500"}, descripcion="Obra")

    def test_datos_no_validos(self):
        casos = [
            (dict(importeCent=999, descripcion="x"), F.INVALID_ARGUMENT, "importe_bajo_minimo"),
            (dict(importeCent="1000", descripcion="x"), F.INVALID_ARGUMENT, "importe_no_entero"),
            (dict(importeCent=1000, descripcion=""), F.INVALID_ARGUMENT, "descripcion_no_valida"),
            (dict(importeCent=1000, descripcion="x", chatId="a/b"), F.INVALID_ARGUMENT, "id_no_valido"),
            (dict(importeCent=1000, descripcion="x", chatId="no_existe"), F.NOT_FOUND, "chat_no_valido"),
        ]
        self.db.docs["chats/ajeno"] = {"participantes": ["cli", "otro"]}
        casos.append((dict(importeCent=1000, descripcion="x", chatId="ajeno"), F.NOT_FOUND, "chat_no_valido"))
        for datos, codigo, motivo in casos:
            with self.subTest(datos=datos):
                self.assertError(pagos.pagos_solicitar, "pro", codigo, motivo, **{"chatId": "chat1", **datos})

    def test_con_un_bloqueo_en_cualquier_sentido_no_se_pide(self):
        for bloqueo in ("bloqueos/cli_pro", "bloqueos/pro_cli"):
            with self.subTest(bloqueo=bloqueo):
                self.db.docs[bloqueo] = {"fecha": 1}
                self.assertError(pagos.pagos_solicitar, "pro", F.FAILED_PRECONDITION, "bloqueo",
                                 chatId="chat1", importeCent=1000, descripcion="x")
                del self.db.docs[bloqueo]

    def test_sin_la_cuenta_connect_activa_no_se_pide(self):
        for cuenta in (cuenta_stripe("pendiente"), cuenta_stripe("restringida"), None):
            with self.subTest(cuenta=cuenta):
                self.db.docs.pop("cuentas_stripe/pro", None)
                if cuenta:
                    self.db.docs["cuentas_stripe/pro"] = cuenta
                self.assertError(pagos.pagos_solicitar, "pro", F.FAILED_PRECONDITION, "cuenta_no_activa",
                                 chatId="chat1", importeCent=1000, descripcion="x")

    def test_tope_diario_de_solicitudes(self):
        for _ in range(pagos.PAGOS_SOLICITUDES_MAX_DIA):
            self.solicitar()
        self.assertError(pagos.pagos_solicitar, "pro", F.RESOURCE_EXHAUSTED, "limite_solicitudes",
                         chatId="chat1", importeCent=1000, descripcion="x")
        self.reloj[0] += DIA_MS
        self.solicitar()

    def test_solo_un_profesional_y_con_el_email_verificado(self):
        self.db.docs["chats/chat1"]["participantes"] = ["cli", "pro"]
        self.assertError(pagos.pagos_solicitar, "cli", F.PERMISSION_DENIED, "no_es_profesional",
                         chatId="chat1", importeCent=1000, descripcion="x")
        e = self.error(pagos.pagos_solicitar, "pro", verificado=False, chatId="chat1", importeCent=1000,
                       descripcion="x")
        self.assertEqual((e.code, e.details), (F.PERMISSION_DENIED, {"motivo": "email_sin_verificar"}))


class AceptarTest(_Base):

    def test_devuelve_lo_que_necesita_paymentsheet(self):
        pago_id = self.solicitar()
        respuesta = self.aceptar(pago_id)
        pago = self.pago(pago_id)
        cobro = self.stripe.cobros[pago["stripePaymentIntentId"]]
        self.assertEqual(set(respuesta), {"paymentIntentClientSecret", "customerId", "ephemeralKeySecret"})
        self.assertEqual(respuesta["paymentIntentClientSecret"], cobro["client_secret"])
        self.assertEqual(respuesta["customerId"], self.db.docs["cuentas_stripe/cli"]["customerId"])
        self.assertEqual((cobro["amount"], cobro["currency"], cobro["metadata"], cobro["transfer_group"]),
                         (1005, "eur", {"pagoId": pago_id}, pago_id))
        self.assertEqual(pago["estado"], "pendiente_pago")
        self.assertEqual(self.stripe.creaciones("crear_cobro"), [f"{pago_id}:cobro:1"])
        self.assertEqual(self.stripe.creaciones("crear_cliente"), ["cliente:cli"])
        self.assertEqual(self.stripe.versiones, [pagos.STRIPE_API_VERSION])
        self.assertEqual(self.pushes[-1][0], "fid-pro")

    def test_la_clave_efimera_usa_la_version_que_pide_la_app(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id, stripeVersion="2020-08-27")
        self.assertEqual(self.stripe.versiones, ["2020-08-27"])
        for version in ("", "ayer", 5, "2020-08-27; drop"):
            with self.subTest(version=version):
                self.assertError(pagos.pagos_aceptar, "cli", F.INVALID_ARGUMENT, "version_no_valida",
                                 pagoId=pago_id, stripeVersion=version)

    def test_repetir_aceptar_devuelve_el_mismo_cobro_con_una_clave_efimera_nueva(self):
        # El cliente cerró la pantalla de pago sin pagar y vuelve a abrirla
        pago_id = self.solicitar()
        primera = self.aceptar(pago_id)
        segunda = self.aceptar(pago_id)
        self.assertEqual(primera["paymentIntentClientSecret"], segunda["paymentIntentClientSecret"])
        self.assertEqual(primera["customerId"], segunda["customerId"])
        self.assertNotEqual(primera["ephemeralKeySecret"], segunda["ephemeralKeySecret"])
        self.assertEqual(len(self.stripe.cobros), 1)
        self.assertEqual(self.stripe.creaciones("crear_cobro"), [f"{pago_id}:cobro:1"])
        self.assertEqual(len(self.stripe.clientes), 1)
        self.assertEqual(self.eventos_de(pago_id), ["solicitar", "aceptar"])
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")

    def test_si_falla_al_guardar_se_repite_sin_crear_otro_cobro(self):
        pago_id = self.solicitar()
        self.db.fallar_en[f"pagos/{pago_id}"] = 1
        with self.assertRaises(RuntimeError):
            self.aceptar(pago_id)
        self.assertEqual(self.pago(pago_id)["estado"], "solicitado")
        self.aceptar(pago_id)
        self.assertEqual(self.stripe.creaciones("crear_cobro"), [f"{pago_id}:cobro:1"] * 2)
        self.assertEqual(len(self.stripe.cobros), 1)        # la misma clave: el mismo PaymentIntent
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")

    def test_solo_el_cliente_del_pago(self):
        pago_id = self.solicitar()
        for uid in ("pro", "otro"):
            for funcion in (pagos.pagos_aceptar, pagos.pagos_reintentar):
                with self.subTest(uid=uid, funcion=funcion.__name__):
                    self.assertError(funcion, uid, F.PERMISSION_DENIED, "uid_no_autorizado", pagoId=pago_id)
        self.assertEqual(self.stripe.cobros, {})
        self.assertError(pagos.pagos_aceptar, "cli", F.NOT_FOUND, "pago_no_encontrado", pagoId="noexiste")

    def test_sin_cuenta_activa_del_profesional_o_con_bloqueo_no_se_cobra(self):
        pago_id = self.solicitar()
        self.db.docs["cuentas_stripe/pro"] = cuenta_stripe("restringida")
        self.assertError(pagos.pagos_aceptar, "cli", F.FAILED_PRECONDITION, "cuenta_no_activa", pagoId=pago_id)
        self.db.docs["cuentas_stripe/pro"] = cuenta_stripe()
        self.db.docs["bloqueos/cli_pro"] = {"fecha": 1}
        self.assertError(pagos.pagos_aceptar, "cli", F.FAILED_PRECONDITION, "bloqueo", pagoId=pago_id)
        self.assertEqual(self.stripe.cobros, {})

    def test_reintentar_reutiliza_el_cobro_si_aun_se_puede_pagar(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        cobro_id = self.pago(pago_id)["stripePaymentIntentId"]
        self.stripe.rechazar(cobro_id)
        self.webhook(self.evento_cobro(pago_id, "payment_intent.payment_failed"))
        self.assertError(pagos.pagos_aceptar, "cli", F.FAILED_PRECONDITION, "transicion_no_permitida", pagoId=pago_id)
        respuesta = llamar(pagos.pagos_reintentar, "cli", pagoId=pago_id)
        self.assertEqual(respuesta["paymentIntentClientSecret"], self.stripe.cobros[cobro_id]["client_secret"])
        self.assertEqual(len(self.stripe.cobros), 1)
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")

    def test_reintentar_con_el_cobro_anulado_crea_otro_con_otra_clave(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        cobro_id = self.pago(pago_id)["stripePaymentIntentId"]
        self.stripe.rechazar(cobro_id)
        self.webhook(self.evento_cobro(pago_id, "payment_intent.payment_failed"))
        self.stripe.cobros[cobro_id]["status"] = "canceled"
        llamar(pagos.pagos_reintentar, "cli", pagoId=pago_id)
        self.assertEqual(self.stripe.creaciones("crear_cobro"), [f"{pago_id}:cobro:1", f"{pago_id}:cobro:2"])
        self.assertNotEqual(self.pago(pago_id)["stripePaymentIntentId"], cobro_id)

    def test_reintentar_con_el_cobro_ya_pagado_no_crea_otro(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        cobro_id = self.pago(pago_id)["stripePaymentIntentId"]
        self.stripe.rechazar(cobro_id)
        self.webhook(self.evento_cobro(pago_id, "payment_intent.payment_failed"))
        self.stripe.pagar(cobro_id)             # pagado en la misma pantalla; el webhook aún no ha llegado
        self.assertError(pagos.pagos_reintentar, "cli", F.FAILED_PRECONDITION, "cobro_en_curso", pagoId=pago_id)
        self.assertEqual(len(self.stripe.cobros), 1)


class CancelarTest(_Base):

    def test_cancelacion_antes_de_pagar(self):
        pago_id = self.solicitar()
        self.assertEqual(llamar(pagos.pagos_cancelar, "pro", pagoId=pago_id), {"estado": "cancelado"})
        self.assertEqual(self.stripe.llamadas, [])
        self.assertEqual(self.pushes[-1][0], "fid-cli")

    def test_cancelar_con_el_cobro_creado_lo_anula_en_stripe(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        cobro_id = self.pago(pago_id)["stripePaymentIntentId"]
        self.assertEqual(llamar(pagos.pagos_cancelar, "cli", pagoId=pago_id), {"estado": "cancelado"})
        self.assertEqual(self.stripe.cobros[cobro_id]["status"], "canceled")
        self.assertEqual(self.stripe.creaciones("cancelar_cobro"), [f"{pago_id}:cancelar:{cobro_id}"])
        self.assertEqual(self.webhook(evento("payment_intent.succeeded", self.stripe.cobros[cobro_id]))[0], 200)
        self.assertEqual(self.pago(pago_id)["estado"], "cancelado")

    def test_con_el_cobro_hecho_no_se_cancela(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        self.stripe.pagar(self.pago(pago_id)["stripePaymentIntentId"])
        self.assertError(pagos.pagos_cancelar, "cli", F.FAILED_PRECONDITION, "cobro_en_curso", pagoId=pago_id)
        self.assertEqual(self.pago(pago_id)["estado"], "pendiente_pago")

    def test_ni_un_tercero_ni_con_el_dinero_retenido(self):
        pago_id = self.solicitar()
        self.assertError(pagos.pagos_cancelar, "otro", F.PERMISSION_DENIED, "uid_no_autorizado", pagoId=pago_id)
        pago_id = self.hasta_retenido()
        for uid in ("cli", "pro"):
            self.assertError(pagos.pagos_cancelar, uid, F.FAILED_PRECONDITION, "transicion_no_permitida",
                             pagoId=pago_id)


# ══ T-E5: terminar, confirmar, disputar, resolver y liberar ══

class LiberacionTest(_Base):

    def test_pago_retencion_y_liberacion_por_confirmacion(self):
        pago_id = self.hasta_terminado()
        self.assertEqual(self.pago(pago_id)["liberarEn"], AHORA + 7 * DIA_MS)
        self.assertEqual(llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id), {"estado": "liberado"})
        pago = self.pago(pago_id)
        cobro = self.stripe.cobros[pago["stripePaymentIntentId"]]
        transferencia, = self.stripe.transferencias
        self.assertEqual(transferencia, {
            "id": pago["stripeTransferId"], "amount": 904, "currency": "eur", "destination": "acct_pro",
            "source_transaction": cobro["latest_charge"], "transfer_group": pago_id, "reversed": False})
        self.assertEqual(self.stripe.creaciones("crear_transferencia"), [f"{pago_id}:transferencia:1"])
        self.assertEqual(self.eventos_de(pago_id),
                         ["solicitar", "aceptar", "cobro_ok", "terminar", "confirmar"])
        self.assertEqual(self.pushes[-1][:3], ("fid-pro", "Payments", "The payment has been released to the professional."))

    def test_actor_equivocado(self):
        pago_id = self.hasta_retenido()
        self.assertError(pagos.pagos_terminar, "cli", F.PERMISSION_DENIED, "uid_no_autorizado", pagoId=pago_id)
        self.assertError(pagos.pagos_terminar, "otro", F.PERMISSION_DENIED, "uid_no_autorizado", pagoId=pago_id)
        self.assertError(pagos.pagos_confirmar, "cli", F.FAILED_PRECONDITION, "transicion_no_permitida",
                         pagoId=pago_id)       # sin terminar
        llamar(pagos.pagos_terminar, "pro", pagoId=pago_id)
        for uid in ("pro", "otro"):
            self.assertError(pagos.pagos_confirmar, uid, F.PERMISSION_DENIED, "uid_no_autorizado", pagoId=pago_id)
        self.assertEqual(self.stripe.transferencias, [])

    def test_liberacion_automatica_al_vencer_el_plazo(self):
        pago_id = self.hasta_terminado()
        liberar = inspect.unwrap(pagos.pagos_liberar_vencidos)
        self.reloj[0] = self.pago(pago_id)["liberarEn"] - 1
        liberar(None)
        self.assertEqual(self.pago(pago_id)["estado"], "terminado")
        self.reloj[0] += 1
        liberar(None)
        self.assertEqual(self.pago(pago_id)["estado"], "liberado")
        self.assertEqual(self.eventos_de(pago_id)[-1], "vencer_plazo")
        liberar(None)
        self.assertEqual(len(self.stripe.transferencias), 1)
        self.assertEqual({p[0] for p in self.pushes[-2:]}, {"fid-cli", "fid-pro"})

    def test_un_fallo_a_medias_se_repite_sin_duplicar_la_transferencia(self):
        pago_id = self.hasta_terminado()
        self.db.fallar_en[f"pagos/{pago_id}"] = 1      # Stripe transfiere y falla al guardar
        with self.assertRaises(RuntimeError):
            llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id)
        self.assertEqual(self.pago(pago_id)["estado"], "terminado")
        self.assertEqual(len(self.stripe.transferencias), 1)
        self.assertEqual(llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id), {"estado": "liberado"})
        self.assertEqual(len(self.stripe.transferencias), 1)
        self.assertEqual(self.pago(pago_id)["stripeTransferId"], self.stripe.transferencias[0]["id"])
        self.assertEqual(self.stripe.creaciones("crear_transferencia"), [f"{pago_id}:transferencia:1"])

    def test_confirmar_y_la_tarea_a_la_vez_dan_una_sola_transferencia(self):
        pago_id = self.hasta_terminado()
        self.reloj[0] = self.pago(pago_id)["liberarEn"]
        # La tarea reserva y transfiere pero no llega a guardar (se corta)
        self.db.fallar_en[f"pagos/{pago_id}"] = 1
        self.assertEqual(pagos.liberar_vencidos(self.db), {"liberados": 0, "fallidos": 1})
        self.assertEqual(llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id), {"estado": "liberado"})
        self.assertEqual(len(self.stripe.transferencias), 1)

    def test_la_tarea_va_por_lotes_con_tope_y_un_fallo_no_para_las_demas(self):
        ids = [self.hasta_terminado() for _ in range(5)]
        self.db.docs["limites_pagos/pro"]["solicitudes"] = 0
        self.db.docs[f"pagos/{ids[1]}"]["stripePaymentIntentId"] = "pi_que_no_existe"
        self.reloj[0] += 8 * DIA_MS
        with mock.patch.object(pagos, "LIBERAR_LOTE", 2), mock.patch.object(pagos, "LIBERAR_MAX_EJECUCION", 4):
            self.assertEqual(pagos.liberar_vencidos(self.db), {"liberados": 3, "fallidos": 1})
            self.assertEqual([self.pago(i)["estado"] for i in ids],
                             ["liberado", "terminado", "liberado", "liberado", "terminado"])
            self.assertEqual(pagos.liberar_vencidos(self.db), {"liberados": 1, "fallidos": 1})
        self.assertEqual(self.pago(ids[4])["estado"], "liberado")


class DisputaTest(_Base):

    def test_disputa_y_reembolso(self):
        pago_id = self.hasta_terminado()
        self.assertEqual(llamar(pagos.pagos_disputar, "cli", pagoId=pago_id, motivo="No terminó la obra"),
                         {"estado": "en_disputa"})
        self.assertIsNone(self.pago(pago_id)["liberarEn"])
        self.assertEqual(self.pushes[-1][0], "fid-pro")
        self.reloj[0] += 30 * DIA_MS                     # el plazo ya no libera nada
        self.assertEqual(pagos.liberar_vencidos(self.db), {"liberados": 0, "fallidos": 0})
        self.assertEqual(llamar(pagos.pagos_resolver, "adm", admin=True, pagoId=pago_id, decision="reembolsar"),
                         {"estado": "reembolsado"})
        pago = self.pago(pago_id)
        reembolso, = self.stripe.reembolsos
        self.assertEqual((reembolso["amount"], reembolso["payment_intent"], pago["stripeRefundId"]),
                         (1005, pago["stripePaymentIntentId"], reembolso["id"]))
        self.assertEqual(self.stripe.creaciones("crear_reembolso"), [f"{pago_id}:reembolso:1"])
        self.assertEqual(self.stripe.transferencias, [])
        self.assertEqual(pago["historial"][-1]["uid"], "adm")
        self.assertEqual({p[0] for p in self.pushes[-2:]}, {"fid-cli", "fid-pro"})

    def test_resolver_liberando(self):
        pago_id = self.hasta_retenido()
        llamar(pagos.pagos_disputar, "cli", pagoId=pago_id, motivo="Dudas")
        self.assertEqual(llamar(pagos.pagos_resolver, "adm", admin=True, pagoId=pago_id, decision="liberar"),
                         {"estado": "liberado"})
        self.assertEqual(len(self.stripe.transferencias), 1)
        self.assertEqual(self.stripe.reembolsos, [])

    def test_actor_equivocado_y_datos_no_validos(self):
        pago_id = self.hasta_terminado()
        self.assertError(pagos.pagos_disputar, "pro", F.PERMISSION_DENIED, "uid_no_autorizado",
                         pagoId=pago_id, motivo="x")
        self.assertError(pagos.pagos_disputar, "cli", F.INVALID_ARGUMENT, "motivo_no_valido", pagoId=pago_id)
        llamar(pagos.pagos_disputar, "cli", pagoId=pago_id, motivo="x")
        for uid in ("cli", "pro"):
            e = self.error(pagos.pagos_resolver, uid, pagoId=pago_id, decision="reembolsar")
            self.assertEqual(e.code, F.PERMISSION_DENIED)
        self.assertError(pagos.pagos_resolver, "adm", F.INVALID_ARGUMENT, "decision_no_valida",
                         admin=True, pagoId=pago_id, decision="partir")
        self.assertEqual((self.stripe.reembolsos, self.stripe.transferencias), ([], []))

    def test_no_se_disputa_mientras_se_transfiere(self):
        pago_id = self.hasta_terminado()
        self.db.docs[f"operaciones_pagos/{pago_id}"] = {"pagoId": pago_id, "operacion": "transferencia",
                                                        "creado": AHORA, "renovado": AHORA}
        self.reloj[0] += 60_000
        self.assertError(pagos.pagos_disputar, "cli", F.FAILED_PRECONDITION, "operacion_en_curso",
                         pagoId=pago_id, motivo="x")
        self.assertEqual(self.pago(pago_id)["estado"], "terminado")

    def test_una_transferencia_abandonada_sin_dinero_movido_deja_disputar(self):
        pago_id = self.hasta_terminado()
        self.db.docs[f"operaciones_pagos/{pago_id}"] = {"pagoId": pago_id, "operacion": "transferencia",
                                                        "creado": AHORA, "renovado": AHORA}
        self.reloj[0] += pagos.OPERACION_CADUCA_MS
        self.assertEqual(llamar(pagos.pagos_disputar, "cli", pagoId=pago_id, motivo="x"), {"estado": "en_disputa"})
        self.assertNotIn(f"operaciones_pagos/{pago_id}", self.db.docs)

    def test_una_transferencia_ya_hecha_no_deja_disputar(self):
        pago_id = self.hasta_terminado()
        self.db.fallar_en[f"pagos/{pago_id}"] = 1      # transferida, sin guardar
        with self.assertRaises(RuntimeError):
            llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id)
        self.reloj[0] += pagos.OPERACION_CADUCA_MS
        self.assertError(pagos.pagos_disputar, "cli", F.FAILED_PRECONDITION, "operacion_en_curso",
                         pagoId=pago_id, motivo="x")

    def test_un_reembolso_en_marcha_impide_liberar(self):
        pago_id = self.hasta_terminado()
        llamar(pagos.pagos_disputar, "cli", pagoId=pago_id, motivo="x")
        self.db.fallar_en[f"pagos/{pago_id}"] = 1      # reembolsado en Stripe, sin guardar
        with self.assertRaises(RuntimeError):
            llamar(pagos.pagos_resolver, "adm", admin=True, pagoId=pago_id, decision="reembolsar")
        self.assertError(pagos.pagos_resolver, "adm", F.FAILED_PRECONDITION, "operacion_en_curso",
                         admin=True, pagoId=pago_id, decision="liberar")
        self.assertEqual(llamar(pagos.pagos_resolver, "adm", admin=True, pagoId=pago_id, decision="reembolsar"),
                         {"estado": "reembolsado"})
        self.assertEqual((len(self.stripe.reembolsos), self.stripe.transferencias), (1, []))
        # Al repetir se encuentra el reembolso ya hecho: no se pide otro a Stripe
        # (la clave de idempotencia caduca a las 24 h y no bastaría)
        self.assertEqual(self.stripe.creaciones("crear_reembolso"), [f"{pago_id}:reembolso:1"])
        self.assertEqual(self.pago(pago_id)["stripeRefundId"], self.stripe.reembolsos[0]["id"])

    def test_la_transaccion_vuelve_a_mirar_si_ha_empezado_otra_operacion(self):
        # Entre la comprobación y la transacción, la tarea programada reserva la transferencia
        pago_id = self.hasta_terminado()
        op = {"pagoId": pago_id, "operacion": "transferencia", "creado": AHORA, "renovado": AHORA}
        real = pagos._comprobar_operacion

        def comprobar_y_que_entre_la_tarea(db, pago, operacion, api=None):
            visto = real(db, pago, operacion, api)
            self.db.docs[f"operaciones_pagos/{pago_id}"] = dict(op)
            return visto
        with mock.patch.object(pagos, "_comprobar_operacion", comprobar_y_que_entre_la_tarea):
            self.assertError(pagos.pagos_disputar, "cli", F.FAILED_PRECONDITION, "operacion_en_curso",
                             pagoId=pago_id, motivo="x")
            self.assertEqual(self.pago(pago_id)["estado"], "terminado")
            llamar(pagos.pagos_disputar, "cli", pagoId=self.hasta_retenido(), motivo="x")   # sin carrera, pasa
        # Y al revés: un reembolso que se reserva mientras se iba a transferir
        pago_id = self.hasta_retenido()
        llamar(pagos.pagos_disputar, "cli", pagoId=pago_id, motivo="x")
        op = {"pagoId": pago_id, "operacion": "reembolso", "creado": AHORA, "renovado": AHORA}
        with mock.patch.object(pagos, "_comprobar_operacion", comprobar_y_que_entre_la_tarea):
            self.assertError(pagos.pagos_resolver, "adm", F.FAILED_PRECONDITION, "operacion_en_curso",
                             admin=True, pagoId=pago_id, decision="liberar")
        self.assertEqual(self.stripe.transferencias, [])

    def test_un_contracargo_mientras_se_transfiere_deja_apuntada_la_transferencia(self):
        pago_id = self.hasta_terminado()
        transferir = self.stripe.crear_transferencia

        def transferir_y_llega_un_contracargo(*args):
            hecha = transferir(*args)
            self.db.docs[f"pagos/{pago_id}"]["estado"] = "contracargo"
            return hecha
        with mock.patch.object(self.stripe, "crear_transferencia", transferir_y_llega_un_contracargo):
            self.assertError(pagos.pagos_confirmar, "cli", F.FAILED_PRECONDITION, "transicion_no_permitida",
                             pagoId=pago_id)
        pago = self.pago(pago_id)
        self.assertEqual((pago["estado"], pago["stripeTransferId"]), ("contracargo", self.stripe.transferencias[0]["id"]))
        self.assertIn("revisarlo a mano", self.logs())

    def test_contracargo_despues_de_liberar(self):
        pago_id = self.hasta_terminado()
        llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id)
        disputa = {"id": "dp_1", "object": "dispute", "amount": 1005,
                   "payment_intent": self.pago(pago_id)["stripePaymentIntentId"]}
        self.assertEqual(self.webhook(evento("charge.dispute.created", disputa)), (200, "aplicado"))
        self.assertEqual(self.pago(pago_id)["estado"], "contracargo")
        self.assertIn("ya transferido", self.logs())
        self.assertEqual(self.webhook(evento("charge.dispute.created", {**disputa, "payment_intent": "pi_x"})),
                         (200, "descartado"))


class PagosApagadosTest(_Base):

    def test_las_acciones_nuevas_se_cortan(self):
        pago_id = self.solicitar()
        self.apagar_pagos()
        casos = [
            (pagos.pagos_solicitar, "pro", dict(chatId="chat1", importeCent=1000, descripcion="x")),
            (pagos.pagos_aceptar, "cli", dict(pagoId=pago_id)),
            (pagos.pagos_reintentar, "cli", dict(pagoId=pago_id)),
            (pagos.stripe_crear_enlace_cuenta, "pro", {}),
        ]
        for funcion, uid, datos in casos:
            with self.subTest(funcion=funcion.__name__):
                self.assertError(funcion, uid, F.FAILED_PRECONDITION, "pagos_deshabilitados", **datos)
        self.assertEqual(self.stripe.llamadas, [])

    def test_los_pagos_que_ya_existen_siguen_hasta_el_final(self):
        confirmado, vencido, disputado = self.hasta_retenido(), self.hasta_terminado(), self.hasta_terminado()
        en_curso = self.solicitar()
        self.aceptar(en_curso)
        self.apagar_pagos()
        self.assertEqual(self.cobrar(en_curso), (200, "aplicado"))                 # webhook
        llamar(pagos.pagos_terminar, "pro", pagoId=confirmado)
        self.assertEqual(llamar(pagos.pagos_confirmar, "cli", pagoId=confirmado), {"estado": "liberado"})
        llamar(pagos.pagos_disputar, "cli", pagoId=disputado, motivo="x")
        self.assertEqual(llamar(pagos.pagos_resolver, "adm", admin=True, pagoId=disputado, decision="reembolsar"),
                         {"estado": "reembolsado"})
        self.reloj[0] += 8 * DIA_MS
        self.assertEqual(pagos.liberar_vencidos(self.db)["liberados"], 1)        # programada
        self.assertEqual(self.pago(vencido)["estado"], "liberado")
        self.assertEqual(llamar(pagos.stripe_estado_cuenta, "pro")["estado"], "activa")


class BorradoCuentaTest(_Base):

    def setUp(self):
        super().setUp()
        self.delete_user = mock.Mock()
        self.play = mock.Mock(return_value=(0, 0))
        for parche in (mock.patch.object(main.auth, "delete_user", self.delete_user),
                       mock.patch.object(main, "_borrar_storage", lambda prefijo: 0),
                       mock.patch.object(main, "_cancelar_suscripciones_play", self.play)):
            parche.start()
            self.addCleanup(parche.stop)

    def test_con_pagos_en_curso_no_se_borra_nada(self):
        pago_id = self.solicitar()
        self.aceptar(pago_id)
        for estado in sorted(pagos.ESTADOS_CON_DINERO):
            for uid in ("cli", "pro"):
                with self.subTest(estado=estado, uid=uid):
                    self.db.docs[f"pagos/{pago_id}"]["estado"] = estado
                    antes = copy.deepcopy(self.db.docs)
                    e = self.error(main.eliminar_mi_cuenta, uid)
                    self.assertEqual((e.code, e.details), (F.FAILED_PRECONDITION, {"motivo": "pagos"}))
                    self.assertEqual(self.db.docs, antes)
        self.delete_user.assert_not_called()
        self.play.assert_not_called()

    def test_los_pagos_terminados_se_conservan_seudonimizados(self):
        pago_id = self.hasta_terminado()
        llamar(pagos.pagos_confirmar, "cli", pagoId=pago_id)
        respuesta = llamar(main.eliminar_mi_cuenta, "cli")
        self.assertEqual(respuesta["stats"]["pagos"], 1)
        pago = self.pago(pago_id)
        alias = hashlib.sha256(b"cli").hexdigest()
        self.assertEqual(alias, main._cuenta_ofuscada("cli"))
        self.assertEqual((pago["cliente"], pago["profesional"], pago["estado"]), (alias, "pro", "liberado"))
        self.assertNotIn("cli", [h["uid"] for h in pago["historial"]])
        self.assertIn(alias, [h["uid"] for h in pago["historial"]])
        self.assertNotIn("cuentas_stripe/cli", self.db.docs)
        self.assertIn("cuentas_stripe/pro", self.db.docs)
        self.delete_user.assert_called_once_with("cli")
        # La otra parte ya no avisa a una cuenta borrada
        self.pushes.clear()
        pagos._avisar(self.db, pago, "contracargo", pagos.SISTEMA)
        self.assertEqual([p[0] for p in self.pushes], ["fid-pro"])

    def test_el_profesional_borrado_pierde_su_cuenta_de_stripe_y_su_limite(self):
        self.solicitar()
        llamar(pagos.pagos_cancelar, "pro", pagoId=self.solicitar())
        main._purgar_usuario(self.db, "pro")
        self.assertNotIn("cuentas_stripe/pro", self.db.docs)
        self.assertNotIn("limites_pagos/pro", self.db.docs)
        self.assertTrue(all(p["profesional"] == pagos.seudonimo("pro")
                            for r, p in self.db.docs.items() if r.startswith("pagos/")))


class AvisosTest(_Base):

    def test_textos_en_los_14_idiomas_para_cada_estado(self):
        self.assertEqual(set(pagos._AVISOS_PAGO), set(main._TEXTOS_EMAIL))
        destinos = {d for d, _ in pagos.TRANSICIONES.values()} | {"solicitado"}
        self.assertEqual(destinos, set(pagos._ESTADOS_AVISO))
        for idioma, (titulo, textos) in pagos._AVISOS_PAGO.items():
            with self.subTest(idioma=idioma):
                self.assertTrue(titulo)
                self.assertEqual(len(textos), len(pagos._ESTADOS_AVISO))
                self.assertTrue(all(t.strip() for t in textos))

    def test_un_aviso_que_falla_no_rompe_la_operacion(self):
        def push_roto(*a, **k):
            raise RuntimeError("FCM no responde")
        with mock.patch.dict(pagos._AYUDAS, {"enviar_push": push_roto}):
            pago_id = self.solicitar()
        self.assertEqual(self.pago(pago_id)["estado"], "solicitado")
        self.assertIn("No se pudo avisar", self.logs())


if __name__ == "__main__":
    unittest.main()
