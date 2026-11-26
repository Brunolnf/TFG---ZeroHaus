# Estado — 2026-11-26

## Hecho
- F1 y F2 cerrados. Plan aprobado (`docs/plan.md`). Auditoría en `docs/auditoria.md` (solo local).
- F3, bloques 0, A, B, C y G fusionados en `main` (#47–#53) y desplegados el 2026-11-17: reglas de Firestore y Storage, functions (`on_tecnico_creado` nueva) y hosting (privacidad).
- Pasos manuales B6 (ya estaba activo), B7 (cuenta de servicio en Play Console con datos financieros y gestión de pedidos y suscripciones) y B8 (texto web y «IDs de usuario» en Seguridad de los datos, guardado sin enviar a revisión).
- T-C4 fusionado en #55.
- Bloque D (denunciar y bloquear) fusionado en #57 y desplegado el 2026-11-18. Se desplegaron:
  - el índice `mensajes (emisorUid, fecha)`;
  - las reglas de Firestore y Storage;
  - las functions `on_denuncia_creada` (nueva), `on_message_created`, `eliminar_usuario_completo` y `eliminar_mi_cuenta`.

  Las reseñas no se bloquean: para eso está «Denunciar reseña». Al borrar una cuenta se borran también las denuncias que ha recibido. Los avisos de denuncias llegan al buzón de SMTP_USUARIO (como mucho 20 al día y 5 por autor).
- Bloque E (pago con retención, T-E1 a T-E8) fusionado en #59 el 2026-11-20, **sin desplegar**.
  - Stripe Connect con cuentas Express y «separate charges and transfers», solo en modo test.
  - El servidor rechaza las claves que no son de test.
  - Con `pagos_habilitados` apagado no se abre dinero nuevo, pero lo que está en curso se puede terminar.
  - El borrado de cuenta no depende de Stripe: si hay dinero retenido no se borra, y la limpieza en Stripe pasa por la cola `/limpieza_stripe`.
  - Los contracargos solo avisan al administrador; no se le revierte la transferencia al profesional.
- Bloque I (revisión externa de ChatGPT, verificada: 8 hallazgos confirmados y 1 plausible) fusionado en #61 el 2026-11-26, **sin desplegar**.
  - El borrado de cuenta ya no pierde archivos si falla Storage: cola `/limpieza_storage`, reintentada cada día.
  - El servidor borra el adjunto de un mensaje eliminado, y lo conserva 30 días si el chat tiene una denuncia pendiente.
  - En la app:
    - «Restaurar compras» funciona con dos cuentas de ZeroHaus.
    - Las gráficas van por vivienda.
    - La vivienda y el informe se guardan en un lote atómico.
    - El mensaje y el chat se escriben en un mismo lote.
    - Las respuestas de IA no se cruzan entre informes.
    - El historial distingue un error de «sin informes».
  - En las reglas:
    - `mediaUrl` tiene que llevar el id del propio mensaje.
    - Los no leídos propios solo se quedan igual o vuelven a 0.
    - Los del otro solo suben de 1 en 1.

## Pendiente del usuario
- **Stripe (para desplegar el bloque E):**
  1. Cuenta en modo test con Connect activado.
  2. Dos endpoints de webhook que apunten a `https://europe-west1-zerohaus-2a865.cloudfunctions.net/stripe_webhook`, con la API `2026-10-28.endive`:
     - Plataforma: `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.dispute.created` y `charge.dispute.closed`.
     - Connect: `account.updated`.
  3. Guardar los secretos con `firebase functions:secrets:set STRIPE_SECRET_KEY` (sk_test_…), `STRIPE_WEBHOOK_SECRET` y `STRIPE_WEBHOOK_SECRET_CONNECT` (whsec_… de cada endpoint).
  4. `STRIPE_PUBLISHABLE_KEY=pk_test_…` en `local.properties` (vacía para el AAB de Play).
  5. Los 4 parámetros de pago en Remote Config, con los valores de `remoteconfig.template.json`. Mientras no existan, los pagos están apagados.
- **Decisiones legales:**
  - Plazo de conservación de los pagos cerrados, que se guardan seudonimizados (§4 del plan).
  - Si se le revierte la transferencia al profesional cuando hay un contracargo después de liberar.
- Play Console: declarar el estado de operador (DSA) y enviar a revisión los cambios de Seguridad de los datos con la próxima versión.
- Revisar en Seguridad de los datos si declarar «Número de teléfono» (los perfiles de profesional lo guardan) y añadir «datos de pago (Stripe)».
- Decisión A5 (revisor del código de Opus). Se usa Sonnet en esfuerzo alto.

## Siguiente paso
0. Desplegar el bloque I, que no depende de Stripe:
   - `firestore:rules`.
   - Functions `on_mensaje_borrado`, `on_limpieza_storage_creada`, `on_denuncia_resuelta`, `revisar_suscripciones_diario`, `eliminar_mi_cuenta` y `eliminar_usuario_completo`.

   Hace falta el permiso expreso del usuario para desplegar.
1. Cuando estén los secretos de Stripe, desplegar en este orden:
   1. Índices.
   2. Reglas.
   3. Functions de pagos, más `eliminar_mi_cuenta`, `eliminar_usuario_completo` y `pagos_limpiar_stripe`.

   Después, una prueba manual con una tarjeta de prueba.
2. Página `https://zerohaus-2a865.web.app/stripe/retorno`, que solo dice «vuelve a la app», y T-E9: borrador de las condiciones de pago y de Stripe como encargado del tratamiento, que necesita revisión legal.
3. Bloque F (certificados) y después H (publicación). T-C4 y los bloques D, E e I llegan a los usuarios con la próxima versión de la app. Antes, hay que probar en un dispositivo:
   - Denunciar, bloquear y el panel de denuncias.
   - Los pagos.
   - Las gráficas con dos viviendas: dos informes del piso y uno del chalet.
4. Worktrees que se pueden borrar: `%TEMP%\wt-observabilidad`, `%TEMP%\wt-endurecer-app` y `.claude/worktrees/agent-*` del checkout principal.

   `%TEMP%\wt-deploy` se usa para desplegar. La limpieza de temporales de Windows le borró archivos y se restauró con `git restore .` y un venv nuevo (Python 3.11) el 2026-11-26. Mejor moverlo fuera de `%TEMP%`.
