# AURA Astronaut — interfaz del astronauta

Instrucciones para el agente que desarrolle en esta carpeta. Léelas enteras antes de escribir código.

## Qué es AURA

**AURA (Astronaut Unified Readiness & Autonomous-health)** es el proyecto del equipo para NASA Space Apps Challenge 2026, reto «Create Health Monitoring Software for Astronauts on Space Missions». Su objetivo es ayudar a la tripulación a conservar las capacidades físicas (fuerza, coordinación, equilibrio, tolerancia al esfuerzo) que necesita para cada día de la misión y, sobre todo, para los **cambios de gravedad**: aterrizar, salir de la nave y regresar a la Tierra.

Tiene dos piezas:

| Pieza | Carpeta | Papel |
| --- | --- | --- |
| **AURA Manager** | `../aura-manager` | Consola web de control de misión. Define el plan de misión y el plan de prevención (ejercicios, evaluaciones, suplementos, recuperación) y publica un **paquete de misión** versionado. Ya existe una primera versión. |
| **AURA Astronaut** | esta carpeta | Interfaz del astronauta: un agente que recoge datos y le muestra, en realidad aumentada, lo que necesita saber y hacer hoy. **Por construir.** |

**AURA no es solo para Artemis II.** Debe servir para misiones futuras a la Luna, a Marte y a estaciones espaciales. Artemis II (misión ya realizada) es el caso con el que se valida y se hace la demo. No escribas nada atado a esa misión: todo lo que dependa de ella (días de vuelo, entornos gravitatorios, transiciones, eventos, cuestionario, umbrales) viene en el paquete.

### Objetivo final y prioridades

Todo lo que hace la interfaz sirve a una meta: que el astronauta **pueda completar la misión** y **llegue a cada cambio de gravedad en condiciones de salir de la nave por su propio pie**. La interfaz nunca afirma que lo conseguirá (no certifica aptitud): vigila los indicadores que lo hacen probable, protege las medidas que lo preparan y avisa a tiempo cuando algo se desvía.

- **Riesgos críticos de cada transición.** En la misión demo (Artemis II, unos 10 días en microgravedad) los que más comprometen la salida al amerizar son la **intolerancia ortostática** (mareo o desmayo al ponerse en pie) y la **alteración del equilibrio** (agravada por el balanceo en el agua). La pérdida de fuerza y hueso es pequeña en un vuelo tan corto; en misiones largas (Marte) pasa a primer plano.
- **Medidas de prevención del plan** para esos riesgos: carga de líquidos y sal antes de la reentrada (`SF`, sin dosis: la fija el protocolo médico), prenda contra la intolerancia ortostática (`OIG`), ejercicio aeróbico (`A`) y preparación del retorno (`V`). Las actividades con `transitionPrep: true` y las que preparan la transición no se pierden de vista.
- **Las reglas protegen el día sin sacrificar la transición.** Quitar carga protege al astronauta hoy, pero cada sesión perdida resta preparación. Por eso:
  - lleva la cuenta de lo planificado frente a lo hecho (incluido lo que la interfaz ha sustituido u omitido);
  - antes que omitir, prefiere reprogramar dentro de la política;
  - si una regla va a quitar una medida que prepara la transición (por ejemplo, el aeróbico de la víspera de la reentrada), lo señala y avisa a control de misión en vez de darlo por resuelto; nunca sube la carga por su cuenta.
- **Preparación para la transición en el HUD.** A medida que se acerca un cambio de gravedad, un panel reúne: cuenta atrás, estado y **tendencia por días** de las capacidades críticas de esa transición frente a la referencia propia, y cumplimiento de las medidas de preparación. Habla de «indicadores para la salida», nunca de «apto».
- **Lo que viene del paquete:** qué capacidades son críticas para cada llegada (`capacities[].criticalFor`). Si un paquete no lo trae, la interfaz usa lo indicado aquí para la misión demo y lo etiqueta como provisional. La dosis mínima de ejercicio sigue pendiente del contrato: hasta entonces solo se cuentan sesiones planificadas frente a hechas.

La especificación funcional completa está en Notion, en la página «Space Apps Challenge 2026» y su subpágina «Mars Ready — Requisitos funcionales y diseño del monitor» (versiones 0.3 y 0.4). Si tienes acceso a Notion, consúltala; en caso de duda entre Notion y este fichero sobre el alcance de la demo, manda este fichero.

## Qué hay que construir

Una app **Android** para la demo del hackathon:

- La pantalla principal es la **imagen de la cámara trasera** del móvil a pantalla completa, con la información superpuesta encima (HUD). Eso es lo que llamamos «realidad aumentada» en la demo: no hace falta anclar objetos 3D al mundo ni detectar planos.
- La app se comporta como un **agente**: carga el paquete de misión, sabe en qué MET y en qué día de vuelo está el astronauta, le presenta el plan del día, recoge sus datos y le avisa de desviaciones con explicación.
- Todos los tiempos se expresan en **MET (Mission Elapsed Time)**, el tiempo transcurrido desde el despegue, con el formato `DD/HH:MM` o `DD/HH:MM:SS` que usan los cronogramas de NASA (por ejemplo, TLI en `01/01:08:42`). Los días son **días de vuelo** (`FD01`, `FD02`…), que no coinciden con el día natural: sus límites en MET vienen en el paquete.
- Funciona **sin conexión**. La red solo se usa, si está disponible, para descargar un paquete nuevo.

