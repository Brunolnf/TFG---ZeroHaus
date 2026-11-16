# Plan hacia producción — ZeroHaus

Estado: **pendiente de tu OK** (F2). Decisiones ya tomadas: los PR los fusionas tú (Dependabot yo, con CI verde); pagos solo en modo test; parámetros del pago en Remote Config; `docs/auditoria.md` fuera del repo hasta desplegar los altos.

## 1. Qué significa «app terminada»

- [ ] Las 4 suites en verde en CI: `:app:testDebugUnitTest`, `rules-tests`, `functions-tests`, `i18n.py comprobar`.
- [ ] `docs/auditoria.md`: 0 hallazgos críticos, altos y medios abiertos; cada bajo, cerrado o aceptado con motivo.
- [ ] Pago con retención en modo test: los 6 escenarios de §3 con test automático (Stripe simulado) y 1 prueba manual con tarjeta de prueba.
- [ ] Certificados: subida → lectura OCR → decisión del administrador → distintivo en el perfil, con tests de reglas y de functions.
- [ ] Precio de la luz: la función diaria tiene test y un despliegue completo no pisa el valor publicado.
- [ ] Crashlytics recibe un fallo de prueba de la build release con la traza desofuscada; alertas de Crashlytics y de coste activas (B2, B3).
- [ ] `:app:bundleRelease` con R8 sin errores y arranque comprobado en un dispositivo.
- [ ] Checklist de Play de §5 completa.
- [ ] Versión, `CHANGELOG.md` y notas de la versión al día; páginas legales actualizadas y desplegadas.

## 2. Propuesta para el pago con retención (A2)

**Stripe Connect, cuentas Express, «separate charges and transfers».** El cliente paga a la plataforma, el dinero queda en el saldo de Stripe de ZeroHaus y se transfiere al profesional al liberar. ZeroHaus nunca custodia fondos fuera de Stripe. No encuentro nada mejor: Play Billing no admite servicios físicos, y la autorización sin captura caduca a los 7 días.

Parámetros en Remote Config (los aplica el **servidor**; la app solo los muestra):

| Parámetro | Por defecto | Significado |
|-----------|-------------|-------------|
| `pagos_habilitados` | `false` | Interruptor. En producción queda apagado mientras Stripe esté en modo test. |
| `pago_comision_pct` | `10` | Comisión de ZeroHaus sobre el importe. |
| `pago_liberacion_dias` | `7` | Días desde que el profesional marca el trabajo como terminado hasta la liberación automática, si no hay disputa. |
| `pago_importe_max_eur` | `3000` | Importe máximo por pago (mínimo fijo: 10 €). |

Estados (solo los cambia el servidor, con tabla de transiciones permitidas y transacción):

`solicitado` → `pendiente_pago` → `retenido` → `terminado` → `liberado`
Salidas: `cancelado` (antes de pagar), `en_disputa` (el cliente, desde `retenido` o `terminado`) → `liberado` o `reembolsado` (administrador), `contracargo` (aviso del banco), `fallido`.

## 3. Tareas

Agentes: **CI** = critical-implementer · **IM** = implementer. Cada bloque es un PR con el ciclo agente → test-runner → reviewer. Rutas de app relativas a `app/src/main/java/com/example/zerohaus/`.

### Bloque 0 — Altos de la auditoría (`fix/auditoria-altos`, PR #48, pendiente de fusionar)
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-01 | CI | `functions/main.py`, `firestore.rules`, tests, `Modelos/Tecnico.kt`, `ViewModel/TecnicosViewModel.kt` | ✅ A-01, A-02 y A-03 con tests; dos revisiones sin bloqueantes | — |

### Bloque A — Resto de la auditoría, servidor (`fix/auditoria-servidor`)
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-A0 | CI | `functions/main.py`, `functions-tests/` | Menores de la revisión del PR #48: al deshacer una verificación se revocan las sesiones y se deshace también si falla la relectura; repaso diario de perfiles ocultos por lotes y con `try` por documento; `_hoy_utc` en el envío de códigos; marcar el perfil cuando la cuenta ya estaba verificada | T-01 |
| T-A1 | CI | `functions/main.py` | M-01: `max_instances` global y por función | T-01 |
| T-A2 | CI | `functions/main.py`, `functions-tests/` | M-04 y M-09: el borrado no deja perfiles vacíos ni perfiles antiguos; test de ambos | T-01 |
| T-A3 | CI | `functions/main.py`, `functions-tests/` | M-05: tarea diaria sin recorridos completos, 540 s, bloqueos primero | T-01 |
| T-A4 | CI | `functions/main.py`, `functions-tests/`, `ViewModel/AjustesViewModel.kt` | M-06: borrar la cuenta cancela la suscripción en Play o lo impide con aviso | T-01 |
| T-A5 | CI | `functions/main.py`, `functions-tests/` | M-07: prompt recortado, idiomas de lista cerrada, tope global diario de IA | T-01 |
| T-A6 | CI | `functions/main.py`, `functions-tests/` | B-07 y B-08 | T-A3 |

