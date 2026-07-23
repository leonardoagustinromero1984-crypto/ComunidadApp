# M10 — Persistencia hogares de tránsito

## Migración

`supabase/migrations/040_m10_foster_homes_core.sql`

Forward-only. **No modifica** 001–039. Legacy `foster_homes` / `foster_requests` permanecen.

**No aplicada remotamente.**

## Tablas

| Tabla | Rol |
|-------|-----|
| `foster_home_profiles` | Perfil, capacidad, ocupación, reservas, especies/tamaños, zona |
| `foster_care_requests` | Solicitudes M10 (nombre distinto al legacy `foster_requests`) |
| `foster_placements` | Reserva / ingreso activo |

Unicidad: un perfil no-`CLOSED` por owner; una solicitud activa por (hogar, mascota); un placement RESERVED/ACTIVE por mascota.

## RLS

Lectura acotada. Writes directos denegados (`with check (false)` / `using (false)`). Mutaciones vía RPC `m10_*`.

## RPC

Perfiles: create/update/availability/status/list/get/my.  
Solicitudes: submit/cancel/under_review/accept/reject/list sent|received/get.  
Ingreso: `m10_start_foster_placement` (transaccional + `TEMPORARY_CUSTODIAN`).

## Privacidad del listado

La proyección pública del cliente omite `private_address_text`. RLS de perfiles ACTIVE no expone campos internos adicionales vía RPC de listado, pero el owner sí puede leer su fila completa.