### Qué muestra el HUD

1. **Contexto de misión:** reloj MET, día de vuelo, fase, hito del día, entorno gravitatorio y carga operativa (CO).
2. **Transición gravitatoria:** si hoy o en los próximos días de vuelo hay un cambio de gravedad (campo `transitionFrom`), aviso destacado con la cuenta atrás en MET, por ejemplo «Amerizaje en 01/06:00 · microgravedad → 1 g».
3. **Plan del día:** actividades asignadas al astronauta ordenadas por MET, con su código, nombre, estado (`authorized` o `conditional`) y si están hechas. Junto a ellas, lo que el cronograma de misión tiene previsto alrededor (sueño, comidas, maniobras), para que el plan se entienda en contexto.
4. **Estado por capacidad:** semáforo verde, ámbar, rojo o **gris**, siempre con texto y motivo.
5. **Siguiente acción recomendada** y su justificación.

Mantén el HUD legible sobre cualquier fondo de cámara: paneles semitransparentes, alto contraste, texto grande, pocos elementos a la vez. Evalúa el uso con una mano y con guantes.

### Avisos por voz

La interfaz comunica por voz, además de en pantalla, la información importante, para que el astronauta la reciba aunque no esté mirando el móvil.

- **Motor:** el `TextToSpeech` del propio Android, en español y **sin conexión**. Al arrancar, comprueba que hay una voz en español que no exige red; si falta, avísalo en pantalla y sigue funcionando solo con el HUD.
- **Qué se dice:** solo los mensajes importantes, una vez cada uno. Son la transición gravitatoria próxima, la siguiente acción recomendada al cambiar, las adaptaciones aplicadas con su motivo, los avisos de control de misión del día y el resultado de la validación de una adaptación. Nunca se lee el HUD entero ni se repite un mensaje ya dicho.
- **Nada que no esté en pantalla:** la voz lee el mismo texto que muestra el HUD en ese momento. Las reglas del HUD también valen para la voz: no diagnostica, dice el motivo y avisa de que los datos simulados son simulados.
- **Silencio:** un control de silencio siempre visible y fácil de pulsar con guantes. La preferencia se guarda entre sesiones. Lo que se dice en voz alta lo oye quien esté cerca.
- **Sin interferir:** pide el foco de audio mientras habla y no corta un mensaje más importante por otro menos importante.

El texto de cada aviso se genera en el mismo sitio que el del HUD; la voz no tiene textos propios.

### Qué datos recoge

- Confirmación de cada actividad del plan (hecha, omitida, motivo).
- Cuestionario diario de fatiga, descanso y síntomas (código `F` del catálogo). Sus preguntas, su selección y sus umbrales vienen en el paquete (sección «Cuestionario diario y semáforo»).
- Señales de la ropa inteligente (variabilidad cardiaca nocturna, respiración, SpO2, ECG, fatiga muscular). En la demo no hay prenda: se **simulan**, siempre etiquetadas.
- Registro de ejercicio: tipo, carga o repeticiones, esfuerzo percibido.
- Prueba táctil de coordinación visomotora (código `C`): aciertos, errores y latencia por intento. Es una prueba experimental del equipo, no un test validado por NASA, y debe decirlo en pantalla.
- Confirmación de toma de suplementos.

- Frecuencia cardiaca en vivo desde un reloj por Bluetooth (sección «Frecuencia cardiaca desde el reloj»).

Cada observación guarda valor, unidad, fecha, origen (`manual`, `sensor` o `simulated`) y calidad.

### Frecuencia cardiaca desde el reloj

La interfaz lee la frecuencia cardiaca de un reloj que la retransmite con el **perfil estándar de Bluetooth LE**, como una banda de pecho. Comprobado con un **Garmin Forerunner 745**: con «Transmitir frecuencia cardiaca» activado en el reloj, anuncia el servicio Heart Rate. No se usa ningún SDK ni app de Garmin, ni ANT+.

