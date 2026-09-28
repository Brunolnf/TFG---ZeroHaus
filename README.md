<div align="center">

# ZeroHaus

**Eficiencia energética para tu hogar, y los profesionales que te ayudan a conseguirla.**

[![CI](https://github.com/Brunolnf/TFG---ZeroHaus/actions/workflows/ci.yml/badge.svg)](https://github.com/Brunolnf/TFG---ZeroHaus/actions/workflows/ci.yml)
![Versión](https://img.shields.io/badge/versión-2.2.0-1F6E43)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Firestore%20·%20Functions%20·%20Auth-FFCA28?logo=firebase&logoColor=black)
![Idiomas](https://img.shields.io/badge/idiomas-14-1F6E43)

</div>

---

## Índice

- [Qué es ZeroHaus](#qué-es-zerohaus)
- [Funcionalidades](#funcionalidades)
- [Arquitectura](#arquitectura)
- [Tecnologías](#tecnologías)
- [Backend: Cloud Functions](#backend-cloud-functions)
- [Seguridad y privacidad](#seguridad-y-privacidad)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Puesta en marcha](#puesta-en-marcha)
- [Tests](#tests)
- [Textos e idiomas](#textos-e-idiomas)
- [Despliegue](#despliegue)
- [Versionado y flujo de trabajo](#versionado-y-flujo-de-trabajo)
- [Autor](#autor)

---

## Qué es ZeroHaus

ZeroHaus es una app Android que ayuda a los propietarios a **entender y mejorar la eficiencia energética de su vivienda** y los pone en contacto con **profesionales** (técnicos certificadores y empresas de reformas).

- El **propietario** describe su vivienda y obtiene un informe con su etiqueta energética (A–G), consumo, emisiones y coste anual, las mejoras que más ahorran y un simulador para ver el efecto de combinarlas.
- El **profesional** aparece en un directorio con mapa, recibe contactos por chat o teléfono y puede suscribirse (Verificado / Destacado) para ganar visibilidad.
- Para el cliente todo es **gratuito**; el modelo de negocio son las suscripciones de los profesionales, gestionadas con **Google Play Billing**.

> Los informes son **estimaciones orientativas** y no sustituyen al certificado de eficiencia energética oficial.

## Funcionalidades

### Propietario
| Área | Qué ofrece |
|---|---|
| **Preestudio energético** | Formulario con 14 variables de la vivienda (superficie, año, zona climática CTE por provincia, envolvente, sistemas, fotovoltaica, ocupantes…). |
| **Informe** | Etiqueta A–G por kWh/m²·año, consumo, emisiones (factores RITE) y coste por fuente de energía; recomendaciones con su ahorro real en €/año. |
| **Simulador «¿qué pasa si…?»** | Combina mejoras y muestra la nueva etiqueta, el ahorro anual, la inversión orientativa y los años de amortización. |
| **Consejos con IA** | Recomendaciones personalizadas con Gemini (Vertex AI) sin enviar datos personales. |
| **Historial y gráficas** | Comparación entre informes, evolución de consumo, emisiones y coste; exportación a PDF. |
| **Directorio y mapa** | Búsqueda de profesionales por especialidad, valoración y cercanía real. |
| **Chat** | Mensajería en tiempo real con fotos y archivos, paginada. |
| **Valoraciones** | Una por profesional, solo si ha habido una conversación real. |

### Profesional
| Área | Qué ofrece |
|---|---|
| **Perfil público** | Especialidades, descripción, contacto y ubicación en el mapa. |
| **Estadísticas** | Visitas al perfil, chats y llamadas de los últimos 30 días, conversión, posición en el directorio de su ciudad y checklist para completar el perfil. |
| **Suscripciones** | Verificado (trimestral / anual) y Destacado, con precios reales de Google Play, restauración y gestión desde Play. |
| **Clientes y reseñas** | Clientes que le han escrito y valoraciones recibidas. |

### Comunes
- **Verificación del email** con código de 6 dígitos (obligatoria para usar la app).
- **Notificaciones** push y por email configurables por tipo, con sonido o en silencio.
- **14 idiomas**: español, inglés, catalán, euskera, gallego, portugués, francés, alemán, italiano, árabe (RTL), chino, rumano, neerlandés y polaco.
- Tema claro / oscuro, unidades de energía y moneda configurables, borrado de cuenta desde la app.

### Administración
Panel restringido por *custom claim* `admin` para buscar, crear, editar, bloquear y eliminar usuarios (el borrado lo ejecuta el servidor con Admin SDK).

## Arquitectura

App de una sola Activity con **Jetpack Compose** y **MVVM**; Firebase como backend.

```
┌──────────────────────────────── App Android ────────────────────────────────┐
│  UserInterface (Compose)  ──►  ViewModel (estado observable)  ──►  Repositorios │
│        ▲                                                             │        │
│        └──────────── Navegacion (NavHost, una sola Activity) ◄───────┘        │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ SDK de Firebase (App Check · Auth)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│  Firestore + reglas  ·  Cloud Storage + reglas  ·  Cloud Messaging          │
│  Cloud Functions (Python): compras, verificación, IA, notificaciones, RGPD  │
│  Google Play Developer API · Vertex AI (Gemini) · SMTP                       │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Paquete | Responsabilidad |
|---|---|
| `UserInterface` | Pantallas y componentes Compose. |
| `ViewModel` | Estado de cada pantalla y lógica de presentación. |
| `Repositorios` | Acceso a Firestore, Storage y Cloud Functions; `AlgoritmoEnergetico` (cálculo puro, testeado). |
| `Modelos` | Clases de datos de Firestore. |
| `Navegacion` | Grafo de navegación y pantalla inicial según la sesión. |
| `Util` | Textos e idiomas, preferencias cifradas, notificaciones, facturación, seguridad y utilidades. |

**Decisiones clave**
- **La lógica sensible vive en el servidor**: activar suscripciones, verificar el email, borrar cuentas, recalcular valoraciones y enviar notificaciones son Cloud Functions; las reglas de Firestore impiden que la app lo haga por su cuenta.
- **Valores de dominio en español, traducción al mostrar**: los datos de la vivienda se guardan en español (el algoritmo los compara) y se traducen en la interfaz (`TextosEnergia`).
- **Nada de spinners infinitos**: las lecturas críticas tienen plazo máximo con caché local (`getOrTimeout`).

## Tecnologías

| Capa | Tecnologías |
|---|---|
| App | Kotlin 2.2 · Jetpack Compose (Material 3, BOM 2026.03) · Navigation Compose · Lifecycle/ViewModel · Coil · Google Maps Compose · Play Billing 8 · Play In-App Review · EncryptedSharedPreferences |
| Firebase | Authentication · Firestore · Storage · Cloud Messaging · Cloud Functions · App Check (Play Integrity) · Crashlytics · Performance · Analytics · Hosting |
| Backend | Python 3.11 · `firebase-functions` · `firebase-admin` · `google-genai` (Vertex AI) · SMTP |
| Tests | JUnit 4 (algoritmo) · `@firebase/rules-unit-testing` + emulador (reglas) |
| Build | Gradle (Kotlin DSL) · AGP 8.13 · R8 · compileSdk / targetSdk 36 · minSdk 26 |

## Backend: Cloud Functions

Código en [`functions/main.py`](functions/main.py) (región `europe-west1`).

| Función | Tipo | Qué hace |
|---|---|---|
| `activar_suscripcion` | callable | Verifica la compra con Google Play (API v2), la liga al usuario y activa el plan. |
| `on_play_rtdn` | Pub/Sub | Avisos en tiempo real de Play: renovaciones, cancelaciones, reembolsos. |
| `revisar_suscripciones_diario` | programada | Respaldo diario de las suscripciones y limpieza de eventos de estadísticas. |
| `enviar_codigo_verificacion` | callable | Genera y envía por email el código de 6 dígitos (irrepetible, un solo uso). |
| `verificar_codigo_email` | callable | Comprueba el código y marca el email como verificado. |
| `generar_sugerencias_ia` | callable | Consejos del informe con Gemini; caché por idioma y límite diario. |
| `on_message_created` | trigger | Nuevo mensaje → notificación, push y email según los ajustes del destinatario. |
| `on_resena_changed` | trigger | Recalcula la valoración del profesional y le avisa de reseñas nuevas. |
| `on_evento_perfil` | trigger | Agrega visitas y contactos en las estadísticas del profesional. |
| `eliminar_mi_cuenta` | callable | Borrado completo de la propia cuenta (RGPD y política de Google Play). |
| `eliminar_usuario_completo` | callable | Borrado completo de un usuario (solo administrador). |
| `backup_firestore_diario` | programada | Exportación diaria de Firestore a Cloud Storage. |

## Seguridad y privacidad

- **Reglas de Firestore y Storage** con validación de campos, propiedad de los datos y email verificado obligatorio; cubiertas por [tests con el emulador](rules-tests/).
- **Rol de administrador por *custom claim*** del token (no por email).
- **App Check (Play Integrity)** en las funciones sensibles; **HMAC** para los códigos de verificación; compras ligadas al usuario mediante `obfuscatedAccountId`.
- **App**: tráfico solo HTTPS con *certificate pinning*, preferencias cifradas (AES-256), `FLAG_SECURE`, cierre de sesión tras 5 min en segundo plano, detección de root / depurador / Frida y ofuscación R8.
- **Privacidad**: la IA nunca recibe nombre, dirección ni email; las visitas individuales a perfiles se borran a las 48 h; borrado total de la cuenta desde la app. Política completa en [`public/privacidad.html`](public/privacidad.html).

## Estructura del repositorio

```
ZeroHaus/
├── app/                          App Android
│   ├── src/main/java/com/example/zerohaus/
│   │   ├── Modelos/              Clases de datos (Firestore)
│   │   ├── Repositorios/         Acceso a datos + AlgoritmoEnergetico
│   │   ├── ViewModel/            Estado y lógica de pantallas
│   │   ├── UserInterface/        Pantallas Compose
│   │   ├── Navegacion/           Grafo de navegación
│   │   └── Util/                 Idiomas, preferencias, billing, seguridad…
│   ├── src/test/                 Tests unitarios del algoritmo energético
│   └── build.gradle.kts
├── functions/                    Cloud Functions (Python)
│   ├── main.py
│   ├── requirements.txt
│   └── set_admin_claim.py        Concede o retira el rol de administrador
├── rules-tests/                  Tests de las reglas de Firestore (emulador)
├── herramientas/i18n.py          Mantenimiento de los textos en los 14 idiomas
├── playstore-assets/             Ficha de Google Play: gráficos, logos y textos
├── .github/workflows/ci.yml      Integración continua
├── public/                       Web (Firebase Hosting): privacidad, términos, datos
├── firestore.rules               Reglas de Firestore
├── firestore.indexes.json        Índices de Firestore
├── storage.rules                 Reglas de Cloud Storage
├── firebase.json                 Configuración de Firebase
├── CHANGELOG.md                  Historial de versiones
└── README.md
```

## Puesta en marcha

### Requisitos
- Android Studio (reciente) con **JDK 17**
- Un proyecto de Firebase con Authentication (email/contraseña), Firestore, Storage, Functions y Messaging
- Para el backend: Python 3.11 y [Firebase CLI](https://firebase.google.com/docs/cli)

### App Android
1. Coloca `google-services.json` de tu proyecto en `app/`.
2. Crea `local.properties` en la raíz (no se sube al repositorio):
   ```properties
   sdk.dir=/ruta/al/Android/Sdk
   MAPS_API_KEY=tu_clave_de_google_maps
   # Solo para compilar la versión firmada:
   KEYSTORE_PASSWORD=...
   KEY_PASSWORD=...
   ```
3. Compila e instala:
   ```bash
   ./gradlew :app:installDebug
   ```

> En los builds de depuración App Check no se instala. Si tienes *enforcement* activado en Firestore, desactívalo mientras desarrollas o registra un token de depuración.

### Backend
```bash
cd functions
python -m venv venv
venv/Scripts/pip install -r requirements.txt   # en Linux/macOS: venv/bin/pip
```

## Tests

```bash
# App (JUnit): algoritmo energético, traducciones, especialidades, estadísticas y ubicaciones
./gradlew :app:testDebugUnitTest

# Reglas de seguridad de Firestore (emulador)
cd rules-tests && npm install && npm test
```

La **integración continua** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) ejecuta en cada push y pull request a `main`: los tests de la app, la compilación del APK, los tests de reglas con el emulador y la validación del backend y de los 14 idiomas.

## Textos e idiomas

Los textos de la interfaz están en `Util/AppCadenas.kt` (una propiedad por texto) y `Util/Traducciones.kt` (un mapa por idioma). Para no editarlos a mano en los 14 idiomas:

```bash
python herramientas/i18n.py añadir textos.json   # {"clave": ["Español", "English", …14 textos]}
python herramientas/i18n.py cambiar textos.json  # cambia textos existentes
python herramientas/i18n.py sin-uso --borrar     # borra claves que ya no usa el código
python herramientas/i18n.py comprobar            # verifica que los 14 idiomas están completos
```

## Despliegue

1. **Secretos del correo** (una sola vez):
   ```bash
   firebase functions:secrets:set SMTP_USUARIO
   firebase functions:secrets:set SMTP_CLAVE
   ```
2. **Backend**:
   ```bash
   firebase deploy --only functions,firestore:indexes,hosting
   ```
3. **App**: compila el bundle firmado y súbelo a Google Play Console.
   ```bash
   ./gradlew :app:bundleRelease
   ```
4. **Reglas** (`firestore:rules`, `storage`) cuando la nueva versión de la app ya esté disponible, porque exigen el email verificado:
   ```bash
   firebase deploy --only firestore:rules,storage
   ```

Configuración externa necesaria: productos de suscripción en Play Console, Play Developer API vinculada, tema Pub/Sub `play-rtdn` con permiso de publicación para Google Play y Vertex AI API habilitada.

## Versionado y flujo de trabajo

- **[Versionado semántico](https://semver.org/lang/es/)**: cada versión publicada tiene un tag anotado `vX.Y.Z`, y su `versionName` / `versionCode` en `app/build.gradle.kts`.
- **Ramas**: `master` contiene siempre la última versión estable; el trabajo se hace en ramas `feat/…`, `fix/…` o `release/X.Y.Z` y se integra con un merge.
- **Commits** con el formato [Conventional Commits](https://www.conventionalcommits.org/es/): `feat(área): …`, `fix(área): …`, `build: …`, `docs: …`.
- Los cambios de cada versión están en el [CHANGELOG](CHANGELOG.md).

## Autor

**Bruno Linares** — Proyecto final de ciclo formativo.

© 2026 ZeroHaus. Todos los derechos reservados.
