# M19 Bloque 3 — Auditoría

**Fecha:** 2026-08-02  
**Módulo:** M19 Red social y contenido

## Navegación (corregida)

| Antes | Después |
|-------|---------|
| Sumate → Eventos → "Feed comunitario (M19)" | **Comunidad → "Red social (M19)"** |
| M18 bajo Sumate → Eventos | Sin cambio — **M18 permanece en Eventos** |

**Decisión:** D01 ubica M19 en R5 Comunidad; la pestaña **Comunidad** es la entrada principal de red social. M18 (eventos operativos) permanece en Sumate → Eventos.

## Alcance Bloque 3 implementado

- Visibilidad `PUBLIC` / `ORGANIZATION` (sin `FOLLOWERS`)
- Estados `ARCHIVED`, `REMOVED_BY_MODERATION`
- Feed paginado con cursor `publishedAt|postId`
- Filtros por tipo (ALL, ORGANIZATIONS, PETS, SHELTERS, CAMPAIGNS, EVENTS, MEDIA, TEXT)
- Referencias contextuales M08/M16/M17/M18 (snapshots sanitizados)
- Media M05 vía referencias (sin binarios en tablas)
- Comentarios: editar, archivar (autor)
- Reacciones: LIKE, LOVE, SUPPORT, CELEBRATE
- Moderación M04 vía `M19SocialModerationAdapter`
- Resiliencia `M19SocialResilience` (PartialData, errores seguros)
- Sanitizer ampliado (PII, HTML, control chars)

## Fuera de alcance (documentado)

- **FOLLOW SYSTEM** — no definido en D01; feed cronológico + filtros
- **GUARDADOS / favoritos** — backlog futuro
- **Respuestas anidadas a comentarios** — no en modelo oficial Bloque 3
- **M06 notificaciones reales** — infra allowlist no ampliada

## Persistencia 060 vs dominio Bloque 3

| Función | 060 | Brecha |
|---------|-----|--------|
| Posts básicos | Sí | — |
| ARCHIVED / REMOVED_BY_MODERATION | No | **061** |
| LOVE reaction | No | **061** |
| visibility | No | **061** |
| content_references JSON | No | **061** |
| media_attachments JSON | No | **061** |
| comment edit / updated_at | No | **061** |
| feed cursor RPC | No (lista completa) | **061** |
| archive RPC | No | **061** |

**061 requerida** — evaluación confirmada al cierre Bloque 3; aplicación en Bloque 4.

## 060 al inicio Bloque 3

No aplicada (correcto).

## Bloque 4

No iniciado al cierre de auditoría Bloque 3.
