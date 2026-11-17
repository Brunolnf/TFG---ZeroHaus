# Estado — 2026-11-17

## Hecho
- F1 y F2 cerrados. Plan aprobado (`docs/plan.md`). Auditoría en `docs/auditoria.md` (solo local).
- F3, bloques 0, A, B, C y G fusionados en `main` (#47–#53) y desplegados el 2026-11-17: reglas de Firestore y Storage, functions (`on_tecnico_creado` nueva) y hosting (privacidad).
- Pasos manuales B6 (ya estaba activo), B7 (cuenta de servicio en Play Console con datos financieros y gestión de pedidos y suscripciones) y B8 (texto web y «IDs de usuario» en Seguridad de los datos, guardado sin enviar a revisión).

## Pendiente del usuario
- Play Console: declarar el estado de operador (DSA) y enviar a revisión los cambios de Seguridad de los datos con la próxima versión.
- Revisar en Seguridad de los datos si declarar «Número de teléfono» (los perfiles de profesional lo guardan).
- Decisión A5 (revisor del código de Opus). Se usa Sonnet en esfuerzo alto.

## Siguiente paso
1. T-C4 (app: errores del borrado de cuenta y aviso `cancelarEnPlay`).
2. Bloque D (denunciar y bloquear), después E (pagos), F (certificados) y H (publicación).
3. Worktrees que se pueden borrar: `%TEMP%\wt-observabilidad`, `%TEMP%\wt-endurecer-app`, `%TEMP%\wt-deploy` y `.claude/worktrees/agent-*` del checkout principal.
