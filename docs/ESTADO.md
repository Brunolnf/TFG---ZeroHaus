# Estado — 2026-11-17

## Hecho
- F1 y F2 cerrados. Plan aprobado (`docs/plan.md`). Auditoría en `docs/auditoria.md` (solo local).
- F3, bloques 0, A, B, C y G con PR abierto, tests y revisión hechos:
  - #48 `fix/auditoria-altos` (bloque 0) · #52 `fix/auditoria-servidor` (A) y #51 `fix/auditoria-reglas` (B), apilados sobre #48.
  - #50 `chore/endurecer-app` (C) · #49 `chore/observabilidad` (G) · #47 `chore/agentes` (subagentes, plan, este archivo).
- Dependabot #44–#46 fusionados.

## Pendiente del usuario
- Fusionar, por orden: #47, #48, y después #49 y #50. Tras #48, avisar para rebasar #51 y #52 sobre `main`.
- Desplegar: reglas antes o a la vez que functions. Antes, pasos B6, B7 y B8 de `docs/pasos-manuales.md`.
- Decisión A5 (revisor del código de Opus). Fable pide créditos; se está usando Sonnet en esfuerzo alto.

## Siguiente paso
1. Rebasar #51 y #52 sobre `main` en cuanto se fusione #48, y repasar su CI.
2. T-C4 (app: errores del borrado de cuenta), con #52 fusionado.
3. Bloque D (denunciar y bloquear), después E (pagos), F (certificados) y H (publicación).
4. Worktrees temporales que se pueden borrar al fusionar: `%TEMP%\wt-observabilidad`, `%TEMP%\wt-endurecer-app` y `.claude/worktrees/agent-*` del checkout principal.
