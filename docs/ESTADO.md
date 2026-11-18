# Estado — 2026-11-18

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

## Pendiente del usuario
- Play Console: declarar el estado de operador (DSA) y enviar a revisión los cambios de Seguridad de los datos con la próxima versión.
- Revisar en Seguridad de los datos si declarar «Número de teléfono» (los perfiles de profesional lo guardan).
- Decisión A5 (revisor del código de Opus). Se usa Sonnet en esfuerzo alto.

## Siguiente paso
1. Bloque E (pagos), después F (certificados) y H (publicación). Lo de T-C4 y el bloque D llega a los usuarios con la próxima versión de la app. Antes, hay que probar en un dispositivo denunciar, bloquear y el panel de denuncias.
2. Worktrees que se pueden borrar: `%TEMP%\wt-observabilidad`, `%TEMP%\wt-endurecer-app` y `.claude/worktrees/agent-*` del checkout principal. `%TEMP%\wt-deploy` se usa para desplegar (tiene el venv de functions).
