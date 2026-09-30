# Changelog

Todos los cambios relevantes de ZeroHaus. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [versionado semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Añadido
- **Factura de la luz con IA**: en el preestudio se puede hacer una foto o subir el PDF de la factura; Gemini lee consumo, días, importe y potencia contratada (función `leer_factura`). El informe calcula el coste y los ahorros con el precio real que paga el usuario y muestra el consumo real frente al estimado, también en el PDF y el texto compartidos y en los consejos de la IA. El archivo no se guarda; cuenta dentro del límite diario de IA.
- **Precios de la energía con Firebase Remote Config** (`precio_electricidad`, `precio_gas`, `precio_biomasa`): se cambian desde la consola sin publicar versión; los valores fuera de rango se ignoran.
- La lista de viviendas indica cuáles tienen factura de la luz y su precio.
- `herramientas/distribuir.ps1`: compila la versión release y la envía a probadores con Firebase App Distribution.
- **Del informe al profesional**: cada mejora que hace un profesional tiene un botón que abre el directorio filtrado por su especialidad.
- **Catálogo único de especialidades**: el profesional las elige de una lista en su perfil y se muestran traducidas en todas las pantallas; el buscador encuentra también por el nombre traducido.
- **Distancias sin GPS**: si el usuario no da permiso de ubicación, el directorio mide desde la capital de la provincia de su vivienda, lo indica y marca las distancias como aproximadas; con un toque se puede usar el GPS.
- **Crashlytics más útil**: cada informe lleva el idioma y el tipo de usuario (sin datos personales) y los fallos del servidor en las Cloud Functions se registran como errores no fatales; las compilaciones de depuración ya no envían fallos.
- **Aviso sin conexión** en toda la app: explica que se ven los datos guardados y que los cambios se enviarán al reconectar.
- El PDF del informe muestra el consumo por m² (kWh/m²·año), que es lo que decide la etiqueta.
- Icono temático de Android 13+ (capa monocroma del icono adaptativo).
- El administrador puede marcar un email como verificado (para la cuenta de prueba de los revisores de Google Play).
- Integración continua con GitHub Actions: tests de la app, compilación, tests de reglas y validación del backend y los textos.
- Dependabot: como mucho un PR mensual por ecosistema (Gradle, Python, npm y GitHub Actions) con todas sus actualizaciones.
- `herramientas/i18n.py` para añadir, cambiar, limpiar y comprobar textos en los 14 idiomas.
- Tests de reglas: 45 con los emuladores de Firestore y Storage (17 nuevos de la auditoría).
- Tests de traducciones (14 idiomas completos, sin textos vacíos, todas las opciones traducidas), especialidades, estadísticas, ubicaciones y formato de números: 46 tests en total.
- Material de la ficha de Google Play versionado (gráficos, logos originales y textos de la 2.2).

### Cambiado
- **La app va más rápida**: el cambio entre pantallas dura 0,2 s en vez de 0,7 s, la pantalla de bienvenida 0,5 s en vez de 1,2 s, los números del último informe ya no se animan desde 0 cada vez que se vuelve al inicio y el directorio de profesionales solo se vuelve a filtrar cuando cambian los datos.
- Consejos con IA: `gemini-3.5-flash` (servidor en la UE) con `gemini-3.8-flash` de respaldo; `gemini-2.0-flash` se apagó en junio de 2026 y `gemini-2.5-flash` se apaga el 20/10/2026.
- Las compilaciones de depuración usan App Check con token de depuración (hay que registrarlo en la consola); antes no llevaban App Check y las funciones protegidas las rechazaban.
- **Herramientas de compilación**: AGP 9 (con Kotlin integrado), Kotlin 2.4, Gradle 9.8 y compileSdk 37 (el targetSdk sigue en 36, sin cambios de comportamiento). Librerías al día: Firebase BOM 34.19, Compose BOM 2026.09, Navigation 2.10, Play Billing 9.1, Maps Compose 8.6, core 1.19, security-crypto 1.1.0 estable.
- Las compilaciones de depuración permiten capturas de pantalla; la versión publicada las sigue bloqueando.
- Los números siguen el idioma elegido en la app (no el del sistema) y llevan separador de miles: «12.345,6 kWh» en español, «12,345.6 kWh» en inglés.
- `AppCadenas` pasa de una `data class` con ~580 parámetros a mapas por idioma: la JVM no admite más de 255 parámetros, por lo que la clase no se podía cargar en tests ni en las vistas previas de Compose. Si a un idioma le falta un texto, se muestra en español.

### Corregido
- Borrar usuarios desde el panel de administración, los consejos con IA y el resto de funciones protegidas con App Check fallaban en las compilaciones de depuración («Unauthenticated»).
- El filtro por especialidad dejaba fuera a los profesionales que la habían escrito a mano de otra forma («placas solares» en vez de «Fotovoltaica»).
- El directorio mostraba distancias calculadas desde Madrid cuando no había ubicación real del usuario.
- Ordenar por proximidad sin ninguna ubicación dejaba la lista en un orden arbitrario; ahora se ordena por valoración.
- Los canales de notificación aparecían en español en los ajustes de Android fuera cual fuera el idioma de la app.
- «Usar mi ubicación» no hacía nada si el permiso se había denegado dos veces; ahora abre los ajustes de la app.
- Los nombres largos de vivienda o de mejora se salían de la página en el PDF.
- La verificación del email decía «Sin conexión» ante cualquier error del servidor (por ejemplo, con la función aún sin desplegar); ahora distingue la falta de red de un servicio no disponible, igual que los consejos de IA.
- `gradlew` no tenía permiso de ejecución en el repositorio (fallaba en Linux y macOS).

### Seguridad
- Auditoría de las reglas de Firestore con la skill oficial de Firebase (*security rules auditor*), con un test de emulador por cada ataque:
  - un profesional podía crear su perfil ya con un plan de pago que no caducaba nunca;
  - quien enviaba un mensaje podía editarlo para atribuírselo al otro participante;
  - el dueño de una notificación podía cambiarle el destinatario y colarla en la lista de otro usuario;
  - una reseña editada podía recibir campos arbitrarios y los ajustes no tenían tamaño máximo;
  - en Storage, un participante del chat podía sobrescribir o borrar los archivos que había enviado el otro.

## [2.2.0] — 2026-09-28 · versionCode 5

Incluye también las versiones internas 2.0 y 2.1, que no llegaron a publicarse por separado.

### Añadido
- **Modelo de negocio por suscripción de profesionales** (Verificado / Destacado) con Google Play Billing 8. Para el cliente todo es gratuito.
- **Simulador «¿qué pasa si…?»** en el informe: nueva etiqueta, ahorro anual, inversión orientativa y amortización al combinar mejoras.
- **Consejos personalizados con IA** (Gemini en Vertex AI) desde el servidor, sin enviar datos personales; caché por idioma y límite diario.
- **Estadísticas del profesional**: visitas, chats y llamadas de 30 días, conversión, gráfica de 14 días, posición en el directorio de su ciudad y checklist del perfil.
- **Verificación del email con código de 6 dígitos** (aleatorio, irrepetible, de un solo uso), obligatoria para usar la app.
- **Avisos por email** de mensajes y valoraciones, y notificaciones que respetan los ajustes del usuario en el servidor (tipo, sonido, canal silencioso).
- Suscripciones completas: verificación con la API v2 de Play, confirmación desde el servidor, notificaciones en tiempo real (RTDN), revisión diaria, restaurar compras y enlace para cancelar.
- **Internacionalización completa a 14 idiomas**, incluido árabe (RTL).
- Ajustes ampliados: notificaciones por tipo, seguridad de la cuenta, cambio de contraseña, borrado de cuenta, privacidad y ayuda.
- Chat paginado (50 mensajes y «cargar anteriores»).
- Páginas de privacidad, términos y gestión de datos (Firebase Hosting).
- Tests de las reglas de Firestore con el emulador.

### Cambiado
- **Algoritmo energético rehecho**: etiqueta por kWh/m²·año (ya no depende del tamaño), desglose por fuente de energía con precios reales y factores de emisión oficiales (RITE), y ahorro de cada recomendación recalculado.
- Dependencias: Kotlin 2.2, compileSdk 36, Firebase BOM 34, Compose BOM 2026.03, Navigation 2.9, Lifecycle 2.10, Maps Compose 8.
- El borrado de usuarios del panel de admin lo ejecuta el servidor (Auth, Firestore y Storage completos).
- Solo se puede valorar a un profesional tras haber hablado con él por chat.

### Corregido
- Cambiar cualquier ajuste borraba el token de notificaciones y dejaban de llegar los push.
- La pantalla de suscripción nunca mostraba los precios reales de Google Play y tomaba la fase de prueba gratuita como precio.
- El mapa colocaba a profesionales sin ubicación en puntos inventados; el directorio calculaba distancias desde Madrid sin GPS.
- El panel de admin borraba automáticamente usuarios con nombres «genéricos».
- Una compra de suscripción podía reutilizarse desde otra cuenta.

### Eliminado
- Modo «demo» de compras, flujo antiguo de presupuestos, proyectos y pagos, certificados y el modelo de pago por contacto.

### Seguridad
- Reglas: email verificado obligatorio, reseñas respaldadas por un chat real, reclamar perfiles solo con email verificado, límites de tamaño en los perfiles.
- Rol de administrador por *custom claim*, App Check en las funciones sensibles, *certificate pinning*, preferencias cifradas, `FLAG_SECURE` y detección de manipulación.

## [1.0.0] — 2026-06-06 · versionCode 2

Primera versión en pruebas internas de Google Play.

### Añadido
- Preestudio energético e informe con etiqueta, consumo, emisiones y recomendaciones.
- Directorio de técnicos con mapa, perfiles y valoraciones.
- Chat en tiempo real con imágenes y archivos.
- Flujo de presupuestos y proyectos entre propietario y técnico.
- Panel de administración, Cloud Functions y App Check.
- Modo oscuro, 9 idiomas y unidades configurables.

[Sin publicar]: https://github.com/Brunolnf/TFG---ZeroHaus/compare/v2.2.0...HEAD
[2.2.0]: https://github.com/Brunolnf/TFG---ZeroHaus/compare/v1.0.0...v2.2.0
[1.0.0]: https://github.com/Brunolnf/TFG---ZeroHaus/releases/tag/v1.0.0
