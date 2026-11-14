import type {
  Capacity,
  Questionnaire,
  QuestionItem,
  QuestionScale,
  QuestionTrigger,
  QuestionVariant,
  Signal,
} from "./types";

// Autoevaluación diaria del astronauta: capacidades con semáforo, señales
// objetivas y cuestionario. Preguntas, escalas, rotación y disparadores salen
// del documento del equipo «ARTEMIS-MED — Lógica médica, cuestionario y
// semáforo» (Ingeniería Biomédica A), adaptados a AURA:
// - Sirve para cualquier misión (Luna, Marte, estación): los disparadores usan
//   entornos gravitatorios y días de vuelo, no fechas de una misión concreta.
//   Las ventanas de 72 h del documento se expresan como 3 días de vuelo.
// - Solo se usan señales que la interfaz puede tener: el reloj (real), la
//   prueba de coordinación (real) y la ropa inteligente (simulada en la demo).
//   Los disparadores que dependen de cámara, analítica, miometría o tonometría
//   no se incluyen.
// - Todos los umbrales son de demostración, no criterios clínicos.

export const QUESTIONNAIRE_SOURCE =
  "ARTEMIS-MED — Lógica médica, cuestionario y semáforo (equipo, Ingeniería Biomédica A)";

const ON_ROJO =
  "No decide por su cuenta: remite al procedimiento de misión, avisa a control de misión y no propone carga intensa.";

export const seedCapacities: Capacity[] = [
  {
    id: "fatiga",
    name: "Fatiga y recuperación",
    description: "Fatiga operativa, reserva mental y calidad del descanso.",
    onAmbar:
      "Sustituye el ejercicio intenso por uno de menor carga del catálogo o lo omite, y protege la recuperación.",
    onRojo: ON_ROJO,
  },
  {
    id: "musculoesqueletico",
    name: "Fuerza y sistema musculoesquelético",
    description: "Rigidez, molestias, fatiga localizada y pérdida de fuerza.",
    onAmbar:
      "Sustituye el ejercicio resistivo por otro de menor carga (bandas, movilidad) o lo omite. Nunca añade carga.",
    onRojo: ON_ROJO,
  },
  {
    id: "vestibular",
    name: "Equilibrio y adaptación vestibular",
    description: "Estabilidad de la mirada, desorientación y equilibrio, sobre todo cerca de un cambio de gravedad.",
    onAmbar:
      "Avisa y prioriza las actividades de preparación de la transición ya planificadas. No crea ejercicios nuevos.",
    onRojo: ON_ROJO,
  },
  {
    id: "cardiovascular",
    name: "Tolerancia cardiovascular y ortostática",
    description: "Mareo al incorporarse, palpitaciones, recuperación tras el esfuerzo e hidratación.",
    onAmbar:
      "Cambia al menú con refuerzo de hidratación y sustituye el ejercicio intenso por uno de menor carga.",
    onRojo: ON_ROJO,
  },
  {
    id: "vision",
    name: "Visión y síntomas cefálicos",
    description: "Pesadez craneal, presión ocular y dificultad para enfocar.",
    onAmbar:
      "Sustituye el ejercicio resistivo intenso (con maniobra de Valsalva) por uno de menor carga y avisa.",
    onRojo: ON_ROJO,
  },
  {
    id: "coordinacion",
    name: "Coordinación visomotora",
    description: "Resultado de la prueba táctil de coordinación (experimental) respecto a la referencia propia.",
    onAmbar: "Pide repetir la prueba y no propone actividades que aumenten la carga.",
    onRojo: ON_ROJO,
  },
];

