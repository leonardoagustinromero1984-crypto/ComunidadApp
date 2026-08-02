# M18 — Matriz funcional (Bloques 1–4)

## Alcance Bloque 1

Fundación local/mock de **eventos comunitarios gratuitos** vinculados a organizaciones M03. Sin venta de entradas.

## Alcance Bloque 2

Persistencia Supabase (migraciones 058–059, **aplicadas staging**): tablas, RLS, RPCs, repositorio remoto. Mock conservado.

## Alcance Bloque 3

Participación operativa: `M18EventOperationsService`, panel organizador, estados `ATTENDED`/`REJECTED`, resumen `M18EventOperationsSummary`, UI participante con privacidad. RPCs 059.

## Alcance Bloque 4

Integraciones M03/M04/M05/M06/M10, enlace M16→M18, filtros descubrimiento, métricas derivadas, resiliencia (`M18EventResilience`), preparación cierre global. **Recurrencia:** fuera de alcance D01.

## Alcance Bloque 5

Paridad remota, aplicación 058+059 staging, validación 110/110, cierre oficial.

## Tipos de evento

| Código | Descripción |
|--------|-------------|
| `ADOPTION_FAIR` | Feria de adopciones |
| `VOLUNTEER_DAY` | Jornada de voluntariado |
| `TRAINING_WORKSHOP` | Taller / capacitación |
| `COMMUNITY_GATHERING` | Encuentro comunitario |
| `FREE_FUNDRAISER` | Recaudación gratuita (sin cobro en app) |
| `AWARENESS_WALK` | Caminata de concientización |

## Estados de evento

| Estado | Público | Acepta inscripciones | Terminal |
|--------|---------|---------------------|----------|
| `DRAFT` | No | No | No |
| `PUBLISHED` | Sí | Sí (con cupo/espera) | No |
| `PAUSED` | Sí (histórico) | No | No |
| `COMPLETED` | Sí (histórico) | No | Sí |
| `CANCELLED` | No en directorio activo | No | Sí |

**Idempotencia:** publicar, pausar, completar y cancelar repetidos son no-op con registro interno.

## Estados de inscripción

| Estado | Ocupa cupo | Terminal |
|--------|-----------|----------|
| `REGISTERED` | Sí | No |
| `WAITLISTED` | No | No |
| `CANCELLED` | No | Sí |
| `CHECKED_IN` | Sí | No |
| `ATTENDED` | Sí (mock) | Sí |
| `NO_SHOW` | No | Sí |
| `REJECTED` | No | Sí |

## Cupos y lista de espera

- `maxCapacity > 0` obligatorio
- Si cupo lleno y `waitlistEnabled`: inscripción → `WAITLISTED`
- Cancelación promueve primer waitlisted a `REGISTERED`
- Reducir cupo por debajo de inscriptos → rechazado

## Recordatorios (mock)

| Estado | Descripción |
|--------|-------------|
| `SCHEDULED` | Programado localmente |
| `SENT` | Enviado (futuro M06) |
| `SKIPPED` | Omitido |

Requiere infra M06 mock flag; sin infra → `M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE`.

## Check-in

- Ventana: `checkInOpensAt` … `checkInClosesAt` (defaults: 1h antes del inicio … fin del evento)
- Solo org manager puede check-in de terceros en pantalla admin (B1 vía repositorio)
- Idempotente si ya `CHECKED_IN`

## Matriz acciones

| Acción | Anónimo | Autenticado | Org manager |
|--------|---------|-------------|-------------|
| Listar eventos publicados | Sí | Sí | Sí |
| Ver detalle público | Sí | Sí | Sí |
| Inscribirse | No | Sí | Sí |
| Cancelar inscripción propia | No | Sí | Sí |
| Programar recordatorio mock | No | Sí* | Sí* |
| Crear / editar evento | No | No | Sí |
| Publicar / pausar / cerrar | No | No | Sí |
| Check-in inscripción | No | No | Sí |

\*Requiere infra M06 mock.

## Permisos propuestos

| Código | Uso |
|--------|-----|
| `event.view` | Ver eventos de la organización |
| `event.manage` | CRUD, transiciones, check-in |

Mock B1: `organizationManagers` (patrón M17).

B2: `event.view` / `event.manage` en SQL + `OrganizationPermissionCode` Kotlin.

## Privacidad pública

- Modelo público **sin** `userId`, email, teléfono ni nombres de inscriptos
- Solo agregados: `registeredCount`, `waitlistCount`, `checkedInCount`
- Textos sanitizados vía `M18PrivacySanitizer`

## Dependencias

| Módulo | Uso |
|--------|-----|
| M03 | Organización propietaria |
| M06 | Hooks recordatorio (mock) |
| M08/M16 | Referencias opcionales |
| M17 | Patrón arquitectónico Bloque 1 |

## Fuera de alcance B1–B2

- Pagos / entradas pagas (M24)
- PII en modelos públicos
- Listado público de inscriptos con nombres
- Notificaciones push reales (M06 allowlist sin cambio)
- QR check-in
