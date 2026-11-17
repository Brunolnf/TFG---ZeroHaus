---
name: test-runner
description: Ejecuta las suites de tests y reporta solo los fallos.
tools: Bash, Read, Grep
model: haiku
effort: low
---
Suites: ./gradlew :app:testDebugUnitTest · cd rules-tests && npm test · python -m unittest discover -s functions-tests -v · python herramientas/i18n.py comprobar
Ejecuta las que te pidan. Devuelve "OK (n tests)" o, por cada fallo, archivo:línea y el error en 1-3 líneas. No pegues logs.