### Bloque B — Resto de la auditoría, reglas (`fix/auditoria-reglas`)
Criterio común: la app 2.3 publicada sigue funcionando con las reglas nuevas.
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-B1 | CI | `firestore.rules`, `rules-tests/` | M-03 y B-01: reseñas con `hasOnly`, topes y sin autovaloración | T-01 |
| T-B2 | CI | `firestore.rules`, `rules-tests/` | B-03 y B-04: chats, mensajes y usuarios | T-B1 |
| T-B3 | CI | `storage.rules`, `rules-tests/` | B-02 y M-02: tipos de imagen cerrados, borrado de la foto propia, ruta de perfil acotada | — |

### Bloque C — App y CI (`chore/endurecer-app`)
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-C1 | IM | `res/xml/file_paths.xml`, `UserInterface/PreestudioScreen.kt`, `UserInterface/ChatScreen.kt`, compartir | B-06: carpeta compartida propia, factura borrada tras leerla, adjuntos solo `https` del bucket | — |
| T-C2 | IM | `app/proguard-rules.pro` | B-10: sin `-keep` de paquetes enteros; `:app:minifyReleaseWithR8` pasa | — |
| T-C3 | IM | `.github/workflows/ci.yml` | B-09: acciones fijadas por SHA | — |

### Bloque D — Denunciar y bloquear usuarios (`feat/denuncias-bloqueos`)
B-11 confirmado: hoy solo el administrador puede bloquear (`AdminScreen.kt:282`); no hay denuncia ni bloqueo entre usuarios.
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-D1 | CI | `firestore.rules`, `rules-tests/`, `functions/main.py` | `/denuncias` (solo crear, una por autor y objetivo) y `/bloqueos`; un bloqueado no puede escribir al otro; aviso al administrador | Bloque B |
| T-D2 | IM | `UserInterface/ChatScreen.kt`, `PerfilTecnicoScreen.kt`, `AdminScreen.kt`, `i18n.py` | Botones de denunciar y bloquear en chat, perfil y reseña; lista en el panel de administrador | T-D1 |

### Bloque E — Pago con retención (`feat/pagos-stripe`)
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-E1 | CI | `functions/pagos.py` (nuevo), `functions/main.py`, `remoteconfig.template.json` | El servidor lee los 4 parámetros de Remote Config con caché y valores seguros si falla; test | Bloque A |
| T-E2 | CI | `functions/pagos.py`, `functions-tests/test_pagos.py` | Máquina de estados pura: toda transición no listada se rechaza; test de la tabla completa | T-E1 |
| T-E3 | CI | `functions/pagos.py`, tests | Alta del profesional en Connect (enlace de Stripe) y estado de su cuenta por webhook | T-E2 |
| T-E4 | CI | `functions/pagos.py`, tests | Solicitar y pagar: importe y comisión calculados en servidor; webhook con firma verificada; un evento repetido no cambia nada | T-E3 |
| T-E5 | CI | `functions/pagos.py`, tests | Terminar, confirmar, disputar, resolver, reembolsar y liberación programada; claves de idempotencia en cada llamada a Stripe | T-E4 |
| T-E6 | CI | `firestore.rules`, `rules-tests/` | `/pagos` y `/cuentas_stripe`: lectura solo de las partes y del administrador, escritura denegada | Bloque B |
| T-E7 | CI | `Repositorios/RepositorioPagos.kt`, `ViewModel/PagosViewModel.kt`, `app/build.gradle.kts` | SDK de Stripe (PaymentSheet), clave publicable desde `local.properties`, sin lógica de importes en el cliente | T-E5 |
| T-E8 | IM | `UserInterface/Pagos*.kt`, `ChatScreen.kt`, `Navegacion/`, `i18n.py` | Pantallas de solicitar, pagar, seguir el estado y disputar, en los 14 idiomas, ocultas si `pagos_habilitados` es falso | T-E7 |
| T-E9 | IM | `public/terminos.html`, `public/privacidad.html` | Borrador de condiciones de pago y de Stripe como encargado; **requiere tu revisión legal** | T-E8 |

Escenarios obligatorios (T-E2 a T-E5): pago → retención → liberación por confirmación; liberación automática al vencer el plazo; disputa → reembolso; cancelación antes de pagar; webhook duplicado; importe por encima del máximo rechazado.

