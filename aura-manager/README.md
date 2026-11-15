# AURA Manager

Mission control console for **AURA (Astronaut Unified Readiness & Autonomous-health)**, a project for the NASA Space Apps Challenge 2026. It is one of the two parts of the [AURA repository](../README.md); the other is the astronaut interface, [AURA Astronaut](../aura-astronaut/README.md).

Deployed at **https://aura-manager-rouge.vercel.app** (Vercel, root directory `aura-manager/`).

Starting from the mission plan, it prepares each astronaut's prevention plan (exercise, assessments, supplements and recovery) for every day, with special attention to gravity changes, and publishes a versioned mission package consumed by the astronaut interface (`../aura-astronaut`).

## Getting started

```bash
npm install
npm run dev
```

Open http://localhost:3000. The first time, `data/state/current.json` is created with the demo mission.

## Languages

The console and the data are multilingual. **English** is the reference language (NASA's) and **Spanish** the second one.

- **Console:** served at `/en` and `/es`. `/` redirects to the language chosen earlier (cookie `aura-locale`) or to the browser's preferred one, English otherwise (`proxy.ts`). The selector in the header switches language in place. UI strings live in `lib/i18n/en.ts` (reference) and `lib/i18n/es.ts`, which must have the same shape: a missing key fails type-checking.
- **Data:** every human-readable text in the state and in the package is a `LocalizedText`, e.g. `{ "en": "Sleep", "es": "Sueño" }` (`lib/i18n/text.ts`). A missing language falls back to English. Fields are edited in the console's current language; a field with no text in that language shows the English one as a dashed placeholder. Codes and identifiers are English and never translated.
- **Validation:** texts with no Spanish version are reported as information; texts with no English version, as a warning.
- **Adding a language:** add it to `LOCALES` in `lib/i18n/text.ts`, write its dictionary and register it in `lib/i18n/index.ts`.

## Mission time: MET

All times are **MET (Mission Elapsed Time)**: time elapsed since launch, written `DD/HH:MM` or `DD/HH:MM:SS` as in NASA timelines. They are stored in seconds (`lib/met.ts`). The mission is split into **flight days** (`FD01`, `FD02`…) with their MET boundaries, and each prevention activity is scheduled at a MET; its flight day is derived from it.

## Demo mission: Artemis II

**Planned** timeline of Artemis II, from NASA's "Artemis II Overview Timeline" (2026-03-18, STRIVES 20260002249). In `lib/seed.ts`:

- **From the document:** the 10 flight days, the phases and the events with their MET.
- **Read from the chart, approximate (±15 min):** crew activity blocks (sleep, meals, exercise, maneuvers, tests).
- **Team example:** each day's operational load (OL), the demonstration crew and the whole prevention plan. They are not NASA data or prescriptions.

## Views

- **Mission plan:** flight days with their MET, phases, gravity environment, transitions, operational load (OL) and events.
- **Prevention plan:** timeline of the flight day on a MET axis and each crew member's activities with their MET.
- **Catalog:** assignable actions. The tool does not invent doses; they are published as recorded.
- **Questionnaire:** the astronaut's daily questionnaire (fixed block and triggered bank), traffic-light capacities and objective signals. Read-only for now; edited in `lib/questionnaire.ts`.
- **Publish:** plan validation and package publishing.
- **Adaptations:** record of the changes the astronaut interface made on its own, to validate or object to them.
- **Alerts:** what the interface does not resolve by adapting (capacity in red, lost transition preparation measure, self-report vs. sensor inconsistency), to mark as seen or handled.

## Fixed and adaptable

The astronaut interface may adapt a bounded part of the plan on its own. Mission control decides which:

- **Adaptable:** exercise, within the timeline's exercise blocks, and menus, at meal blocks (`ADAPTABLE_WINDOW` in `lib/types.ts`). Each activity in those categories has an Adaptable/Fixed toggle.
- **Fixed:** everything else: maneuvers, tests, sleep, operations, assessments, return preparation and supplements.

The package publishes the policy (`adaptationPolicy`) and flags every block and activity with `adaptable`. The interface may replace an adaptable activity with another of the same category without increasing the load, move it within its window, or omit it.

Each adaptation is applied on board without waiting for a reply and is sent to `POST /api/adaptations`. The server always records it, checks it against the policy and flags those outside it; mission control validates or objects to it in the Adaptations tab. `GET /api/adaptations?crewId=` returns the record with the review outcome. They are stored in the `adaptations` collection.

## Alerts from the interface (`lib/alerts.ts`)

`POST /api/alerts` receives one alert or a list: `{ id, packageVersion, crewId, kind, level, capacityId?, entryId?, met, message, evidence[], source }`, with `kind` `capacity-red`, `preparation-lost` or `inconsistency` and `level` `red` or `warning`. Like adaptations, it is always recorded (`202`), resending the same `id` does not duplicate, and it only answers `400` if the message is malformed. `GET /api/alerts?crewId=` returns the record with the acknowledgement (`ack.status`: `new`, `seen` or `handled`, and `ack.note`), which the interface shows the astronaut. Mission control marks it in the Alerts tab (`PATCH /api/alerts`). They are stored in the `alerts` collection (files locally, Postgres on Vercel).

## Questionnaire and traffic light (`lib/questionnaire.ts`)

Mission control defines and publishes in the package what the interface uses to assess the astronaut:

- **Capacities** with a traffic light (green, amber, red, and gray for missing data) and what the interface does in amber (a permitted adaptation) and in red (refer and alert).
- **Objective signals:** watch (real), coordination test (real) and smart clothing (**simulated** in the demo).
- **Daily questionnaire:** 3 fixed questions with rotating wording and 2 from the bank, chosen by triggers (gravity change, environment, days in microgravity, EVA, signals). It comes from the team document "ARTEMIS-MED", adapted to AURA's principles: it does not diagnose, does not certify fitness and uses no camera, blood tests or myometry.

It is mission-agnostic: triggers use gravity environments and flight days, so it works the same for the Moon, Mars or a station. All thresholds are for demonstration (`thresholdsAreDemo: true`).

## Validation rules (`lib/rules.ts`)

| Rule | Severity |
| --- | --- |
| Authorized demanding load on a day of critical OL | Blocker (prevents publishing) |
| Demanding load on the day before a critical day | Warning |
| Day after a critical one with no recovery window | Warning |
| Transition to an environment with gravity with no preparation in the previous 2 flight days | Warning |
| Activity scheduled within a sleep period or outside the mission | Warning |
| Crew member with no plan on a flight day | Warning |
| Text with no English version | Warning |
| Conditional activity that does not say what it depends on (no `note`) | Warning |
| Gravity transition whose `transitionMet` falls outside its flight day; crew resting HR baseline outside 30–120 bpm | Warning |
| Gravity transition with no exact `transitionMet` | Information |
| Amber action (`onAmberActions`) on a non-adaptable category, a missing item or another category | Blocker |
| Amber action pointing to a demanding item (the interface will never apply it) | Warning |
| Day with no fatigue questionnaire, supplements with no dose, conditional activities, texts with no Spanish version | Information |
| Inconsistent questionnaire (non-existent capacity, signal or action, thresholds off the scale) | Blocker |
| Bank question with no available triggers | Information |

They are design rules for the demonstrator, not clinical criteria. Their messages are generated in every language and travel in the package (`validation[]`).

## Mission package

- `GET /api/package`: latest published package.
- `GET /api/package?version=N`: a specific version.
- `GET /api/package?list=1`: summary of versions.

The format (`aura.mission-package/v5`) is defined in `lib/package.ts`. v5 changed every code to English and every text to a `LocalizedText`, and adds `languages` and `defaultLanguage`; the v4 → v5 mapping is in `../aura-astronaut/contract/v4-to-v5.md`. During the migration, the adaptation and alert endpoints still accept v4 (Spanish) codes and convert them (`lib/legacy.ts`), and records stored with v4 codes are converted when read. A saved state from an older schema is replaced by the demo mission.

Optional v5 fields (a package without them is still valid and the interface falls back to a provisional behavior):

- `days[].transitionMet`: exact MET of each gravity transition, for the countdown.
- `catalog[].durationMin` and `plans[…].activities[].endMet`: planned end of each activity.
- `capacities[].onAmberActions`: concrete substitutions the interface applies in amber (e.g. the hydration menu).
- `capacities[].criticalFor`: arrival environments where a capacity is critical for leaving the vehicle.
- `crew[].restingHrBpm`: demonstration resting heart rate baseline.

The contract example consumed by the Android app is `../aura-astronaut/contract/mission-package.example.json`; pending contract changes are tracked in `../aura-astronaut/contract/pending-changes.md`.

## Data

Stored as JSON documents (`lib/storage.ts`): files in `data/` locally (outside git), the `aura_documents` table in Postgres when `DATABASE_URL` is set (Vercel). No authentication: it is meant for a single user in the demonstrator.
