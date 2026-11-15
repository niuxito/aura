# AURA Astronaut — the astronaut interface

Instructions for any agent or developer working in this folder. Read them in full before writing code.

## What AURA is

**AURA (Astronaut Unified Readiness & Autonomous-health)** is the team's project for the NASA Space Apps Challenge 2026, challenge "Create Health Monitoring Software for Astronauts on Space Missions". Its goal is to help the crew preserve the physical capacities (strength, coordination, balance, exercise tolerance) they need for every day of the mission and, above all, for **gravity changes**: landing, leaving the spacecraft and returning to Earth.

It has two parts, in the same repository:

| Part | Folder | Role |
| --- | --- | --- |
| **AURA Manager** | `../aura-manager` | Mission control web console. Defines the mission plan and the prevention plan (exercise, assessments, supplements, recovery) and publishes a versioned **mission package**. |
| **AURA Astronaut** | this folder | The astronaut interface: an agent that collects data and shows, in augmented reality, what the astronaut needs to know and do today. |

**AURA is not only for Artemis II.** It must serve future missions to the Moon, Mars and space stations. Artemis II is the case used to validate it and to run the demo. Do not write anything tied to that mission: everything that depends on it (flight days, gravity environments, transitions, events, questionnaire, thresholds) comes in the package.

### Final goal and priorities

Everything the interface does serves one goal: that the astronaut **can complete the mission** and **reaches each gravity change able to walk out of the spacecraft on their own**. The interface never claims they will (it does not certify fitness): it watches the indicators that make it likely, protects the measures that prepare it and warns in time when something deviates.

- **Critical risks of each transition.** In the demo mission (Artemis II, about 10 days in microgravity) the ones that most compromise egress after splashdown are **orthostatic intolerance** (dizziness or fainting when standing up) and **balance impairment** (made worse by the rocking on the water). Loss of strength and bone is small in such a short flight; on long missions (Mars) it comes to the fore.
- **Prevention measures in the plan** for those risks: fluid and salt loading before reentry (`SF`, with no dose: the medical protocol sets it), the orthostatic intolerance garment (`OIG`), aerobic exercise (`A`) and return preparation (`V`). Activities with `transitionPrep: true` and those that prepare the transition are never lost from sight.
- **Rules protect the day without sacrificing the transition.** Removing load protects the astronaut today, but every lost session takes away preparation. Therefore:
  - keep track of planned versus done (including what the interface substituted or omitted);
  - rather than omitting, prefer rescheduling within the policy;
  - if a rule is about to remove a measure that prepares the transition (for example, the aerobic session on the eve of reentry), flag it and alert mission control instead of treating it as resolved; never raise the load on its own.
- **Transition preparation in the HUD.** As a gravity change approaches, a panel gathers: countdown, state and **day-by-day trend** of the capacities critical for that transition against the astronaut's own baseline, and compliance with the preparation measures. It speaks of "indicators for egress", never of "fit".
- **What comes from the package:** which capacities are critical for each arrival (`capacities[].criticalFor`). If a package does not carry it, the interface uses what is stated here for the demo mission and labels it as provisional. The minimum exercise dose is still pending in the contract: until then only planned versus done sessions are counted.

The full functional specification is in the team's Notion page "Space Apps Challenge 2026" and its subpage "Mars Ready — Functional requirements and monitor design" (versions 0.3 and 0.4). If in doubt between Notion and this file about the scope of the demo, this file prevails.

## What it is

An **Android** app for the hackathon demo:

- The main screen is the **rear camera image** in full screen, with the information overlaid on top (HUD). That is what we call "augmented reality" in the demo: there is no need to anchor 3D objects to the world or detect planes.
- The app behaves as an **agent**: it loads the mission package, knows the astronaut's MET and flight day, presents the day's plan, collects their data and warns them about deviations, with an explanation.
- All times are expressed in **MET (Mission Elapsed Time)**, the time elapsed since launch, in the `DD/HH:MM` or `DD/HH:MM:SS` format used in NASA timelines (for example, TLI at `01/01:08:42`). Days are **flight days** (`FD01`, `FD02`…), which do not match calendar days: their MET boundaries come in the package.
- It works **offline**. The network is used only, when available, to download a new package and to send adaptations and alerts.