- **Protocolo:** servicio Heart Rate `0x180D`, característica Heart Rate Measurement `0x2A37` por notificaciones. Lee el byte de flags: el bit 0 indica si el valor va en `uint8` o `uint16`, y el bit 4 si vienen intervalos RR (en 1/1024 s). Los RR son opcionales: úsalos si llegan, sin contar con ellos.
- **Emparejamiento:** busca solo dispositivos que anuncien `0x180D`, deja que el astronauta elija el suyo y guarda su dirección para reconectar sola al arrancar y tras una desconexión. Si el reloj no aparece, el HUD explica cómo activar la retransmisión en el reloj.
- **Permisos:** en Android 12 o superior, `BLUETOOTH_SCAN` (con `neverForLocation`) y `BLUETOOTH_CONNECT`; en versiones anteriores, los de ubicación que exija el escaneo. Gestiona el permiso denegado y el Bluetooth apagado sin romper la app.
- **Sin conexión:** funciona en modo avión con el Bluetooth activado. No necesita red ni la app Garmin Connect.
- **Observaciones:** se guardan con origen `sensor`, unidad `lpm` y su MET. No guardes cada notificación: agrega (por ejemplo, una muestra por intervalo) con la frecuencia definida en el módulo de reglas.
- **En el HUD:** valor en vivo, estado de la conexión y antigüedad del último dato. Sin reloj conectado o con el dato caducado, se muestra **gris** («sin datos del reloj»), nunca el último valor como si fuera actual.
- **En las reglas:** la frecuencia cardiaca es un indicador más y puede ir como evidencia de una adaptación (`evidence[].source: "sensor"`). Cualquier referencia (por ejemplo, la frecuencia en reposo propia del astronauta) y sus umbrales son de demostración y se etiquetan así. No se diagnostica nada a partir de ella.
- **Sin reloj:** la demo debe poder enseñarse sin reloj. Separa la fuente de frecuencia cardiaca detrás de una interfaz, con una implementación Bluetooth y otra simulada para los escenarios de demo; la simulada guarda origen `simulated` y el HUD lo indica.
- **Verificación:** el emulador no tiene Bluetooth LE real. Prueba la lectura y el formato con datos simulados y pruebas unitarias del parseo de `0x2A37`, y la conexión real en un móvil con el reloj.

### Cuestionario diario y semáforo

Lo define control de misión en AURA Manager (pestaña Cuestionario) y viaja en el paquete: `capacities`, `signals` y `questionnaire`. La interfaz **no lleva preguntas, umbrales ni capacidades escritos en el código**; los lee del paquete y aplica el algoritmo de esta sección. Procede del documento del equipo «ARTEMIS-MED — Lógica médica, cuestionario y semáforo», adaptado a los principios de AURA.

**Selección de las preguntas del día** (cada vez que el plan programa la actividad `questionnaire.catalogItemId`):

1. **Bloque fijo:** las preguntas con `block: "fixed"` (hoy, 3), en el orden del paquete. De cada una se muestra la variante `(día de vuelo + índice del tripulante en crew[] + índice de la pregunta) % número de variantes`.
2. **Banco:** de las preguntas con `block: "bank"`, se reúnen los disparadores que se cumplen hoy, se ordenan por `priority` descendente y se toman las `bankPerDay` primeras preguntas distintas. Si no llegan, se completan con los disparadores `filler` por prioridad. Variante: `día de vuelo % número de variantes`.
3. Disparadores (`triggers[].kind`), siempre por días de vuelo:
   - `transition`: hoy está entre `daysBefore` días de vuelo antes y `daysAfter` después de un día de vuelo con `transitionFrom`. Si `toGravity` no está vacío, solo cuentan las transiciones hacia esos entornos (el `gravity` de ese día). `minDaysInMicro`: solo si antes de la transición se llevan al menos esos días de vuelo seguidos en microgravedad.
   - `environment`: el `gravity` del día de vuelo de hoy está en la lista.
   - `micro-days`: días de vuelo seguidos en microgravedad hasta hoy, incluido, ≥ `min`.
   - `event`: hay un evento de ese `kind` (por ejemplo, `eva`) en los últimos `withinDays` días de vuelo.
   - `signal`: la última medida de esa señal cumple `op value`; si la señal es booleana, que sea verdadera. Sin medida, no dispara.
4. Cada pregunta mostrada puede explicar por qué se pregunta hoy con el `reason` de su disparador.

**Textos:** el texto de la variante no incluye la escala. Muéstrala con `scale.min`/`scale.max` y los rótulos `minLabel`/`maxLabel` de la variante (y dila así en los avisos por voz). Respuesta con un toque, apta para guantes.

**Normalización:** si la variante tiene `inverted: true`, el valor se guarda normalizado como `(max + min) − respuesta`. En todo el sistema, alto = peor. Guarda la respuesta original, la normalizada, la variante y el MET.

**Semáforo por capacidad** (`capacities[]`; hoy son 6, entre ellas coordinación, que sale de la prueba `C`):

- Cada pregunta respondida da un nivel: `red` si el valor normalizado ≥ `red`, `amber` si ≥ `amber`, `green` si no (umbral `null` = no aplica). Cada señal, igual con `amber`/`red` en el sentido de `worseWhen` (`higher`: valor ≥ umbral; `lower`: valor ≤ umbral), o con `whenTrue` si es booleana.
- El nivel de la capacidad es **el peor** de sus preguntas y señales del día. Un valor alto cuenta siempre por sí solo, aunque falten otros datos: nunca se rebaja a verde por no tener la analítica o la cámara que pedía el documento original.
- **Gris** si la capacidad no tiene ningún dato del día. Nunca verde por defecto.
- `amber` → adaptación permitida por `adaptationPolicy`, guiada por `capacities[].onAmber`. `red` → `capacities[].onRed`: remite al procedimiento de misión y avisa; no decide por su cuenta.
- Cada estado dice qué pregunta o señal lo causa, con el valor y el umbral, y que los umbrales son de demostración (`thresholdsAreDemo: true`).

