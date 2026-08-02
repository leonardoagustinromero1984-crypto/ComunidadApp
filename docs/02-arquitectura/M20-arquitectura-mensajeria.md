# M20 — Arquitectura mensajería

## Límites

M20 modela **conversaciones privadas entre usuarios** con contexto opcional (mascota, organización, evento, campaña, publicación social). No reemplaza M06 (notificaciones), M04 (moderación) ni M19 (feed público).

## Relación con contexto

```text
M20Conversation
  └── M20ContextSnapshot (PET | ORGANIZATION | EVENT | CAMPAIGN | SOCIAL_POST)
M20Message
  └── attachmentRef / M20MessageAttachment (referencia M05 — sin upload en B3)
  └── replyToMessageId → M20MessageReplyReference (público)
```

## Separación interno / público

```text
M20Conversation (participantUserIds, participantState, blockedByUserId)
  └── M20PublicConversation (peerDisplayName, contextHint, unreadCount, sin IDs)

M20Message (senderUserId, clientMessageId, deletedAt)
  └── M20PublicMessage (senderDisplayName, isOwnMessage, placeholder eliminado)
```

Bloque 1: mock local. **Bloque 2:** persistencia Supabase RPC (`062`, **no aplicada**). **Bloque 3:** operaciones mock completas + UI hilo/bandeja.

## Capas

```text
UI (M20MessagingScreens)
  → ViewModels (M20MessagingViewModels)
  → M20MessagingRepository (interface)
  → MockM20MessagingRepository | SupabaseM20MessagingRepository
  → M20MessagingMemoryStore | SupabaseM20RemoteDataSource (RPC)
  → M20MessagingValidators / M20ContextHintResolver / M20MessagingModerationAdapter
  → M20PrivacySanitizer → M20PublicConversation / M20PublicMessage
```

## Estados de conversación

```text
ACTIVE → ARCHIVED (per-participante via participantState)
ACTIVE → BLOCKED (global + m20_user_blocks)
ARCHIVED (actor) — peer puede seguir ACTIVE
BLOCKED → unblock → ACTIVE
```

## Estados de mensaje

```text
PENDING_LOCAL → SENT → DELIVERED → READ
SENT → EDITED
* → DELETED (lógico, placeholder público)
FAILED (reservado)
```

`markRead(conversationId, lastReadMessageId)` avanza cursor monótono por participante.

## Privacidad

Toda lectura pública pasa por `M20PrivacySanitizer`. Mensajes eliminados → `[mensaje eliminado]`. Emails/teléfonos redactados.

## Rutas

| Ruta | Pantalla |
|------|----------|
| `m20/inbox` | Bandeja (entrada principal) |
| `m20/conversations` | Alias bandeja |
| `m20/conversations/{conversationId}` | Hilo + composer |

Entrada: **Comunidad → Mensajería (M20)**.

## Bloque 2 (implementado)

- Migración SQL `062` — **creada, no aplicada**
- `SupabaseM20MessagingRepository` + mapeadores remotos

## Bloque 3 (implementado localmente)

- Modelos ampliados, mock 20 escenarios, paginación, idempotencia
- Editar/eliminar/responder, archivo per-participante, bloqueo/unblock
- Reportes M04 stub adapter
- UI bandeja + hilo con estados completos
- Tests `M20MessagingOperationsTest` (25)

## Bloque 4 (pendiente)

- Aplicación 062 (+ evaluación 063 si brecha persiste)
- Upload adjuntos M05
- Recibos READ remotos SQL
- Retención y políticas
