import { L, type LocalizedText } from "./i18n/text";
import type {
  Capacity,
  Questionnaire,
  QuestionItem,
  QuestionScale,
  QuestionTrigger,
  QuestionVariant,
  Signal,
} from "./types";

// Astronaut daily self-assessment: traffic-light capacities, objective signals
// and questionnaire. Questions, scales, rotation and triggers come from the
// team document "ARTEMIS-MED — Medical logic, questionnaire and traffic light"
// (Biomedical Engineering A), adapted to AURA:
// - Works for any mission (Moon, Mars, station): triggers use gravity
//   environments and flight days, not dates of a specific mission. The
//   document's 72 h windows are expressed as 3 flight days.
// - Only signals the interface can have are used: the watch (real), the
//   coordination test (real) and smart clothing (simulated in the demo).
//   Triggers that depend on camera, blood tests, myometry or tonometry are
//   not included.
// - All thresholds are for demonstration, not clinical criteria.
// Every text is in English (reference language) and Spanish.

export const QUESTIONNAIRE_SOURCE = L(
  "ARTEMIS-MED — Medical logic, questionnaire and traffic light (team, Biomedical Engineering A)",
  "ARTEMIS-MED — Lógica médica, cuestionario y semáforo (equipo, Ingeniería Biomédica A)",
);

const ON_RED = L(
  "Does not decide on its own: refers to the mission procedure, alerts mission control and does not propose intense load.",
  "No decide por su cuenta: remite al procedimiento de misión, avisa a control de misión y no propone carga intensa.",
);

export const seedCapacities: Capacity[] = [
  {
    id: "fatigue",
    name: L("Fatigue and recovery", "Fatiga y recuperación"),
    description: L(
      "Operational fatigue, mental reserve and quality of rest.",
      "Fatiga operativa, reserva mental y calidad del descanso.",
    ),
    onAmber: L(
      "Replaces intense exercise with a lower-load one from the catalog or omits it, and protects recovery.",
      "Sustituye el ejercicio intenso por uno de menor carga del catálogo o lo omite, y protege la recuperación.",
    ),
    onRed: ON_RED,
  },
  {
    id: "musculoskeletal",
    name: L("Strength and musculoskeletal system", "Fuerza y sistema musculoesquelético"),
    description: L(
      "Stiffness, discomfort, localized fatigue and loss of strength.",
      "Rigidez, molestias, fatiga localizada y pérdida de fuerza.",
    ),
    onAmber: L(
      "Replaces resistive exercise with a lower-load one (bands, mobility) or omits it. Never adds load.",
      "Sustituye el ejercicio resistivo por otro de menor carga (bandas, movilidad) o lo omite. Nunca añade carga.",
    ),
    onRed: ON_RED,
  },
  {
    id: "vestibular",
    name: L("Balance and vestibular adaptation", "Equilibrio y adaptación vestibular"),
    description: L(
      "Gaze stability, disorientation and balance, especially near a gravity change.",
      "Estabilidad de la mirada, desorientación y equilibrio, sobre todo cerca de un cambio de gravedad.",
    ),
    onAmber: L(
      "Alerts and prioritizes the transition preparation activities already planned. Does not create new exercises.",
      "Avisa y prioriza las actividades de preparación de la transición ya planificadas. No crea ejercicios nuevos.",
    ),
    onRed: ON_RED,
  },
  {
    id: "cardiovascular",
    name: L("Cardiovascular and orthostatic tolerance", "Tolerancia cardiovascular y ortostática"),
    description: L(
      "Dizziness on standing up, palpitations, recovery after exertion and hydration.",
      "Mareo al incorporarse, palpitaciones, recuperación tras el esfuerzo e hidratación.",
    ),
    onAmber: L(
      "Switches to the menu with extra hydration and replaces intense exercise with a lower-load one.",
      "Cambia al menú con refuerzo de hidratación y sustituye el ejercicio intenso por uno de menor carga.",
    ),
    onRed: ON_RED,
  },
  {
    id: "vision",
    name: L("Vision and head symptoms", "Visión y síntomas cefálicos"),
    description: L(
      "Head fullness, eye pressure and difficulty focusing.",
      "Pesadez craneal, presión ocular y dificultad para enfocar.",
    ),
    onAmber: L(
      "Replaces intense resistive exercise (with Valsalva maneuver) with a lower-load one and alerts.",
      "Sustituye el ejercicio resistivo intenso (con maniobra de Valsalva) por uno de menor carga y avisa.",
    ),
    onRed: ON_RED,
  },
  {
    id: "coordination",
    name: L("Visuomotor coordination", "Coordinación visomotora"),
    description: L(
      "Result of the tactile coordination test (experimental) against the astronaut's own baseline.",
      "Resultado de la prueba táctil de coordinación (experimental) respecto a la referencia propia.",
    ),
    onAmber: L(
      "Asks to repeat the test and does not propose activities that increase the load.",
      "Pide repetir la prueba y no propone actividades que aumenten la carga.",
    ),
    onRed: ON_RED,
  },
];