**Coherencia** (`questionnaire.consistencyChecks`): si la respuesta normalizada a `questionId` es ≤ `answerAtMost` y la señal cumple `op value`, muestra `message`. El nivel no baja: se usa el peor de los dos.

**Ropa inteligente simulada:** las señales con `simulated: true` no tienen dispositivo. Genéralas desde los escenarios de demo con valores verosímiles y coherentes entre sí. Guárdalas con origen `simulated` y muéstralas como «simulado» (en inglés, «simulated») en el HUD y en la evidencia de las adaptaciones (`evidence[].source: "simulated"`). Las de `source: "watch"` salen del reloj por Bluetooth cuando esté conectado (origen `sensor`), y las de `source: "test"`, de la prueba de coordinación. Las de `source: "garment"` son la ropa inteligente.

**Lo que no se hace**, aunque estuviera en el documento original: la alerta roja colectiva entre tripulantes, certificar «apto» o «no apto» para salir de la nave, diagnosticar, añadir actividades o dosis, y leer el estado de salud en la cara con la cámara.

### Silueta y actividad simulada

En la columna derecha del HUD gira una silueta humana en 3D, semitransparente, con la forma de la piel de BodyParts3D (DBCLS, CC BY-SA) tomada de anatomia.alexisparron.com y simplificada (`assets/models/body.bin`; atribución en el modo demo y en `assets/models/LICENSE-body.txt`).

- **Movimiento:** imita lo que registraría la ropa inteligente. Con el acelerómetro de la tablet se detectan pasos (caminar) y sentadillas (agacharse), y la silueta los reproduce de perfil. En el modo demo se puede forzar CAMINAR, AGACHARSE o QUIETO por si el sensor falla en directo. Los umbrales están en `motion/BodyMotion.kt` (`MotionRules`) y son de demostración.
- **Músculos:** se iluminan los grupos que trabajan (cuádriceps, glúteos e isquiotibiales, gemelos y abdomen) en un color propio, distinto del semáforo.
- **Corazón y aura:** dentro del pecho late un corazón al ritmo de la frecuencia cardiaca (reloj o simulada) y en cada latido sale un anillo de luz, el «aura». Sin dato reciente no late. Su color es propio, distinto del semáforo.
- **Es simulado y solo visual:** se etiqueta «SIM» junto a la silueta, no se guarda como observación, no se envía a control de misión y no alimenta el semáforo. La iluminación indica qué grupos trabajan, nunca fatiga, lesión ni estado de salud.

## Alcance de la primera versión

Hazlo en este orden y enseña cada paso funcionando antes de seguir:

1. Proyecto Android que compila, con vista de cámara a pantalla completa y permiso de cámara bien gestionado (incluido el caso de permiso denegado).
2. Carga del paquete de misión desde `assets` (copia de `contract/mission-package.example.json`) y selección de astronauta y de MET actual. Para la demo el MET se fija a mano (y puede avanzar acelerado); si el paquete trae `mission.launchUtc`, también puede calcularse con el reloj.
3. HUD con contexto de misión, transición gravitatoria y plan del día, con avisos por voz (sección «Avisos por voz») para la transición y la siguiente acción. Cada paso posterior añade a la voz sus propios avisos (adaptaciones, validación).
4. Recogida de datos: confirmar actividades y cuestionario diario leído del paquete (selección, normalización), con persistencia local que sobrevive a cerrar la app.
5. Estado por capacidad (sección «Cuestionario diario y semáforo», con las señales de la ropa simuladas por escenario) y avisos explicados, y adaptación del plan según la sección «Adaptación del plan por la interfaz»: decisión, aplicación local, cola de envío a control de misión y lectura del resultado de la validación.
5b. Preparación para la transición (sección «Objetivo final y prioridades»): panel con cuenta atrás, capacidades críticas y su tendencia por días, cumplimiento de las medidas de preparación y cuenta de lo planificado frente a lo hecho; las reglas avisan cuando una adaptación quita una medida de preparación.
6. Prueba táctil de coordinación.
7. Frecuencia cardiaca en vivo desde el reloj por Bluetooth (sección «Frecuencia cardiaca desde el reloj»), con su fuente simulada, su indicador en el HUD y su uso como evidencia en las reglas.
8. Descarga del paquete desde AURA Manager (`https://aura-manager-rouge.vercel.app`, o el Manager local en desarrollo).

Tres escenarios de demo, con datos simulados y etiquetados: perfil estable, fatiga acumulada tras un día de carga operativa crítica, y empeoramiento en la prueba de coordinación. Todo debe poder enseñarse en **modo avión**.

Además, la silueta 3D del HUD reproduce con el acelerómetro de la tablet el movimiento que registraría la ropa inteligente (sección «Silueta y actividad simulada»).

Queda fuera de esta versión: otros sensores externos reales (IMU corporal, equipos de ejercicio; del reloj solo entra la frecuencia cardiaca por Bluetooth, y la ropa inteligente solo simulada), la integración con Garmin Connect, Health Connect o los SDK de Garmin, reconocimiento de emociones por cámara, reconocimiento de voz (órdenes habladas; los avisos por voz sí entran) y envío de las observaciones en bruto a control de misión (solo se envían las adaptaciones, con su evidencia).

