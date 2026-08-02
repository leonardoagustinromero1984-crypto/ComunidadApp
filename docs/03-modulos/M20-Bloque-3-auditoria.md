# M20 Bloque 3 — Auditoría

**Fecha:** 2026-08-02  
**Módulo:** M20 Mensajería

## Alcance Bloque 3 implementado

| # | Punto | Estado |
|---|-------|--------|
| 1 | Tipos `M20ConversationType` (DIRECT, ORGANIZATION, SUPPORT, CONTEXTUAL) | ✓ |
| 2 | Tipos `M20MessageType` (TEXT, IMAGE_REFERENCE, FILE_REFERENCE, SYSTEM_CONTEXT) | ✓ |
| 3 | Estados `M20MessageStatus` ampliados (PENDING_LOCAL … DELETED) | ✓ |
| 4 | Participantes + `participantState` (archivo/read por usuario) | ✓ |
| 5 | Contexto CAMPAIGN (M17) y SOCIAL_POST (M19) | ✓ |
| 6 | `createDirectConversation` idempotente | ✓ |
| 7 | Paginación mensajes cursor `sentAt\|messageId` | ✓ |
| 8 | Envío idempotente `clientMessageId` | ✓ |
| 9 | Editar / eliminar lógico mensaje propio | ✓ |
| 10 | `markRead` cursor monótono por participante | ✓ |
| 11 | Archivo per-participante (peer sigue ACTIVE) | ✓ |
| 12 | Bloqueo vía mapa `m20_user_blocks` mock + unblock | ✓ |
| 13 | Reportes M04 vía `M20MessagingModerationAdapter` | ✓ |
| 14 | Validadores edit/delete/reply + adjunto sin texto | ✓ |
| 15 | Sanitizer mensajes eliminados `[mensaje eliminado]` | ✓ |
| 16 | UI bandeja con badges unread | ✓ |
| 17 | UI hilo: paginación, composer, reply, edit, delete | ✓ |
| 18 | Estados UI Loading/Empty/Sending/SendFailed/Blocked/… | ✓ |
| 19 | Ruta `m20/inbox` + alias `m20/conversations` | ✓ |
| 20 | Seeds mock 20 escenarios (direct/org/context/archived/…) | ✓ |

## Fuera de alcance (documentado)

- Aplicación migración **062** en staging — pendiente Bloque 4
- Upload adjuntos M05 real — referencias only
- Retención avanzada / políticas SQL
- Recibos READ remotos completos en Supabase repo (stub parcial)

## Persistencia 062 vs dominio Bloque 3

| Función | 062 | Brecha Bloque 3 |
|---------|-----|-----------------|
| Conversaciones DIRECT básicas | Sí | — |
| `conversationType` / tipos mensaje ampliados | No | **063 futuro** |
| `clientMessageId` idempotente | No | **063 futuro** |
| Edición / borrado lógico | No | **063 futuro** |
| Archivo per-participante | No | **063 futuro** |
| Context CAMPAIGN / SOCIAL_POST | No | **063 futuro** |

**062 no aplicada** al cierre Bloque 3 (correcto). No se creó 063 en este bloque.

## Bloque 4

No iniciado al cierre de auditoría Bloque 3.
