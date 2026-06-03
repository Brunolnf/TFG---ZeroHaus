# ZeroHaus

Aplicación móvil Android para la mejora de la eficiencia energética de viviendas y la intermediación con técnicos de rehabilitación.

Trabajo de Fin de Grado — Bruno Linares — Curso 2025/2026.

---

## Qué hace

ZeroHaus permite a un propietario de vivienda:

- Registrar sus viviendas con datos constructivos (superficie, año, aislamiento, ventanas, calefacción, ACS, orientación).
- Realizar un **preestudio energético** que aplica un algoritmo basado en el CTE / RD 390/2021 y devuelve una **etiqueta A–G**, consumo estimado (kWh/año), emisiones (kg CO₂/año) y coste anual.
- Consultar el **histórico de informes** y gráficas de evolución del consumo.
- **Compartir el informe** en PDF.
- Buscar **técnicos de rehabilitación energética** por especialidad, rating y proximidad (mapa).
- Solicitar **presupuesto**, aceptar la ficha de inicio, pagar y dejar **reseña** al finalizar.
- Chat 1:1 con técnicos (texto, imágenes, archivos) y notificaciones push.

Y a un técnico profesional:

- Gestionar su perfil público (especialidades, ciudad, métodos de cobro PayPal/Bizum).
- Recibir solicitudes de clientes, enviar presupuestos, gestionar el ciclo de vida de cada proyecto.
- Panel con estadísticas: ingresos, proyectos completados, valoración media.

Además incluye un **panel de administración** restringido al admin (`brulinf9@gmail.com`) para listar, bloquear y eliminar usuarios.

---

## Stack

| Capa | Tecnología |
| --- | --- |
| Lenguaje | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM (UserInterface / ViewModel / Repositorios / Modelos / Util) |
| Auth | Firebase Authentication |
| Base de datos | Cloud Firestore |
| Almacenamiento | Firebase Storage (avatares, adjuntos chat, certificados) |
| Push | Firebase Cloud Messaging |
| Backend | Cloud Functions (Python 3.11, region europe-west1) |
| Seguridad | Firebase App Check (Play Integrity) |
| Monitorización | Crashlytics + Performance Monitoring |
| Mapas | Google Maps Compose |
| Imágenes | Coil |
| min/target SDK | 26 / 36 |
| Locales | 9 idiomas (es, en, ca, eu, gl, pt, fr, de, it) |

---

## Estructura del proyecto

```
app/src/main/java/com/example/zerohaus/
├── MainActivity.kt              # entry-point, App Check, splash, theme
├── ServicioNotificaciones.kt    # FCM service
├── Modelos/                     # data classes (Usuario, Vivienda, Informe…)
├── Repositorios/                # acceso a Firestore/Auth/Storage/Functions
├── ViewModel/                   # estado y lógica por pantalla
├── UserInterface/               # 30+ pantallas Compose
├── Navegacion/                  # AppNavegacion, rutas
└── Util/                        # AppEstado, AppCadenas, AppPreferencias, Formato, AdminConfig

functions/                       # Cloud Functions Python
  main.py                        # 6 funciones: borrado cascada, push, rating, backup
  requirements.txt

firestore.rules                  # security rules
firestore.indexes.json           # índices compuestos
storage.rules                    # reglas de Storage
```

---

## Build

### Requisitos

- Android Studio Iguana o superior
- JDK 17
- Cuenta Firebase con proyecto `zerohaus-2a865` (o el tuyo propio + actualizar `google-services.json`)

### Variables locales

Crear `local.properties` en la raíz con:

```properties
MAPS_API_KEY=tu_api_key_de_google_maps
KEYSTORE_PASSWORD=…
KEY_PASSWORD=…
```

El keystore release (`app/zerohaus-release.jks`) y `local.properties` están en `.gitignore`.

### Compilar

```bash
./gradlew assembleDebug      # APK debug
./gradlew assembleRelease    # APK release firmado (necesita keystore)
./gradlew bundleRelease      # AAB para Play Store
```

### Cloud Functions

```bash
cd functions
python -m venv venv
source venv/bin/activate     # o venv\Scripts\activate en Windows
pip install -r requirements.txt
firebase deploy --only functions
```

---

## Sesiones recientes

| Fecha | Cambio |
| --- | --- |
| 2026-04-29 | Bugs chat y registro técnico, navegación con loader |
| 2026-05-09 | Mapa de técnicos, dark mode parte 1 |
| 2026-05-21 | Bugs reales, calidad, recuperación contraseña con i18n |
| 2026-05-22 | Mejoras UX (splash skip, validaciones, solicitar presupuesto desde chat) |
| 2026-05-29 | Upgrade Blaze: panel admin, 6 Cloud Functions, App Check |
| 2026-06-03 | Limpieza pre-Play Store: ProGuard, Splash API, localización, adaptive icon |

---

## Licencia

Proyecto académico. Todos los derechos reservados.