## Stack propuesto

- **Kotlin + Jetpack Compose**, una sola actividad.
- **CameraX** para la vista previa de cámara, con el HUD dibujado en Compose por encima.
- **Room** para las observaciones y el estado local; el paquete de misión se guarda tal cual como fichero JSON.
- **kotlinx.serialization** para leer el paquete.
- **`android.speech.tts.TextToSpeech`** para los avisos por voz, sin librerías externas.
- **API Bluetooth LE de Android** (`BluetoothLeScanner`, `BluetoothGatt`) para la frecuencia cardiaca del reloj, sin librerías externas.
- Sin servicios en la nube y sin dependencias que exijan conexión para funcionar.

Antes de fijar versiones de Gradle, AGP, Kotlin, Compose y CameraX, comprueba cuáles son las estables actuales y qué hay instalado en la máquina (Android Studio, SDK, JDK); no te fíes de versiones recordadas. Si el equipo prefiere otro stack, pregunta antes de cambiarlo.

## Contrato: el paquete de misión

AURA Manager publica un JSON con esquema `aura.mission-package/v5` (desde el 2026-11-15). La v5 tiene el mismo contenido que la v4, con dos cambios que rompen la compatibilidad:

1. **Códigos en inglés.** Todos los valores enumerados y algunos campos pasan al inglés; la tabla completa de equivalencias está en `contract/v4-to-v5.md`. Los identificadores de catálogo (`R`, `A`, `M`, `N2`, `SF`, `OIG`, `V`…) no cambian; los de capacidades, señales y preguntas sí (`fatiga` → `fatigue`, `fc_reposo_delta` → `resting_hr_delta`, `Q1_FATIGA` → `Q1_FATIGUE`…).
2. **Textos multiidioma.** Todo texto legible (nombres, descripciones, preguntas, rótulos de escala, motivos de los disparadores, mensajes de `validation[]`, reglas de la política…) es un objeto `LocalizedText`, por ejemplo `{ "en": "Sleep", "es": "Sueño" }`. El paquete declara `languages` (`["en", "es"]`) y `defaultLanguage` (`"en"`). Ver «Idiomas» más abajo.

**Acepta v5** (`SUPPORTED_SCHEMA`). Mientras dure la migración puedes seguir leyendo v4 y v3 (`LEGACY_SCHEMA`) si ya lo tienes hecho, pero no hace falta mantenerlo: en cuanto el Manager publique un paquete v5, es el que se descarga. Si se carga un paquete v3, no hay cuestionario: las capacidades quedan en gris y se avisa de que el paquete es antiguo.

- **Ejemplo real:** `contract/mission-package.example.json` (v5), generado por el Manager con la misión demo. El ejemplo v4 anterior queda en `contract/mission-package.v4.example.json` solo como referencia. Úsalo como dato de desarrollo y como paquete embebido en `assets`.
- **Misión demo:** el cronograma **planificado** de Artemis II (10 días de vuelo, de `00/00:00` al amerizaje en `09/01:42:48`), tomado del «Artemis II Overview Timeline» de NASA del 18/03/2026. Es un sobrevuelo lunar sin alunizaje: la tripulación está en microgravedad todo el vuelo y las únicas transiciones gravitatorias son el despegue y el regreso a la Tierra. Los eventos y sus MET son del documento; los bloques de actividad están leídos de su gráfica y son aproximados; la carga operativa, la tripulación (CDR, PLT, MS1, MS2, sin nombres reales) y todo el plan de prevención son un ejemplo del equipo, no datos de NASA.
- **MET en el paquete:** cada tiempo viaja dos veces, en segundos (`met`, `startMet`, `endMet`) para calcular y como etiqueta (`metLabel`, `startMetLabel`, `endMetLabel`) para mostrar. Calcula siempre con los segundos.
- **Definición de tipos:** `../aura-manager/lib/package.ts` y `../aura-manager/lib/types.ts`. Son la fuente de verdad; si cambian, actualiza el ejemplo y los modelos Kotlin.
- **Descarga:** `GET /api/package` devuelve el último publicado; `GET /api/package?version=N`, una versión concreta; `GET /api/package?list=1`, el resumen de versiones. Si todavía no hay ningún paquete publicado, `GET /api/package` responde `404` y `?list=1` devuelve `[]`: la app sigue con el paquete que ya tenga (el de `assets` o el último descargado).
- **Servidor:** AURA Manager está desplegado en **`https://aura-manager-rouge.vercel.app`** (HTTPS). Es la URL base por defecto para descargar paquetes, enviar adaptaciones y leer su revisión. Guárdala en un único sitio de configuración (por ejemplo, `BuildConfig`) y permite cambiarla en el modo demo. Para desarrollo con el Manager en local (`npm run dev` en `../aura-manager`, puerto 3000), desde el emulador el ordenador es `http://10.0.2.2:3000` y desde un móvil real, la IP del ordenador en la red local. Esas URLs locales son HTTP sin cifrar: permítelo solo para esos hosts en la configuración de seguridad de red y solo en la variante de depuración.