### What the HUD shows

1. **Mission context:** MET clock, flight day, phase, milestone of the day, gravity environment and operational load (OL).
2. **Gravity transition:** if there is a gravity change today or in the next flight days (field `transitionFrom`), a highlighted notice with the MET countdown, for example "Splashdown in 01/06:00 · microgravity → 1 g".
3. **Day plan:** the astronaut's activities sorted by MET, with their code, name, status (`authorized` or `conditional`) and whether they are done. Next to them, what the mission timeline has planned around (sleep, meals, maneuvers), so the plan is understood in context.
4. **State per capacity:** green, amber, red or **gray** traffic light, always with text and a reason.
5. **Next recommended action** and its rationale.

Keep the HUD readable over any camera background: semi-transparent panels, high contrast, large text, few elements at a time. Evaluate use with one hand and with gloves.

### Voice notices

Besides the screen, the interface speaks the important information, so the astronaut receives it even when not looking at the device.

- **Engine:** Android's own `TextToSpeech`, in the interface's active language and **offline**. At startup it checks that there is a voice for that language that needs no network; if there is none, it says so on screen and keeps working with the HUD only.
- **What is said:** only important messages, once each: the upcoming gravity transition, the next recommended action when it changes, adaptations applied with their reason, the day's mission control notices and the result of an adaptation's validation. The whole HUD is never read out and a message already said is never repeated.
- **Nothing that is not on screen:** the voice reads the same text the HUD shows at that moment. The HUD rules also apply to the voice: it does not diagnose, it states the reason and says that simulated data is simulated.
- **Mute:** a mute control, always visible and easy to press with gloves. The preference is kept between sessions. Whatever is said aloud can be heard by anyone nearby.
- **No interference:** it requests audio focus while speaking and does not cut a more important message for a less important one.

The text of each notice is produced in the same place as the HUD's; the voice has no texts of its own.

### What data it collects

- Confirmation of each plan activity (done, omitted, reason).
- Daily fatigue, rest and symptoms questionnaire (catalog code `F`). Its questions, selection and thresholds come in the package (section "Daily questionnaire and traffic light").
- Smart clothing signals (nighttime heart rate variability, breathing, SpO2, ECG, muscle fatigue). There is no garment in the demo: they are **simulated**, always labeled.
- Exercise log: type, load or repetitions, perceived exertion. *Not built yet: exercise and supplement activities are confirmed as done or omitted.*
- Tactile visuomotor coordination test (code `C`): hits, errors and latency per attempt. It is an experimental team test, not a NASA-validated one, and it must say so on screen. *Not built yet: its signal (`coordination_delta`) is simulated by the demo scenarios.*
- Confirmation of supplement intake (as a plan activity done or omitted).
- Live heart rate from a watch over Bluetooth (section "Heart rate from the watch").

Every observation stores value, unit, date, source (`manual`, `sensor` or `simulated`) and quality.

### Heart rate from the watch

The interface reads the heart rate from a watch that broadcasts it with the **standard Bluetooth LE profile**, like a chest strap. Tested with a **Garmin Forerunner 745**: with "Broadcast Heart Rate" enabled on the watch, it advertises the Heart Rate service. No Garmin SDK or app is used, nor ANT+.

