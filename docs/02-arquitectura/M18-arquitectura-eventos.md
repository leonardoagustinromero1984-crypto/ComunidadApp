# M18 — Arquitectura eventos comunitarios

## Límites

M18 modela **eventos comunitarios gratuitos** de organizaciones autorizadas M03. No reemplaza M03 (organizaciones), M08 (mascotas), M16 (refugios) ni M24 (pagos).

## Relación con organizaciones

```text
Organization (M03) 1 — * M18CommunityEvent
Tipos elegibles: SHELTER, RESCUE_GROUP, NGO, TRAINING_CENTER
```

Un evento **nunca** crea organización, usuario, mascota ni refugio.

## Separación evento / inscripción / recordatorio

```text
M18CommunityEvent
        │
        ├── M18EventRegistration (interno: userId, attendeeDisplayName)
        │         └── estados: REGISTERED | WAITLISTED | CANCELLED | CHECKED_IN | ATTENDED | NO_SHOW | REJECTED
        │
        └── M18EventReminder (mock M06)
                  └── estados: SCHEDULED | SENT | SKIPPED
```

Bloque 1: mock local en `M18EventMemoryStore`. **Bloque 2:** persistencia Supabase vía RPC.

## Capas

```text
UI (M18EventScreens)
  → ViewModels (M18EventViewModels)
  → M18EventRepository (interface)
  → MockM18EventRepository | SupabaseM18EventRepository
  → M18EventMemoryStore (mock) | SupabaseM18RemoteDataSource (RPC)
  → M18EventValidators / M18CapacityCalculator / M18EventOperationsService
  → M18PrivacySanitizer → M18PublicEvent
```

## Modelo interno vs público

| Campo | Interno (`M18CommunityEvent`) | Público (`M18PublicEvent`) |
|-------|------------------------------|---------------------------|
| organizationId | Sí | No |
| createdBy | Sí | No |
| internalNotes | Sí | No |
| moderationStatus | Sí | No |
| userId inscripción | En registro interno | No |
| título, descripción, fechas | Sí | Sí (sanitizado) |
| cupos agregados | Calculado | Sí |

Toda lectura pública pasa por `M18PrivacySanitizer.toPublicEvent()`.

## Cupos

- Cálculo: `M18CapacityCalculator.summarize()`
- Solo `REGISTERED` y `CHECKED_IN` ocupan cupo
- Lista de espera opcional (`waitlistEnabled`)

## Transiciones de estado (evento)

```text
DRAFT → PUBLISHED → PAUSED ↔ PUBLISHED
PUBLISHED|PAUSED → COMPLETED | CANCELLED (terminales)
COMPLETED|CANCELLED → (sin reapertura)
```

Validación: `M18EventValidators.validateStateTransition()`.

## Check-in

Validación: `M18EventValidators.validateCheckIn()` — ventana temporal + estado `REGISTERED`.

## Permisos

Mock: `MockM18EventAuthorityPolicy` + `organizationManagers`.

Producción (B2): `event.view` / `event.manage` vía M03 membership + RPC `has_org_permission`.

## Errores

`M18Exception` + `M18EventErrorMapper.userMessage()` — sin filtrar rutas ni PII.

## Notificaciones

`M18M06Hooks` preparados; allowlist M06 **no ampliada** en Bloque 1.

## Estrategia Bloque 1 / 2

- **B1:** `M18EventMemoryStore` con 8 eventos seed + inscripciones variadas
- **B2:** migraciones `058` + `059` — **aplicadas staging**; validación 110/110 PASS
- `DataProvider.m18EventRepository` — mock o Supabase según `useSupabase`

## Rutas

```text
m18/events              — directorio
m18/events/{id}         — detalle + inscripción
m18/events/manage       — administración org
m18/events/create       — crear borrador
m18/events/{id}/edit    — editar
```

Entrada: Sumate → Eventos → "Eventos comunitarios (M18)".

## Dependencias

| Módulo | Relación |
|--------|----------|
| M03 | Propietario del evento |
| M06 | Recordatorios (mock) |
| M08/M16 | Referencias opcionales |
| M17 | Patrón Bloque 1 |

## Bloque 2–5 (completado)

- Migraciones SQL 058 + 059 + RLS + RPCs — aplicadas staging
- `SupabaseM18EventRepository` / `SupabaseM18RemoteDataSource` — operaciones remotas completas
- `M18EventModerationAdapter` (M04)
- Cierre oficial 2026-08-02
- Notificaciones M06 reales — **fuera de alcance M18**
- Check-in QR / geo — evaluar módulos futuros