export const seedSignals: Signal[] = [
  {
    id: "resting_hr_delta",
    name: L(
      "Resting heart rate against own baseline",
      "Frecuencia cardiaca en reposo respecto a la referencia propia",
    ),
    unit: "bpm",
    source: "watch",
    simulated: false,
    capacityId: "cardiovascular",
    kind: "number",
    worseWhen: "higher",
    amber: 10,
    red: 15,
    description: L(
      "Difference between today's resting HR and the astronaut's own baseline.",
      "Diferencia entre la FC en reposo de hoy y la referencia del propio astronauta.",
    ),
  },
  {
    id: "slow_hr_recovery",
    name: L("Slow HR recovery after exercise", "Recuperación lenta de la FC tras el ejercicio"),
    unit: "",
    source: "watch",
    simulated: false,
    capacityId: "cardiovascular",
    kind: "boolean",
    whenTrue: "amber",
    description: L(
      "HR takes longer than usual to come down after an exercise session.",
      "La FC tarda más de lo habitual en bajar tras una sesión de ejercicio.",
    ),
  },
  {
    id: "night_hrv_delta",
    name: L(
      "Nighttime heart rate variability (RMSSD) against own baseline",
      "Variabilidad cardiaca nocturna (RMSSD) respecto a la referencia propia",
    ),
    unit: "%",
    source: "garment",
    simulated: true,
    capacityId: "fatigue",
    kind: "number",
    worseWhen: "lower",
    amber: -15,
    red: -25,
    description: L(
      "Percentage change in RMSSD during sleep.",
      "Variación porcentual del RMSSD durante el sueño.",
    ),
  },
  {
    id: "resp_rate_delta",
    name: L(
      "Respiratory rate against own baseline",
      "Frecuencia respiratoria respecto a la referencia propia",
    ),
    unit: "%",
    source: "garment",
    simulated: true,
    capacityId: "cardiovascular",
    kind: "number",
    worseWhen: "higher",
    amber: 25,
    red: null,
    description: L(
      "Percentage change in resting respiratory rate.",
      "Variación porcentual de la frecuencia respiratoria en reposo.",
    ),
  },
  {
    id: "spo2",
    name: L("Oxygen saturation", "Saturación de oxígeno"),
    unit: "%",
    source: "garment",
    simulated: true,
    capacityId: "cardiovascular",
    kind: "number",
    worseWhen: "lower",
    amber: null,
    red: 92,
    description: L("Resting SpO2.", "SpO2 en reposo."),
  },
  {
    id: "ecg_arrhythmia",
    name: L("Extrasystoles or irregular rhythm on the ECG", "Extrasístoles o ritmo irregular en el ECG"),
    unit: "",
    source: "garment",
    simulated: true,
    capacityId: "cardiovascular",
    kind: "boolean",
    whenTrue: "amber",
    description: L(
      "The garment's ECG recorded irregular beats in the last 24 h.",
      "El ECG de la prenda registra latidos irregulares en las últimas 24 h.",
    ),
  },
  {
    id: "emg_fatigue",
    name: L("Muscle fatigue (sEMG)", "Fatiga muscular (sEMG)"),
    unit: "%",
    source: "garment",
    simulated: true,
    capacityId: "musculoskeletal",
    kind: "number",
    worseWhen: "higher",
    amber: 20,
    red: null,
    description: L(
      "Muscle fatigue indicator from the garment's surface electromyography.",
      "Indicador de fatiga muscular de la electromiografía de superficie de la prenda.",
    ),
  },
  {
    id: "coordination_delta",
    name: L(
      "Worsening in the coordination test against own baseline",
      "Empeoramiento en la prueba de coordinación respecto a la referencia propia",
    ),
    unit: "%",
    source: "test",
    simulated: false,
    capacityId: "coordination",
    kind: "number",
    worseWhen: "higher",
    amber: 15,
    red: 30,
    description: L(
      "Combined worsening of errors and latency in the tactile test (code C).",
      "Empeoramiento combinado de errores y latencia en la prueba táctil (código C).",
    ),
  },
];

