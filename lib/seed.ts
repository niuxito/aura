import { met } from "./met";
import { seedCapacities, seedQuestionnaire, seedSignals } from "./questionnaire";
import {
  SCHEMA_VERSION,
  type AppState,
  type BlockKind,
  type CatalogItem,
  type EntryStatus,
  isAdaptableCategory,
  type EventKind,
  type FlightDay,
  type Gravity,
  type LoadLevel,
  type MissionEvent,
  type PlanEntry,
  type TimelineBlock,
} from "./types";

// Misión demo: cronograma PLANIFICADO de Artemis II.
//
// Qué es real y qué no:
// - Eventos, fases y días de vuelo: «Artemis II Overview Timeline» de NASA
//   (versión del 18/03/2026, aprobada para difusión pública, STRIVES
//   20260002249). Los MET de los eventos son los que el documento rotula.
// - Bloques de actividad de la tripulación: leídos de la gráfica del mismo
//   documento, con una precisión de unos 15 minutos. Son aproximados.
// - Carga operativa (CO), tripulación de demostración y TODO el plan de
//   prevención: ejemplo del equipo AURA. No son datos ni prescripciones de NASA.

const TIMELINE_SOURCE =
  "NASA — Artemis II Overview Timeline (18/03/2026, STRIVES 20260002249)";
const EXERCISE_URL = "https://www.nasa.gov/ochmo-tb-031-exercise-overview-2/";
const SENSORIMOTOR_URL =
  "https://www.nasa.gov/directorates/esdmd/hhp/sensorimotor-risk/";
const POSTFLIGHT_URL =
  "https://www.nasa.gov/ochmo/health-and-medical-systems/pre-postflight-medical-care/";

// ---------- Días de vuelo ----------

type DayRow = [
  start: string,
  end: string,
  phase: string,
  milestone: string,
  co: LoadLevel,
  gravity: Gravity,
  transitionFrom?: Gravity,
];

const dayRows: DayRow[] = [
  ["00/00:00", "00/17:00", "LEO → órbita terrestre alta", "Lanzamiento, ARB, operaciones de proximidad y PRB", "critica", "micro", "tierra"],
  ["00/17:00", "01/17:00", "Órbita terrestre alta → translunar", "Inyección translunar (TLI)", "critica", "micro"],
  ["01/17:00", "02/17:00", "Translunar", "OTC-1 y demostraciones médicas y de comunicaciones", "media", "micro"],
  ["02/17:00", "03/17:00", "Translunar", "OTC-2 y pilotaje manual", "media", "micro"],
  ["03/17:00", "04/17:00", "Translunar", "Operaciones con trajes OCSS, OTC-3 y entrada en la esfera de influencia lunar", "alta", "micro"],
  ["04/17:00", "05/17:00", "Translunar → transterrestre", "Sobrevuelo lunar: máxima aproximación", "alta", "micro"],
  ["05/17:00", "06/17:00", "Transterrestre", "RTC-1 y tiempo libre de la tripulación", "baja", "micro"],
  ["06/17:00", "07/17:00", "Transterrestre", "Ejercicio, refugio de radiación y pilotaje manual", "media", "micro"],
  ["07/17:00", "08/17:00", "Transterrestre", "Preparación de la entrada y RTC-2", "alta", "micro"],
  ["08/17:00", "09/01:42:48", "Transterrestre → EDL", "RTC-3, entrada y amerizaje", "critica", "tierra", "micro"],
];

// ---------- Eventos (MET rotulados en el documento) ----------

type EventRow = [met: string, name: string, kind: EventKind, approx?: boolean];

