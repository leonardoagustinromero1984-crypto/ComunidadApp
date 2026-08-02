# M26 — Auditoría inicial

Bloque 1 implementa una base local para inteligencia asistida: matching visual, duplicados, asistencia stub y recomendaciones evaluadas.

- Persistencia: memoria determinista; no hay SQL ni wiring Supabase en Bloque 1.
- Integración M04: la asistencia es stub; no reemplaza moderación ni soporte administrativo.
- Integración M24: explícitamente excluida (sin pagos).
- Privacidad: las proyecciones públicas no incluyen IDs internos, propietario ni PII; los scores se exponen sin datos personales.
