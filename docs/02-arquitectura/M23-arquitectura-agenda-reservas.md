# M23 — Arquitectura agenda y reservas

## Capas

```
UI (m23/*) → ViewModels → Repositories (mock | Supabase B2 RPC)
                              ↓
                    M23BookingOperationsService
                    M23SlotGenerator
                    M23PrivacySanitizer
```

## Autoridades

- **M22:** providerId, offeringId, branchId opcional, duración comercial.
- **M01:** customerUserId solo en capa interna; RPC deriva de `auth.uid()`.
- **M03:** permisos `booking.view`, `booking.manage` (Bloque 2 SQL).

## Slots

No tabla de slots infinita. Proyección acotada desde reglas + excepciones − reservas activas.

## Persistencia Bloque 2

`068_m23_scheduling_availability_and_bookings.sql` crea reglas, excepciones, reservas e historial internos. RLS niega acceso directo a `anon` y `authenticated`; los repositorios Android consumen RPC `SECURITY DEFINER` con `search_path = public`.

La migración 068–069 está **aplicada en staging**. M23 cerrado oficialmente. M24 en preauditoría.

## Operaciones Bloque 3

- Transiciones: REQUESTED → CONFIRMED/REJECTED/EXPIRED; CONFIRMED → COMPLETED/NO_SHOW/CANCELLED; reprogramación atómica mock.
- Filtros cliente/prestador vía `M23BookingFilters`.
- Integraciones: M20 conversación contextual; M21 elegibilidad post-COMPLETED; M06 notificaciones best-effort.

## Concurrencia (Bloque 2)

Creación atómica en RPC: validar slot → lock lógico → insert → historial.

`client_request_id` es único por cliente para idempotencia. No existe tabla infinita de slots y no hay columnas de pago.

## Privacidad

DTOs públicos sin IDs internos de usuario/organización.

## Rutas

`m23/home`, `m23/availability/{offeringId}`, `m23/bookings`, `m23/manage/*`

Entrada: Comunidad → Agenda; M22 detalle → Reservar.