const eventRows: EventRow[] = [
  ["00/00:00", "Despegue", "hito"],
  ["00/00:30", "Inicio de la despresurización de cabina (DFTO)", "prueba", true],
  ["00/00:50", "ICPS PRM", "maniobra"],
  ["00/01:47", "ARB TIG", "maniobra"],
  ["00/03:23", "Separación Orion/ICPS", "hito"],
  ["00/04:51", "Orion USS", "maniobra"],
  ["00/05:00", "Dock Cam Bearing (FTO 225)", "prueba", true],
  ["00/05:02", "ICPS Disposal Burn", "maniobra"],
  ["00/05:27", "Despliegue de cargas secundarias (SPL)", "hito", true],
  ["00/10:06", "Activación inicial de OpComm", "operacion", true],
  ["00/13:30", "Orion PRB", "maniobra", true],
  ["00/14:15", "Comprobación de OpNav", "operacion", true],
  ["01/01:08:42", "Orion TLI", "maniobra", true],
  ["01/14:00", "Inspección CM/SM", "operacion"],
  ["01/17:40", "Paneles solares en sombra total", "hito", true],
  ["02/01:08:42", "OTC-1", "maniobra", true],
  ["03/01:08:42", "OTC-2", "maniobra"],
  ["03/07:00", "NAV FTO EM2-223", "prueba"],
  ["04/04:29:52", "OTC-3", "maniobra"],
  ["04/06:38", "Entrada en la esfera de influencia lunar", "hito"],
  ["05/00:29:59", "Máxima aproximación a la Luna", "hito"],
  ["05/00:35", "Distancia máxima a la Tierra", "hito"],
  ["05/12:30", "Inspección CM/SM", "operacion"],
  ["05/18:53", "Salida de la esfera de influencia lunar", "hito"],
  ["06/01:29:52", "RTC-1", "maniobra"],
  ["07/17:30", "Comprobaciones del FCS", "operacion"],
  ["08/04:29:10", "RTC-2", "maniobra"],
  ["08/20:29:10", "RTC-3", "maniobra"],
  ["09/01:09", "Separación CM/SM", "hito"],
  ["09/01:29", "Interfaz de entrada (EI)", "hito"],
  ["09/01:42:48", "Amerizaje", "hito"],
];

// ---------- Bloques de la tripulación (leídos de la gráfica, aproximados) ----------

type BlockRow = [start: string, end: string, kind: BlockKind, name: string];

const SLEEP = "Sueño";
const PRE = "Pre/postsueño";
const MEAL = "Comida";

