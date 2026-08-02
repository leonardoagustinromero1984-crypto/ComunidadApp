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

## Incidencia de validación local (tests)

| Evento | Detalle |
|--------|---------|
| Runs interrumpidos | >9 min sin completar; exit `4294967295` |
| Interpretación | Interrupción de proceso, **no** fallo de assertions |
| Causa funcional | `validateReplyTarget(null)` rechazaba envíos sin reply |
| Fix | Guard en `sendMessage`: validar reply solo si `replyToMessageId != null` |
| Resultado post-fix | 46/46 PASS (~47 s) |

## Fuera de alcance (documentado al cierre B3)

- Aplicación migración **062** en staging — completada en **Bloque 4**
- Upload adjuntos M05 real — referencias only
- Retención avanzada / políticas SQL
- Recibos READ remotos — completados en B4 vía RPC `m20_mark_conversation_read`

## Persistencia 062 vs dominio Bloque 3

| Función | 062 | Brecha Bloque 3 | Resolución |
|---------|-----|-----------------|------------|
| Conversaciones DIRECT básicas | Sí | — | 062 |
| `conversationType` / tipos mensaje ampliados | No | Sí | **063** (B4) |
| `clientMessageId` idempotente | No | Sí | **063** (B4) |
| Edición / borrado lógico | No | Sí | **063** (B4) |
| Archivo per-participante | No | Sí | **063** (B4) |
| Context CAMPAIGN / SOCIAL_POST | No | Sí | **063** (B4) |

**062 no aplicada** al cierre de implementación Bloque 3 (correcto). **063** creada y aplicada en Bloque 4.

## Bloque 4

Cerrado — ver `M20-Bloque-4-validacion.md` y `M20-cierre-oficial.md`.
