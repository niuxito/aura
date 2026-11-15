# Pending changes to the `aura.mission-package/v5` contract

Data the astronaut interface needs and the package does not carry. Until it does, the app **does not invent it**: it uses the stated provisional solution and makes that clear on screen. Every change also requires changing AURA Manager (`lib/types.ts`, `lib/package.ts`) and regenerating `mission-package.example.json`.

**2026-11-15:** items 1, 2, 3, 4, 6a and 7 are resolved with **optional** v5 fields, without changing the schema: a package that does not carry them is still valid and the app falls back to its provisional solution. The Manager fills in the missing ones in a saved state with the demo mission values, without touching what mission control has already set.

| # | What was missing | Status | Field |
| --- | --- | --- | --- |
| 1 | Exact MET of each gravity transition | **Resolved.** The countdown uses `transitionMet`; without it, the milestone at the start or end of the day. | `days[].transitionMet` (s) and `transitionMetLabel`. |
| 2 | Duration or end of each activity | **Resolved.** "In progress" until the planned end; with no duration, the 30-minute demonstration window. If the interface replaces the action, the replacement's duration applies. | `catalog[].durationMin` (team estimate) and `activities[].endMet` / `endMetLabel`. |
| 3 | What a conditional activity depends on | **Resolved.** Every conditional activity of the demo mission carries a `note`; the Manager warns (`conditional-without-note`) if one is missing. | `activities[].note`. |
| 4 | Which alternative to use for each capacity in amber | **Resolved.** With cardiovascular in amber, the day's menu switches to N2 (extra hydration). Only in amber, same category and without raising the load; the Manager validates the references. | `capacities[].onAmberActions: [{ category, replacementItemId }]`. |
| 5 | Alert channel to mission control | **Resolved on 2026-11-14:** `POST/GET/PATCH /api/alerts`. | — |
| 6a | Critical capacities for each transition | **Resolved.** Cardiovascular/orthostatic tolerance and balance, critical when arriving on Earth, the Moon or Mars. Without the field, the provisional list and the "provisional" label. | `capacities[].criticalFor: Gravity[]`. |
| 6b | Minimum exercise dose | **Pending.** Only planned versus done sessions are counted. | Proposal: `exercisePolicy.minSessionsPerDays` or similar. |
| 7 | Aerobic session on the eve of reentry | **Resolved in the demo plan:** FD09's aerobic session moves to FD08's exercise block (06/22:00). The critical-eve rule stays active in case mission control puts it back there. | — |
| 8 | Crew member's baseline resting HR | **Resolved.** Baseline of the `resting_hr_delta` indicator with the watch; demonstration value. Without it, a demonstration value of 62 bpm. | `crew[].restingHrBpm`. |