const blockRows: BlockRow[] = [
  // FD01
  ["00/00:00", "00/00:15", "maniobra", "Ascenso"],
  ["00/00:30", "00/01:30", "operacion", "Comprobaciones previas a ARB"],
  ["00/01:30", "00/02:00", "maniobra", "ARB"],
  ["00/02:00", "00/03:15", "operacion", "DCAM y retirada de trajes OCSS"],
  ["00/03:15", "00/04:45", "prueba", "Demostración de operaciones de proximidad"],
  ["00/04:45", "00/05:00", "maniobra", "USS"],
  ["00/05:00", "00/06:00", "operacion", "Configuración de cabina"],
  ["00/06:00", "00/07:00", "comunicacion", "PAO, PMC y conferencia de día de vuelo"],
  ["00/07:00", "00/08:30", "presueno", PRE],
  ["00/08:30", "00/12:30", "sueno", `${SLEEP} (4 h)`],
  ["00/12:30", "00/13:00", "presueno", PRE],
  ["00/13:00", "00/13:45", "maniobra", "PRB"],
  ["00/14:00", "00/15:00", "presueno", PRE],
  ["00/15:00", "00/19:30", "sueno", `${SLEEP} (4,5 h)`],
  // FD02
  ["00/19:30", "00/21:00", "presueno", PRE],
  ["00/21:00", "00/21:45", "comunicacion", "DPC y conferencia de TLI"],
  ["00/22:00", "00/23:30", "ejercicio", "Prueba de ejercicio (Exercise Test)"],
  ["01/00:15", "01/01:30", "maniobra", "TLI"],
  ["01/01:30", "01/02:30", "comida", MEAL],
  ["01/02:30", "01/02:50", "salud", "Pulsioximetría"],
  ["01/04:00", "01/04:30", "comunicacion", "PAO"],
  ["01/05:00", "01/06:00", "operacion", "Inspección de ventanas"],
  ["01/06:40", "01/07:20", "comunicacion", "PMC y conferencia de día de vuelo"],
  ["01/07:30", "01/09:30", "presueno", PRE],
  ["01/09:30", "01/18:00", "sueno", `${SLEEP} (8,5 h)`],
  // FD03
  ["01/18:00", "01/20:00", "presueno", PRE],
  ["01/20:00", "01/21:15", "comunicacion", "DPC, pulsioximetría y PFC"],
  ["01/22:15", "01/23:00", "prueba", "Ruido del aseo (DFTO)"],
  ["02/00:20", "02/01:15", "maniobra", "OTC-1"],
  ["02/01:15", "02/02:15", "comida", MEAL],
  ["02/02:30", "02/03:00", "salud", "Demostración de RCP"],
  ["02/03:00", "02/03:20", "comunicacion", "PAO"],
  ["02/03:45", "02/04:45", "salud", "Botiquín médico"],
  ["02/04:45", "02/05:15", "operacion", "Cabina lunar"],
  ["02/05:30", "02/06:15", "prueba", "Comunicaciones de emergencia DSN (DFTO)"],
  ["02/06:30", "02/07:20", "comunicacion", "CSA PAO, PMC y conferencia de día de vuelo"],
  ["02/07:20", "02/09:30", "presueno", PRE],
  ["02/09:30", "02/18:00", "sueno", `${SLEEP} (8,5 h)`],
  // FD04
  ["02/18:00", "02/20:15", "presueno", PRE],
  ["02/20:15", "02/21:00", "comunicacion", "DPC y pulsioximetría"],
  ["02/21:00", "02/23:45", "comunicacion", "PAO, PFC y divulgación"],
  ["03/00:20", "03/01:15", "maniobra", "OTC-2"],
  ["03/01:15", "03/02:30", "comida", MEAL],
  ["03/02:30", "03/03:00", "prueba", "Pilotaje manual (Man PLT)"],
  ["03/03:05", "03/03:25", "salud", "Cognición (COGN)"],
  ["03/03:30", "03/04:45", "ejercicio", "Volante de inercia: «Flywheel Day in the life»"],
  ["03/06:10", "03/06:45", "comunicacion", "PMC y conferencia de día de vuelo"],
  ["03/06:45", "03/08:45", "presueno", PRE],
  ["03/08:45", "03/17:15", "sueno", `${SLEEP} (8,5 h), adelantado 45 min`],
  // FD05
  ["03/17:15", "03/19:15", "presueno", PRE],
  ["03/19:15", "03/20:00", "comunicacion", "DPC y pulsioximetría"],
  ["03/20:00", "04/00:00", "prueba", "Operaciones con trajes OCSS (DFTO)"],
  ["04/00:00", "04/01:15", "operacion", "Despresurización (Depress Ops)"],
  ["04/01:30", "04/02:30", "comida", MEAL],
  ["04/03:00", "04/03:45", "prueba", "ECLSS Wall"],
  ["04/03:45", "04/04:45", "maniobra", "OTC-3"],
  ["04/05:15", "04/05:45", "comunicacion", "PMC y conferencia de día de vuelo"],
  ["04/05:45", "04/07:45", "presueno", PRE],
  ["04/07:45", "04/16:15", "sueno", `${SLEEP} (8,5 h), adelantado 1 h`],
  // FD06
  ["04/16:15", "04/18:15", "presueno", PRE],
  ["04/18:15", "04/19:30", "comunicacion", "DPC, pulsioximetría, conferencia lunar y PAO"],
  ["04/19:30", "04/20:00", "operacion", "Configuración lunar"],
  ["04/20:00", "05/00:45", "operacion", "Observación lunar"],
  ["05/01:00", "05/02:00", "comida", MEAL],
  ["05/02:00", "05/02:45", "operacion", "Observación lunar"],
  ["05/02:45", "05/04:00", "operacion", "Documentación y transferencia lunar"],
  ["05/04:15", "05/05:30", "comunicacion", "PAO, PMC y conferencia de día de vuelo"],
  ["05/05:30", "05/07:30", "presueno", PRE],
  ["05/07:30", "05/17:00", "sueno", `${SLEEP} (9,5 h), retrasado 1 h`],
  // FD07
  ["05/17:00", "05/19:30", "presueno", PRE],
  ["05/19:30", "05/21:00", "comunicacion", "DPC, pulsioximetría, llamada C2C y debrief poslunar"],
  ["05/21:00", "05/23:30", "libre", "Tiempo libre (Off Duty)"],
  ["06/00:00", "06/00:45", "comida", MEAL],
  ["06/00:45", "06/01:45", "maniobra", "RTC-1"],
  ["06/02:00", "06/02:45", "comunicacion", "PFC"],
  ["06/02:45", "06/05:00", "ejercicio", "Ejercicio (P/TV Exercise)"],
  ["06/05:15", "06/06:15", "comunicacion", "PMC y conferencia de día de vuelo"],
  ["06/06:15", "06/08:30", "presueno", PRE],
  ["06/08:30", "06/17:00", "sueno", `${SLEEP} (8,5 h)`],
  // FD08
  ["06/17:00", "06/19:15", "presueno", PRE],
  ["06/19:15", "06/20:00", "comunicacion", "DPC y pulsioximetría"],
  ["06/20:00", "06/23:15", "ejercicio", "Ejercicio (P/TV Exercise)"],
  ["06/23:45", "07/00:30", "operacion", "Represurización"],
  ["07/00:30", "07/01:30", "comida", MEAL],
  ["07/01:45", "07/03:30", "prueba", "Demostración de refugio de radiación"],
  ["07/04:15", "07/05:15", "prueba", "Pilotaje manual (Man PLT DFTO 1)"],
  ["07/05:50", "07/06:30", "comunicacion", "PMC y conferencia de día de vuelo"],
  ["07/06:30", "07/08:30", "presueno", PRE],
  ["07/08:30", "07/17:00", "sueno", `${SLEEP} (8,5 h)`],
  // FD09
  ["07/17:00", "07/19:15", "presueno", PRE],
  ["07/19:15", "07/19:45", "comunicacion", "DPC y pulsioximetría"],
  ["07/19:45", "07/21:30", "prueba", "Prenda de intolerancia ortostática (OIG Don DFTO)"],
  ["07/21:30", "07/23:15", "operacion", "Estudio y conferencia de entrada"],
  ["07/23:30", "08/00:30", "comunicacion", "PAO"],
  ["08/00:30", "08/01:30", "comida", MEAL],
  ["08/02:15", "08/03:45", "prueba", "OIG Don DFTO y estiba de entrada"],
  ["08/03:45", "08/04:45", "maniobra", "RTC-2"],
  ["08/05:15", "08/06:00", "operacion", "Estiba de entrada"],
  ["08/06:00", "08/06:30", "comunicacion", "PMC y conferencia de día de vuelo"],
  ["08/06:30", "08/08:30", "presueno", PRE],
  ["08/08:30", "08/17:00", "sueno", `${SLEEP} (8,5 h)`],
  // FD10
  ["08/17:00", "08/19:00", "presueno", PRE],
  ["08/19:10", "08/19:30", "comunicacion", "PMC"],
  ["08/20:00", "08/20:45", "maniobra", "RTC-3"],
  ["08/21:00", "08/22:30", "operacion", "Configuración de cabina"],
  ["08/22:30", "09/01:09", "operacion", "Lista de comprobación de entrada"],
  ["09/01:09", "09/01:42:48", "maniobra", "Entrada, descenso y amerizaje (EDL)"],
];