export const seedSignals: Signal[] = [
  {
    id: "fc_reposo_delta",
    name: "Frecuencia cardiaca en reposo respecto a la referencia propia",
    unit: "lpm",
    source: "reloj",
    simulated: false,
    capacityId: "cardiovascular",
    kind: "number",
    worseWhen: "mayor",
    ambar: 10,
    rojo: 15,
    description: "Diferencia entre la FC en reposo de hoy y la referencia del propio astronauta.",
  },
  {
    id: "recuperacion_fc_lenta",
    name: "Recuperación lenta de la FC tras el ejercicio",
    unit: "",
    source: "reloj",
    simulated: false,
    capacityId: "cardiovascular",
    kind: "boolean",
    whenTrue: "ambar",
    description: "La FC tarda más de lo habitual en bajar tras una sesión de ejercicio.",
  },
  {
    id: "hrv_nocturna_delta",
    name: "Variabilidad cardiaca nocturna (RMSSD) respecto a la referencia propia",
    unit: "%",
    source: "ropa",
    simulated: true,
    capacityId: "fatiga",
    kind: "number",
    worseWhen: "menor",
    ambar: -15,
    rojo: -25,
    description: "Variación porcentual del RMSSD durante el sueño.",
  },
  {
    id: "fr_delta",
    name: "Frecuencia respiratoria respecto a la referencia propia",
    unit: "%",
    source: "ropa",
    simulated: true,
    capacityId: "cardiovascular",
    kind: "number",
    worseWhen: "mayor",
    ambar: 25,
    rojo: null,
    description: "Variación porcentual de la frecuencia respiratoria en reposo.",
  },
  {
    id: "spo2",
    name: "Saturación de oxígeno",
    unit: "%",
    source: "ropa",
    simulated: true,
    capacityId: "cardiovascular",
    kind: "number",
    worseWhen: "menor",
    ambar: null,
    rojo: 92,
    description: "SpO2 en reposo.",
  },
  {
    id: "arritmia_ecg",
    name: "Extrasístoles o ritmo irregular en el ECG",
    unit: "",
    source: "ropa",
    simulated: true,
    capacityId: "cardiovascular",
    kind: "boolean",
    whenTrue: "ambar",
    description: "El ECG de la prenda registra latidos irregulares en las últimas 24 h.",
  },
  {
    id: "fatiga_emg",
    name: "Fatiga muscular (sEMG)",
    unit: "%",
    source: "ropa",
    simulated: true,
    capacityId: "musculoesqueletico",
    kind: "number",
    worseWhen: "mayor",
    ambar: 20,
    rojo: null,
    description: "Indicador de fatiga muscular de la electromiografía de superficie de la prenda.",
  },
  {
    id: "coordinacion_delta",
    name: "Empeoramiento en la prueba de coordinación respecto a la referencia propia",
    unit: "%",
    source: "prueba",
    simulated: false,
    capacityId: "coordinacion",
    kind: "number",
    worseWhen: "mayor",
    ambar: 15,
    rojo: 30,
    description: "Empeoramiento combinado de errores y latencia en la prueba táctil (código C).",
  },
];

// ---------- Cuestionario ----------

const v = (
  id: string,
  text: string,
  minLabel: string,
  maxLabel: string,
  inverted = false,
): QuestionVariant => ({ id, text, minLabel, maxLabel, inverted });

const S_0_2: QuestionScale = { min: 0, max: 2 };
const S_0_3: QuestionScale = { min: 0, max: 3 };
const S_1_5: QuestionScale = { min: 1, max: 5 };
const S_1_7: QuestionScale = { min: 1, max: 7 };

/** Ventana alrededor de un aterrizaje o regreso a una superficie con gravedad. */
const LANDING_TO = ["tierra", "luna", "marte"] as const;

const trig = (t: QuestionTrigger) => t;

