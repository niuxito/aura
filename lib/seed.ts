import { L, type LocalizedText } from "./i18n/text";
import { met } from "./met";
import { seedCapacities, seedQuestionnaire, seedSignals } from "./questionnaire";
import {
  SCHEMA_VERSION,
  type AppState,
  type BlockKind,
  type CatalogItem,
  type CrewMember,
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

// Demo mission: PLANNED timeline of Artemis II.
//
// What is real and what is not:
// - Events, phases and flight days: NASA "Artemis II Overview Timeline"
//   (version of 2026-03-18, approved for public release, STRIVES
//   20260002249). Event METs are those labeled in the document.
// - Crew activity blocks: read from the chart of the same document, with a
//   precision of about 15 minutes. They are approximate.
// - Operational load (OL), demonstration crew and ALL of the prevention plan:
//   AURA team example. They are not NASA data or prescriptions.
//
// Every text is in English (reference language) and Spanish.

const TIMELINE_SOURCE =
  "NASA — Artemis II Overview Timeline (2026-03-18, STRIVES 20260002249)";
const EXERCISE_URL = "https://www.nasa.gov/ochmo-tb-031-exercise-overview-2/";
const SENSORIMOTOR_URL =
  "https://www.nasa.gov/directorates/esdmd/hhp/sensorimotor-risk/";
const POSTFLIGHT_URL =
  "https://www.nasa.gov/ochmo/health-and-medical-systems/pre-postflight-medical-care/";

// ---------- Flight days ----------

type DayRow = [
  start: string,
  end: string,
  phase: LocalizedText,
  milestone: LocalizedText,
  opLoad: LoadLevel,
  gravity: Gravity,
  transitionFrom?: Gravity,
];

const TRANSLUNAR = L("Translunar", "Translunar");
const TRANSEARTH = L("Trans-Earth", "Transterrestre");

const dayRows: DayRow[] = [
  ["00/00:00", "00/17:00", L("LEO → high Earth orbit", "LEO → órbita terrestre alta"), L("Launch, ARB, proximity operations and PRB", "Lanzamiento, ARB, operaciones de proximidad y PRB"), "critical", "micro", "earth"],
  ["00/17:00", "01/17:00", L("High Earth orbit → translunar", "Órbita terrestre alta → translunar"), L("Translunar injection (TLI)", "Inyección translunar (TLI)"), "critical", "micro"],
  ["01/17:00", "02/17:00", TRANSLUNAR, L("OTC-1 and medical and communications demonstrations", "OTC-1 y demostraciones médicas y de comunicaciones"), "medium", "micro"],
  ["02/17:00", "03/17:00", TRANSLUNAR, L("OTC-2 and manual piloting", "OTC-2 y pilotaje manual"), "medium", "micro"],
  ["03/17:00", "04/17:00", TRANSLUNAR, L("OCSS suit operations, OTC-3 and entry into the lunar sphere of influence", "Operaciones con trajes OCSS, OTC-3 y entrada en la esfera de influencia lunar"), "high", "micro"],
  ["04/17:00", "05/17:00", L("Translunar → trans-Earth", "Translunar → transterrestre"), L("Lunar flyby: closest approach", "Sobrevuelo lunar: máxima aproximación"), "high", "micro"],
  ["05/17:00", "06/17:00", TRANSEARTH, L("RTC-1 and crew off-duty time", "RTC-1 y tiempo libre de la tripulación"), "low", "micro"],
  ["06/17:00", "07/17:00", TRANSEARTH, L("Exercise, radiation shelter and manual piloting", "Ejercicio, refugio de radiación y pilotaje manual"), "medium", "micro"],
  ["07/17:00", "08/17:00", TRANSEARTH, L("Entry preparation and RTC-2", "Preparación de la entrada y RTC-2"), "high", "micro"],
  ["08/17:00", "09/01:42:48", L("Trans-Earth → EDL", "Transterrestre → EDL"), L("RTC-3, entry and splashdown", "RTC-3, entrada y amerizaje"), "critical", "earth", "micro"],
];

// ---------- Events (METs labeled in the document) ----------

type EventRow = [met: string, name: LocalizedText, kind: EventKind, approx?: boolean];

/** Same text in every language (acronyms and proper names). */
const same = (text: string) => L(text, text);

const eventRows: EventRow[] = [
  ["00/00:00", L("Liftoff", "Despegue"), "milestone"],
  ["00/00:30", L("Start of cabin depressurization (DFTO)", "Inicio de la despresurización de cabina (DFTO)"), "test", true],
  ["00/00:50", same("ICPS PRM"), "maneuver"],
  ["00/01:47", same("ARB TIG"), "maneuver"],
  ["00/03:23", L("Orion/ICPS separation", "Separación Orion/ICPS"), "milestone"],
  ["00/04:51", same("Orion USS"), "maneuver"],
  ["00/05:00", same("Dock Cam Bearing (FTO 225)"), "test", true],
  ["00/05:02", same("ICPS Disposal Burn"), "maneuver"],
  ["00/05:27", L("Secondary payload deployment (SPL)", "Despliegue de cargas secundarias (SPL)"), "milestone", true],
  ["00/10:06", L("Initial OpComm activation", "Activación inicial de OpComm"), "operation", true],
  ["00/13:30", same("Orion PRB"), "maneuver", true],
  ["00/14:15", L("OpNav checkout", "Comprobación de OpNav"), "operation", true],
  ["01/01:08:42", same("Orion TLI"), "maneuver", true],
  ["01/14:00", L("CM/SM inspection", "Inspección CM/SM"), "operation"],
  ["01/17:40", L("Solar arrays in full shadow", "Paneles solares en sombra total"), "milestone", true],
  ["02/01:08:42", same("OTC-1"), "maneuver", true],
  ["03/01:08:42", same("OTC-2"), "maneuver"],
  ["03/07:00", same("NAV FTO EM2-223"), "test"],
  ["04/04:29:52", same("OTC-3"), "maneuver"],
  ["04/06:38", L("Entry into the lunar sphere of influence", "Entrada en la esfera de influencia lunar"), "milestone"],
  ["05/00:29:59", L("Closest approach to the Moon", "Máxima aproximación a la Luna"), "milestone"],
  ["05/00:35", L("Maximum distance from Earth", "Distancia máxima a la Tierra"), "milestone"],
  ["05/12:30", L("CM/SM inspection", "Inspección CM/SM"), "operation"],
  ["05/18:53", L("Exit from the lunar sphere of influence", "Salida de la esfera de influencia lunar"), "milestone"],
  ["06/01:29:52", same("RTC-1"), "maneuver"],
  ["07/17:30", L("FCS checkout", "Comprobaciones del FCS"), "operation"],
  ["08/04:29:10", same("RTC-2"), "maneuver"],
  ["08/20:29:10", same("RTC-3"), "maneuver"],
  ["09/01:09", L("CM/SM separation", "Separación CM/SM"), "milestone"],
  ["09/01:29", L("Entry interface (EI)", "Interfaz de entrada (EI)"), "milestone"],
  ["09/01:42:48", L("Splashdown", "Amerizaje"), "milestone"],
];

// ---------- Crew blocks (read from the chart, approximate) ----------

type BlockRow = [start: string, end: string, kind: BlockKind, name: LocalizedText];

const sleep = (hours: string, horas: string, noteEn = "", noteEs = "") =>
  L(`Sleep (${hours} h)${noteEn}`, `Sueño (${horas} h)${noteEs}`);
const PRE = L("Pre/post-sleep", "Pre/postsueño");
const MEAL = L("Meal", "Comida");
const FD_CONFERENCE = L("PMC and flight day conference", "PMC y conferencia de día de vuelo");
const DPC_PULSE = L("DPC and pulse oximetry", "DPC y pulsioximetría");
const PAO = same("PAO");
const ENTRY_STOWAGE = L("Entry stowage", "Estiba de entrada");
const LUNAR_OBSERVATION = L("Lunar observation", "Observación lunar");

const blockRows: BlockRow[] = [
  // FD01
  ["00/00:00", "00/00:15", "maneuver", L("Ascent", "Ascenso")],
  ["00/00:30", "00/01:30", "operation", L("Pre-ARB checks", "Comprobaciones previas a ARB")],
  ["00/01:30", "00/02:00", "maneuver", same("ARB")],
  ["00/02:00", "00/03:15", "operation", L("DCAM and OCSS suit doffing", "DCAM y retirada de trajes OCSS")],
  ["00/03:15", "00/04:45", "test", L("Proximity operations demonstration", "Demostración de operaciones de proximidad")],
  ["00/04:45", "00/05:00", "maneuver", same("USS")],
  ["00/05:00", "00/06:00", "operation", L("Cabin configuration", "Configuración de cabina")],
  ["00/06:00", "00/07:00", "communication", L("PAO, PMC and flight day conference", "PAO, PMC y conferencia de día de vuelo")],
  ["00/07:00", "00/08:30", "presleep", PRE],
  ["00/08:30", "00/12:30", "sleep", sleep("4", "4")],
  ["00/12:30", "00/13:00", "presleep", PRE],
  ["00/13:00", "00/13:45", "maneuver", same("PRB")],
  ["00/14:00", "00/15:00", "presleep", PRE],
  ["00/15:00", "00/19:30", "sleep", sleep("4.5", "4,5")],
  // FD02
  ["00/19:30", "00/21:00", "presleep", PRE],
  ["00/21:00", "00/21:45", "communication", L("DPC and TLI conference", "DPC y conferencia de TLI")],
  ["00/22:00", "00/23:30", "exercise", L("Exercise Test", "Prueba de ejercicio (Exercise Test)")],
  ["01/00:15", "01/01:30", "maneuver", same("TLI")],
  ["01/01:30", "01/02:30", "meal", MEAL],
  ["01/02:30", "01/02:50", "health", L("Pulse oximetry", "Pulsioximetría")],
  ["01/04:00", "01/04:30", "communication", PAO],
  ["01/05:00", "01/06:00", "operation", L("Window inspection", "Inspección de ventanas")],
  ["01/06:40", "01/07:20", "communication", FD_CONFERENCE],
  ["01/07:30", "01/09:30", "presleep", PRE],
  ["01/09:30", "01/18:00", "sleep", sleep("8.5", "8,5")],
  // FD03
  ["01/18:00", "01/20:00", "presleep", PRE],
  ["01/20:00", "01/21:15", "communication", L("DPC, pulse oximetry and PFC", "DPC, pulsioximetría y PFC")],
  ["01/22:15", "01/23:00", "test", L("Toilet noise (DFTO)", "Ruido del aseo (DFTO)")],
  ["02/00:20", "02/01:15", "maneuver", same("OTC-1")],
  ["02/01:15", "02/02:15", "meal", MEAL],
  ["02/02:30", "02/03:00", "health", L("CPR demonstration", "Demostración de RCP")],
  ["02/03:00", "02/03:20", "communication", PAO],
  ["02/03:45", "02/04:45", "health", L("Medical kit", "Botiquín médico")],
  ["02/04:45", "02/05:15", "operation", L("Lunar cabin", "Cabina lunar")],
  ["02/05:30", "02/06:15", "test", L("DSN emergency communications (DFTO)", "Comunicaciones de emergencia DSN (DFTO)")],
  ["02/06:30", "02/07:20", "communication", L("CSA PAO, PMC and flight day conference", "CSA PAO, PMC y conferencia de día de vuelo")],
  ["02/07:20", "02/09:30", "presleep", PRE],
  ["02/09:30", "02/18:00", "sleep", sleep("8.5", "8,5")],
  // FD04
  ["02/18:00", "02/20:15", "presleep", PRE],
  ["02/20:15", "02/21:00", "communication", DPC_PULSE],
  ["02/21:00", "02/23:45", "communication", L("PAO, PFC and outreach", "PAO, PFC y divulgación")],
  ["03/00:20", "03/01:15", "maneuver", same("OTC-2")],
  ["03/01:15", "03/02:30", "meal", MEAL],
  ["03/02:30", "03/03:00", "test", L("Manual piloting (Man PLT)", "Pilotaje manual (Man PLT)")],
  ["03/03:05", "03/03:25", "health", L("Cognition (COGN)", "Cognición (COGN)")],
  ["03/03:30", "03/04:45", "exercise", L("Flywheel: “Flywheel Day in the life”", "Volante de inercia: «Flywheel Day in the life»")],
  ["03/06:10", "03/06:45", "communication", FD_CONFERENCE],
  ["03/06:45", "03/08:45", "presleep", PRE],
  ["03/08:45", "03/17:15", "sleep", sleep("8.5", "8,5", ", shifted 45 min earlier", ", adelantado 45 min")],
  // FD05
  ["03/17:15", "03/19:15", "presleep", PRE],
  ["03/19:15", "03/20:00", "communication", DPC_PULSE],
  ["03/20:00", "04/00:00", "test", L("OCSS suit operations (DFTO)", "Operaciones con trajes OCSS (DFTO)")],
  ["04/00:00", "04/01:15", "operation", L("Depressurization (Depress Ops)", "Despresurización (Depress Ops)")],
  ["04/01:30", "04/02:30", "meal", MEAL],
  ["04/03:00", "04/03:45", "test", same("ECLSS Wall")],
  ["04/03:45", "04/04:45", "maneuver", same("OTC-3")],
  ["04/05:15", "04/05:45", "communication", FD_CONFERENCE],
  ["04/05:45", "04/07:45", "presleep", PRE],
  ["04/07:45", "04/16:15", "sleep", sleep("8.5", "8,5", ", shifted 1 h earlier", ", adelantado 1 h")],
  // FD06
  ["04/16:15", "04/18:15", "presleep", PRE],
  ["04/18:15", "04/19:30", "communication", L("DPC, pulse oximetry, lunar conference and PAO", "DPC, pulsioximetría, conferencia lunar y PAO")],
  ["04/19:30", "04/20:00", "operation", L("Lunar configuration", "Configuración lunar")],
  ["04/20:00", "05/00:45", "operation", LUNAR_OBSERVATION],
  ["05/01:00", "05/02:00", "meal", MEAL],
  ["05/02:00", "05/02:45", "operation", LUNAR_OBSERVATION],
  ["05/02:45", "05/04:00", "operation", L("Lunar documentation and transfer", "Documentación y transferencia lunar")],
  ["05/04:15", "05/05:30", "communication", L("PAO, PMC and flight day conference", "PAO, PMC y conferencia de día de vuelo")],
  ["05/05:30", "05/07:30", "presleep", PRE],
  ["05/07:30", "05/17:00", "sleep", sleep("9.5", "9,5", ", shifted 1 h later", ", retrasado 1 h")],
  // FD07
  ["05/17:00", "05/19:30", "presleep", PRE],
  ["05/19:30", "05/21:00", "communication", L("DPC, pulse oximetry, C2C call and post-lunar debrief", "DPC, pulsioximetría, llamada C2C y debrief poslunar")],
  ["05/21:00", "05/23:30", "off-duty", L("Off Duty", "Tiempo libre (Off Duty)")],
  ["06/00:00", "06/00:45", "meal", MEAL],
  ["06/00:45", "06/01:45", "maneuver", same("RTC-1")],
  ["06/02:00", "06/02:45", "communication", same("PFC")],
  ["06/02:45", "06/05:00", "exercise", L("P/TV Exercise", "Ejercicio (P/TV Exercise)")],
  ["06/05:15", "06/06:15", "communication", FD_CONFERENCE],
  ["06/06:15", "06/08:30", "presleep", PRE],
  ["06/08:30", "06/17:00", "sleep", sleep("8.5", "8,5")],
  // FD08
  ["06/17:00", "06/19:15", "presleep", PRE],
  ["06/19:15", "06/20:00", "communication", DPC_PULSE],
  ["06/20:00", "06/23:15", "exercise", L("P/TV Exercise", "Ejercicio (P/TV Exercise)")],
  ["06/23:45", "07/00:30", "operation", L("Repressurization", "Represurización")],
  ["07/00:30", "07/01:30", "meal", MEAL],
  ["07/01:45", "07/03:30", "test", L("Radiation shelter demonstration", "Demostración de refugio de radiación")],
  ["07/04:15", "07/05:15", "test", L("Manual piloting (Man PLT DFTO 1)", "Pilotaje manual (Man PLT DFTO 1)")],
  ["07/05:50", "07/06:30", "communication", FD_CONFERENCE],
  ["07/06:30", "07/08:30", "presleep", PRE],
  ["07/08:30", "07/17:00", "sleep", sleep("8.5", "8,5")],
  // FD09
  ["07/17:00", "07/19:15", "presleep", PRE],
  ["07/19:15", "07/19:45", "communication", DPC_PULSE],
  ["07/19:45", "07/21:30", "test", L("Orthostatic intolerance garment (OIG Don DFTO)", "Prenda de intolerancia ortostática (OIG Don DFTO)")],
  ["07/21:30", "07/23:15", "operation", L("Entry study and conference", "Estudio y conferencia de entrada")],
  ["07/23:30", "08/00:30", "communication", PAO],
  ["08/00:30", "08/01:30", "meal", MEAL],
  ["08/02:15", "08/03:45", "test", L("OIG Don DFTO and entry stowage", "OIG Don DFTO y estiba de entrada")],
  ["08/03:45", "08/04:45", "maneuver", same("RTC-2")],
  ["08/05:15", "08/06:00", "operation", ENTRY_STOWAGE],
  ["08/06:00", "08/06:30", "communication", FD_CONFERENCE],
  ["08/06:30", "08/08:30", "presleep", PRE],
  ["08/08:30", "08/17:00", "sleep", sleep("8.5", "8,5")],
  // FD10
  ["08/17:00", "08/19:00", "presleep", PRE],
  ["08/19:10", "08/19:30", "communication", same("PMC")],
  ["08/20:00", "08/20:45", "maneuver", same("RTC-3")],
  ["08/21:00", "08/22:30", "operation", L("Cabin configuration", "Configuración de cabina")],
  ["08/22:30", "09/01:09", "operation", L("Entry checklist", "Lista de comprobación de entrada")],
  ["09/01:09", "09/01:42:48", "maneuver", L("Entry, descent and splashdown (EDL)", "Entrada, descenso y amerizaje (EDL)")],
];

// ---------- Prevention catalog (team example) ----------

const base = {
  intense: false,
  transitionPrep: false,
  selfReport: false,
  dose: null,
  experimental: false,
} as const;

const AURA_SPEC = L("AURA specification v0.4", "Especificación AURA v0.4");
const EXERCISE_SOURCE = same("NASA OCHMO — Exercise Overview");
const TEAM_PROTOCOL = L("Team example, pending protocol", "Ejemplo del equipo, pendiente de protocolo");
const TEAM_NUTRITION = L(
  "Team example, pending the mission nutrition plan",
  "Ejemplo del equipo, pendiente del plan nutricional de misión",
);
const TEAM_MEDICAL = L(
  "Team example, pending medical protocol",
  "Ejemplo del equipo, pendiente de protocolo médico",
);
const MENU_LIMIT = L(
  "Example; the composition depends on the nutrition plan and the inventory",
  "Ejemplo; la composición depende del plan nutricional y del inventario",
);
const SUPPLEMENT_LIMIT = L(
  "Catalog example; requires a mission medical prescription",
  "Ejemplo de catálogo; requiere prescripción médica de misión",
);
const ENERGY = L("Energy intake", "Aporte energético");
const FATIGUE_RECOVERY = L("Fatigue and recovery", "Fatiga y recuperación");
const STRENGTH = L("Strength", "Fuerza");
const CARDIO_RESERVE = L("Cardiovascular reserve", "Reserva cardiovascular");
const ORTHOSTATIC = L("Orthostatic tolerance", "Tolerancia ortostática");

const catalogItems: CatalogItem[] = [
  {
    ...base,
    id: "P",
    code: "P",
    name: L("Calibration and capacity check", "Calibración y chequeo de capacidad"),
    category: "assessment",
    loadType: "none",
    capabilities: [L("Individual baseline", "Referencia individual")],
    record: L(
      "Preflight baseline, current trial, source, method and context",
      "Baseline preflight, ensayo actual, fuente, método y contexto",
    ),
    limit: L(
      "The microgravity baseline does not replace the terrestrial one",
      "La baseline en microgravedad no sustituye a la terrestre",
    ),
    source: AURA_SPEC,
  },
  {
    ...base,
    id: "R",
    code: "R",
    name: L("Flywheel resistance exercise", "Resistencia en volante de inercia"),
    category: "exercise",
    loadType: "CL",
    intense: true,
    capabilities: [STRENGTH, L("Postural control", "Control postural")],
    record: L(
      "Session, load, repetitions and perceived exertion",
      "Sesión, carga, repeticiones y esfuerzo percibido",
    ),
    limit: L(
      "Only with the permitted device and regimen",
      "Solo con el dispositivo y el régimen permitidos",
    ),
    source: EXERCISE_SOURCE,
    sourceUrl: EXERCISE_URL,
  },
  {
    ...base,
    id: "A",
    code: "A",
    name: L("Aerobic conditioning", "Acondicionamiento aeróbico"),
    category: "exercise",
    loadType: "CL",
    intense: true,
    capabilities: [L("Endurance", "Resistencia"), CARDIO_RESERVE],
    record: L(
      "Duration, exertion and physiological response if a sensor is available",
      "Duración, esfuerzo y respuesta fisiológica si existe sensor",
    ),
    limit: L("Adjust only according to protocol", "Ajuste solo según protocolo"),
    source: EXERCISE_SOURCE,
    sourceUrl: EXERCISE_URL,
  },
  {
    ...base,
    id: "RB",
    code: "RB",
    name: L("Resistance bands, reduced load", "Resistencia con bandas, carga reducida"),
    category: "exercise",
    loadType: "CL",
    capabilities: [STRENGTH],
    record: L(
      "Session, repetitions and perceived exertion",
      "Sesión, repeticiones y esfuerzo percibido",
    ),
    limit: L(
      "Lower-load alternative; not equivalent to the full session",
      "Alternativa de menor carga; no equivale a la sesión completa",
    ),
    source: TEAM_PROTOCOL,
  },
  {
    ...base,
    id: "M",
    code: "M",
    name: L("Mobility and stretching", "Movilidad y estiramientos"),
    category: "exercise",
    loadType: "CL",
    capabilities: [L("Mobility", "Movilidad")],
    record: L("Session completed and discomfort", "Sesión realizada y molestias"),
    limit: L(
      "Low-load alternative; does not replace the strength or aerobic stimulus",
      "Alternativa de baja carga; no sustituye el estímulo de fuerza o aeróbico",
    ),
    source: TEAM_PROTOCOL,
  },
  {
    ...base,
    id: "N1",
    code: "N1",
    name: L("Standard mission menu", "Menú estándar de misión"),
    category: "nutrition",
    loadType: "none",
    capabilities: [ENERGY],
    record: L("Meal eaten and appetite", "Comida realizada y apetito"),
    limit: MENU_LIMIT,
    source: TEAM_NUTRITION,
  },
  {
    ...base,
    id: "N2",
    code: "N2",
    name: L("Menu with extra hydration", "Menú con refuerzo de hidratación"),
    category: "nutrition",
    loadType: "none",
    capabilities: [L("Hydration", "Hidratación")],
    record: L("Meal eaten, fluids and symptoms", "Comida realizada, líquidos y síntomas"),
    limit: MENU_LIMIT,
    source: TEAM_NUTRITION,
  },
  {
    ...base,
    id: "N3",
    code: "N3",
    name: L("Higher-energy menu", "Menú de mayor aporte energético"),
    category: "nutrition",
    loadType: "none",
    capabilities: [ENERGY],
    record: L("Meal eaten and appetite", "Comida realizada y apetito"),
    limit: MENU_LIMIT,
    source: TEAM_NUTRITION,
  },
  {
    ...base,
    id: "N4",
    code: "N4",
    name: L("Light menu", "Menú ligero"),
    category: "nutrition",
    loadType: "none",
    capabilities: [L("Digestive tolerance", "Tolerancia digestiva")],
    record: L("Meal eaten and symptoms", "Comida realizada y síntomas"),
    limit: MENU_LIMIT,
    source: TEAM_NUTRITION,
  },
  {
    ...base,
    id: "C",
    code: "C",
    name: L("Brief visuomotor coordination test", "Coordinación visomotora breve"),
    category: "assessment",
    loadType: "CL",
    capabilities: [L("Hand-eye coordination", "Coordinación ojo-mano"), L("Dexterity", "Destreza")],
    record: L("Errors and reaction time", "Errores y tiempo de reacción"),
    limit: L(
      "Experimental test; does not certify fitness for critical tasks",
      "Prueba experimental; no certifica aptitud para tareas críticas",
    ),
    source: AURA_SPEC,
    experimental: true,
  },
  {
    ...base,
    id: "F",
    code: "F",
    name: L("Fatigue, rest and symptoms questionnaire", "Cuestionario de fatiga, descanso y síntomas"),
    category: "assessment",
    loadType: "none",
    selfReport: true,
    capabilities: [FATIGUE_RECOVERY],
    record: L(
      "Self-report and sleep, with time and reliability",
      "Autoinforme y sueño, con hora y fiabilidad",
    ),
    limit: L(
      "Warning signs are escalated; they are not treated with more load",
      "Las señales de alarma se escalan; no se tratan con más carga",
    ),
    source: AURA_SPEC,
  },
  {
    ...base,
    id: "T",
    code: "T",
    name: L("Preparation for surface landing", "Preparación para aterrizaje en superficie"),
    category: "preparation",
    loadType: "CL",
    transitionPrep: true,
    capabilities: [L("Balance", "Equilibrio"), L("Sensorimotor control", "Control sensoriomotor")],
    record: L(
      "Phase-specific assessments and guidance",
      "Evaluaciones y guías específicas de la fase",
    ),
    limit: L(
      "Real gait and balance only when appropriate and safe",
      "Marcha y equilibrio reales solo cuando proceda y sea seguro",
    ),
    source: same("NASA — Sensorimotor Risk"),
    sourceUrl: SENSORIMOTOR_URL,
  },
  {
    ...base,
    id: "V",
    code: "V",
    name: L("Return-to-Earth preparation", "Preparación del retorno terrestre"),
    category: "preparation",
    loadType: "CL",
    transitionPrep: true,
    capabilities: [CARDIO_RESERVE, STRENGTH, L("Sensorimotor function", "Sensoriomotricidad")],
    record: L(
      "Cardiovascular, strength and sensorimotor trends",
      "Tendencias cardiovasculares, de fuerza y sensoriomotoras",
    ),
    limit: L(
      "Continues with the postflight protocol; does not “approve” the return",
      "Continúa con el protocolo postflight; no «aprueba» el regreso",
    ),
    source: same("NASA — Pre-/Postflight Medical Care"),
    sourceUrl: POSTFLIGHT_URL,
  },
  {
    ...base,
    id: "OIG",
    code: "OIG",
    name: L("Orthostatic intolerance garment", "Prenda contra la intolerancia ortostática"),
    category: "preparation",
    loadType: "none",
    transitionPrep: true,
    capabilities: [ORTHOSTATIC],
    record: L("Donning confirmed, fit and symptoms", "Colocación confirmada, ajuste y síntomas"),
    limit: L(
      "On Artemis II it is a test objective (OIG Don DFTO), not a closed protocol",
      "En Artemis II es un objetivo de prueba (OIG Don DFTO), no un protocolo cerrado",
    ),
    source: same(TIMELINE_SOURCE),
  },
  {
    ...base,
    id: "RE",
    code: "RE",
    name: L("Protected recovery window", "Ventana de recuperación protegida"),
    category: "recovery",
    loadType: "RE",
    capabilities: [FATIGUE_RECOVERY],
    record: L("Sleep, breaks and accumulated load", "Sueño, descansos y carga acumulada"),
    limit: L(
      "It is a planning requirement, not expendable free time",
      "Es un requisito de planificación, no tiempo libre prescindible",
    ),
    source: AURA_SPEC,
  },
  {
    ...base,
    id: "SD",
    code: "SD",
    name: L("Vitamin D supplement (example)", "Suplemento de vitamina D (ejemplo)"),
    category: "supplement",
    loadType: "none",
    capabilities: [L("Bone health", "Salud ósea")],
    record: L("Intake confirmed by the astronaut", "Toma confirmada por el astronauta"),
    limit: SUPPLEMENT_LIMIT,
    source: TEAM_MEDICAL,
  },
  {
    ...base,
    id: "SF",
    code: "SF",
    name: L("Pre-return fluid and salt loading (example)", "Carga de fluidos y sal previa al regreso (ejemplo)"),
    category: "supplement",
    loadType: "none",
    capabilities: [ORTHOSTATIC],
    record: L("Intake confirmed and symptoms", "Toma confirmada y síntomas"),
    limit: SUPPLEMENT_LIMIT,
    source: TEAM_MEDICAL,
  },
];

/**
 * Planned duration of each action, in minutes: team estimate for the demo, so
 * the interface knows when an activity is in progress. Absent = a single
 * moment (a supplement) or set by the medical protocol (fluid loading).
 */
const DURATION_MIN: Record<string, number> = {
  P: 20,
  R: 45,
  A: 30,
  RB: 30,
  M: 20,
  N1: 30,
  N2: 30,
  N3: 30,
  N4: 30,
  C: 5,
  F: 5,
  T: 30,
  V: 45,
  OIG: 30,
  RE: 60,
};

export const seedCatalog: CatalogItem[] = catalogItems.map((c) => ({
  ...c,
  durationMin: DURATION_MIN[c.id] ?? null,
}));

// ---------- Example prevention plan (the same for the whole crew) ----------
// Placed in gaps of the real timeline and, where one exists, on the matching
// block (exercise, meal, cognition, OIG). "?" marks a conditional activity.
// Exercise and menus start out adaptable by the interface.

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
  // Aerobic before the return: in FD08's exercise block, not on the day before
  // splashdown (FD10 has critical operational load and protects FD09's rest).
  ["06/22:00", "A"],
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
  ["08/06:10", "SF?"],
  // FD10
  ["08/17:30", "F"],
  ["08/21:15", "V"],
  ["08/23:00", "SF"],
  ["09/00:15", "OIG"],
];

