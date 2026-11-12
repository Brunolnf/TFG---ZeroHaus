# Pasos manuales previstos (proyecto entero)

Los secretos nunca se pegan en el chat: van a la variable o archivo indicado.
Estado: ☐ pendiente · ☑ hecho. «Cuándo» indica la fase que lo necesita.

## A. Decisiones (antes de F2)

| ID | Qué decidir | Respóndeme |
|----|-------------|------------|
| A1 | Quién fusiona los PR. Abiertos hoy: #43 (legal, 2.3.1) y Dependabot #44–#46. | «fusiona tú» (rebase, con CI y reviewer en verde) o «fusiono yo» |
| A2 | Pago con retención: comisión de ZeroHaus (%), cuándo se libera el dinero (confirmación del cliente o plazo) e importe máximo por pago. | Los tres valores, o «propón tú» |
| A3 | Titular legal. Hoy el aviso legal lleva nombre sin NIF ni domicilio. Cobrar comisiones exige identificar al prestador (LSSI art. 10) y Stripe en real exige actividad dada de alta. **Confirmar en fuente oficial.** | «solo modo test» (pagos reales fuera del alcance) o «me doy de alta» |
| A4 | El repo es público: `docs/auditoria.md` describe fallos explotables. Propongo no subirlo hasta desplegar los arreglos críticos y altos. | «ok» o «súbelo ya» |

## B. Cuentas y claves

**B1 · Stripe en modo test** (F3, bloque de pagos)
- Cómo: dashboard.stripe.com → crear cuenta (sin activar pagos reales) → interruptor «Modo de prueba» activado → menú **Connect** → *Empezar* → tipo *Plataforma o marketplace*. Después **Desarrolladores → Claves de API**.
- Dónde guardar: clave secreta `sk_test_…` con `firebase functions:secrets:set STRIPE_SECRET_KEY` (la pide el terminal); clave publicable `pk_test_…` en `local.properties` del checkout principal como `STRIPE_PUBLISHABLE_KEY=…`.
- El secreto del webhook (`STRIPE_WEBHOOK_SECRET`) se crea después del primer despliegue: **Desarrolladores → Webhooks → Añadir endpoint** con la URL que te daré.
- Comprobar: `firebase functions:secrets:get STRIPE_SECRET_KEY` lista una versión activa.
- Respóndeme: «hecho B1».

**B2 · Alertas de coste** (F3, bloque de observabilidad)
- Cómo: console.cloud.google.com → proyecto `zerohaus-2a865` → **Facturación → Presupuestos y alertas → Crear presupuesto** → ámbito: este proyecto → importe mensual → umbrales 50 %, 90 % y 100 % → avisos por correo a los administradores de facturación.
- Comprobar: el presupuesto aparece en la lista con sus tres umbrales.
- Respóndeme: «hecho B2» y el importe elegido.

**B3 · Alertas de Crashlytics** (F3)
- Cómo: console.firebase.google.com → proyecto → ⚙ **Configuración del proyecto → Alertas** → Crashlytics → activar correo para *problemas fatales nuevos*, *regresiones* y *alertas de velocidad*.
- Comprobar: los tres interruptores quedan activados.
- Respóndeme: «hecho B3».

**B4 · App Check con Play Integrity** (F4, después de publicar)
- Cómo: Play Console → app → **Integridad de la app → Firma de apps** → copiar la huella SHA-256 de la clave de firma de la app. Firebase → **App Check → Apps** → `es.zerohaus.app` → Play Integrity → pegar la huella. Con las métricas de solicitudes verificadas por encima del 95 %: **App Check → APIs** → Firestore y Storage → *Aplicar*.
- Comprobar: en APIs aparece «Aplicado» y la app sigue leyendo datos.
- Respóndeme: «hecho B4».

**B5 · API de OCR** (F3, solo si en F2 se elige Cloud Vision en vez de Gemini en Vertex, que ya está activo)
- Cómo: Cloud Console → **APIs y servicios → Biblioteca** → *Cloud Vision API* → Habilitar.
- Respóndeme: «hecho B5».

## C. Play Console (F4)

**C1 · Suscripciones y API** — **Monetizar → Suscripciones**: activos `verificado_trimestral`, `verificado_anual`, `destacado_mensual`, `destacado_anuncios_mensual`. **Configuración → Acceso a API**: cuenta de servicio de Functions con permiso de datos financieros. Comprobar: una compra de prueba activa el plan en la app.

**C2 · Notificaciones en tiempo real** — Cloud Console → Pub/Sub → tema `play-rtdn` → Permisos → añadir `google-play-developer-notifications@system.gserviceaccount.com` como *Publicador de Pub/Sub*. Play Console → **Monetizar → Configuración de monetización** → nombre del tema → *Enviar notificación de prueba*. Comprobar: la prueba aparece en el log de `on_play_rtdn`.

**C3 · Publicación** — subir el AAB firmado a prueba interna o cerrada, ficha, seguridad de los datos, clasificación de contenido y cuenta de prueba con email verificado para los revisores. Si la cuenta personal de desarrollador es posterior al 13-11-2023, Google exige prueba cerrada con 12 testers durante 14 días (**confirmar en la ayuda oficial de Play Console**).

**C4 · Testers de licencia** — Play Console → **Configuración → Pruebas de licencias** → añadir tu cuenta de Google, para comprar sin cargo.

## D. Despliegues (F4, te doy los comandos y el orden)

`firebase deploy --only functions` → `--only firestore:rules,storage` → `--only hosting`. Antes del primero deben existir todos los secretos (B1).

## E. Móvil físico (F4)

Compra de suscripción con tester de licencia, pago de Stripe con la tarjeta de prueba `4242 4242 4242 4242`, foto de un certificado para el OCR y recepción de un push.

## F. Entorno local (solo si fallan las suites)

La ruta del worktree es muy larga. Si Gradle o el venv de Python fallan por longitud de ruta: activar rutas largas de Windows (PowerShell como administrador: `New-ItemProperty -Path HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem -Name LongPathsEnabled -Value 1 -PropertyType DWORD -Force`) y `git config --global core.longpaths true`.
