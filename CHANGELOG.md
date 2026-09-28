# Changelog

Todos los cambios relevantes de ZeroHaus. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [versionado semántico](https://semver.org/lang/es/).

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

[2.2.0]: https://github.com/Brunolnf/TFG---ZeroHaus/compare/v1.0.0...v2.2.0
[1.0.0]: https://github.com/Brunolnf/TFG---ZeroHaus/releases/tag/v1.0.0