/** What a conditional activity depends on, by catalog item. */
const CONDITION_NOTE: Record<string, LocalizedText> = {
  SD: L(
    "Depends on the medical protocol confirming the supplement and its dose.",
    "Depende de que el protocolo médico confirme el suplemento y su dosis.",
  ),
  SF: L(
    "Depends on the medical team bringing the fluid loading forward to the day before.",
    "Depende de que el equipo médico adelante la carga de fluidos a la víspera.",
  ),
};

/** Exact MET of each gravity transition, by flight day. */
const TRANSITION_MET: Record<number, string> = {
  1: "00/00:00", // Liftoff
  10: "09/01:42:48", // Splashdown
};

/** Resting heart rate baselines: demonstration values, not real crew data. */
const DEMO_RESTING_HR: Record<string, number> = { cdr: 58, plt: 62, ms1: 60, ms2: 64 };

export function createSeedState(): AppState {
  const demoCrew = L("Demonstration crew member", "Tripulante de demostración");
  const crew: CrewMember[] = [
    { id: "cdr", name: "CDR · Commander", role: demoCrew },
    { id: "plt", name: "PLT · Pilot", role: demoCrew },
    { id: "ms1", name: "MS1 · Mission Specialist 1", role: demoCrew },
    { id: "ms2", name: "MS2 · Mission Specialist 2", role: demoCrew },
  ].map((c) => ({ ...c, restingHrBpm: DEMO_RESTING_HR[c.id] ?? null }));

  const days: FlightDay[] = dayRows.map(
    ([start, end, phase, milestone, opLoad, gravity, transitionFrom], i) => ({
      day: i + 1,
      startMet: met(start),
      endMet: met(end),
      phase,
      milestone,
      gravity,
      ...(transitionFrom ? { transitionFrom } : {}),
      ...(transitionFrom && TRANSITION_MET[i + 1] ? { transitionMet: met(TRANSITION_MET[i + 1]) } : {}),
      opLoad,
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
      const status: EntryStatus = conditional ? "conditional" : "authorized";
      plan.push({
        id: `${member.id}-${itemId}-${met(at)}`,
        crewId: member.id,
        itemId,
        met: met(at),
        status,
        adaptable: isAdaptableCategory(categoryOf.get(itemId) ?? "assessment"),
        ...(conditional && CONDITION_NOTE[itemId] ? { note: CONDITION_NOTE[itemId] } : {}),
      });
    }
  }

  return {
    schemaVersion: SCHEMA_VERSION,
    mission: {
      id: "artemis-ii-plan",
      name: L("Artemis II · planned timeline", "Artemis II · cronograma planificado"),
      destination: L("Lunar flyby", "Sobrevuelo lunar"),
      description: L(
        "Crewed flight of about nine days around the Moon, with no landing. " +
          "The timeline comes from NASA's Overview Timeline; operational load, " +
          "demonstration crew and prevention plan are a team example.",
        "Vuelo tripulado de unos nueve días alrededor de la Luna, sin alunizaje. " +
          "El cronograma procede del Overview Timeline de NASA; la carga operativa, " +
          "la tripulación de demostración y el plan de prevención son un ejemplo del equipo.",
      ),
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