// ---------- Catálogo de prevención (ejemplo del equipo) ----------

const base = {
  intense: false,
  transitionPrep: false,
  selfReport: false,
  dose: null,
  experimental: false,
} as const;

export const seedCatalog: CatalogItem[] = [
  {
    ...base,
    id: "P",
    code: "P",
    name: "Calibración y chequeo de capacidad",
    category: "evaluacion",
    loadType: "ninguna",
    capabilities: ["Referencia individual"],
    record: "Baseline preflight, ensayo actual, fuente, método y contexto",
    limit: "La baseline en microgravedad no sustituye a la terrestre",
    source: "Especificación AURA v0.4",
  },
  {
    ...base,
    id: "R",
    code: "R",
    name: "Resistencia en volante de inercia",
    category: "ejercicio",
    loadType: "CA",
    intense: true,
    capabilities: ["Fuerza", "Control postural"],
    record: "Sesión, carga, repeticiones y esfuerzo percibido",
    limit: "Solo con el dispositivo y el régimen permitidos",
    source: "NASA OCHMO — Exercise Overview",
    sourceUrl: EXERCISE_URL,
  },
  {
    ...base,
    id: "A",
    code: "A",
    name: "Acondicionamiento aeróbico",
    category: "ejercicio",
    loadType: "CA",
    intense: true,
    capabilities: ["Resistencia", "Reserva cardiovascular"],
    record: "Duración, esfuerzo y respuesta fisiológica si existe sensor",
    limit: "Ajuste solo según protocolo",
    source: "NASA OCHMO — Exercise Overview",
    sourceUrl: EXERCISE_URL,
  },
  {
    ...base,
    id: "RB",
    code: "RB",
    name: "Resistencia con bandas, carga reducida",
    category: "ejercicio",
    loadType: "CA",
    capabilities: ["Fuerza"],
    record: "Sesión, repeticiones y esfuerzo percibido",
    limit: "Alternativa de menor carga; no equivale a la sesión completa",
    source: "Ejemplo del equipo, pendiente de protocolo",
  },
  {
    ...base,
    id: "M",
    code: "M",
    name: "Movilidad y estiramientos",
    category: "ejercicio",
    loadType: "CA",
    capabilities: ["Movilidad"],
    record: "Sesión realizada y molestias",
    limit: "Alternativa de baja carga; no sustituye el estímulo de fuerza o aeróbico",
    source: "Ejemplo del equipo, pendiente de protocolo",
  },
  {
    ...base,
    id: "N1",
    code: "N1",
    name: "Menú estándar de misión",
    category: "nutricion",
    loadType: "ninguna",
    capabilities: ["Aporte energético"],
    record: "Comida realizada y apetito",
    limit: "Ejemplo; la composición depende del plan nutricional y del inventario",
    source: "Ejemplo del equipo, pendiente del plan nutricional de misión",
  },
  {
    ...base,
    id: "N2",
    code: "N2",
    name: "Menú con refuerzo de hidratación",
    category: "nutricion",
    loadType: "ninguna",
    capabilities: ["Hidratación"],
    record: "Comida realizada, líquidos y síntomas",
    limit: "Ejemplo; la composición depende del plan nutricional y del inventario",
    source: "Ejemplo del equipo, pendiente del plan nutricional de misión",
  },
  {
    ...base,
    id: "N3",
    code: "N3",
    name: "Menú de mayor aporte energético",
    category: "nutricion",
    loadType: "ninguna",
    capabilities: ["Aporte energético"],
    record: "Comida realizada y apetito",
    limit: "Ejemplo; la composición depende del plan nutricional y del inventario",
    source: "Ejemplo del equipo, pendiente del plan nutricional de misión",
  },
  {
    ...base,
    id: "N4",
    code: "N4",
    name: "Menú ligero",
    category: "nutricion",
    loadType: "ninguna",
    capabilities: ["Tolerancia digestiva"],
    record: "Comida realizada y síntomas",
    limit: "Ejemplo; la composición depende del plan nutricional y del inventario",
    source: "Ejemplo del equipo, pendiente del plan nutricional de misión",
  },
  {
    ...base,
    id: "C",
    code: "C",
    name: "Coordinación visomotora breve",
    category: "evaluacion",
    loadType: "CA",
    capabilities: ["Coordinación ojo-mano", "Destreza"],
    record: "Errores y tiempo de reacción",
    limit: "Prueba experimental; no certifica aptitud para tareas críticas",
    source: "Especificación AURA v0.4",
    experimental: true,
  },
  {
    ...base,
    id: "F",
    code: "F",
    name: "Cuestionario de fatiga, descanso y síntomas",
    category: "evaluacion",
    loadType: "ninguna",
    selfReport: true,
    capabilities: ["Fatiga y recuperación"],
    record: "Autoinforme y sueño, con hora y fiabilidad",
    limit: "Las señales de alarma se escalan; no se tratan con más carga",
    source: "Especificación AURA v0.4",
  },
  {
    ...base,
    id: "T",
    code: "T",
    name: "Preparación para aterrizaje en superficie",
    category: "preparacion",
    loadType: "CA",
    transitionPrep: true,
    capabilities: ["Equilibrio", "Control sensoriomotor"],
    record: "Evaluaciones y guías específicas de la fase",
    limit: "Marcha y equilibrio reales solo cuando proceda y sea seguro",
    source: "NASA — Sensorimotor Risk",
    sourceUrl: SENSORIMOTOR_URL,
  },
  {
    ...base,
    id: "V",
    code: "V",
    name: "Preparación del retorno terrestre",
    category: "preparacion",
    loadType: "CA",
    transitionPrep: true,
    capabilities: ["Reserva cardiovascular", "Fuerza", "Sensoriomotricidad"],
    record: "Tendencias cardiovasculares, de fuerza y sensoriomotoras",
    limit: "Continúa con el protocolo postflight; no «aprueba» el regreso",
    source: "NASA — Pre-/Postflight Medical Care",
    sourceUrl: POSTFLIGHT_URL,
  },
  {
    ...base,
    id: "OIG",
    code: "OIG",
    name: "Prenda contra la intolerancia ortostática",
    category: "preparacion",
    loadType: "ninguna",
    transitionPrep: true,
    capabilities: ["Tolerancia ortostática"],
    record: "Colocación confirmada, ajuste y síntomas",
    limit: "En Artemis II es un objetivo de prueba (OIG Don DFTO), no un protocolo cerrado",
    source: TIMELINE_SOURCE,
  },
  {
    ...base,
    id: "RE",
    code: "RE",
    name: "Ventana de recuperación protegida",
    category: "recuperacion",
    loadType: "RE",
    capabilities: ["Fatiga y recuperación"],
    record: "Sueño, descansos y carga acumulada",
    limit: "Es un requisito de planificación, no tiempo libre prescindible",
    source: "Especificación AURA v0.4",
  },
  {
    ...base,
    id: "SD",
    code: "SD",
    name: "Suplemento de vitamina D (ejemplo)",
    category: "suplemento",
    loadType: "ninguna",
    capabilities: ["Salud ósea"],
    record: "Toma confirmada por el astronauta",
    limit: "Ejemplo de catálogo; requiere prescripción médica de misión",
    source: "Ejemplo del equipo, pendiente de protocolo médico",
  },
  {
    ...base,
    id: "SF",
    code: "SF",
    name: "Carga de fluidos y sal previa al regreso (ejemplo)",
    category: "suplemento",
    loadType: "ninguna",
    capabilities: ["Tolerancia ortostática"],
    record: "Toma confirmada y síntomas",
    limit: "Ejemplo de catálogo; requiere prescripción médica de misión",
    source: "Ejemplo del equipo, pendiente de protocolo médico",
  },
];

