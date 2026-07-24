# M11 — Matriz funcional final (Refugios)

LeoVer. Cierre técnico M11 — cobertura por operación.

Leyenda: **M** modelo · **R** repo · **F** fake · **V** ViewModel · **U** UI · **P** permiso · **E** error · **T** test

## Bloque 1 — Operación

| Operación | M | R | F | V | U | P | E | T |
|-----------|---|---|---|---|---|---|---|---|
| Perfil refugio CRUD/estado | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.manage | ✓ | ✓ |
| Capacidad / disponibilidad | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| Ingreso / reserva / egreso mascota | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.pet.* | ✓ | ✓ |
| Voluntarios | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.volunteer.* | ✓ | ✓ |
| Dashboard | — | ✓ | ✓ | ✓ | ✓ | view | — | ✓ |
| Hook M09 adopción → cierre alojamiento | — | ✓ | ✓ | — | — | — | — | ✓ |
| Hook M10 FOSTER_RETURN | — | ✓ | ✓ | — | — | — | — | ✓ |

## Bloque 2 — Campañas e insumos

| Operación | M | R | F | V | U | P | E | T |
|-----------|---|---|---|---|---|---|---|---|
| Campañas PUBLIC/INTERNAL | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.campaign.* | ✓ | ✓ |
| Estados campaña | ✓ | ✓ | ✓ | ✓ | ✓ | manage | ✓ | ✓ |
| Novedades / evidencia M05 | ✓ | ✓ | ✓ | ✓ | ✓ | manage | ✓ | ✓ |
| Pedidos insumos + progreso | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.supply.* | ✓ | ✓ |
| Aportes / recepción | ✓ | ✓ | ✓ | ✓ | ✓ | contribution.* | ✓ | ✓ |
| Hardening grants 044 | — | SQL | — | — | — | — | — | ✓ |

## Bloque 3 — Urgencias, eventos, reportes

| Operación | M | R | F | V | U | P | E | T |
|-----------|---|---|---|---|---|---|---|---|
| Urgencias + severidad/resolución | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.emergency.* | ✓ | ✓ |
| Eventos gratuitos | ✓ | ✓ | ✓ | ✓ | ✓ | shelter.event.* | ✓ | ✓ |
| Cupos / waitlist / asistencia | ✓ | ✓ | ✓ | ✓ | ✓ | manage | ✓ | ✓ |
| Métricas agregadas | ✓ | ✓ | ✓ | ✓ | ✓ | report.read | ✓ | ✓ |
| Export CSV sin PII | ✓ | ✓ | ✓ | ✓ | ✓ | report.export | ✓ | ✓ |
| Listados públicos urgencias/eventos | ✓ | ✓ | ✓ | ✓ | ✓ | público | — | ✓ |

## Fuera de alcance M11 (otros módulos / externo)

| Ítem | Clasificación |
|------|----------------|
| Push real M06 | PENDIENTE_EXTERNO |
| Chat / reputación / IA | FUERA_DE_ALCANCE |
| Smoke UI manual en dispositivo | PENDIENTE_EXTERNO |
| RPC remota dedicada M09→placement (Supabase) | Criterio de reapertura (posible 046) |
| APK / promoción a producción | PENDIENTE_EXTERNO |