Campos principales:

| Campo | Contenido |
| --- | --- |
| `schema`, `packageVersion`, `publishedAt`, `checksum` | Identidad y versión del paquete. Muestra siempre la versión cargada. |
| `simulated` | `true` si la misión es ficticia. Si lo es, el HUD lo indica de forma permanente. |
| `schema`, `languages`, `defaultLanguage` | `languages`: idiomas en los que vienen los textos; `defaultLanguage`: idioma de respaldo (`en`). |
| `mission` | Nombre, destino, descripción (`LocalizedText`), `timelineSource` (documento del que sale el cronograma) y `launchUtc` (instante UTC de MET 0, o `null` si no está fijado). |
| `gravityReference` | Gravedad relativa a la Tierra: `earth` 1, `mars` 0,38, `moon` 0,165, `micro` 0. |
| `crew[]` | Tripulantes: `id`, `name`, `role` y `restingHrBpm` opcional (FC en reposo de referencia, de demostración). |
| `days[]` | Un elemento por día de vuelo: `day`, `label` (`FD03`), `startMet`, `endMet`, `phase`, `milestone`, `gravity` (entorno al acabar el día de vuelo), `transitionFrom` (solo si ese día hay transición), `transitionMet` opcional (MET exacto de la transición) y `opLoad` (antes `co`: `low`, `medium`, `high`, `critical`). Un MET pertenece al día cuyo intervalo `[startMet, endMet)` lo contiene. |
| `events[]` | Eventos puntuales del cronograma, ordenados por MET: `met`, `metLabel`, `day`, `name`, `kind` (`maneuver`, `milestone`, `test`, `operation`, `eva`) y `approx` (`true` si el cronograma lo da como aproximado; muéstralo con `~`). |
| `blocks[]` | Bloques de actividad de la tripulación: `startMet`, `endMet`, `name`, `adaptable`, `adaptableCategories`, `kind` (`sleep`, `presleep`, `meal`, `exercise`, `maneuver`, `test`, `operation`, `communication`, `health`, `off-duty`). Pueden cruzar el límite entre dos días de vuelo. Son aproximados. |
| `catalog[]` | Acciones de prevención. Claves: `id`, `code`, `name`, `category` (`exercise`, `nutrition`, `assessment`, `preparation`, `recovery`, `supplement`), `loadType` (`CL`, `RE`, `none`), `intense`, `transitionPrep`, `selfReport`, `record` (qué debe registrar el monitor), `limit` (límite de uso o interpretación, que hay que mostrar), `capabilities` (lista de `LocalizedText`), `dose` (`LocalizedText` o `null` = sin dosis prescrita), `source`, `experimental` y `durationMin` opcional (estimación del equipo). |
| `adaptationPolicy` | Qué puede adaptar la interfaz por su cuenta: `windows` (categoría adaptable → tipo de bloque en el que puede moverse), `allowed` (tipos de adaptación), `rules` (las reglas en texto) y `report` (dónde enviar cada adaptación; `blocking` es siempre `false`). |
| `plans` | Por `crewId`, una lista con un elemento por día de vuelo: `{ day, activities: [{ entryId, itemId, met, metLabel, status, adaptable, endMet?, endMetLabel?, note? }] }`, con las actividades ordenadas por MET. `itemId` apunta a `catalog[].id`; `entryId` es la referencia estable que citan las adaptaciones; `adaptable` dice si la interfaz puede cambiarla. |
| `capacities[]` | Capacidades con semáforo: `id`, `name`, `description`, `onAmber` y `onRed` (qué hace la interfaz en cada nivel), y opcionales `onAmberActions` (sustituciones concretas en ámbar, `[{ category, replacementItemId }]`) y `criticalFor` (entornos de llegada en los que es crítica para salir de la nave). |
| `signals[]` | Señales objetivas: `id`, `name`, `unit`, `source` (`watch`, `garment`, `test`), `simulated`, `capacityId`, `kind` (`number` o `boolean`) y sus umbrales (`worseWhen`: `higher`/`lower`, `amber`, `red` o `whenTrue`: `amber`/`red`). `unit` es un símbolo sin traducir (`bpm`, `%`). |
| `questionnaire` | Cuestionario diario: `catalogItemId`, `fixedPerDay`, `bankPerDay`, `items[]` (`id`, `block`, `domain`, `capacityId`, `scale`, `inspiredBy`, `variants[]` con `text`, `minLabel`, `maxLabel` e `inverted`, umbrales `amber`/`red` y `triggers[]`) y `consistencyChecks[]`. `block` es `fixed` o `bank`; `triggers[].kind` es `transition`, `environment`, `micro-days`, `event`, `signal` o `filler`. |
| `thresholdsAreDemo` | Siempre `true`: los umbrales de capacidades, señales y cuestionario son de demostración. |
| `validation[]` | Avisos e informaciones que control de misión dejó vigentes al publicar: `severity` (`warning` o `info`), `day`, `crewId`, `message` (`LocalizedText`), `rule`. Muéstralos en el día que corresponda. |

