// English UI dictionary: the reference language. Every other dictionary has
// the same shape (type Messages), so a missing key fails type-checking.

export const en = {
  languageName: { en: "English", es: "Spanish" },

  app: {
    title: "AURA · Mission Control",
    description: "Mission plan and crew prevention plan",
    brandSub: "Mission control · prevention plan",
    missionMeta: (destination: string, days: number, crew: number) =>
      `${destination} · ${days} flight days · ${crew} crew members`,
    saved: "Saved",
    saving: "Saving…",
    saveError: "Save failed",
    resetDemo: "Restore demo",
    resetConfirm: "Restore the demo mission? Changes to the draft will be lost.",
    loadError: "Could not load the mission plan.",
    loading: "Loading mission plan…",
    language: "Language",
    banner: (source: string) =>
      `Timeline: ${source}. Times in MET (mission elapsed time since launch, DD/HH:MM).`,
    bannerSimulated:
      " Operational load, crew and prevention plan are a team example: they are not NASA data or a medical prescription.",
  },

  tabs: {
    mission: "Mission plan",
    prevention: "Prevention plan",
    catalog: "Catalog",
    questionnaire: "Questionnaire",
    publish: "Publish",
    adaptations: "Adaptations",
    alerts: "Alerts",
  },

  labels: {
    gravity: {
      earth: "Earth · 1.00 g",
      mars: "Mars · 0.38 g",
      moon: "Moon · 0.165 g",
      micro: "Microgravity · ≈0 g",
    },
    gravityShort: { earth: "Earth", mars: "Mars", moon: "Moon", micro: "Micro-g" },
    load: { low: "Low", medium: "Medium", high: "High", critical: "Critical" },
    category: {
      exercise: "Exercise",
      nutrition: "Nutrition",
      assessment: "Assessment",
      supplement: "Supplement",
      recovery: "Recovery",
      preparation: "Preparation",
    },
    loadType: { CL: "CL · conditioning", RE: "RE · recovery", none: "None" },
    eventKind: {
      maneuver: "Maneuver",
      milestone: "Milestone",
      test: "Test (FTO/DFTO)",
      operation: "Operation",
      eva: "EVA",
    },
    blockKind: {
      sleep: "Sleep",
      presleep: "Pre/post-sleep",
      meal: "Meal",
      exercise: "Exercise",
      maneuver: "Maneuver",
      test: "Test (FTO/DFTO)",
      operation: "Operation",
      communication: "Conferences and PAO",
      health: "Health",
      "off-duty": "Off duty",
    },
    entryStatus: { authorized: "authorized", conditional: "conditional" },
    severity: { blocker: "blocker", warning: "warning", info: "info" },
    adaptationType: { substitution: "Substitution", reschedule: "Reschedule", omission: "Omission" },
    reviewStatus: { pending: "Pending validation", validated: "Validated", objected: "Objected" },
    alertKind: {
      "capacity-red": "Capacity in red",
      "preparation-lost": "Preparation measure lost",
      inconsistency: "Self-report/sensor inconsistency",
    },
    alertLevel: { red: "Red", warning: "Warning" },
    alertStatus: { new: "New", seen: "Seen", handled: "Handled" },
    signalSource: { watch: "Watch", garment: "Smart clothing", test: "Coordination test" },
    evidenceSource: { manual: "manual", sensor: "sensor", simulated: "simulated" },
  },

  common: {
    experimental: "experimental",
    adaptable: "Adaptable",
    fixed: "Fixed",
    simulated: "Simulated",
    unknown: "unknown",
    choose: "Choose…",
    remove: (name: string) => `Remove ${name}`,
    metOf: (name: string) => `MET of ${name}`,
    received: (version: number, date: string) => `Package v${version} · received ${date}`,
    fallbackHint: "No text in this language yet: the English text is shown as a hint.",
  },

  strip: {
    title: "Gravity profile and operational load by flight day",
    dayTitle: (fd: string, start: string, end: string, milestone: string, gravity: string, load: string) =>
      `${fd} · MET ${start} – ${end} · ${milestone} · ${gravity} · OL ${load}`,
    hint: "Under each flight day, its start MET · ▲ gravity transition · strip: operational load",
  },

  mission: {
    title: "Mission",
    timeline: (source: string) => `Timeline: ${source}`,
    name: "Name",
    destination: "Destination",
    launchUtc: "Launch in UTC (MET 00/00:00), optional",
    launchPlaceholder: "YYYY-MM-DDTHH:MM:SSZ",
    description: "Description",
    crew: "Crew",
    crewName: "Name",
    crewRole: "Role",
    daysTitle: (n: number) => `Flight days · ${n}`,
    daysHint: "MET boundaries from the timeline; phase, gravity and operational load (OL) are editable",
    colFd: "FD",
    colStart: "Start MET",
    colEnd: "End MET",
    colPhase: "Phase",
    colMilestone: "Main milestone",
    colTransition: "Transition from",
    colGravity: "Gravity",
    colLoad: "OL",
    colWarnings: "Warnings",
    eventsTitle: (n: number) => `Mission events · ${n}`,
    addEvent: (fd: string) => `+ Add event on ${fd}`,
    newEvent: "New event",
    colMet: "MET",
    colEvent: "Event",
    colKind: "Kind",
    approxTitle: "The timeline gives it as approximate",
    eventName: "Event name",
    eventKind: "Event kind",
    approx: "Approximate MET",
  },

  timeline: {
    title: "Flight day timeline",
    adaptableLegend: "Window adaptable by the interface",
    axis: "MET",
    crewRow: "Crew",
    eventsRow: "Events",
    blockTitle: (name: string, start: string, end: string, adaptable: boolean) =>
      `${name} · MET ${start} – ${end} (approx.) · ${adaptable ? "window adaptable by the interface" : "fixed"}`,
    chipTitle: (name: string, met: string, status: string, adaptable: boolean) =>
      `${name} · MET ${met} · ${status} · ${adaptable ? "adaptable" : "fixed"}`,
    hint:
      "Events: MET labeled in the mission timeline. Crew blocks: read from the chart, approximate. " +
      "Lower rows: each crew member's prevention plan; each chip starts at its MET and, if it overlaps " +
      "another, moves to the line below; activities the interface may adapt are rounded, fixed ones are square.",
  },

  prevention: {
    noIssues: "No warnings for this flight day.",
    loadBadge: (load: string) => `OL ${load}`,
    clTitle: "Conditioning load activities",
    reTitle: "Recovery windows",
    noActivities: "No activities assigned.",
    adaptableOnTitle:
      "The astronaut interface may substitute it, move it within its window or omit it. Click to fix it.",
    adaptableOffTitle: "Fixed by mission control. Click to let the interface adapt it.",
    staticFixedTitle: "The astronaut interface does not adapt this category",
    dose: (dose: string) => ` · dose: ${dose}`,
    dosePending: "pending protocol",
    toggleStatus: "Switch between authorized and conditional",
    addFromCatalog: "+ Add from catalog…",
    addTo: (name: string) => `Add an activity to ${name}`,
    copyFrom: "Copy from…",
    copyTo: (name: string) => `Copy another crew member's plan to ${name}`,
  },

  catalog: {
    title: "Prevention catalog",
    hint: "Actions mission control can assign. The tool does not invent doses: they are published exactly as recorded here.",
    colCode: "Code",
    colAction: "Action",
    colLoad: "Load",
    colLimit: "Limit",
    colDose: "Dose / prescribed range",
    colSource: "Source",
    colUses: "Uses",
    demanding: "demanding",
    dosePlaceholder: "Pending protocol",
    inUse: "In use in the plan: remove it from the days it is assigned to first",
    removeFromCatalog: "Remove from catalog",
    newTitle: "New action",
    code: "Code",
    name: "Name",
    category: "Category",
    loadType: "Load type",
    doseOptional: "Dose / prescribed range (optional)",
    source: "Source (protocol or reference)",
    limit: "Limit of use",
    intense: "Demanding load (not allowed on critical days or the day before)",
    add: "Add to catalog",
    duplicate: "An action with that code already exists.",
    languageNote: (language: string) =>
      `Texts are saved in ${language}. To add another language, switch the console language and edit them.`,
  },

  questionnaire: {
    appliesTo: (item: string) =>
      `Published in the package and applied by the astronaut interface every time the plan schedules ${item}.`,
    perDayStart: "Each day:",
    fixedCount: (n: number) => `${n} fixed questions`,
    perDayMid: "(one wording in rotation) and",
    bankCount: (n: number) => `${n} from the bank`,
    perDayEnd: (source: string) =>
      `, chosen by priority according to the triggers. With no answer, the capacity shows gray. Source: ${source}.`,
    demo:
      "Demonstration thresholds, not clinical criteria. Each question is inspired by a published instrument, " +
      "but a single question is not equivalent to that validated instrument.",
    capacitiesTitle: "Traffic-light capacities",
    capacitiesHint: "What the interface does at each level, always within the adaptation policy.",
    colCapacity: "Capacity",
    colInAmber: "In amber",
    colInRed: "In red",
    blockA: "Block A · fixed questions",
    blockB: "Block B · question bank",
    thresholdsHint: "Thresholds on the normalized value (high = worse), inclusive.",
    colQuestion: "Question",
    colWordings: "Wordings",
    colTriggers: "Triggers (priority)",
    colAmber: "Amber",
    colRed: "Red",
    itemMeta: (capacity: string, min: number, max: number, inspiredBy: string) =>
      `${capacity} · scale ${min}–${max} · inspired by ${inspiredBy}`,
    inverted: " · inverted (high = better)",
    noTrigger: "No trigger available",
    signalsTitle: "Objective signals",
    signalsHint:
      "Smart-clothing signals are simulated in the demo: the interface stores and shows them as “simulated”.",
    colSignal: "Signal",
    colOrigin: "Source",
    ifOccurs: "If it occurs",
    consistencyTitle: "Consistency between self-report and sensors",
    consistencyHint: "If they contradict each other, the interface explains it and uses the worse of the two.",
    consistencyRule: (question: string, atMost: number, signal: string) =>
      `${question} ≤ ${atMost} and ${signal}`,
    trigger: {
      transitionTo: (list: string) => ` to ${list}`,
      transitionMicro: (n: number) => `, after ≥ ${n} FD in micro-g`,
      transition: (to: string, before: number, after: number, micro: string) =>
        `Gravity change${to}: ${before} FD before to ${after} FD after${micro}`,
      environment: (list: string) => `In ${list}`,
      microDays: (n: number) => `≥ ${n} consecutive FD in micro-g`,
      event: (kind: string, n: number) => `${kind} in the last ${n} FD`,
      filler: "Filler if questions are missing",
    },
  },

  publish: {
    validationTitle: "Plan validation",
    blockers: (n: number) => `${n} blockers`,
    warnings: (n: number) => `${n} warnings`,
    infos: (n: number) => `${n} informational`,
    noIssues: "The plan has no warnings.",
    missionScope: "Mission",
    title: "Publish mission package",
    publishing: "Publishing…",
    publishVersion: (v: number) => `Publish version ${v}`,
    resolveBlockers: "Resolve the blockers to be able to publish.",
    warningsTravel:
      "Warnings do not prevent publishing: they travel inside the package so the monitor can show them.",
    monitorBefore: "The astronaut monitor downloads the latest package from",
    monitorAfter: "and works offline from it.",
    languages: (list: string) =>
      `Every text travels in ${list}; the astronaut interface picks its language and falls back to English.`,
    listError: "Could not read the package list.",
    publishError: "Could not publish",
    loadingVersions: "Loading versions…",
    noPackages: "No package has been published yet.",
    colVersion: "Version",
    colPublished: "Published",
    colMission: "Mission",
    colSchema: "Schema",
    colChecksum: "Checksum",
    download: "Download JSON",
  },

  adaptations: {
    title: "Adaptations received from the astronaut interface",
    pending: (n: number) => `${n} pending`,
    validated: (n: number) => `${n} validated`,
    objected: (n: number) => `${n} objected`,
    outOfPolicy: (n: number) => `${n} out of policy`,
    intro:
      "The interface adapts on its own the activities marked as adaptable (exercise within exercise periods " +
      "and menus at meals) and applies them on board without waiting for a reply. They are recorded here for " +
      "validation. Validating or objecting does not undo what was done: it leaves a record and the interface " +
      "can check the result.",
    saveError: "Could not save the review.",
    none: "No adaptation has been received yet.",
    withinPolicy: "Within policy",
    outOfPolicyBadge: "Out of policy",
    originalPlan: "Original plan",
    notFound: "Not found in the package",
    decision: "Interface decision",
    rescheduledTo: "Rescheduled to",
    omitted: "Activity omitted",
    reason: "Reason",
    notePlaceholder: "Mission control note (optional)",
    noteLabel: "Mission control note",
    validate: "Validate",
    object: "Object",
    sim: {
      title: "Simulate an adaptation",
      hint: "To test the loop while the astronaut interface is not connected",
      publishFirst: "Publish a package first: adaptations refer to a published version.",
      crewMember: "Crew member",
      activity: "Plan activity",
      decision: "Decision",
      replaceWith: "Replace with",
      reason: "Reason",
      defaultReason: "High fatigue reported after a bad night's sleep.",
      evidenceIndicator: "Self-reported fatigue",
      send: "Send test adaptation",
      sendHint: (v: number) =>
        `Sent against package v${v}. Choosing a fixed activity checks that it is flagged as out of policy.`,
      sent: "Test adaptation recorded.",
      sendError: "Could not record it.",
    },
  },

  alerts: {
    title: "Alerts from the astronaut interface",
    openRed: (n: number) => `${n} red not handled`,
    new: (n: number) => `${n} new`,
    seen: (n: number) => `${n} seen`,
    handled: (n: number) => `${n} handled`,
    intro:
      "What the interface does not resolve by adapting the plan: a capacity in red (the interface decides " +
      "nothing more and refers to the mission procedure), an adaptation that removes a transition preparation " +
      "measure, or an inconsistency between what the astronaut reports and what the sensor measures. " +
      "Thresholds are for demonstration; the interface does not diagnose. Marking an alert as seen or handled " +
      "tells the astronaut.",
    saveError: "Could not save the acknowledgement.",
    none: "No alert has been received yet.",
    notePlaceholder: "Note for the astronaut (optional)",
    noteLabel: "Mission control note",
    markSeen: "Mark seen",
    markHandled: "Handled",
  },

  metInput: { title: "MET · DD/HH:MM or DD/HH:MM:SS" },

  rules: {
    outsideMission: (name: string, met: string) =>
      `${name} at MET ${met}: outside the mission's flight days.`,
    noPlan: "No prevention plan assigned for this flight day.",
    inSleep: (name: string, met: string, start: string, end: string) =>
      `${name} at MET ${met}: falls within the sleep period (${start} – ${end}).`,
    adaptableOutsideWindow: (name: string, met: string, block: string) =>
      `${name} at MET ${met}: it is adaptable but not inside a “${block}” block; the interface will be able to substitute or omit it, not move it.`,
    intenseOnCritical: (name: string, milestone: string) =>
      `${name}: no demanding load is added on a day of critical operational load (${milestone}).`,
    intenseOnEve: (name: string, fd: string, milestone: string) =>
      `${name} on the day before ${fd} (“${milestone}”): protect the prior rest.`,
    noRecovery: (fd: string, milestone: string) => `No recovery window after ${fd} (“${milestone}”).`,
    unpreparedTransition: (from: string, to: string, n: number) =>
      `${from} → ${to} transition without gravity preparation in the previous ${n} flight days.`,
    noSelfReport: "No fatigue questionnaire: the monitor will show gray (insufficient data).",
    supplementNoDose: (name: string) =>
      `${name}: dose pending medical protocol; the package publishes it without a dose.`,
    conditionals: (n: number) =>
      `${n} conditional activities: they depend on confirming equipment, protocol or recovery.`,
    missingTranslation: (n: number, language: string) =>
      `${n} texts have no ${language} version: the console and the astronaut interface will show them in English.`,
    missingEnglish: (n: number) =>
      `${n} texts have no English version, the reference language: add it so every reader can follow the plan.`,
    q: {
      catalogMissing: (id: string) =>
        `The questionnaire points to action ${id}, which is not in the catalog.`,
      signalCapacity: (id: string, capacity: string) =>
        `Signal ${id} points to a non-existent capacity (${capacity}).`,
      question: (id: string) => `Question ${id}`,
      capacityMissing: (where: string, capacity: string) =>
        `${where}: non-existent capacity (${capacity}).`,
      noVariants: (where: string) => `${where}: has no wording.`,
      thresholds: (where: string, min: number, max: number) =>
        `${where}: thresholds outside the ${min}–${max} scale or out of order.`,
      triggerSignal: (where: string, signal: string) =>
        `${where}: the trigger uses a non-existent signal (${signal}).`,
      neverSelected: (where: string, domain: string) =>
        `${where} (${domain}): no available triggers; it will never be selected.`,
      checkMissing: (id: string) => `Check ${id}: non-existent question or signal.`,
      fixedShort: (need: number, have: number) =>
        `The fixed block asks for ${need} questions and there are only ${have}.`,
      fillers: (have: number, slots: number) =>
        `There are ${have} filler questions for ${slots} bank slots: some days will be short of questions.`,
    },
  },

  policy: {
    rules: [
      "Only activities with adaptable: true are adapted. All others, and every block with adaptable: false (maneuvers, tests, sleep, operations), are never modified from the interface.",
      "Substitution: only with another catalog action of the same category.",
      "A substitution cannot increase the load: an action is not replaced with one with intense: true if the original was not.",
      "Reschedule: only to a MET within an adaptable block of its category and on the same flight day.",
      "Omission: allowed for adaptable activities, always with a reason.",
      "Each adaptation is applied on board without waiting for a reply and is sent to mission control to be recorded and validated. Without a connection, it is stored and sent later.",
    ],
    packageMissing: (v: number) => `Package v${v} does not exist.`,
    oldSchema: (v: number) => `Package v${v} uses an older schema with no adaptation policy.`,
    crewMissing: "The crew member is not in the package.",
    activityMissing: "The activity is not in that crew member's plan.",
    fixed: "The activity is marked as fixed: only mission control can change it.",
    replacementMissing: "The replacement action does not exist in the package catalog.",
    otherCategory: "The replacement action belongs to another category.",
    increasesLoad: "The substitution increases the load: it introduces a demanding action.",
    outsideDay: (met: string) => `The new MET ${met} falls outside the original flight day.`,
    outsideWindow: (met: string) =>
      `The new MET ${met} is not within an adaptable window of its category.`,
  },
};

export type Messages = typeof en;
