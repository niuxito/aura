# De `aura.mission-package/v4` a `v5`

Desde el 2026-11-15, AURA Manager usa códigos en inglés y textos multiidioma (ver AGENTS.md, «Contrato» e «Idiomas»). El contenido es el mismo que en v4; cambian los nombres.

## Textos: de `string` a `LocalizedText`

Pasan a ser objetos `{ "en": "…", "es": "…" }` (un idioma puede faltar: usa `defaultLanguage`):

- `mission.name`, `mission.destination`, `mission.description`
- `crew[].role` (`crew[].name` sigue siendo texto: es un nombre o indicativo)
- `days[].phase`, `days[].milestone`
- `events[].name`, `blocks[].name`
- `catalog[].name`, `capabilities[]` (cada elemento), `record`, `limit`, `dose` (o `null`), `source`
- `plans[…].activities[].note`
- `capacities[].name`, `description`, `onAmber`, `onRed`
- `signals[].name`, `description` (`unit` sigue siendo texto)
- `questionnaire.name`, `source`, `items[].domain`, `inspiredBy`, `variants[].text`, `minLabel`, `maxLabel`, `triggers[].reason`, `consistencyChecks[].message`
- `adaptationPolicy.rules[]`, `validation[].message`
- En `GET /api/adaptations`: `policy.violations[]`

Nuevos en la raíz: `languages` (`["en", "es"]`) y `defaultLanguage` (`"en"`).

`mission.timelineSource` sigue siendo texto (es una cita).

## Campos renombrados

| v4 | v5 |
| --- | --- |
| `days[].co` | `days[].opLoad` |
| `capacities[].onAmbar` / `onRojo` | `onAmber` / `onRed` |
| `signals[].ambar` / `rojo` | `amber` / `red` |
| `questionnaire.items[].ambar` / `rojo` | `amber` / `red` |

## Valores

| Dónde | v4 | v5 |
| --- | --- | --- |
| `gravity`, `transitionFrom`, `gravityReference`, `toGravity` | `tierra`, `micro`, `luna`, `marte` | `earth`, `micro`, `moon`, `mars` |
| `days[].opLoad` | `baja`, `media`, `alta`, `critica` | `low`, `medium`, `high`, `critical` |
| `catalog[].category`, `adaptableCategories`, `adaptationPolicy.windows` (claves) | `ejercicio`, `nutricion`, `evaluacion`, `suplemento`, `recuperacion`, `preparacion` | `exercise`, `nutrition`, `assessment`, `supplement`, `recovery`, `preparation` |
| `catalog[].loadType` | `CA`, `RE`, `ninguna` | `CL`, `RE`, `none` |
| `activities[].status` | `autorizado`, `condicional` | `authorized`, `conditional` |
| `events[].kind`, `triggers[].eventKind` | `maniobra`, `hito`, `prueba`, `operacion`, `eva` | `maneuver`, `milestone`, `test`, `operation`, `eva` |
| `blocks[].kind`, `adaptationPolicy.windows` (valores) | `sueno`, `presueno`, `comida`, `ejercicio`, `maniobra`, `prueba`, `operacion`, `comunicacion`, `salud`, `libre` | `sleep`, `presleep`, `meal`, `exercise`, `maneuver`, `test`, `operation`, `communication`, `health`, `off-duty` |
| `signals[].source` | `reloj`, `ropa`, `prueba` | `watch`, `garment`, `test` |
| `signals[].worseWhen` | `mayor`, `menor` | `higher`, `lower` |
| `signals[].whenTrue` | `ambar`, `rojo` | `amber`, `red` |
| Nivel de capacidad (en la app) | `verde`, `ambar`, `rojo`, `gris` | `green`, `amber`, `red`, `gray` |
| `questionnaire.items[].block` | `fijo`, `banco` | `fixed`, `bank` |
| `triggers[].kind` | `transicion`, `entorno`, `dias-micro`, `evento`, `senal`, `relleno` | `transition`, `environment`, `micro-days`, `event`, `signal`, `filler` |
| `validation[].severity` | `aviso`, `info` (`bloqueo` nunca se publica) | `warning`, `info` (`blocker`) |
| `adaptationPolicy.allowed`, adaptación `type` | `sustitucion`, `reprogramacion`, `omision` | `substitution`, `reschedule`, `omission` |
| `evidence[].source` | `manual`, `sensor`, `simulado` | `manual`, `sensor`, `simulated` |
| adaptación y alerta `source` | `interfaz`, `simulada` | `interface`, `simulated` |
| `review.status` | `pendiente`, `validada`, `objetada` | `pending`, `validated`, `objected` |
| alerta `kind` | `capacidad-roja`, `preparacion-perdida`, `incoherencia` | `capacity-red`, `preparation-lost`, `inconsistency` |
| alerta `level` | `rojo`, `aviso` | `red`, `warning` |
| `ack.status` | `nueva`, `vista`, `atendida` | `new`, `seen`, `handled` |

## Identificadores

Los del catálogo (`P`, `R`, `A`, `RB`, `M`, `N1`–`N4`, `C`, `F`, `T`, `V`, `OIG`, `RE`, `SD`, `SF`), los de tripulantes y los `entryId` no cambian.

| Tipo | v4 | v5 |
| --- | --- | --- |
| Capacidad | `fatiga`, `musculoesqueletico`, `coordinacion` | `fatigue`, `musculoskeletal`, `coordination` (`vestibular`, `cardiovascular` y `vision` no cambian) |
| Señal | `fc_reposo_delta`, `recuperacion_fc_lenta`, `hrv_nocturna_delta`, `fr_delta`, `arritmia_ecg`, `fatiga_emg`, `coordinacion_delta` | `resting_hr_delta`, `slow_hr_recovery`, `night_hrv_delta`, `resp_rate_delta`, `ecg_arrhythmia`, `emg_fatigue`, `coordination_delta` (`spo2` no cambia) |
| Pregunta | `Q1_FATIGA` | `Q1_FATIGUE` (el resto no cambia) |
| Comprobación de coherencia | `fatiga-vs-hrv` | `fatigue-vs-hrv` |
| Unidad | `lpm` | `bpm` |

## Compatibilidad del servidor

`POST /api/adaptations`, `POST /api/alerts` y los `PATCH` siguen aceptando los códigos v4 de esta tabla y los convierten; las respuestas y el registro usan siempre v5. Los paquetes v4 ya publicados se siguen sirviendo tal cual con `?version=N`.
