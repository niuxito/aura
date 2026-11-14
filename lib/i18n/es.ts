// Spanish UI dictionary. Same shape as the English one (type Messages).

import type { Messages } from "./en";

export const es: Messages = {
  languageName: { en: "inglés", es: "español" },

  app: {
    title: "AURA · Control de misión",
    description: "Plan de misión y plan de prevención para la tripulación",
    brandSub: "Control de misión · plan de prevención",
    missionMeta: (destination, days, crew) =>
      `${destination} · ${days} días de vuelo · ${crew} tripulantes`,
    saved: "Guardado",
    saving: "Guardando…",
    saveError: "Error al guardar",
    resetDemo: "Restaurar demo",
    resetConfirm: "¿Restaurar la misión demo? Se perderán los cambios del borrador.",
    loadError: "No se pudo cargar el plan de misión.",
    loading: "Cargando plan de misión…",
    language: "Idioma",
    banner: (source) =>
      `Cronograma: ${source}. Tiempos en MET (tiempo transcurrido desde el despegue, DD/HH:MM).`,
    bannerSimulated:
      " La carga operativa, la tripulación y el plan de prevención son un ejemplo del equipo: no son datos de NASA ni una prescripción médica.",
  },

  tabs: {
    mission: "Plan de misión",
    prevention: "Plan de prevención",
    catalog: "Catálogo",
    questionnaire: "Cuestionario",
    publish: "Publicar",
    adaptations: "Adaptaciones",
    alerts: "Alertas",
  },

  labels: {
    gravity: {
      earth: "Tierra · 1,00 g",
      mars: "Marte · 0,38 g",
      moon: "Luna · 0,165 g",
      micro: "Microgravedad · ≈0 g",
    },
    gravityShort: { earth: "Tierra", mars: "Marte", moon: "Luna", micro: "Micro-g" },
    load: { low: "Baja", medium: "Media", high: "Alta", critical: "Crítica" },
    category: {
      exercise: "Ejercicio",
      nutrition: "Nutrición",
      assessment: "Evaluación",
      supplement: "Suplemento",
      recovery: "Recuperación",
      preparation: "Preparación",
    },
    loadType: { CL: "CL · acondicionamiento", RE: "RE · recuperación", none: "Ninguna" },
    eventKind: {
      maneuver: "Maniobra",
      milestone: "Hito",
      test: "Prueba (FTO/DFTO)",
      operation: "Operación",
      eva: "EVA",
    },
    blockKind: {
      sleep: "Sueño",
      presleep: "Pre/postsueño",
      meal: "Comida",
      exercise: "Ejercicio",
      maneuver: "Maniobra",
      test: "Prueba (FTO/DFTO)",
      operation: "Operación",
      communication: "Conferencias y PAO",
      health: "Salud",
      "off-duty": "Tiempo libre",
    },
    entryStatus: { authorized: "autorizado", conditional: "condicional" },
    severity: { blocker: "bloqueo", warning: "aviso", info: "info" },
    adaptationType: { substitution: "Sustitución", reschedule: "Reprogramación", omission: "Omisión" },
    reviewStatus: { pending: "Pendiente de validar", validated: "Validada", objected: "Objetada" },
    alertKind: {
      "capacity-red": "Capacidad en rojo",
      "preparation-lost": "Medida de preparación perdida",
      inconsistency: "Incoherencia autoinforme/sensor",
    },
    alertLevel: { red: "Rojo", warning: "Aviso" },
    alertStatus: { new: "Nueva", seen: "Vista", handled: "Atendida" },
    signalSource: { watch: "Reloj", garment: "Ropa inteligente", test: "Prueba de coordinación" },
    evidenceSource: { manual: "manual", sensor: "sensor", simulated: "simulado" },
  },

  common: {
    experimental: "experimental",
    adaptable: "Adaptable",
    fixed: "Fija",
    simulated: "Simulada",
    unknown: "desconocida",
    choose: "Elegir…",
    remove: (name) => `Eliminar ${name}`,
    metOf: (name) => `MET de ${name}`,
    received: (version, date) => `Paquete v${version} · recibida ${date}`,
    fallbackHint: "Aún no hay texto en este idioma: se muestra el texto en inglés como pista.",
  },

  strip: {
    title: "Perfil gravitatorio y carga operativa por día de vuelo",
    dayTitle: (fd, start, end, milestone, gravity, load) =>
      `${fd} · MET ${start} – ${end} · ${milestone} · ${gravity} · CO ${load}`,
    hint: "Bajo cada día de vuelo, su MET de inicio · ▲ transición gravitatoria · franja: carga operativa",
  },

  mission: {
    title: "Misión",
    timeline: (source) => `Cronograma: ${source}`,
    name: "Nombre",
    destination: "Destino",
    launchUtc: "Despegue en UTC (MET 00/00:00), opcional",
    launchPlaceholder: "AAAA-MM-DDTHH:MM:SSZ",
    description: "Descripción",
    crew: "Tripulación",
    crewName: "Nombre",
    crewRole: "Función",
    daysTitle: (n) => `Días de vuelo · ${n}`,
    daysHint: "Límites en MET según el cronograma; fase, gravedad y carga operativa (CO) editables",
    colFd: "FD",
    colStart: "MET inicio",
    colEnd: "MET fin",
    colPhase: "Fase",
    colMilestone: "Hito principal",
    colTransition: "Transición desde",
    colGravity: "Gravedad",
    colLoad: "CO",
    colWarnings: "Avisos",
    eventsTitle: (n) => `Eventos de misión · ${n}`,
    addEvent: (fd) => `+ Añadir evento en ${fd}`,
    newEvent: "Nuevo evento",
    colMet: "MET",
    colEvent: "Evento",
    colKind: "Tipo",
    approxTitle: "El cronograma lo da como aproximado",
    eventName: "Nombre del evento",
    eventKind: "Tipo de evento",
    approx: "MET aproximado",
  },

  timeline: {
    title: "Cronograma del día de vuelo",
    adaptableLegend: "Periodo adaptable por la interfaz",
    axis: "MET",
    crewRow: "Tripulación",
    eventsRow: "Eventos",
    blockTitle: (name, start, end, adaptable) =>
      `${name} · MET ${start} – ${end} (aprox.) · ${adaptable ? "periodo adaptable por la interfaz" : "fijo"}`,
    chipTitle: (name, met, status, adaptable) =>
      `${name} · MET ${met} · ${status} · ${adaptable ? "adaptable" : "fija"}`,
    hint:
      "Eventos: MET rotulados en el cronograma de misión. Bloques de la tripulación: leídos de la gráfica, " +
      "aproximados. Filas inferiores: plan de prevención de cada tripulante; cada ficha empieza en su MET y, " +
      "si coincide con otra, pasa a la línea de debajo; las adaptables por la interfaz van redondeadas y las fijas, rectas.",
  },

  prevention: {
    noIssues: "Sin avisos para este día de vuelo.",
    loadBadge: (load) => `CO ${load}`,
    clTitle: "Actividades de carga de acondicionamiento",
    reTitle: "Ventanas de recuperación",
    noActivities: "Sin actividades asignadas.",
    adaptableOnTitle:
      "La interfaz del astronauta puede sustituirla, moverla dentro de su periodo u omitirla. Pulsa para fijarla.",
    adaptableOffTitle: "Fijada por control de misión. Pulsa para permitir que la interfaz la adapte.",
    staticFixedTitle: "Esta categoría no la adapta la interfaz del astronauta",
    dose: (dose) => ` · dosis: ${dose}`,
    dosePending: "pendiente de protocolo",
    toggleStatus: "Cambiar entre autorizado y condicional",
    addFromCatalog: "+ Añadir del catálogo…",
    addTo: (name) => `Añadir actividad a ${name}`,
    copyFrom: "Copiar de…",
    copyTo: (name) => `Copiar a ${name} el plan de otro tripulante`,
  },

  catalog: {
    title: "Catálogo de prevención",
    hint: "Acciones que control de misión puede asignar. La herramienta no inventa dosis: se publican tal como se registren aquí.",
    colCode: "Código",
    colAction: "Acción",
    colLoad: "Carga",
    colLimit: "Límite",
    colDose: "Dosis / rango prescrito",
    colSource: "Procedencia",
    colUses: "Usos",
    demanding: "exigente",
    dosePlaceholder: "Pendiente de protocolo",
    inUse: "En uso en el plan: quítala antes de los días en que está asignada",
    removeFromCatalog: "Eliminar del catálogo",
    newTitle: "Nueva acción",
    code: "Código",
    name: "Nombre",
    category: "Categoría",
    loadType: "Tipo de carga",
    doseOptional: "Dosis / rango prescrito (opcional)",
    source: "Procedencia (protocolo o fuente)",
    limit: "Límite de uso",
    intense: "Carga exigente (no admite días críticos ni vísperas)",
    add: "Añadir al catálogo",
    duplicate: "Ya existe una acción con ese código.",
    languageNote: (language) =>
      `Los textos se guardan en ${language}. Para añadir otro idioma, cambia el idioma de la consola y edítalos.`,
  },

  questionnaire: {
    appliesTo: (item) =>
      `Se publica en el paquete y lo aplica la interfaz del astronauta cada vez que el plan programa ${item}.`,
    perDayStart: "Cada día:",
    fixedCount: (n) => `${n} preguntas fijas`,
    perDayMid: "(una redacción en rotación) y",
    bankCount: (n) => `${n} del banco`,
    perDayEnd: (source) =>
      `, elegidas por prioridad según los disparadores. Sin respuesta, la capacidad se muestra en gris. Procedencia: ${source}.`,
    demo:
      "Umbrales de demostración, no criterios clínicos. Cada pregunta se inspira en un instrumento publicado, " +
      "pero una pregunta suelta no equivale a ese instrumento validado.",
    capacitiesTitle: "Capacidades con semáforo",
    capacitiesHint: "Qué hace la interfaz en cada nivel, siempre dentro de la política de adaptación.",
    colCapacity: "Capacidad",
    colInAmber: "En ámbar",
    colInRed: "En rojo",
    blockA: "Bloque A · preguntas fijas",
    blockB: "Bloque B · banco de preguntas",
    thresholdsHint: "Umbrales sobre el valor normalizado (alto = peor), incluidos.",
    colQuestion: "Pregunta",
    colWordings: "Redacciones",
    colTriggers: "Disparadores (prioridad)",
    colAmber: "Ámbar",
    colRed: "Rojo",
    itemMeta: (capacity, min, max, inspiredBy) =>
      `${capacity} · escala ${min}–${max} · inspirada en ${inspiredBy}`,
    inverted: " · invertida (alto = mejor)",
    noTrigger: "Sin disparador disponible",
    signalsTitle: "Señales objetivas",
    signalsHint:
      "Las de la ropa inteligente se simulan en la demo: la interfaz las guarda y muestra como «simulado».",
    colSignal: "Señal",
    colOrigin: "Origen",
    ifOccurs: "Si ocurre",
    consistencyTitle: "Coherencia entre autoinforme y sensores",
    consistencyHint: "Si se contradicen, la interfaz lo explica y usa el peor de los dos.",
    consistencyRule: (question, atMost, signal) => `${question} ≤ ${atMost} y ${signal}`,
    trigger: {
      transitionTo: (list) => ` hacia ${list}`,
      transitionMicro: (n) => `, tras ≥ ${n} FD en micro-g`,
      transition: (to, before, after, micro) =>
        `Cambio de gravedad${to}: ${before} FD antes a ${after} FD después${micro}`,
      environment: (list) => `En ${list}`,
      microDays: (n) => `≥ ${n} FD seguidos en micro-g`,
      event: (kind, n) => `${kind} en los últimos ${n} FD`,
      filler: "Relleno si faltan preguntas",
    },
  },

  publish: {
    validationTitle: "Validación del plan",
    blockers: (n) => `${n} bloqueos`,
    warnings: (n) => `${n} avisos`,
    infos: (n) => `${n} informativos`,
    noIssues: "El plan no tiene avisos.",
    missionScope: "Misión",
    title: "Publicar paquete de misión",
    publishing: "Publicando…",
    publishVersion: (v) => `Publicar versión ${v}`,
    resolveBlockers: "Resuelve los bloqueos para poder publicar.",
    warningsTravel:
      "Los avisos no impiden publicar: viajan dentro del paquete para que el monitor los muestre.",
    monitorBefore: "El monitor del astronauta descarga el último paquete desde",
    monitorAfter: "y trabaja sin conexión a partir de él.",
    languages: (list) =>
      `Todos los textos viajan en ${list}; la interfaz del astronauta elige su idioma y, si falta, usa el inglés.`,
    listError: "No se pudo leer la lista de paquetes.",
    publishError: "No se pudo publicar",
    loadingVersions: "Cargando versiones…",
    noPackages: "Todavía no se ha publicado ningún paquete.",
    colVersion: "Versión",
    colPublished: "Publicado",
    colMission: "Misión",
    colSchema: "Esquema",
    colChecksum: "Checksum",
    download: "Descargar JSON",
  },

  adaptations: {
    title: "Adaptaciones recibidas de la interfaz del astronauta",
    pending: (n) => `${n} pendientes`,
    validated: (n) => `${n} validadas`,
    objected: (n) => `${n} objetadas`,
    outOfPolicy: (n) => `${n} fuera de política`,
    intro:
      "La interfaz adapta por su cuenta las actividades marcadas como adaptables (ejercicios dentro de los " +
      "periodos de ejercicio y menús en las comidas) y las aplica a bordo sin esperar respuesta. Aquí quedan " +
      "registradas para su validación. Validar u objetar no cambia lo ya hecho: deja constancia y la interfaz " +
      "puede consultar el resultado.",
    saveError: "No se pudo guardar la revisión.",
    none: "Todavía no se ha recibido ninguna adaptación.",
    withinPolicy: "Dentro de política",
    outOfPolicyBadge: "Fuera de política",
    originalPlan: "Plan original",
    notFound: "No localizada en el paquete",
    decision: "Decisión de la interfaz",
    rescheduledTo: "Reprogramada a",
    omitted: "Actividad omitida",
    reason: "Motivo",
    notePlaceholder: "Nota de control de misión (opcional)",
    noteLabel: "Nota de control de misión",
    validate: "Validar",
    object: "Objetar",
    sim: {
      title: "Simular una adaptación",
      hint: "Para probar el circuito mientras la interfaz del astronauta no esté conectada",
      publishFirst: "Publica antes un paquete: las adaptaciones se refieren a una versión publicada.",
      crewMember: "Tripulante",
      activity: "Actividad del plan",
      decision: "Decisión",
      replaceWith: "Sustituir por",
      reason: "Motivo",
      defaultReason: "Fatiga alta declarada tras una mala noche de sueño.",
      evidenceIndicator: "Fatiga autodeclarada",
      send: "Enviar adaptación de prueba",
      sendHint: (v) =>
        `Se envía contra el paquete v${v}. Elegir una actividad fija sirve para comprobar que queda señalada como fuera de política.`,
      sent: "Adaptación de prueba registrada.",
      sendError: "No se pudo registrar.",
    },
  },

  alerts: {
    title: "Alertas de la interfaz del astronauta",
    openRed: (n) => `${n} en rojo sin atender`,
    new: (n) => `${n} nuevas`,
    seen: (n) => `${n} vistas`,
    handled: (n) => `${n} atendidas`,
    intro:
      "Lo que la interfaz no resuelve adaptando el plan: una capacidad en rojo (la interfaz no decide más y " +
      "remite al procedimiento de misión), una adaptación que quita una medida de preparación de la transición " +
      "o una incoherencia entre lo que declara el astronauta y lo que mide el sensor. Los umbrales son de " +
      "demostración; la interfaz no diagnostica. Marcar una alerta como vista o atendida se lo comunica al astronauta.",
    saveError: "No se pudo guardar el acuse.",
    none: "Todavía no se ha recibido ninguna alerta.",
    notePlaceholder: "Nota para el astronauta (opcional)",
    noteLabel: "Nota de control de misión",
    markSeen: "Marcar vista",
    markHandled: "Atendida",
  },

  metInput: { title: "MET · DD/HH:MM o DD/HH:MM:SS" },

  rules: {
    outsideMission: (name, met) => `${name} en MET ${met}: fuera de los días de vuelo de la misión.`,
    noPlan: "Sin plan de prevención asignado para este día de vuelo.",
    inSleep: (name, met, start, end) =>
      `${name} en MET ${met}: cae dentro del periodo de sueño (${start} – ${end}).`,
    adaptableOutsideWindow: (name, met, block) =>
      `${name} en MET ${met}: es adaptable pero no está dentro de un bloque de «${block}»; la interfaz podrá sustituirla u omitirla, no moverla.`,
    intenseOnCritical: (name, milestone) =>
      `${name}: no se añade carga exigente a un día de carga operativa crítica (${milestone}).`,
    intenseOnEve: (name, fd, milestone) =>
      `${name} en la víspera de ${fd} («${milestone}»): proteger el descanso previo.`,
    noRecovery: (fd, milestone) => `Sin ventana de recuperación tras ${fd} («${milestone}»).`,
    unpreparedTransition: (from, to, n) =>
      `Transición ${from} → ${to} sin preparación gravitatoria en los ${n} días de vuelo previos.`,
    noSelfReport: "Sin cuestionario de fatiga: el monitor mostrará gris (datos insuficientes).",
    supplementNoDose: (name) =>
      `${name}: dosis pendiente de protocolo médico; el paquete lo publica sin dosis.`,
    conditionals: (n) =>
      `${n} actividades condicionales: dependen de confirmar equipo, protocolo o recuperación.`,
    missingTranslation: (n, language) =>
      `${n} textos no tienen versión en ${language}: la consola y la interfaz del astronauta los mostrarán en inglés.`,
    missingEnglish: (n) =>
      `${n} textos no tienen versión en inglés, el idioma de referencia: añádela para que cualquier lector pueda seguir el plan.`,
    q: {
      catalogMissing: (id) => `El cuestionario apunta a la acción ${id}, que no está en el catálogo.`,
      signalCapacity: (id, capacity) => `La señal ${id} apunta a una capacidad inexistente (${capacity}).`,
      question: (id) => `Pregunta ${id}`,
      capacityMissing: (where, capacity) => `${where}: capacidad inexistente (${capacity}).`,
      noVariants: (where) => `${where}: no tiene ninguna redacción.`,
      thresholds: (where, min, max) =>
        `${where}: umbrales fuera de la escala ${min}–${max} o desordenados.`,
      triggerSignal: (where, signal) => `${where}: el disparador usa una señal inexistente (${signal}).`,
      neverSelected: (where, domain) =>
        `${where} (${domain}): sin disparadores disponibles; no se seleccionará nunca.`,
      checkMissing: (id) => `Comprobación ${id}: pregunta o señal inexistente.`,
      fixedShort: (need, have) => `El bloque fijo pide ${need} preguntas y solo hay ${have}.`,
      fillers: (have, slots) =>
        `Hay ${have} preguntas de relleno para ${slots} huecos del banco: algunos días faltarán preguntas.`,
    },
  },

  policy: {
    rules: [
      "Solo se adaptan las actividades con adaptable: true. Las demás, y todos los bloques con adaptable: false (maniobras, pruebas, sueño, operaciones), no se modifican nunca desde la interfaz.",
      "Sustitución: solo por otra acción del catálogo de la misma categoría.",
      "Una sustitución no puede aumentar la carga: no se cambia una acción por otra con intense: true si la original no lo era.",
      "Reprogramación: solo a un MET dentro de un bloque adaptable de su categoría y en el mismo día de vuelo.",
      "Omisión: permitida en actividades adaptables, siempre con motivo.",
      "Cada adaptación se aplica a bordo sin esperar respuesta y se envía a control de misión para su registro y validación. Si no hay conexión, se guarda y se envía después.",
    ],
    packageMissing: (v) => `No existe el paquete v${v}.`,
    oldSchema: (v) => `El paquete v${v} es de un esquema anterior, sin política de adaptación.`,
    crewMissing: "El tripulante no figura en el paquete.",
    activityMissing: "La actividad no figura en el plan de ese tripulante.",
    fixed: "La actividad está marcada como fija: solo puede cambiarla control de misión.",
    replacementMissing: "La acción sustituta no existe en el catálogo del paquete.",
    otherCategory: "La acción sustituta es de otra categoría.",
    increasesLoad: "La sustitución aumenta la carga: introduce una acción exigente.",
    outsideDay: (met) => `El nuevo MET ${met} cae fuera del día de vuelo original.`,
    outsideWindow: (met) => `El nuevo MET ${met} no está dentro de un periodo adaptable de su categoría.`,
  },
};