El `checksum` es el sha256 del JSON canónico (claves ordenadas, sin espacios) del paquete sin el campo `checksum`. Verificarlo en la app es deseable pero no bloquea la primera versión.

Si la app necesita algo que el paquete no trae, no lo inventes en el cliente: anótalo como cambio pendiente del contrato y avisa, porque hay que tocar también el Manager.

## Adaptación del plan por la interfaz

La interfaz toma decisiones sobre la salud del astronauta y **adapta ciertas partes del plan por sí misma**. Qué partes, lo decide control de misión y viene marcado en el paquete; la interfaz nunca lo deduce ni lo amplía.

### Qué es fijo y qué es adaptable

- **Fijo:** todo bloque con `adaptable: false` (maniobras, pruebas, sueño, operaciones, conferencias) y toda actividad con `adaptable: false` (cuestionarios, evaluaciones, preparación del retorno, suplementos). No se modifica desde la interfaz bajo ninguna circunstancia: una operación crucial para la misión no se toca.
- **Adaptable:** las actividades con `adaptable: true`. Hoy son los **ejercicios**, dentro de los bloques de ejercicio, y los **menús**, en los bloques de comida. Control de misión puede fijar una actividad concreta aunque su categoría sea adaptable; respeta siempre el campo, no la categoría.

Muestra en el HUD qué actividades son adaptables y cuáles fijas, y señala con claridad las que la interfaz ha cambiado respecto al plan original.

### Qué puede hacer la interfaz

Solo lo que permite `adaptationPolicy` (lee `rules` y `allowed` del paquete; este resumen no las sustituye):

1. **Sustitución:** cambiar una actividad adaptable por otra acción del catálogo de la **misma categoría**. Nunca por una con `intense: true` si la original no lo era: la interfaz no aumenta la carga.
2. **Reprogramación:** moverla a otro MET dentro de un bloque adaptable de su categoría y en el mismo día de vuelo.
3. **Omisión:** suprimirla, siempre con motivo.

No crea actividades nuevas ni cambia dosis.

### Cuándo decide

Las decisiones salen de un único módulo de reglas, a partir de los datos recogidos:

1. Si faltan datos, pide el dato o la prueba y muestra **gris**; no adapta a ciegas y nunca muestra verde por defecto.
2. Ante fatiga, mal sueño o peor rendimiento, sustituye el ejercicio por una alternativa de menor carga o lo omite, y puede cambiar el menú (por ejemplo, refuerzo de hidratación). Nunca aumenta la carga para compensar.
3. Si el siguiente día de vuelo tiene carga operativa `critical`, no propone actividades con `intense: true`.
4. Una actividad `conditional` no se presenta como obligatoria: se indica de qué depende.
5. Ante una señal de riesgo que no se resuelve adaptando ejercicio o dieta, no decide por su cuenta: remite al procedimiento de misión y avisa a control de misión.
6. Cada decisión dice qué indicador ha cambiado, respecto a qué referencia, con qué dato y en qué MET, y qué se ha cambiado.

Los umbrales de estas reglas son de demostración, no clínicos, y se etiquetan así.

### Envío a control de misión: registro y validación, sin bloquear

Toda adaptación se **aplica a bordo de inmediato** y además se envía a control de misión para que quede registrada y la validen. El envío **no es bloqueante**: la interfaz no espera respuesta para aplicar el cambio ni para seguir funcionando.

- Guarda cada adaptación en la base local con estado de envío (`pending`, `sent`) y envíala en segundo plano. Sin conexión, se queda en cola y se reintenta; la app debe funcionar igual en modo avión.
- Genera el `id` en el dispositivo (UUID). Reenviar el mismo `id` no duplica el registro, así que reintentar es seguro.
- `POST /api/adaptations` acepta un objeto o una lista y responde `202` con lo registrado. Solo responde `400` si el mensaje está mal formado.

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

`type` es `substitution` (lleva `replacementItemId`), `reschedule` (lleva `newMet`, en segundos) u `omission`. `reason` es obligatorio. `evidence[].source` es `manual`, `sensor` o `simulated`; `source` es `interface` o `simulated`. `reason`, `indicator` y `message` son texto libre: escríbelos en el idioma en que se los mostraste al astronauta. Durante la migración el servidor sigue aceptando los códigos v4 (`sustitucion`, `simulado`, `interfaz`…) y los convierte, pero responde siempre con los de v5.

- El servidor comprueba cada adaptación contra la política y la marca como dentro o fuera de ella (`policy.withinPolicy`, `policy.violations`), pero **no la rechaza**. Una adaptación fuera de política indica un fallo de la interfaz: no debería ocurrir, y hay pruebas unitarias para garantizarlo.
- Control de misión la valida o la objeta después. `GET /api/adaptations?crewId=cdr` devuelve el registro con `review.status` (`pending`, `validated`, `objected`) y `review.note`. `policy.violations[]` son `LocalizedText`. Muestra ese resultado al astronauta cuando llegue. Una objeción no deshace sola lo ya hecho: se muestra junto a la nota, y es control de misión quien cambia el plan publicando un paquete nuevo.

### Alertas a control de misión

