# From `aura.mission-package/v4` to `v5`

Since 2026-11-15, AURA Manager uses English codes and multilingual texts (see AGENTS.md, "Contract" and "Languages"). The content is the same as in v4; the names change.

## Texts: from `string` to `LocalizedText`

They become `{ "en": "…", "es": "…" }` objects (a language may be missing: use `defaultLanguage`):

- `mission.name`, `mission.destination`, `mission.description`
- `crew[].role` (`crew[].name` stays plain text: it is a name or call sign)
- `days[].phase`, `days[].milestone`
- `events[].name`, `blocks[].name`
- `catalog[].name`, `capabilities[]` (each element), `record`, `limit`, `dose` (or `null`), `source`
- `plans[…].activities[].note`
- `capacities[].name`, `description`, `onAmber`, `onRed`
- `signals[].name`, `description` (`unit` stays plain text)
- `questionnaire.name`, `source`, `items[].domain`, `inspiredBy`, `variants[].text`, `minLabel`, `maxLabel`, `triggers[].reason`, `consistencyChecks[].message`
- `adaptationPolicy.rules[]`, `validation[].message`
- In `GET /api/adaptations`: `policy.violations[]`

New at the root: `languages` (`["en", "es"]`) and `defaultLanguage` (`"en"`).

`mission.timelineSource` stays plain text (it is a citation).

## Renamed fields

| v4 | v5 |
| --- | --- |
| `days[].co` | `days[].opLoad` |
| `capacities[].onAmbar` / `onRojo` | `onAmber` / `onRed` |
| `signals[].ambar` / `rojo` | `amber` / `red` |
| `questionnaire.items[].ambar` / `rojo` | `amber` / `red` |

## Values

| Where | v4 | v5 |
| --- | --- | --- |
| `gravity`, `transitionFrom`, `gravityReference`, `toGravity` | `tierra`, `micro`, `luna`, `marte` | `earth`, `micro`, `moon`, `mars` |
| `days[].opLoad` | `baja`, `media`, `alta`, `critica` | `low`, `medium`, `high`, `critical` |
| `catalog[].category`, `adaptableCategories`, `adaptationPolicy.windows` (keys) | `ejercicio`, `nutricion`, `evaluacion`, `suplemento`, `recuperacion`, `preparacion` | `exercise`, `nutrition`, `assessment`, `supplement`, `recovery`, `preparation` |
| `catalog[].loadType` | `CA`, `RE`, `ninguna` | `CL`, `RE`, `none` |
| `activities[].status` | `autorizado`, `condicional` | `authorized`, `conditional` |
| `events[].kind`, `triggers[].eventKind` | `maniobra`, `hito`, `prueba`, `operacion`, `eva` | `maneuver`, `milestone`, `test`, `operation`, `eva` |
| `blocks[].kind`, `adaptationPolicy.windows` (values) | `sueno`, `presueno`, `comida`, `ejercicio`, `maniobra`, `prueba`, `operacion`, `comunicacion`, `salud`, `libre` | `sleep`, `presleep`, `meal`, `exercise`, `maneuver`, `test`, `operation`, `communication`, `health`, `off-duty` |
| `signals[].source` | `reloj`, `ropa`, `prueba` | `watch`, `garment`, `test` |
| `signals[].worseWhen` | `mayor`, `menor` | `higher`, `lower` |
| `signals[].whenTrue` | `ambar`, `rojo` | `amber`, `red` |
| Capacity level (in the app) | `verde`, `ambar`, `rojo`, `gris` | `green`, `amber`, `red`, `gray` |
| `questionnaire.items[].block` | `fijo`, `banco` | `fixed`, `bank` |
| `triggers[].kind` | `transicion`, `entorno`, `dias-micro`, `evento`, `senal`, `relleno` | `transition`, `environment`, `micro-days`, `event`, `signal`, `filler` |
| `validation[].severity` | `aviso`, `info` (`bloqueo`, never published) | `warning`, `info` (`blocker`) |
| `adaptationPolicy.allowed`, adaptation `type` | `sustitucion`, `reprogramacion`, `omision` | `substitution`, `reschedule`, `omission` |
| `evidence[].source` | `manual`, `sensor`, `simulado` | `manual`, `sensor`, `simulated` |
| adaptation and alert `source` | `interfaz`, `simulada` | `interface`, `simulated` |
| `review.status` | `pendiente`, `validada`, `objetada` | `pending`, `validated`, `objected` |
| alert `kind` | `capacidad-roja`, `preparacion-perdida`, `incoherencia` | `capacity-red`, `preparation-lost`, `inconsistency` |
| alert `level` | `rojo`, `aviso` | `red`, `warning` |
| `ack.status` | `nueva`, `vista`, `atendida` | `new`, `seen`, `handled` |

## Identifiers

Catalog ids (`P`, `R`, `A`, `RB`, `M`, `N1`–`N4`, `C`, `F`, `T`, `V`, `OIG`, `RE`, `SD`, `SF`), crew ids and `entryId`s do not change.

| Kind | v4 | v5 |
| --- | --- | --- |
| Capacity | `fatiga`, `musculoesqueletico`, `coordinacion` | `fatigue`, `musculoskeletal`, `coordination` (`vestibular`, `cardiovascular` and `vision` do not change) |
| Signal | `fc_reposo_delta`, `recuperacion_fc_lenta`, `hrv_nocturna_delta`, `fr_delta`, `arritmia_ecg`, `fatiga_emg`, `coordinacion_delta` | `resting_hr_delta`, `slow_hr_recovery`, `night_hrv_delta`, `resp_rate_delta`, `ecg_arrhythmia`, `emg_fatigue`, `coordination_delta` (`spo2` does not change) |
| Question | `Q1_FATIGA` | `Q1_FATIGUE` (the rest do not change) |
| Consistency check | `fatiga-vs-hrv` | `fatigue-vs-hrv` |
| Unit | `lpm` | `bpm` |

## Server compatibility

`POST /api/adaptations`, `POST /api/alerts` and the `PATCH` endpoints still accept the v4 codes in this table and convert them; responses and records always use v5. v4 packages already published are still served as is with `?version=N`.
