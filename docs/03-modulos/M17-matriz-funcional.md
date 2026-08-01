# M17 — Matriz funcional (Bloque 1)

## Alcance Bloque 1

Fundación local/mock de **campañas solidarias** vinculadas a organizaciones M03. Sin pagos reales, sin SQL, sin migración 054.

## Tipos de campaña

| Código | Descripción |
|--------|-------------|
| `MEDICAL` | Gastos veterinarios |
| `FOOD_AND_SUPPLIES` | Alimento e insumos |
| `RESCUE` | Operación de rescate |
| `SHELTER_INFRASTRUCTURE` | Infraestructura refugio |
| `TRANSPORT` | Traslados |
| `EMERGENCY` | Emergencia |
| `GENERAL_SUPPORT` | Apoyo general |

## Estados de campaña

| Estado | Público | Acepta contribuciones mock | Terminal |
|--------|---------|---------------------------|----------|
| `DRAFT` | No | No | No |
| `PUBLISHED` | Sí | Sí (mock) | No |
| `PAUSED` | Sí (histórico) | No | No |
| `COMPLETED` | Sí (histórico) | No | Sí |
| `CANCELLED` | No en directorio activo | No | Sí |

**Idempotencia:** publicar, pausar, completar y cancelar repetidos son no-op con registro interno.

## Estados de contribución (mock)

| Estado | Suma al total confirmado |
|--------|-------------------------|
| `PENDING` | No |
| `CONFIRMED` | Sí |
| `FAILED` | No |
| `CANCELLED` | No |
| `REFUNDED` | No |

## Visibilidad donante

| Valor | Listado público |
|-------|-----------------|
| `PUBLIC` | Sí, con nombre |
| `ANONYMOUS` | Sí, etiqueta genérica |
| `PRIVATE` | No |

## Matriz acciones

| Acción | Anónimo | Autenticado | Org manager |
|--------|---------|-------------|-------------|
| Listar campañas publicadas | Sí | Sí | Sí |
| Ver detalle público | Sí | Sí | Sí |
| Filtrar / buscar | Sí | Sí | Sí |
| Contribución mock | No* | Sí* | Sí* |
| Crear campaña | No | No | Sí |
| Editar borrador | No | No | Sí |
| Publicar / pausar / cerrar | No | No | Sí |
| Ver borradores org | No | No | Sí |
| Registrar contribución mock admin | No | No | Sí |

\*Con aviso: pagos reales no habilitados.

## Permisos propuestos

| Código | Uso |
|--------|-----|
| `donation.view` | Ver campañas de la organización |
| `donation.manage` | CRUD y transiciones de estado |

Mock B1: `organizationManagers` (patrón M16). Producción: membership M03.

## Referencias opcionales

- Mascota M08 (`petId`, `petPublicName`)
- Refugio M16 (`shelterProfileId`, `shelterPublicName`)
- Necesidad (`needDescription`)
- Ubicación aproximada (`publicLocationText`)
- Imágenes M05 (`coverImageRef`, galería)

## Fuera de alcance Bloque 1

- Pagos reales (M24)
- Voluntariado y bienes (M17 producto completo)
- SQL / migración 054
- Supabase remoto
- M18 eventos
- Roles paralelos (`DONATION_ADMIN`, etc.)

## Navegación

| Ruta | Pantalla |
|------|----------|
| `m17/campaigns` | Directorio público |
| `m17/campaigns/{campaignId}` | Detalle público |
| `m17/campaigns/manage` | Administración org |
| `m17/campaigns/create` | Crear borrador |
| `m17/campaigns/{campaignId}/edit` | Editar borrador |

**Entrada:** Sumate → Donaciones → "Campañas solidarias (M17)". Legacy DonationsContent preservado.