Lo que la interfaz no resuelve adaptando se avisa a control de misión con `POST /api/alerts`, con las mismas garantías que las adaptaciones: se guarda en la base local con estado de envío, se envía en segundo plano sin bloquear, se reintenta sin conexión y el servidor responde `202` sin rechazarla por su contenido.

- **Cuándo** (una alerta por situación; `id` UUID derivado de tripulante y situación, así que reenviar no duplica):
  - `capacity-red` (`level: "red"`): una capacidad en rojo, una vez por capacidad y día de vuelo. Es el «avisa a control de misión» de la regla 5 y de `capacities[].onRed`.
  - `preparation-lost` (`level: "warning"`): una adaptación que quita una medida de preparación de la transición.
  - `inconsistency` (`level: "warning"`): el astronauta declara estar bien y el sensor indica lo contrario (`consistencyChecks`).
  - Lo que solo está en ámbar no se avisa aparte: la adaptación ya llega con su evidencia.
- **Forma:** `{ id, packageVersion, crewId, kind, level, capacityId?, entryId?, met, message, evidence[], source }`. `message` es el texto que vio el astronauta; `evidence[]` como en las adaptaciones.
- **Acuse:** `GET /api/alerts?crewId=` devuelve `ack.status` (`new`, `seen`, `handled`) y `ack.note`. El HUD lo muestra junto al aviso en rojo y la voz lo dice una vez. Control de misión lo marca en la pestaña Alertas del Manager (`PATCH /api/alerts`).
- Adaptaciones y alertas se sincronizan por separado: si un servidor aún no tiene `/api/alerts`, las adaptaciones siguen llegando.

## Idiomas

AURA es multiidioma. El **inglés** es el idioma de referencia (el de NASA) y el **español**, el segundo. La app debe funcionar entera en los dos:

- **Textos de la interfaz** (rótulos del HUD, botones, avisos fijos, la voz): en recursos de Android, `res/values/strings.xml` en inglés (por defecto) y `res/values-es/strings.xml` en español. Nada de textos escritos en el código Kotlin.
- **Idioma activo:** el del sistema si es uno de `languages` del paquete; si no, `defaultLanguage` (`en`). Permite cambiarlo en el modo demo (por ejemplo, con las preferencias de idioma por app de Android 13+, `AppCompatDelegate.setApplicationLocales`).
- **Textos del paquete:** todo `LocalizedText` se resuelve con una única función: el idioma activo; si falta, `defaultLanguage`; si también falta, el primero que haya. Nunca muestres el objeto ni un texto vacío si existe en otro idioma.
- **Lo que envía la app** (`reason`, `evidence[].indicator`, `message` de las alertas) es texto libre y viaja tal cual, en el idioma activo. Los códigos (`type`, `kind`, `level`, `source`…) son siempre los de v5 en inglés, sea cual sea el idioma.
- **Formatos:** el MET (`DD/HH:MM`) no se localiza; números y fechas, con el `Locale` activo.

## Principios no negociables

Vienen de la especificación y valen para toda la interfaz y todos los textos:

- La app **no diagnostica**, no certifica que alguien es «apto» y no muestra un porcentaje de salud ni un «100 % funcional».
- El color nunca va solo: siempre con texto y motivo. El gris significa «datos insuficientes».
- No se inventan umbrales clínicos ni dosis. Los umbrales del prototipo son reglas de demostración y se etiquetan así.
- Los datos simulados se etiquetan como simulados en pantalla y en la base de datos.
- La prueba táctil no equivale a la capacidad real de salir de una cápsula ni a destreza con guantes.
- Lo medido en microgravedad no predice por sí solo el rendimiento en la Luna, Marte o la Tierra.
- La cámara se usa como fondo del HUD. No se infieren emociones ni estado de salud a partir del rostro.
- Los datos de salud se quedan en el dispositivo.

## Convenciones

- Interfaz y textos en **español**. Identificadores de código en inglés.
- Nada de valores mágicos repartidos por la interfaz: las reglas y sus umbrales viven en un único módulo, con nombre y comentario de por qué.
- Pruebas unitarias para la lectura del paquete y para las reglas de adaptación, que son lo que no puede fallar en la demo. En particular: ninguna regla puede producir una adaptación sobre algo fijo ni fuera de `adaptationPolicy`.
- Comprueba cada hito en emulador o dispositivo antes de darlo por hecho, incluido el modo avión y el reinicio de la app.
- No hagas commits ni cambies `../aura-manager` sin que te lo pidan.

## Preguntas abiertas para el equipo

Pregunta cuando alguna te bloquee; mientras tanto, usa el valor por defecto indicado.

- **Dispositivo de la demo** y versión mínima de Android. Por defecto: un móvil reciente, orientación horizontal.
- **Orientación del HUD.** Por defecto: horizontal, como un visor.
- **Cómo se fija el MET actual en la demo.** Por defecto: selector manual de MET visible solo en un modo demo.
- **Qué capacidades llevan semáforo.** Resuelta: las de `capacities[]` del paquete. Si en el HUD no caben todas, muestra primero las que no están en verde.
- **Si las observaciones en bruto deben volver a control de misión** durante el hackathon, además de las adaptaciones. Por defecto: no.
