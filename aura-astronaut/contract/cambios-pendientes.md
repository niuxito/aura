# Cambios pendientes del contrato `aura.mission-package/v5`

Datos que la interfaz del astronauta necesita y el paquete no trae. Mientras no estén, la app **no los inventa**: usa la solución provisional indicada y lo deja claro en pantalla. Cada cambio exige tocar también AURA Manager (`lib/types.ts`, `lib/package.ts`) y regenerar `mission-package.example.json`.

**2026-11-15:** los puntos 1, 2, 3, 4, 6 (capacidades críticas) y 7 quedan resueltos con campos **opcionales** del v5, sin cambiar el esquema: un paquete que no los traiga sigue siendo válido y la app vuelve a su solución provisional. El Manager rellena los que falten en un estado guardado con los valores de la misión demo, sin tocar lo que control de misión ya haya fijado.

| # | Qué faltaba | Estado | Campo |
| --- | --- | --- | --- |
| 1 | MET exacto de cada transición gravitatoria | **Resuelto.** La cuenta atrás usa `transitionMet`; sin él, el hito del inicio o fin del día. | `days[].transitionMet` (s) y `transitionMetLabel`. |
| 2 | Duración o fin de cada actividad | **Resuelto.** «En curso» hasta el fin previsto; sin duración, la ventana de demostración de 30 min. Si la interfaz sustituye la acción, manda la duración de la sustituta. | `catalog[].durationMin` (estimación del equipo) y `activities[].endMet` / `endMetLabel`. |
| 3 | De qué depende una actividad condicional | **Resuelto.** Todas las condicionales de la misión demo traen `note`; el Manager avisa (`conditional-without-note`) si falta. | `activities[].note`. |
| 4 | Qué alternativa usar en cada capacidad en ámbar | **Resuelto.** Con cardiovascular en ámbar, el menú del día pasa a N2 (refuerzo de hidratación). Solo en ámbar, misma categoría y sin subir la carga; el Manager valida las referencias. | `capacities[].onAmberActions: [{ category, replacementItemId }]`. |
| 5 | Canal de alertas a control de misión | **Resuelto el 2026-11-14:** `POST/GET/PATCH /api/alerts`. | — |
| 6a | Capacidades críticas de cada transición | **Resuelto.** Tolerancia cardiovascular/ortostática y equilibrio, críticas al llegar a Tierra, Luna o Marte. Sin el campo, la lista provisional y la etiqueta «provisionales». | `capacities[].criticalFor: Gravity[]`. |
| 6b | Dosis mínima de ejercicio | **Pendiente.** Solo se cuentan sesiones planificadas frente a hechas. | Propuesta: `exercisePolicy.minSessionsPerDays` o similar. |
| 7 | Aeróbico de la víspera de la reentrada | **Resuelto en el plan demo:** el aeróbico de FD09 pasa al bloque de ejercicio de FD08 (06/22:00). La regla de la víspera crítica sigue activa por si control de misión vuelve a ponerlo ahí. | — |
| 8 | FC en reposo de referencia del tripulante | **Resuelto.** Base del indicador `resting_hr_delta` con el reloj; valor de demostración. Sin él, 62 lpm de demostración. | `crew[].restingHrBpm`. |
