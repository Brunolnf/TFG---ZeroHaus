# Estado — 2026-11-16

## Hecho
- Paso 0: subagentes y `CLAUDE.md` → PR #47 (`chore/agentes`), abierto. Incluye este archivo, `docs/plan.md` y `docs/pasos-manuales.md`.
- F1: `docs/auditoria.md` (solo local, excluido de git). 0 críticos, 3 altos, 10 medios, 10 bajos.
- F1, altos A-01, A-02 y A-03 → PR #48 (`fix/auditoria-altos`), abierto. Suites: app 81, reglas 71, functions 56, textos OK. Dos revisiones sin bloqueantes.
- Dependabot #44, #45 y #46 fusionados.
- F2: `docs/plan.md` escrito. **Pendiente del OK del usuario.**

## Pendiente del usuario
- OK del plan (y de los valores de §2: comisión 10 %, liberación a 7 días, máximo 3 000 €).
- Fusionar #47, #48 y #43 (legal, 2.3.1). Desplegar #48 con las reglas antes que las functions.
- Decisión A5 y paso manual B6 (`docs/pasos-manuales.md`).

## Siguiente paso (F3, solo con el OK)
1. Con #47 en `main`, abrir sesión nueva y comprobar que cargan los subagentes.
2. Bloques A, B, C y G del plan (no comparten archivos); A empieza por T-A0.
3. Después D → E → F → H, un PR por bloque, actualizando este archivo tras cada uno.