// ---------- Questionnaire ----------

const v = (
  id: string,
  text: LocalizedText,
  minLabel: LocalizedText,
  maxLabel: LocalizedText,
  inverted = false,
): QuestionVariant => ({ id, text, minLabel, maxLabel, inverted });

const S_0_2: QuestionScale = { min: 0, max: 2 };
const S_0_3: QuestionScale = { min: 0, max: 3 };
const S_1_5: QuestionScale = { min: 1, max: 5 };
const S_1_7: QuestionScale = { min: 1, max: 7 };

// Scale-end labels used by several questions.
const NO = L("No", "No");
const NONE = L("None", "Ninguna");
const NOT_AT_ALL = L("Not at all", "Nada");
const A_LOT = L("A lot", "Mucha");
const A_LOT_M = L("A lot", "Mucho");
const SEVERE = L("Severe", "Intenso");
const SEVERE_F = L("Severe", "Intensa");
const CLEARLY = L("Clearly", "Claramente");

/** Window around a landing or return to a surface with gravity. */
const LANDING_TO = ["earth", "moon", "mars"] as const;

const FILLER_REASON = L(
  "Filler question when no others are triggered.",
  "Pregunta de relleno cuando no hay otras disparadas.",
);

const trig = (t: QuestionTrigger) => t;

