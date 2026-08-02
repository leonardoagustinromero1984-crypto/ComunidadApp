# M18 — Auditoría inicial (Bloque 1)

## 1. HEAD inicial

Referencia al cierre M17 y estado previo a M18 Bloque 1.

## 2. Estado Git inicial

- M17 Bloques 1–4 implementados localmente
- M18 no iniciado antes de este bloque
- Sin migración SQL para eventos

## 3. Nombre oficial vs alcance del bloque

| Fuente | Nombre |
|--------|--------|
| **D01 (autoritativo)** | **M18 Eventos** |
| Prompt Bloque 1 | Creación, cupos, inscripción, recordatorios y check-in |

**Decisión:** conservar número **M18** y nombre D01. Bloque 1 implementa fundación local/mock de eventos comunitarios gratuitos (sin venta de entradas ni pagos).

## 4. Documentos revisados

- `docs/01-producto/D01-Modulos-y-Orden.md`
- Patrones M17 Bloque 1 (modelos, repositorio mock, navegación, Sumate)
- Código focal: M03 organizaciones, M06 notificaciones (hooks), M08/M16 referencias opcionales, `DataProvider.kt`, `NavRoutes.kt`

## 5. Auditoría M01 / M02

| Aspecto | Autoridad | Uso M18 |
|---------|-----------|---------|
| Sesión actual | M01 AuthRepository | Actor `mock_user_admin` en seeds |
| Identidad usuario | M01 userId | `createdBy` / `userId` internos; no expuestos en público |
| Permisos plataforma | M02 roles | Sin roles M18 paralelos |

## 6. Auditoría M03

| Aspecto | Hallazgo | M18 |
|---------|----------|-----|
| Organización autoritativa | `organizationId` M03 | **Todo evento pertenece a org M03** |
| Tipos elegibles | SHELTER, RESCUE_GROUP, NGO, TRAINING_CENTER | `M18_ELIGIBLE_ORGANIZATION_TYPES` |
| Membresías / managers | Patrón mock M17/M16 | `canManageOrganization` |
| Permisos propuestos | — | `event.view`, `event.manage` |

## 7. Auditoría M04 / M05

- Campo `moderationStatus` interno; sin cola paralela en B1
- `coverImageRef` como referencia M05; solo ref en modelo público

## 8. Auditoría M06

- Hooks `M18M06Hooks` definidos
- Recordatorios mock requieren `m06InfrastructureAvailable`; fallback honesto si no hay infra
- Allowlist M06 **no ampliada** en Bloque 1

## 9. Auditoría M08 / M16

- Referencia opcional mascota (`petPublicName`) y refugio (`shelterPublicName`)
- Sin duplicar entidades M08/M16

## 10. Alcance explícito excluido

- SQL / migraciones Supabase
- Pagos / venta de entradas (M24)
- PII en modelos públicos
- Listado público de inscriptos con nombres

## 11. Veredicto auditoría

```text
M18 BLOQUE 1 — FUNDACIÓN LOCAL VIABLE
PATRÓN M17 REUTILIZADO
ORGANIZACIONES M03 COMO ANCLA
SIN SQL
SIN PAGOS
```