const items: QuestionItem[] = [
  // --- Bloque A: núcleo fijo, una variante por día en rotación ---
  {
    id: "Q1_FATIGA",
    block: "fijo",
    domain: "Fatiga operativa y reserva mental",
    capacityId: "fatiga",
    scale: S_1_7,
    inspiredBy: "Samn-Perelli (USAF)",
    variants: [
      v("Q1_V1", "¿Cuál es tu nivel de fatiga operativa ahora mismo?", "Totalmente despejado", "Agotamiento extremo"),
      v("Q1_V2", "¿Cuánto esfuerzo mental extra necesitas hoy para mantener la concentración en tareas de precisión?", "Ninguno", "Extremo"),
      v("Q1_V3", "¿Qué nivel de agilidad mental y reserva de energía sientes hoy?", "Mínima", "Máxima", true),
    ],
    ambar: 4,
    rojo: 6,
    triggers: [],
  },
  {
    id: "Q2_SANS",
    block: "fijo",
    domain: "Redistribución de fluidos hacia la cabeza",
    capacityId: "vision",
    scale: S_0_3,
    inspiredBy: "Cribado de síntomas SANS (NASA)",
    variants: [
      v("Q2_V1", "¿Percibes pesadez craneal o dolor de cabeza pulsátil hoy?", "No", "Intenso"),
      v("Q2_V2", "¿Notas mayor dificultad hoy para enfocar con nitidez los indicadores pequeños de la consola?", "No", "Mucha"),
      v("Q2_V3", "¿Sientes congestión sinusal o presión detrás de los ojos similar a estar inclinado hacia abajo?", "No", "Intensa"),
    ],
    ambar: 2,
    rojo: 3,
    triggers: [],
  },
  {
    id: "Q3_MUSCULAR",
    block: "fijo",
    domain: "Integridad musculoesquelética global",
    capacityId: "musculoesqueletico",
    scale: S_1_5,
    inspiredBy: "Escala visual analógica discretizada",
    variants: [
      v("Q3_V1", "¿Qué grado de rigidez o molestia musculoesquelética sientes hoy?", "Ninguna", "Severa"),
      v("Q3_V2", "¿Cuánta resistencia o falta de elasticidad notas en tobillos y espalda al iniciar el movimiento?", "Ninguna", "Mucha"),
      v("Q3_V3", "¿Con qué soltura y ligereza sientes tus articulaciones hoy?", "Muy rígidas", "Totalmente fluidas", true),
    ],
    ambar: 3,
    rojo: 4,
    triggers: [],
  },

  // --- Bloque B: banco, dos preguntas al día según disparadores ---
  {
    id: "LOC-01",
    block: "banco",
    domain: "Tendón de Aquiles y sóleo",
    capacityId: "musculoesqueletico",
    scale: S_1_5,
    inspiredBy: "VISA-A",
    variants: [
      v("LOC-01_V1", "¿Cuánta rigidez o tirantez notas en el tendón de Aquiles al iniciar el movimiento?", "Nada", "Mucha"),
      v("LOC-01_V2", "¿Sientes falta de elasticidad o sobrecarga en la parte baja del gemelo al apoyar el pie?", "Nada", "Mucha"),
    ],
    ambar: 3,
    rojo: 4,
    triggers: [trig({ kind: "relleno", priority: 2, reason: "Pregunta de relleno cuando no hay otras disparadas." })],
  },
  {
    id: "LOC-02",
    block: "banco",
    domain: "Columna lumbar",
    capacityId: "musculoesqueletico",
    scale: S_1_5,
    inspiredBy: "Oswestry (ODI)",
    variants: [
      v("LOC-02_V1", "¿Sientes presión o dolor lumbar bajo al realizar flexión de tronco o estirarte?", "Nada", "Mucho"),
      v("LOC-02_V2", "¿Notas tensión o fatiga postural en la zona baja de la espalda al mantenerte erguido?", "Nada", "Mucha"),
    ],
    ambar: 3,
    rojo: 4,
    triggers: [trig({ kind: "dias-micro", min: 7, priority: 6, reason: "Llevas 7 o más días de vuelo seguidos en microgravedad." })],
  },
  {
    id: "LOC-03",
    block: "banco",
    domain: "Extremidad superior tras una EVA",
    capacityId: "musculoesqueletico",
    scale: S_1_5,
    inspiredBy: "Cuestionario Nórdico (NMQ)",
    variants: [
      v("LOC-03_V1", "¿Percibes fatiga en antebrazos o rigidez articular en hombros por el traje presurizado?", "Nada", "Mucha"),
      v("LOC-03_V2", "¿Notas pérdida de fuerza de agarre o sobrecarga en muñecas y hombros tras la actividad?", "Nada", "Mucha"),
    ],
    ambar: 3,
    rojo: 4,
    triggers: [trig({ kind: "evento", eventKind: "eva", withinDays: 1, priority: 6, reason: "Hubo una EVA en el último día de vuelo." })],
  },
  {
    id: "LOC-04",
    block: "banco",
    domain: "Fatiga aguda del tren inferior",
    capacityId: "musculoesqueletico",
    scale: S_1_5,
    inspiredBy: "LEFS",
    variants: [
      v("LOC-04_V1", "¿Sientes pesadez muscular o fatiga en muslos y glúteos durante los ejercicios resistivos?", "Nada", "Mucha"),
      v("LOC-04_V2", "¿Cuánta fatiga localizada percibes en las piernas al realizar movimientos de empuje?", "Nada", "Mucha"),
    ],
    ambar: 3,
    rojo: 4,
    // El documento la dispara con CK en sangre o caída de potencia en sentadilla:
    // ninguno está disponible en la interfaz.
    triggers: [],
  },
  {
    id: "LOC-05",
    block: "banco",
    domain: "Atrofia y pérdida de fuerza muscular",
    capacityId: "musculoesqueletico",
    scale: S_1_5,
    inspiredBy: "Escala MRC / NASA HRP",
    variants: [
      v("LOC-05_V1", "¿Notas dificultad para mover cargas resistivas que la semana pasada levantabas sin problema?", "Nada", "Mucha"),
      v("LOC-05_V2", "¿Percibes pérdida de firmeza o sensación de debilidad en piernas y tronco al estabilizarte?", "Nada", "Mucha"),
    ],
    ambar: 3,
    rojo: 4,
    triggers: [
      trig({ kind: "senal", signalId: "fatiga_emg", op: ">=", value: 20, priority: 9, reason: "La prenda registra fatiga muscular elevada." }),
      trig({
        kind: "transicion",
        toGravity: ["marte"],
        daysBefore: 3,
        daysAfter: 0,
        minDaysInMicro: 150,
        priority: 9,
        reason: "Llegada a Marte tras un tránsito largo en microgravedad.",
      }),
    ],
  },
  {
    id: "VES-01",
    block: "banco",
    domain: "Reflejo vestíbulo-ocular",
    capacityId: "vestibular",
    scale: S_0_2,
    inspiredBy: "Graybiel / DHI",
    variants: [
      v("VES-01_V1", "Al girar rápido la cabeza de lado a lado, ¿notas que los objetos tardan en estabilizarse?", "No", "Claramente"),
      v("VES-01_V2", "¿Percibes sensación de rebote visual o desorientación breve al cambiar rápido de mirada?", "No", "Claramente"),
    ],
    ambar: 1,
    rojo: 2,
    triggers: [
      trig({ kind: "transicion", toGravity: [], daysBefore: 3, daysAfter: 3, priority: 9, reason: "Estás a 3 días de vuelo o menos de un cambio de gravedad." }),
      trig({ kind: "relleno", priority: 1, reason: "Pregunta de relleno cuando no hay otras disparadas." }),
    ],
  },
  {
    id: "VES-02",
    block: "banco",
    domain: "Intolerancia ortostática",
    capacityId: "cardiovascular",
    scale: S_0_3,
    inspiredBy: "NASA HRP — riesgo ortostático",
    variants: [
      v("VES-02_V1", "¿Has sentido aturdimiento o visión en túnel al incorporarte o cambiar de posición?", "No", "Intenso"),
      v("VES-02_V2", "¿Notas sensación de mareo leve o inestabilidad al pasar a posición vertical?", "No", "Intensa"),
    ],
    ambar: 1,
    rojo: 2,
    triggers: [
      trig({ kind: "transicion", toGravity: [...LANDING_TO], daysBefore: 3, daysAfter: 0, priority: 10, reason: "Faltan 3 días de vuelo o menos para un aterrizaje." }),
      trig({ kind: "senal", signalId: "fc_reposo_delta", op: ">=", value: 12, priority: 7, reason: "Tu FC en reposo está 12 lpm o más por encima de tu referencia." }),
    ],
  },
  {
    id: "VES-03",
    block: "banco",
    domain: "Equilibrio y propiocepción plantar",
    capacityId: "vestibular",
    scale: S_1_5,
    inspiredBy: "Escala ABC",
    variants: [
      v("VES-03_V1", "¿Cuánta inseguridad o falta de estabilidad notas en la planta de los pies al caminar?", "Nada", "Mucha"),
      v("VES-03_V2", "¿Sientes dificultad para mantener el equilibrio en línea recta sin apoyarte?", "Nada", "Mucha"),
    ],
    ambar: 3,
    rojo: 4,
    triggers: [trig({ kind: "entorno", gravity: [...LANDING_TO], priority: 8, reason: "Estás en un entorno con gravedad." })],
  },
  {
    id: "SAN-01",
    block: "banco",
    domain: "Agudeza visual",
    capacityId: "vision",
    scale: S_0_3,
    inspiredBy: "Guías SANS (NASA)",
    variants: [
      v("SAN-01_V1", "¿Notas mayor dificultad hoy para enfocar texto pequeño a distancia de lectura?", "No", "Mucha"),
      v("SAN-01_V2", "¿Percibes visión borrosa cercana o fatiga ocular al leer pantallas de cabina?", "No", "Mucha"),
    ],
    ambar: 2,
    rojo: 3,
    triggers: [trig({ kind: "transicion", toGravity: ["marte"], daysBefore: 3, daysAfter: 0, priority: 9, reason: "Faltan 3 días de vuelo o menos para aterrizar en Marte." })],
  },
  {
    id: "CIR-01",
    block: "banco",
    domain: "Calidad del sueño",
    capacityId: "fatiga",
    scale: S_1_5,
    inspiredBy: "KSS / PSQI",
    variants: [
      v("CIR-01_V1", "¿Qué tan poco reparador sentiste tu descanso nocturno?", "Óptimo", "Muy fragmentado"),
      v("CIR-01_V2", "¿Con qué frecuencia te despertaste o tuviste sueño ligero durante el último descanso?", "Nunca", "Muy a menudo"),
    ],
    ambar: 4,
    rojo: null,
    triggers: [trig({ kind: "senal", signalId: "hrv_nocturna_delta", op: "<=", value: -15, priority: 8, reason: "Tu variabilidad cardiaca nocturna ha caído un 15 % o más." })],
  },
  {
    id: "CAR-01",
    block: "banco",
    domain: "Tolerancia cardiaca y autonómica",
    capacityId: "cardiovascular",
    scale: S_0_3,
    inspiredBy: "NASA HRP — riesgo cardiovascular",
    variants: [
      v("CAR-01_V1", "¿Has percibido palpitaciones, latidos irregulares o falta de aire inusual hoy?", "No", "Intensas"),
      v("CAR-01_V2", "¿Notas que el corazón tarda más de lo habitual en calmarse tras un esfuerzo físico?", "No", "Mucho"),
    ],
    ambar: 1,
    rojo: 2,
    triggers: [
      trig({ kind: "senal", signalId: "arritmia_ecg", priority: 10, reason: "La prenda ha registrado latidos irregulares." }),
      trig({ kind: "senal", signalId: "recuperacion_fc_lenta", priority: 10, reason: "Tu FC tardó más de lo habitual en bajar tras el ejercicio." }),
    ],
  },
  {
    id: "MET-01",
    block: "banco",
    domain: "Hidratación y estrés metabólico",
    capacityId: "cardiovascular",
    scale: S_1_5,
    inspiredBy: "NASA — nutrición",
    variants: [
      v("MET-01_V1", "¿Sientes sed persistente, sequedad bucal o inapetencia en las últimas horas?", "Nada", "Mucha"),
      v("MET-01_V2", "¿Notas signos de deshidratación o tensión física acumulada sin causa aparente?", "Nada", "Muchos"),
    ],
    ambar: 3,
    rojo: null,
    // El documento la dispara con hematocrito o cortisol en sangre: no disponibles.
    triggers: [],
  },
];

export const seedQuestionnaire: Questionnaire = {
  catalogItemId: "F",
  name: "Cuestionario diario de fatiga, descanso y síntomas",
  source: QUESTIONNAIRE_SOURCE,
  fixedPerDay: 3,
  bankPerDay: 2,
  items,
  consistencyChecks: [
    {
      id: "fatiga-vs-hrv",
      questionId: "Q1_FATIGA",
      answerAtMost: 2,
      signalId: "hrv_nocturna_delta",
      op: "<=",
      value: -18,
      message:
        "Indicas estar despejado, pero tu variabilidad cardiaca nocturna ha caído. Se tiene en cuenta el dato del sensor.",
    },
  ],
};