### Bloque F — Certificados con OCR (`feat/certificados-ocr`)
Motor: Gemini en Vertex, el mismo que ya lee facturas (sin API nueva ni paso manual B5). El OCR no aprueba solo: propone y decide el administrador.
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-F1 | CI | `storage.rules`, `firestore.rules`, `rules-tests/` | `certificados/{uid}/…` (subida propia, PDF o imagen, 10 MB; lectura propia y del administrador); `/certificados` solo servidor | Bloque E |
| T-F2 | CI | `functions/certificados.py` (nuevo), tests | Lectura con esquema (titular, entidad, número, tipo, caducidad), comparación con el nombre del perfil, cupo de IA; test con Gemini simulado | T-F1 |
| T-F3 | CI | `functions/certificados.py`, `functions/main.py`, tests | Decisión del administrador, distintivo en `/tecnicos` congelado en reglas, borrado del archivo tras decidir | T-F2 |
| T-F4 | IM | `UserInterface/` (perfil profesional, `AdminScreen.kt`, `PerfilTecnicoScreen.kt`), `i18n.py` | Subida, estado, revisión en el panel y distintivo visible | T-F3 |

### Bloque G — Observabilidad y coste (`chore/observabilidad`)
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-G1 | IM | `Util/Diagnostico.kt`, `ZeroHausApp.kt` | Ya recoge solo en release y registra los errores de functions; falta un identificador de usuario sin datos personales (hash del uid) y la clave `version` | — |
| T-G2 | IM | `firebase.json`, `remoteconfig.template.json`, `README.md` | Un despliegue completo no pisa `precio_electricidad`; test de `actualizar_precio_luz_diario` presente | — |
| T-G3 | — | consola | Pasos manuales B2 y B3 | — |

### Bloque H — Publicación (`release/2.4.0`), F4
| ID | Ag. | Archivos | Hecho cuando | Dep. |
|----|-----|----------|--------------|------|
| T-H1 | IM | `docs/publicacion.md` | Checklist de §5 con el estado de cada punto | todos |
| T-H2 | IM | `app/build.gradle.kts`, `CHANGELOG.md`, notas de la versión | Versión 2.4.0, `bundleRelease` correcto | T-H1 |

### Orden y paralelismo
0 → (A ∥ B ∥ C ∥ G) → D → E → F → H. A, B, C y G no comparten archivos; para ejecutarlos a la vez cada agente necesita su propio worktree. E y F tocan los mismos archivos que A y B: van después y en serie.

## 4. Datos que hay que confirmar en fuente oficial

| Dato | Dónde |
|------|-------|
| Tarifas de Stripe y de Connect; plazo máximo de retención de fondos (creo que 90 días) | stripe.com/es/pricing, docs.stripe.com/connect |
| Que retener fondos vía Stripe no exige licencia propia de entidad de pago (PSD2) | Banco de España o asesor |
| Identificación del prestador (LSSI art. 10) y condiciones para profesionales (Reglamento UE 2019/1150) | BOE, EUR-Lex |
| Derecho de desistimiento en servicios y sus excepciones | BOE (texto refundido de consumidores) |
| Obligación de informar de los ingresos de los profesionales (DAC7, modelo 238) e IVA de la comisión | AEAT |
| Impuesto eléctrico (5,11269632 %) e IVA (21 %) usados en el precio de la luz | BOE, AEAT |
| OCR de titulaciones: base jurídica, plazo de conservación y transferencias fuera del EEE (Vertex responde desde `global`) | AEPD, condiciones de Google Cloud |
| Play: pagos de servicios físicos fuera de Play Billing, contenido de usuarios, borrado de cuenta, 12 testers durante 14 días, nivel de API objetivo, páginas de 16 KB | Ayuda de Play Console |
| Precios de las suscripciones | Play Console |
| Modelos de Gemini disponibles y su precio | cloud.google.com/vertex-ai |

## 5. Checklist de publicación en Play

- [ ] AAB firmado con la clave de producción, versión nueva y `mapping.txt` subido a Crashlytics.
- [ ] Nivel de API objetivo y compatibilidad con páginas de 16 KB dentro del requisito vigente.
- [ ] Ficha: textos, capturas, icono y gráfico de funciones al día.
- [ ] Política de privacidad y página de borrado de cuenta accesibles por URL (devuelven 200).
- [ ] Seguridad de los datos: Analytics, Crashlytics, Performance, ubicación aproximada, fotos y archivos, datos de pago (Stripe).
- [ ] Clasificación de contenido y declaración de anuncios.
- [ ] Contenido de usuarios: denunciar y bloquear disponibles (Bloque D).
- [ ] Suscripciones activas, enlace de gestión en la app y RTDN probado (C1, C2).
- [ ] Cuenta de prueba con email verificado e instrucciones para los revisores.
- [ ] Prueba cerrada con los testers exigidos, si aplica a la cuenta (C3).
- [ ] Informe previo al lanzamiento sin fallos.
- [ ] Orden de despliegue: functions → reglas → hosting → publicar → App Check aplicado (B4).
- [ ] `pagos_habilitados` en `false` en la plantilla de producción.
