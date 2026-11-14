# AURA Manager

Consola de control de misión de **AURA (Astronaut Unified Readiness & Autonomous-health)**, proyecto para NASA Space Apps Challenge 2026.

A partir del plan de misión, permite preparar el plan de prevención de cada astronauta (ejercicios, evaluaciones, suplementos y recuperación) para cada día, con especial atención a los cambios de gravedad, y publicar un paquete de misión versionado que consume la interfaz del astronauta (`../aura-astronaut`).

## Arrancar

```bash
npm install
npm run dev
```

Abre http://localhost:3000. La primera vez se crea `data/state.json` con la misión demo.

## Tiempo de misión: MET

Todos los tiempos son **MET (Mission Elapsed Time)**: tiempo transcurrido desde el despegue, escrito `DD/HH:MM` o `DD/HH:MM:SS` como en los cronogramas de NASA. Internamente se guardan en segundos (`lib/met.ts`). La misión se divide en **días de vuelo** (`FD01`, `FD02`…) con sus límites en MET, y cada actividad de prevención se programa en un MET; su día de vuelo se deduce de él.

## Misión demo: Artemis II

Cronograma **planificado** de Artemis II, del «Artemis II Overview Timeline» de NASA (18/03/2026, STRIVES 20260002249). En `lib/seed.ts`:

- **Del documento:** los 10 días de vuelo, las fases y los eventos con su MET.
- **Leído de la gráfica, aproximado (±15 min):** los bloques de actividad de la tripulación (sueño, comidas, ejercicio, maniobras, pruebas).
- **Ejemplo del equipo:** la carga operativa de cada día, la tripulación de demostración y todo el plan de prevención. No son datos ni prescripciones de NASA.

## Vistas

- **Plan de misión:** días de vuelo con su MET, fases, entorno gravitatorio, transiciones, carga operativa (CO) y eventos.
- **Plan de prevención:** cronograma del día de vuelo sobre un eje MET y actividades de cada tripulante con su MET.
- **Catálogo:** acciones asignables. La herramienta no inventa dosis; se publican como se registren.
- **Cuestionario:** cuestionario diario del astronauta (bloque fijo y banco con disparadores), capacidades con semáforo y señales objetivas. Solo lectura por ahora; se edita en `lib/questionnaire.ts`.
- **Publicar:** validación del plan y publicación del paquete.
- **Adaptaciones:** registro de los cambios que la interfaz del astronauta ha hecho por su cuenta, para validarlos u objetarlos.

## Fijo y adaptable

La interfaz del astronauta puede adaptar por sí misma una parte acotada del plan. Control de misión decide cuál:

- **Adaptable:** los ejercicios, dentro de los bloques de ejercicio del cronograma, y los menús, en los bloques de comida (`ADAPTABLE_WINDOW` en `lib/types.ts`). Cada actividad de esas categorías lleva un interruptor Adaptable/Fija.
- **Fijo:** todo lo demás: maniobras, pruebas, sueño, operaciones, evaluaciones, preparación del retorno y suplementos.

El paquete publica la política (`adaptationPolicy`) y marca cada bloque y cada actividad con `adaptable`. La interfaz puede sustituir una actividad adaptable por otra de la misma categoría sin aumentar la carga, moverla dentro de su periodo u omitirla.

Cada adaptación se aplica a bordo sin esperar respuesta y se envía a `POST /api/adaptations`. El servidor la registra siempre, la comprueba contra la política y señala las que se salen de ella; control de misión la valida o la objeta en la pestaña Adaptaciones. `GET /api/adaptations?crewId=` devuelve el registro con el resultado de la revisión. Se guardan en `data/adaptations.json`.

## Cuestionario y semáforo (`lib/questionnaire.ts`)

Control de misión define y publica en el paquete lo que la interfaz usa para evaluar al astronauta:

- **Capacidades** con semáforo (verde, ámbar, rojo y gris por falta de datos) y qué hace la interfaz en ámbar (una adaptación permitida) y en rojo (remitir y avisar).
- **Señales** objetivas: reloj (real), prueba de coordinación (real) y ropa inteligente (**simulada** en la demo).
- **Cuestionario diario:** 3 preguntas fijas con redacción rotativa y 2 del banco, elegidas por disparadores (cambio de gravedad, entorno, días en microgravedad, EVA, señales). Procede del documento del equipo «ARTEMIS-MED», adaptado a los principios de AURA: no diagnostica, no certifica aptitud y no usa cámara, analítica ni miometría.

No depende de la misión: los disparadores usan entornos gravitatorios y días de vuelo, así que sirve igual para la Luna, Marte o una estación. Todos los umbrales son de demostración (`thresholdsAreDemo: true`).

## Reglas de validación (`lib/rules.ts`)

| Regla | Gravedad |
| --- | --- |
| Carga exigente autorizada en un día de CO crítica | Bloqueo (impide publicar) |
| Carga exigente en la víspera de un día crítico | Aviso |
| Día posterior a uno crítico sin ventana de recuperación | Aviso |
| Transición a un entorno con gravedad sin preparación en los 2 días de vuelo previos | Aviso |
| Actividad programada dentro de un periodo de sueño o fuera de la misión | Aviso |
| Astronauta sin plan en un día de vuelo | Aviso |
| Día sin cuestionario de fatiga, suplementos sin dosis, actividades condicionales | Informativo |
| Cuestionario incoherente (capacidad, señal o acción inexistente, umbrales fuera de escala) | Bloqueo |
| Pregunta del banco sin disparadores disponibles | Informativo |

Son reglas de diseño del demostrador, no criterios clínicos.

## Paquete de misión

- `GET /api/package`: último paquete publicado.
- `GET /api/package?version=N`: una versión concreta.
- `GET /api/package?list=1`: resumen de versiones.

El formato (`aura.mission-package/v4`) está definido en `lib/package.ts`. Los paquetes se guardan en `data/packages/`.

## Datos

Persistencia local en ficheros JSON dentro de `data/` (fuera de git). Sin autenticación ni nube: está pensada para un único usuario en el demostrador.
