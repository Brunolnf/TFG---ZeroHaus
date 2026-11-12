# Estado — 2026-11-12

## Hecho
- Paso 0: subagentes en `.claude/agents/`, reparto en `CLAUDE.md`. Rama `chore/agentes` (sin subir, sin PR).
- Pasos manuales previstos: `docs/pasos-manuales.md`.
- F1, informe: `docs/auditoria.md` (solo local, excluido de git; no subir hasta desplegar los altos). 0 críticos, 3 altos, 8 medios, 10 bajos.

## Falta de F1
- Arreglos altos A-01, A-02 y A-03 con el ciclo critical-implementer → test-runner → reviewer → PR. Rama prevista: `fix/auditoria-altos`, desde `main`.
- No hechos en la primera sesión: los subagentes nuevos solo se cargan al arrancar una sesión.

## Pendiente del usuario
- Decisiones A1–A4 de `docs/pasos-manuales.md`.
- PR abiertos: #43 (legal, 2.3.1) y Dependabot #44–#46.

## Siguiente paso
1. Comprobar que existen los subagentes (`code-searcher`, etc.). Si no, hay que fusionar `chore/agentes` en `main` y abrir sesión nueva.
2. Lanzar critical-implementer con A-01, A-02 y A-03 (un test que falle antes por cada uno).
3. test-runner (reglas y functions) → reviewer → PR.
4. F2: `docs/plan.md` y parar a esperar el OK.