// ---------- Plan de prevención de ejemplo (igual para toda la tripulación) ----------
// Colocado en huecos del cronograma real y, donde existe, sobre el bloque
// equivalente (ejercicio, comida, cognición, OIG). «?» marca una actividad
// condicional. Los ejercicios y los menús nacen adaptables por la interfaz.

const planRows: [met: string, item: string][] = [
  // FD01
  ["00/05:30", "SD?"],
  ["00/07:15", "F"],
  // FD02
  ["00/19:45", "F"],
  ["00/22:00", "P"],
  ["01/01:30", "N1"],
  ["01/01:45", "SD?"],
  ["01/03:00", "C"],
  ["01/07:45", "RE"],
  // FD03
  ["01/18:15", "F"],
  ["02/01:15", "N1"],
  ["02/01:30", "SD?"],
  ["02/07:30", "RE"],
  // FD04
  ["02/18:15", "F"],
  ["03/01:15", "N1"],
  ["03/01:30", "SD?"],
  ["03/03:05", "C"],
  ["03/04:00", "R"],
  // FD05
  ["03/17:30", "F"],
  ["04/01:30", "N1"],
  ["04/01:45", "SD?"],
  ["04/02:40", "C"],
  // FD06
  ["04/17:15", "F"],
  ["05/01:00", "N1"],
  ["05/01:15", "SD?"],
  ["05/05:45", "RE"],
  // FD07
  ["05/17:15", "F"],
  ["05/21:15", "RE"],
  ["06/00:00", "N1"],
  ["06/00:15", "SD?"],
  ["06/02:45", "A"],
  ["06/05:15", "V"],
  // FD08
  ["06/17:15", "F"],
  ["06/20:15", "R"],
  ["07/00:05", "C"],
  ["07/00:30", "N1"],
  ["07/01:00", "SD?"],
  ["07/05:30", "V"],
  // FD09
  ["07/17:15", "F"],
  ["07/19:45", "OIG"],
  ["07/21:45", "V"],
  ["08/00:30", "N1"],
  ["08/00:45", "SD?"],
  ["08/05:15", "A"],
  ["08/06:10", "SF?"],
  // FD10
  ["08/17:30", "F"],
  ["08/21:15", "V"],
  ["08/23:00", "SF"],
  ["09/00:15", "OIG"],
];