- **Protocol:** Heart Rate service `0x180D`, Heart Rate Measurement characteristic `0x2A37` via notifications. Read the flags byte: bit 0 says whether the value is `uint8` or `uint16`, and bit 4 whether RR intervals are present (in 1/1024 s). RR intervals are optional: use them if they arrive, without relying on them.
- **Pairing:** scan only for devices advertising `0x180D`, let the astronaut pick theirs and store its address to reconnect on its own at startup and after a disconnection. If the watch does not show up, the HUD explains how to enable broadcasting on the watch.
- **Permissions:** on Android 12 or later, `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT`; on earlier versions, the location permissions scanning requires. Handle a denied permission and Bluetooth turned off without breaking the app.
- **Offline:** it works in airplane mode with Bluetooth on. It needs neither network nor the Garmin Connect app.
- **Observations:** stored with source `sensor`, unit `bpm` and their MET. Do not store every notification: aggregate (for example, one sample per interval) at the rate defined in the rules module.
- **In the HUD:** live value, connection state and age of the last data point. With no watch connected or with stale data, it shows **gray** ("no watch data"), never the last value as if it were current.
- **In the rules:** heart rate is one more indicator and may be evidence for an adaptation (`evidence[].source: "sensor"`). Any baseline (for example, the astronaut's own resting rate) and its thresholds are for demonstration and labeled as such. Nothing is diagnosed from it.
- **Without a watch:** the demo must be shown without a watch. The heart rate source sits behind an interface, with a Bluetooth implementation and a simulated one for the demo scenarios; the simulated one stores source `simulated` and the HUD says so.
- **Verification:** the emulator has no real Bluetooth LE. Test parsing and formatting with simulated data and unit tests of `0x2A37`, and the real connection on a device with the watch.

### Daily questionnaire and traffic light

Mission control defines it in AURA Manager (Questionnaire tab) and it travels in the package: `capacities`, `signals` and `questionnaire`. The interface **has no questions, thresholds or capacities written in the code**; it reads them from the package and applies the algorithm in this section. It comes from the team document "ARTEMIS-MED — Medical logic, questionnaire and traffic light", adapted to AURA's principles.

**Selecting the day's questions** (every time the plan schedules the activity `questionnaire.catalogItemId`):

1. **Fixed block:** the questions with `block: "fixed"` (currently 3), in package order. For each one, the variant shown is `(flight day + crew member index in crew[] + question index) % number of variants`.
2. **Bank:** among the questions with `block: "bank"`, collect the triggers that hold today, sort them by descending `priority` and take the first `bankPerDay` distinct questions. If there are not enough, complete with the `filler` triggers by priority. Variant: `flight day % number of variants`.
3. Triggers (`triggers[].kind`), always by flight days:
   - `transition`: today is between `daysBefore` flight days before and `daysAfter` after a flight day with `transitionFrom`. If `toGravity` is not empty, only transitions into those environments count (that day's `gravity`). `minDaysInMicro`: only if at least that many consecutive flight days in microgravity precede the transition.
   - `environment`: today's flight day `gravity` is in the list.
   - `micro-days`: consecutive flight days in microgravity up to today, inclusive, ≥ `min`.
   - `event`: there is an event of that `kind` (for example, `eva`) in the last `withinDays` flight days.
   - `signal`: the latest measurement of that signal satisfies `op value`; for a boolean signal, that it is true. With no measurement, it does not fire.
4. Each question shown can explain why it is asked today with its trigger's `reason`.

**Texts:** the variant text does not include the scale. Show it with `scale.min`/`scale.max` and the variant's `minLabel`/`maxLabel` labels (and say it that way in voice notices). One-tap answers, suitable for gloves.

**Normalization:** if the variant has `inverted: true`, the value is stored normalized as `(max + min) − answer`. Across the system, high = worse. Store the original answer, the normalized one, the variant and the MET.

**Traffic light per capacity** (`capacities[]`; currently 6, including coordination, which comes from test `C`):

- Each answered question gives a level: `red` if the normalized value ≥ `red`, `amber` if ≥ `amber`, `green` otherwise (`null` threshold = n/a). Each signal likewise with `amber`/`red` in the direction of `worseWhen` (`higher`: value ≥ threshold; `lower`: value ≤ threshold), or with `whenTrue` if it is boolean.
- The capacity's level is **the worst** of its questions and signals of the day. A high value always counts on its own, even if other data is missing: it is never lowered to green for lack of the blood tests or camera the original document asked for.
- **Gray** if the capacity has no data for the day. Never green by default.
- `amber` → adaptation allowed by `adaptationPolicy`, guided by `capacities[].onAmber` and `onAmberActions`. `red` → `capacities[].onRed`: refers to the mission procedure and alerts; it does not decide on its own.
- Every state says which question or signal causes it, with the value and the threshold, and that thresholds are for demonstration (`thresholdsAreDemo: true`).

**Consistency** (`questionnaire.consistencyChecks`): if the normalized answer to `questionId` is ≤ `answerAtMost` and the signal satisfies `op value`, show `message`. The level does not drop: the worse of the two is used.

**Simulated smart clothing:** signals with `simulated: true` have no device. They are generated from the demo scenarios with plausible values that are consistent with each other, stored with source `simulated` and shown as "simulated" in the HUD and in the adaptation evidence (`evidence[].source: "simulated"`). Signals with `source: "watch"` come from the watch over Bluetooth when connected (source `sensor`), those with `source: "test"` from the coordination test, and those with `source: "garment"` are smart clothing.

**What is not done**, even though it was in the original document: a collective red alert across crew members, certifying "fit" or "unfit" to leave the spacecraft, diagnosing, adding activities or doses, and reading health status from the face with the camera.

### Silhouette and simulated activity

In the right column of the HUD a semi-transparent 3D human silhouette rotates. It has the shape of the BodyParts3D skin (DBCLS, CC BY-SA) taken from anatomia.alexisparron.com and simplified (`assets/models/body.bin`; attribution in demo mode and in `assets/models/LICENSE-body.txt`). `docs/silueta/silueta.html` renders the same drawing in a browser.

- **Movement:** it imitates what smart clothing would record. The tablet's accelerometer detects steps (walking) and squats, and the silhouette reproduces them in profile. Demo mode can force WALK, SQUAT or STILL in case the sensor fails live. Thresholds live in `motion/BodyMotion.kt` (`MotionRules`) and are for demonstration.
- **Muscles:** the groups at work light up (quadriceps, glutes and hamstrings, calves and abdomen) in a color of their own, distinct from the traffic light.
- **Heart and aura:** inside the chest a heart beats at the heart rate (watch or simulated) and on every beat a ring of light leaves it, the "aura". With no recent data it does not beat. Its color is its own, distinct from the traffic light.
- **It is simulated and visual only:** labeled "SIM" next to the silhouette, not stored as an observation, not sent to mission control and not fed into the traffic light. The lighting shows which groups are working, never fatigue, injury or health status.

## Scope of the first version

Built in this order, each step shown working before moving on. Steps 1–5b, 7 and 8 are delivered; step 6 is pending.

1. Android project with full-screen camera view and a well-handled camera permission (including denial).
2. Mission package loaded from `assets` (copy of `contract/mission-package.example.json`), choice of astronaut and current MET. For the demo the MET is set by hand (and may run accelerated); if the package carries `mission.launchUtc`, it can also be computed from the clock.
3. HUD with mission context, gravity transition and day plan, with voice notices for the transition and the next action. Each later step adds its own voice notices (adaptations, validation).
4. Data collection: confirming activities and the daily questionnaire read from the package (selection, normalization), with local persistence that survives closing the app.
5. State per capacity (with the smart clothing signals simulated per scenario) and explained notices, and plan adaptation according to "Plan adaptation by the interface": decision, local application, send queue to mission control and reading of the validation result.
5b. Transition preparation ("Final goal and priorities"): panel with countdown, critical capacities and their day-by-day trend, compliance with the preparation measures and planned versus done count; the rules alert when an adaptation removes a preparation measure.
6. Tactile coordination test. *Pending: the demo simulates its signal.*
7. Live heart rate from the watch over Bluetooth, with its simulated source, its HUD indicator and its use as evidence in the rules.
8. Package download from AURA Manager (`https://aura-manager-rouge.vercel.app`, or the local Manager in development).

Three demo scenarios, with simulated and labeled data: stable profile, accumulated fatigue after a day of critical operational load, and worsening in the coordination test. Everything can be shown in **airplane mode**.

Out of scope for this version: other real external sensors (body IMU, exercise equipment; from the watch only the heart rate over Bluetooth, and smart clothing only simulated), integration with Garmin Connect, Health Connect or Garmin SDKs, emotion recognition through the camera, speech recognition (spoken commands; voice notices are in scope) and sending raw observations to mission control (only adaptations and alerts are sent, with their evidence).

## Stack

- **Kotlin + Jetpack Compose**, a single activity.
- **CameraX** for the camera preview, with the HUD drawn in Compose on top.
- **Room** for observations and local state; the mission package is stored as is as a JSON file.
- **kotlinx.serialization** to read the package.
- **`android.speech.tts.TextToSpeech`** for voice notices, with no external libraries.
- **Android Bluetooth LE API** (`BluetoothLeScanner`, `BluetoothGatt`) for the watch heart rate, with no external libraries.
- No cloud services and no dependencies that require a connection to work.

Before changing Gradle, AGP, Kotlin, Compose or CameraX versions, check which are the current stable ones and what is installed on the machine (Android Studio, SDK, JDK); do not trust remembered versions. If the team prefers another stack, ask before changing it.

## Contract: the mission package

AURA Manager publishes a JSON document with schema `aura.mission-package/v5`. v5 uses **English codes** for every enumerated value and **multilingual texts**: every human-readable text (names, descriptions, questions, scale labels, trigger reasons, `validation[]` messages, policy rules…) is a `LocalizedText` object, for example `{ "en": "Sleep", "es": "Sueño" }`. The package declares `languages` (`["en", "es"]`) and `defaultLanguage` (`"en"`). See "Languages" below. The equivalence with the previous v4 contract (Spanish codes) is in `contract/v4-to-v5.md`.

The app **accepts only v5** (`SUPPORTED_SCHEMA`); older packages are rejected with an explanation and the app keeps the package it already has.

- **Real example:** `contract/mission-package.example.json` (v5), generated by the Manager with the demo mission. It is also the package embedded in `app/src/main/assets/mission-package.json` (a test checks that both are identical). `contract/mission-package.v4.example.json` is kept only as a legacy fixture for the test that rejects old schemas.
- **Demo mission:** the **planned** timeline of Artemis II (10 flight days, from `00/00:00` to splashdown at `09/01:42:48`), taken from NASA's "Artemis II Overview Timeline" of 2026-03-18. It is a lunar flyby with no landing: the crew is in microgravity for the whole flight and the only gravity transitions are launch and the return to Earth. Events and their METs come from the document; activity blocks are read from its chart and are approximate; the operational load, the crew (CDR, PLT, MS1, MS2, no real names) and the whole prevention plan are a team example, not NASA data.
- **MET in the package:** every time travels twice, in seconds (`met`, `startMet`, `endMet`) to compute with and as a label (`metLabel`, `startMetLabel`, `endMetLabel`) to display. Always compute with the seconds.
- **Type definitions:** `../aura-manager/lib/package.ts` and `../aura-manager/lib/types.ts`. They are the source of truth; if they change, update the example and the Kotlin models.
- **Download:** `GET /api/package` returns the latest published one; `GET /api/package?version=N`, a specific version; `GET /api/package?list=1`, the summary of versions. If no package has been published yet, `GET /api/package` answers `404` and `?list=1` returns `[]`: the app keeps the package it has (the one in `assets` or the last downloaded).
- **Server:** AURA Manager is deployed at **`https://aura-manager-rouge.vercel.app`** (HTTPS). It is the default base URL to download packages, send adaptations and alerts and read their review. It lives in a single configuration place (`BuildConfig.MANAGER_URL`) and can be changed in demo mode. For development with the local Manager (`npm run dev` in `../aura-manager`, port 3000), from the emulator the computer is `http://10.0.2.2:3000`, over USB `http://localhost:3000` with `adb reverse tcp:3000 tcp:3000`, and from a real device on the network, the computer's LAN IP. Those local URLs are unencrypted HTTP: they are allowed only for those hosts in the network security configuration and only in the debug variant.

Main fields:

| Field | Content |
| --- | --- |
| `schema`, `packageVersion`, `publishedAt`, `checksum` | Package identity and version. Always show the loaded version. |
| `languages`, `defaultLanguage` | `languages`: languages the texts come in; `defaultLanguage`: fallback language (`en`). |
| `simulated` | `true` if the mission is fictitious. If so, the HUD shows it permanently. |
| `mission` | Name, destination, description (`LocalizedText`), `timelineSource` (document the timeline comes from) and `launchUtc` (UTC instant of MET 0, or `null` if not set). |
| `gravityReference` | Gravity relative to Earth: `earth` 1, `mars` 0.38, `moon` 0.165, `micro` 0. |
| `crew[]` | Crew members: `id`, `name`, `role` and optional `restingHrBpm` (baseline resting HR, for demonstration). |
| `days[]` | One element per flight day: `day`, `label` (`FD03`), `startMet`, `endMet`, `phase`, `milestone`, `gravity` (environment at the end of the flight day), `transitionFrom` (only if there is a transition that day), optional `transitionMet` (exact MET of the transition) and `opLoad` (`low`, `medium`, `high`, `critical`). A MET belongs to the day whose `[startMet, endMet)` interval contains it. |
| `events[]` | Point events of the timeline, sorted by MET: `met`, `metLabel`, `day`, `name`, `kind` (`maneuver`, `milestone`, `test`, `operation`, `eva`) and `approx` (`true` if the timeline gives it as approximate; show it with `~`). |
| `blocks[]` | Crew activity blocks: `startMet`, `endMet`, `name`, `adaptable`, `adaptableCategories`, `kind` (`sleep`, `presleep`, `meal`, `exercise`, `maneuver`, `test`, `operation`, `communication`, `health`, `off-duty`). They may cross the boundary between two flight days. They are approximate. |
| `catalog[]` | Prevention actions. Keys: `id`, `code`, `name`, `category` (`exercise`, `nutrition`, `assessment`, `preparation`, `recovery`, `supplement`), `loadType` (`CL`, `RE`, `none`), `intense`, `transitionPrep`, `selfReport`, `record` (what the monitor must record), `limit` (limit of use or interpretation, which must be shown), `capabilities` (list of `LocalizedText`), `dose` (`LocalizedText` or `null` = no prescribed dose), `source`, `experimental` and optional `durationMin` (team estimate). |
| `adaptationPolicy` | What the interface may adapt on its own: `windows` (adaptable category → block kind within which it may move), `allowed` (adaptation types), `rules` (the rules as text) and `report` (where to send each adaptation; `blocking` is always `false`). |
| `plans` | Per `crewId`, a list with one element per flight day: `{ day, activities: [{ entryId, itemId, met, metLabel, status, adaptable, endMet?, endMetLabel?, note? }] }`, with activities sorted by MET. `itemId` points to `catalog[].id`; `entryId` is the stable reference adaptations cite; `adaptable` says whether the interface may change it. |
| `capacities[]` | Traffic-light capacities: `id`, `name`, `description`, `onAmber` and `onRed` (what the interface does at each level), and optional `onAmberActions` (concrete substitutions in amber, `[{ category, replacementItemId }]`) and `criticalFor` (arrival environments where it is critical for leaving the spacecraft). |
| `signals[]` | Objective signals: `id`, `name`, `unit`, `source` (`watch`, `garment`, `test`), `simulated`, `capacityId`, `kind` (`number` or `boolean`) and their thresholds (`worseWhen`: `higher`/`lower`, `amber`, `red`, or `whenTrue`: `amber`/`red`). `unit` is an untranslated symbol (`bpm`, `%`). |
| `questionnaire` | Daily questionnaire: `catalogItemId`, `fixedPerDay`, `bankPerDay`, `items[]` (`id`, `block`, `domain`, `capacityId`, `scale`, `inspiredBy`, `variants[]` with `text`, `minLabel`, `maxLabel` and `inverted`, `amber`/`red` thresholds and `triggers[]`) and `consistencyChecks[]`. `block` is `fixed` or `bank`; `triggers[].kind` is `transition`, `environment`, `micro-days`, `event`, `signal` or `filler`. |
| `thresholdsAreDemo` | Always `true`: capacity, signal and questionnaire thresholds are for demonstration. |
| `validation[]` | Warnings and information mission control left in force when publishing: `severity` (`warning` or `info`), `day`, `crewId`, `message` (`LocalizedText`), `rule`. Show them on the corresponding day. |

The `checksum` is the sha256 of the package's canonical JSON (sorted keys, no whitespace) without the `checksum` field. The app verifies it; a mismatch in a downloaded package discards it.

If the app needs something the package does not carry, do not invent it in the client: record it as a pending contract change (`contract/pending-changes.md`) and say so, because the Manager must change too.

## Plan adaptation by the interface

The interface makes decisions about the astronaut's health and **adapts certain parts of the plan on its own**. Which parts is decided by mission control and flagged in the package; the interface never infers or extends it.

### What is fixed and what is adaptable

- **Fixed:** every block with `adaptable: false` (maneuvers, tests, sleep, operations, conferences) and every activity with `adaptable: false` (questionnaires, assessments, return preparation, supplements). It is never modified from the interface under any circumstances: an operation that is crucial for the mission is not touched.
- **Adaptable:** activities with `adaptable: true`. Currently **exercise**, within exercise blocks, and **menus**, in meal blocks. Mission control may fix a specific activity even if its category is adaptable; always respect the field, not the category.

The HUD shows which activities are adaptable and which are fixed, and clearly flags those the interface changed from the original plan.

### What the interface may do

Only what `adaptationPolicy` allows (read `rules` and `allowed` from the package; this summary does not replace them):

1. **Substitution:** replace an adaptable activity with another catalog action of the **same category**. Never with one with `intense: true` if the original was not: the interface does not increase the load.
2. **Reschedule:** move it to another MET within an adaptable block of its category and on the same flight day.
3. **Omission:** remove it, always with a reason.

It does not create new activities or change doses.

### When it decides

Decisions come from a single rules module, based on the data collected:

1. If data is missing, it asks for the data point or the test and shows **gray**; it does not adapt blindly and never shows green by default.
2. Given fatigue, poor sleep or worse performance, it replaces exercise with a lower-load alternative or omits it, and may change the menu (for example, extra hydration). It never increases the load to compensate.
3. If the next flight day has `critical` operational load, it does not propose activities with `intense: true`.
4. A `conditional` activity is not presented as mandatory: it states what it depends on.
5. Given a risk signal that adapting exercise or diet does not resolve, it does not decide on its own: it refers to the mission procedure and alerts mission control.
6. Every decision says which indicator changed, against which baseline, with which data point and at which MET, and what was changed.

The thresholds of these rules are for demonstration, not clinical, and are labeled as such.

### Sending to mission control: record and validation, non-blocking

Every adaptation is **applied on board immediately** and also sent to mission control to be recorded and validated. Sending is **non-blocking**: the interface does not wait for a reply to apply the change or to keep working.

- Each adaptation is stored in the local database with a send state (`pending`, `sent`) and sent in the background. Offline, it stays queued and is retried; the app works the same in airplane mode.
- The `id` is generated on the device (UUID). Resending the same `id` does not duplicate the record, so retrying is safe.
- `POST /api/adaptations` accepts an object or a list and answers `202` with what was recorded. It only answers `400` if the message is malformed.

```json
{
  "id": "0d3c1f0e-…",
  "packageVersion": 3,
  "crewId": "cdr",
  "entryId": "cdr-R-273600",
  "type": "substitution",
  "replacementItemId": "M",
  "decidedAtMet": 273000,
  "reason": "High fatigue reported after 4 h of sleep.",
  "evidence": [
    { "indicator": "Self-reported fatigue", "value": "8", "unit": "0–10", "source": "manual", "met": 272400 },
    { "indicator": "Sleep", "value": "4", "unit": "h", "source": "manual" }
  ],
  "source": "interface"
}
```

`type` is `substitution` (carries `replacementItemId`), `reschedule` (carries `newMet`, in seconds) or `omission`. `reason` is required. `evidence[].source` is `manual`, `sensor` or `simulated`; `source` is `interface` or `simulated`. `reason`, `indicator` and `message` are free text, written in the language they were shown to the astronaut in. The server still accepts the v4 codes (`sustitucion`, `simulado`, `interfaz`…) and converts them, but always answers with v5 codes.

- The server checks each adaptation against the policy and flags it as within or outside it (`policy.withinPolicy`, `policy.violations`), but **does not reject it**. An out-of-policy adaptation means an interface bug: it should not happen, and unit tests guarantee it.
- Mission control validates or objects to it afterwards. `GET /api/adaptations?crewId=cdr` returns the record with `review.status` (`pending`, `validated`, `objected`) and `review.note`. `policy.violations[]` are `LocalizedText`. The result is shown to the astronaut when it arrives. An objection does not undo by itself what was done: it is shown with the note, and mission control changes the plan by publishing a new package.

### Alerts to mission control

What the interface does not resolve by adapting is reported to mission control with `POST /api/alerts`, with the same guarantees as adaptations: stored in the local database with a send state, sent in the background without blocking, retried offline, and the server answers `202` without rejecting it for its content.

- **When** (one alert per situation; `id` is a UUID derived from crew member and situation, so resending does not duplicate):
  - `capacity-red` (`level: "red"`): a capacity in red, once per capacity and flight day. It is the "alert mission control" of rule 5 and of `capacities[].onRed`.
  - `preparation-lost` (`level: "warning"`): an adaptation that removes a transition preparation measure.
  - `inconsistency` (`level: "warning"`): the astronaut reports being fine and the sensor indicates otherwise (`consistencyChecks`).
  - What is only amber is not reported separately: the adaptation already arrives with its evidence.
- **Shape:** `{ id, packageVersion, crewId, kind, level, capacityId?, entryId?, met, message, evidence[], source }`. `message` is the text the astronaut saw; `evidence[]` as in adaptations.
- **Acknowledgement:** `GET /api/alerts?crewId=` returns `ack.status` (`new`, `seen`, `handled`) and `ack.note`. The HUD shows it next to the red notice and the voice says it once. Mission control marks it in the Manager's Alerts tab (`PATCH /api/alerts`).
- Adaptations and alerts are synced separately: if a server does not have `/api/alerts` yet, adaptations keep arriving.

## Languages

AURA is multilingual. **English** is the reference language (NASA's) and **Spanish** the second one. The app works fully in both:

- **Interface texts** (HUD labels, buttons, fixed notices, the voice): in Android resources, `res/values/strings.xml` in English (default) and `res/values-es/strings.xml` in Spanish. No texts written in Kotlin code. A unit test checks that both files have the same keys and format arguments.
- **Active language:** the system's if it is one of the package `languages`; otherwise `defaultLanguage` (`en`). It can be changed in demo mode (Android 13+ per-app language preferences, `AppCompatDelegate.setApplicationLocales`).
- **Package texts:** every `LocalizedText` is resolved by a single function: the active language; if missing, `defaultLanguage`; if also missing, the first available. Never show the object or an empty text if it exists in another language.
- **What the app sends** (`reason`, `evidence[].indicator`, alert `message`) is free text and travels as is, in the active language. Codes (`type`, `kind`, `level`, `source`…) are always the English v5 ones, whatever the language.
- **Formats:** the MET (`DD/HH:MM`) is not localized; numbers and dates use the active `Locale`.

## Non-negotiable principles

They come from the specification and apply to the whole interface and every text:

- The app **does not diagnose**, does not certify that someone is "fit" and does not show a health percentage or a "100% functional".
- Color never goes alone: always with text and a reason. Gray means "insufficient data".
- No clinical thresholds or doses are invented. The prototype's thresholds are demonstration rules and labeled as such.
- Simulated data is labeled as simulated on screen and in the database.
- The tactile test is not equivalent to the real ability to leave a capsule or to dexterity with gloves.
- What is measured in microgravity does not by itself predict performance on the Moon, Mars or Earth.
- The camera is used as the HUD background. No emotions or health status are inferred from the face.
- Health data stays on the device.

## Conventions

- Code, comments, documentation and commit messages in **English**. Interface texts in English and Spanish, from the string resources.
- No magic values spread across the interface: rules and their thresholds live in a single module, with a name and a comment saying why.
- Unit tests for package parsing and for the adaptation rules, which are what cannot fail in the demo. In particular: no rule may produce an adaptation on something fixed or outside `adaptationPolicy`.
- Check every milestone on an emulator or device before calling it done, including airplane mode and restarting the app.
- Build and test with JDK 21: `./gradlew testDebugUnitTest assembleDebug` (set `JAVA_HOME` if Java is not on the path).
- The Manager (`../aura-manager`) lives in the same repository. Do not change it as part of an app task unless asked; contract changes go through `contract/pending-changes.md`.

## Open questions for the team

Ask when one of them blocks you; meanwhile, use the stated default.

- **Demo device** and minimum Android version. Default: a recent phone or tablet, landscape orientation.
- **HUD orientation.** Default: landscape, like a viewer.
- **How the current MET is set in the demo.** Default: manual MET selector visible only in demo mode.
- **Which capacities have a traffic light.** Resolved: those in the package `capacities[]`. If they do not all fit in the HUD, show first those that are not green.
- **Whether raw observations should go back to mission control** during the hackathon, besides adaptations and alerts. Default: no.
