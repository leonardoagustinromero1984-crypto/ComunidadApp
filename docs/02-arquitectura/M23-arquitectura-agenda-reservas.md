# M23 — Arquitectura agenda y reservas

## Capas

```
UI (m23/*) → ViewModels → Repositories (mock | Supabase B2)
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

## Concurrencia (Bloque 2)

Creación atómica en RPC: validar slot → lock lógico → insert → historial.

## Privacidad

DTOs públicos sin IDs internos de usuario/organización.

## Rutas

`m23/home`, `m23/availability/{offeringId}`, `m23/bookings`, `m23/manage/*`

Entrada: Comunidad → Agenda; M22 detalle → Reservar.