export function createSeedState(): AppState {
  const crew = [
    { id: "cdr", name: "CDR · Comandante", role: "Tripulante de demostración" },
    { id: "plt", name: "PLT · Piloto", role: "Tripulante de demostración" },
    { id: "ms1", name: "MS1 · Especialista de misión 1", role: "Tripulante de demostración" },
    { id: "ms2", name: "MS2 · Especialista de misión 2", role: "Tripulante de demostración" },
  ];

  const days: FlightDay[] = dayRows.map(
    ([start, end, phase, milestone, co, gravity, transitionFrom], i) => ({
      day: i + 1,
      startMet: met(start),
      endMet: met(end),
      phase,
      milestone,
      gravity,
      ...(transitionFrom ? { transitionFrom } : {}),
      co,
    }),
  );

  const events: MissionEvent[] = eventRows.map(([at, name, kind, approx], i) => ({
    id: `ev-${i + 1}`,
    met: met(at),
    name,
    kind,
    approx: approx ?? false,
  }));

  const blocks: TimelineBlock[] = blockRows.map(([start, end, kind, name], i) => ({
    id: `bl-${i + 1}`,
    startMet: met(start),
    endMet: met(end),
    name,
    kind,
  }));

  const categoryOf = new Map(seedCatalog.map((c) => [c.id, c.category]));
  const plan: PlanEntry[] = [];
  for (const member of crew) {
    for (const [at, token] of planRows) {
      const conditional = token.endsWith("?");
      const itemId = conditional ? token.slice(0, -1) : token;
      const status: EntryStatus = conditional ? "condicional" : "autorizado";
      plan.push({
        id: `${member.id}-${itemId}-${met(at)}`,
        crewId: member.id,
        itemId,
        met: met(at),
        status,
        adaptable: isAdaptableCategory(categoryOf.get(itemId) ?? "evaluacion"),
      });
    }
  }

  return {
    schemaVersion: SCHEMA_VERSION,
    mission: {
      id: "artemis-ii-plan",
      name: "Artemis II · cronograma planificado",
      destination: "Sobrevuelo lunar",
      description:
        "Vuelo tripulado de unos nueve días alrededor de la Luna, sin alunizaje. " +
        "El cronograma procede del Overview Timeline de NASA; la carga operativa, " +
        "la tripulación de demostración y el plan de prevención son un ejemplo del equipo.",
      launchUtc: null,
      timelineSource: TIMELINE_SOURCE,
      simulated: true,
    },
    crew,
    days,
    events,
    blocks,
    catalog: seedCatalog,
    plan,
    capacities: seedCapacities,
    signals: seedSignals,
    questionnaire: seedQuestionnaire,
    updatedAt: new Date().toISOString(),
  };
}