const items: QuestionItem[] = [
  // --- Block A: fixed core, one variant per day in rotation ---
  {
    id: "Q1_FATIGUE",
    block: "fixed",
    domain: L("Operational fatigue and mental reserve", "Fatiga operativa y reserva mental"),
    capacityId: "fatigue",
    scale: S_1_7,
    inspiredBy: same("Samn-Perelli (USAF)"),
    variants: [
      v("Q1_V1", L("What is your level of operational fatigue right now?", "¿Cuál es tu nivel de fatiga operativa ahora mismo?"), L("Fully alert", "Totalmente despejado"), L("Extreme exhaustion", "Agotamiento extremo")),
      v("Q1_V2", L("How much extra mental effort do you need today to stay focused on precision tasks?", "¿Cuánto esfuerzo mental extra necesitas hoy para mantener la concentración en tareas de precisión?"), NONE, L("Extreme", "Extremo")),
      v("Q1_V3", L("How much mental sharpness and energy reserve do you feel today?", "¿Qué nivel de agilidad mental y reserva de energía sientes hoy?"), L("Minimal", "Mínima"), L("Maximal", "Máxima"), true),
    ],
    amber: 4,
    red: 6,
    triggers: [],
  },
  {
    id: "Q2_SANS",
    block: "fixed",
    domain: L("Headward fluid shift", "Redistribución de fluidos hacia la cabeza"),
    capacityId: "vision",
    scale: S_0_3,
    inspiredBy: L("SANS symptom screening (NASA)", "Cribado de síntomas SANS (NASA)"),
    variants: [
      v("Q2_V1", L("Do you notice head fullness or a throbbing headache today?", "¿Percibes pesadez craneal o dolor de cabeza pulsátil hoy?"), NO, SEVERE),
      v("Q2_V2", L("Is it harder today to sharply focus on the small indicators on the console?", "¿Notas mayor dificultad hoy para enfocar con nitidez los indicadores pequeños de la consola?"), NO, A_LOT),
      v("Q2_V3", L("Do you feel sinus congestion or pressure behind the eyes, like being tilted head-down?", "¿Sientes congestión sinusal o presión detrás de los ojos similar a estar inclinado hacia abajo?"), NO, SEVERE_F),
    ],
    amber: 2,
    red: 3,
    triggers: [],
  },
  {
    id: "Q3_MUSCULAR",
    block: "fixed",
    domain: L("Overall musculoskeletal integrity", "Integridad musculoesquelética global"),
    capacityId: "musculoskeletal",
    scale: S_1_5,
    inspiredBy: L("Discretized visual analog scale", "Escala visual analógica discretizada"),
    variants: [
      v("Q3_V1", L("How much musculoskeletal stiffness or discomfort do you feel today?", "¿Qué grado de rigidez o molestia musculoesquelética sientes hoy?"), NONE, L("Severe", "Severa")),
      v("Q3_V2", L("How much resistance or lack of elasticity do you notice in your ankles and back when you start moving?", "¿Cuánta resistencia o falta de elasticidad notas en tobillos y espalda al iniciar el movimiento?"), NONE, A_LOT),
      v("Q3_V3", L("How loose and light do your joints feel today?", "¿Con qué soltura y ligereza sientes tus articulaciones hoy?"), L("Very stiff", "Muy rígidas"), L("Completely fluid", "Totalmente fluidas"), true),
    ],
    amber: 3,
    red: 4,
    triggers: [],
  },

  // --- Block B: bank, two questions a day according to triggers ---
  {
    id: "LOC-01",
    block: "bank",
    domain: L("Achilles tendon and soleus", "Tendón de Aquiles y sóleo"),
    capacityId: "musculoskeletal",
    scale: S_1_5,
    inspiredBy: same("VISA-A"),
    variants: [
      v("LOC-01_V1", L("How much stiffness or tightness do you notice in your Achilles tendon when you start moving?", "¿Cuánta rigidez o tirantez notas en el tendón de Aquiles al iniciar el movimiento?"), NOT_AT_ALL, A_LOT),
      v("LOC-01_V2", L("Do you feel a lack of elasticity or overload in your lower calf when you put your foot down?", "¿Sientes falta de elasticidad o sobrecarga en la parte baja del gemelo al apoyar el pie?"), NOT_AT_ALL, A_LOT),
    ],
    amber: 3,
    red: 4,
    triggers: [trig({ kind: "filler", priority: 2, reason: FILLER_REASON })],
  },
  {
    id: "LOC-02",
    block: "bank",
    domain: L("Lumbar spine", "Columna lumbar"),
    capacityId: "musculoskeletal",
    scale: S_1_5,
    inspiredBy: L("Oswestry (ODI)", "Oswestry (ODI)"),
    variants: [
      v("LOC-02_V1", L("Do you feel pressure or low back pain when bending your trunk or stretching?", "¿Sientes presión o dolor lumbar bajo al realizar flexión de tronco o estirarte?"), NOT_AT_ALL, A_LOT_M),
      v("LOC-02_V2", L("Do you notice tension or postural fatigue in your lower back when standing upright?", "¿Notas tensión o fatiga postural en la zona baja de la espalda al mantenerte erguido?"), NOT_AT_ALL, A_LOT),
    ],
    amber: 3,
    red: 4,
    triggers: [trig({ kind: "micro-days", min: 7, priority: 6, reason: L("You have spent 7 or more consecutive flight days in microgravity.", "Llevas 7 o más días de vuelo seguidos en microgravedad.") })],
  },
  {
    id: "LOC-03",
    block: "bank",
    domain: L("Upper limb after an EVA", "Extremidad superior tras una EVA"),
    capacityId: "musculoskeletal",
    scale: S_1_5,
    inspiredBy: L("Nordic Musculoskeletal Questionnaire (NMQ)", "Cuestionario Nórdico (NMQ)"),
    variants: [
      v("LOC-03_V1", L("Do you notice forearm fatigue or shoulder joint stiffness from the pressurized suit?", "¿Percibes fatiga en antebrazos o rigidez articular en hombros por el traje presurizado?"), NOT_AT_ALL, A_LOT),
      v("LOC-03_V2", L("Do you notice loss of grip strength or overload in your wrists and shoulders after the activity?", "¿Notas pérdida de fuerza de agarre o sobrecarga en muñecas y hombros tras la actividad?"), NOT_AT_ALL, A_LOT),
    ],
    amber: 3,
    red: 4,
    triggers: [trig({ kind: "event", eventKind: "eva", withinDays: 1, priority: 6, reason: L("There was an EVA in the last flight day.", "Hubo una EVA en el último día de vuelo.") })],
  },
  {
    id: "LOC-04",
    block: "bank",
    domain: L("Acute lower-body fatigue", "Fatiga aguda del tren inferior"),
    capacityId: "musculoskeletal",
    scale: S_1_5,
    inspiredBy: same("LEFS"),
    variants: [
      v("LOC-04_V1", L("Do you feel muscle heaviness or fatigue in your thighs and glutes during resistive exercise?", "¿Sientes pesadez muscular o fatiga en muslos y glúteos durante los ejercicios resistivos?"), NOT_AT_ALL, A_LOT),
      v("LOC-04_V2", L("How much localized fatigue do you feel in your legs during pushing movements?", "¿Cuánta fatiga localizada percibes en las piernas al realizar movimientos de empuje?"), NOT_AT_ALL, A_LOT),
    ],
    amber: 3,
    red: 4,
    // The document triggers it with blood CK or a drop in squat power:
    // neither is available to the interface.
    triggers: [],
  },
  {
    id: "LOC-05",
    block: "bank",
    domain: L("Muscle atrophy and loss of strength", "Atrofia y pérdida de fuerza muscular"),
    capacityId: "musculoskeletal",
    scale: S_1_5,
    inspiredBy: L("MRC scale / NASA HRP", "Escala MRC / NASA HRP"),
    variants: [
      v("LOC-05_V1", L("Do you find it hard to move resistive loads you lifted without trouble last week?", "¿Notas dificultad para mover cargas resistivas que la semana pasada levantabas sin problema?"), NOT_AT_ALL, A_LOT),
      v("LOC-05_V2", L("Do you notice a loss of firmness or a feeling of weakness in your legs and trunk when stabilizing?", "¿Percibes pérdida de firmeza o sensación de debilidad en piernas y tronco al estabilizarte?"), NOT_AT_ALL, A_LOT),
    ],
    amber: 3,
    red: 4,
    triggers: [
      trig({ kind: "signal", signalId: "emg_fatigue", op: ">=", value: 20, priority: 9, reason: L("The garment records high muscle fatigue.", "La prenda registra fatiga muscular elevada.") }),
      trig({
        kind: "transition",
        toGravity: ["mars"],
        daysBefore: 3,
        daysAfter: 0,
        minDaysInMicro: 150,
        priority: 9,
        reason: L("Arrival on Mars after a long transit in microgravity.", "Llegada a Marte tras un tránsito largo en microgravedad."),
      }),
    ],
  },
  {
    id: "VES-01",
    block: "bank",
    domain: L("Vestibulo-ocular reflex", "Reflejo vestíbulo-ocular"),
    capacityId: "vestibular",
    scale: S_0_2,
    inspiredBy: same("Graybiel / DHI"),
    variants: [
      v("VES-01_V1", L("When you turn your head quickly from side to side, do objects take time to settle?", "Al girar rápido la cabeza de lado a lado, ¿notas que los objetos tardan en estabilizarse?"), NO, CLEARLY),
      v("VES-01_V2", L("Do you notice visual bounce or brief disorientation when you shift your gaze quickly?", "¿Percibes sensación de rebote visual o desorientación breve al cambiar rápido de mirada?"), NO, CLEARLY),
    ],
    amber: 1,
    red: 2,
    triggers: [
      trig({ kind: "transition", toGravity: [], daysBefore: 3, daysAfter: 3, priority: 9, reason: L("You are 3 flight days or less from a gravity change.", "Estás a 3 días de vuelo o menos de un cambio de gravedad.") }),
      trig({ kind: "filler", priority: 1, reason: FILLER_REASON }),
    ],
  },
  {
    id: "VES-02",
    block: "bank",
    domain: L("Orthostatic intolerance", "Intolerancia ortostática"),
    capacityId: "cardiovascular",
    scale: S_0_3,
    inspiredBy: L("NASA HRP — orthostatic risk", "NASA HRP — riesgo ortostático"),
    variants: [
      v("VES-02_V1", L("Have you felt lightheaded or had tunnel vision when standing up or changing position?", "¿Has sentido aturdimiento o visión en túnel al incorporarte o cambiar de posición?"), NO, SEVERE),
      v("VES-02_V2", L("Do you notice mild dizziness or unsteadiness when moving to an upright position?", "¿Notas sensación de mareo leve o inestabilidad al pasar a posición vertical?"), NO, SEVERE_F),
    ],
    amber: 1,
    red: 2,
    triggers: [
      trig({ kind: "transition", toGravity: [...LANDING_TO], daysBefore: 3, daysAfter: 0, priority: 10, reason: L("3 flight days or less to a landing.", "Faltan 3 días de vuelo o menos para un aterrizaje.") }),
      trig({ kind: "signal", signalId: "resting_hr_delta", op: ">=", value: 12, priority: 7, reason: L("Your resting HR is 12 bpm or more above your baseline.", "Tu FC en reposo está 12 lpm o más por encima de tu referencia.") }),
    ],
  },
  {
    id: "VES-03",
    block: "bank",
    domain: L("Balance and plantar proprioception", "Equilibrio y propiocepción plantar"),
    capacityId: "vestibular",
    scale: S_1_5,
    inspiredBy: L("ABC scale", "Escala ABC"),
    variants: [
      v("VES-03_V1", L("How much insecurity or instability do you notice in the soles of your feet when walking?", "¿Cuánta inseguridad o falta de estabilidad notas en la planta de los pies al caminar?"), NOT_AT_ALL, A_LOT),
      v("VES-03_V2", L("Do you find it hard to keep your balance walking in a straight line without support?", "¿Sientes dificultad para mantener el equilibrio en línea recta sin apoyarte?"), NOT_AT_ALL, A_LOT),
    ],
    amber: 3,
    red: 4,
    triggers: [trig({ kind: "environment", gravity: [...LANDING_TO], priority: 8, reason: L("You are in an environment with gravity.", "Estás en un entorno con gravedad.") })],
  },
  {
    id: "SAN-01",
    block: "bank",
    domain: L("Visual acuity", "Agudeza visual"),
    capacityId: "vision",
    scale: S_0_3,
    inspiredBy: L("SANS guidelines (NASA)", "Guías SANS (NASA)"),
    variants: [
      v("SAN-01_V1", L("Is it harder today to focus on small text at reading distance?", "¿Notas mayor dificultad hoy para enfocar texto pequeño a distancia de lectura?"), NO, A_LOT),
      v("SAN-01_V2", L("Do you notice blurred near vision or eye strain when reading cabin screens?", "¿Percibes visión borrosa cercana o fatiga ocular al leer pantallas de cabina?"), NO, A_LOT),
    ],
    amber: 2,
    red: 3,
    triggers: [trig({ kind: "transition", toGravity: ["mars"], daysBefore: 3, daysAfter: 0, priority: 9, reason: L("3 flight days or less to landing on Mars.", "Faltan 3 días de vuelo o menos para aterrizar en Marte.") })],
  },
  {
    id: "CIR-01",
    block: "bank",
    domain: L("Sleep quality", "Calidad del sueño"),
    capacityId: "fatigue",
    scale: S_1_5,
    inspiredBy: same("KSS / PSQI"),
    variants: [
      v("CIR-01_V1", L("How unrefreshing did your night's rest feel?", "¿Qué tan poco reparador sentiste tu descanso nocturno?"), L("Optimal", "Óptimo"), L("Very fragmented", "Muy fragmentado")),
      v("CIR-01_V2", L("How often did you wake up or sleep lightly during your last rest period?", "¿Con qué frecuencia te despertaste o tuviste sueño ligero durante el último descanso?"), L("Never", "Nunca"), L("Very often", "Muy a menudo")),
    ],
    amber: 4,
    red: null,
    triggers: [trig({ kind: "signal", signalId: "night_hrv_delta", op: "<=", value: -15, priority: 8, reason: L("Your nighttime heart rate variability has dropped 15 % or more.", "Tu variabilidad cardiaca nocturna ha caído un 15 % o más.") })],
  },
  {
    id: "CAR-01",
    block: "bank",
    domain: L("Cardiac and autonomic tolerance", "Tolerancia cardiaca y autonómica"),
    capacityId: "cardiovascular",
    scale: S_0_3,
    inspiredBy: L("NASA HRP — cardiovascular risk", "NASA HRP — riesgo cardiovascular"),
    variants: [
      v("CAR-01_V1", L("Have you noticed palpitations, irregular heartbeats or unusual shortness of breath today?", "¿Has percibido palpitaciones, latidos irregulares o falta de aire inusual hoy?"), NO, L("Severe", "Intensas")),
      v("CAR-01_V2", L("Does your heart take longer than usual to settle after physical exertion?", "¿Notas que el corazón tarda más de lo habitual en calmarse tras un esfuerzo físico?"), NO, A_LOT_M),
    ],
    amber: 1,
    red: 2,
    triggers: [
      trig({ kind: "signal", signalId: "ecg_arrhythmia", priority: 10, reason: L("The garment has recorded irregular heartbeats.", "La prenda ha registrado latidos irregulares.") }),
      trig({ kind: "signal", signalId: "slow_hr_recovery", priority: 10, reason: L("Your HR took longer than usual to come down after exercise.", "Tu FC tardó más de lo habitual en bajar tras el ejercicio.") }),
    ],
  },
  {
    id: "MET-01",
    block: "bank",
    domain: L("Hydration and metabolic stress", "Hidratación y estrés metabólico"),
    capacityId: "cardiovascular",
    scale: S_1_5,
    inspiredBy: L("NASA — nutrition", "NASA — nutrición"),
    variants: [
      v("MET-01_V1", L("Have you felt persistent thirst, dry mouth or loss of appetite in the last few hours?", "¿Sientes sed persistente, sequedad bucal o inapetencia en las últimas horas?"), NOT_AT_ALL, A_LOT),
      v("MET-01_V2", L("Do you notice signs of dehydration or accumulated physical strain with no apparent cause?", "¿Notas signos de deshidratación o tensión física acumulada sin causa aparente?"), NOT_AT_ALL, L("Many", "Muchos")),
    ],
    amber: 3,
    red: null,
    // The document triggers it with blood hematocrit or cortisol: not available.
    triggers: [],
  },
];

/** Same text in every language (instrument names and acronyms). */
function same(text: string): LocalizedText {
  return L(text, text);
}

export const seedQuestionnaire: Questionnaire = {
  catalogItemId: "F",
  name: L(
    "Daily fatigue, rest and symptoms questionnaire",
    "Cuestionario diario de fatiga, descanso y síntomas",
  ),
  source: QUESTIONNAIRE_SOURCE,
  fixedPerDay: 3,
  bankPerDay: 2,
  items,
  consistencyChecks: [
    {
      id: "fatigue-vs-hrv",
      questionId: "Q1_FATIGUE",
      answerAtMost: 2,
      signalId: "night_hrv_delta",
      op: "<=",
      value: -18,
      message: L(
        "You report feeling alert, but your nighttime heart rate variability has dropped. The sensor data is taken into account.",
        "Indicas estar despejado, pero tu variabilidad cardiaca nocturna ha caído. Se tiene en cuenta el dato del sensor.",
      ),
    },
  ],
};
