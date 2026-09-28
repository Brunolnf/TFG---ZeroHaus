"""
Asigna (o revoca) el custom claim `admin: true` a un usuario de Firebase Auth.

Sustituye al email hardcodeado que antes se comparaba en cliente, rules y
functions. Ejecutar UNA VEZ por cada admin que se quiera dar de alta; el
claim viaja firmado en el JWT del usuario y NO es modificable desde el
cliente Android.

Uso:
    # Asignar admin a un email
    GOOGLE_APPLICATION_CREDENTIALS=/ruta/cuenta-servicio.json \\
        python set_admin_claim.py grant tu@email.com

    # Revocar admin
    GOOGLE_APPLICATION_CREDENTIALS=/ruta/cuenta-servicio.json \\
        python set_admin_claim.py revoke tu@email.com

    # Comprobar quién es admin actualmente
    GOOGLE_APPLICATION_CREDENTIALS=/ruta/cuenta-servicio.json \\
        python set_admin_claim.py check tu@email.com

El JSON de cuenta de servicio se descarga desde la Firebase Console:
    Project settings → Service accounts → Generate new private key.

Tras ejecutar `grant`, el usuario debe **cerrar sesión y volver a entrar**
en la app para que su ID token incluya el nuevo claim (el token cacheado
sigue siendo válido hasta 1 hora). Lo mismo para `revoke`.
"""
import sys

import firebase_admin
from firebase_admin import auth, credentials


PROJECT_ID = "zerohaus-2a865"


def _init() -> None:
    if not firebase_admin._apps:
        cred = credentials.ApplicationDefault()
        firebase_admin.initialize_app(cred, {"projectId": PROJECT_ID})


def grant(email: str) -> None:
    user = auth.get_user_by_email(email)
    claims = dict(user.custom_claims or {})
    claims["admin"] = True
    auth.set_custom_user_claims(user.uid, claims)
    print(f"OK · admin=True asignado a {email} (uid={user.uid})")
    print("   El usuario debe cerrar sesion y volver a entrar para refrescar el token.")


def revoke(email: str) -> None:
    user = auth.get_user_by_email(email)
    claims = dict(user.custom_claims or {})
    claims.pop("admin", None)
    auth.set_custom_user_claims(user.uid, claims or None)
    print(f"OK · admin revocado de {email} (uid={user.uid})")


def check(email: str) -> None:
    user = auth.get_user_by_email(email)
    es_admin = bool((user.custom_claims or {}).get("admin"))
    print(f"{email} -> admin={es_admin} (uid={user.uid})")


def _usage_and_exit() -> None:
    print(__doc__)
    sys.exit(1)


def main() -> None:
    if len(sys.argv) != 3:
        _usage_and_exit()

    accion, email = sys.argv[1], sys.argv[2]
    if accion not in ("grant", "revoke", "check"):
        _usage_and_exit()

    _init()
    {"grant": grant, "revoke": revoke, "check": check}[accion](email)


if __name__ == "__main__":
    main()
