# M20 — Arquitectura mensajería

## Límites

M20 modela **conversaciones privadas entre usuarios** con contexto opcional (mascota, organización, evento). No reemplaza M06 (notificaciones), M04 (moderación) ni M19 (feed público).

## Relación con contexto

```text
M20Conversation
  └── M20ContextSnapshot (PET | ORGANIZATION | EVENT)
M20Message
  └── attachmentRef (referencia M05 — sin upload en B1)
```

## Separación interno / público

```text
M20Conversation (participantUserIds, peerUserId, blockedByUserId)
  └── M20PublicConversation (peerDisplayName, contextHint, sin IDs)

M20Message (senderUserId)
  └── M20PublicMessage (senderDisplayName, isOwnMessage, sin userId)
```

Bloque 1: mock local en `M20MessagingMemoryStore`. **Bloque 2:** persistencia Supabase vía RPC (`062_m20_messaging_conversations_and_messages.sql`, **no aplicada**).

## Capas

```text
UI (M20MessagingScreens)
  → ViewModels (M20MessagingViewModels)
  → M20MessagingRepository (interface)
  → MockM20MessagingRepository | SupabaseM20MessagingRepository
  → M20MessagingMemoryStore | SupabaseM20RemoteDataSource (RPC)
  → M20MessagingValidators / M20ContextHintResolver
  → M20PrivacySanitizer → M20PublicConversation / M20PublicMessage
```

## Estados de conversación

```text
ACTIVE → ARCHIVED
ACTIVE → BLOCKED (stub Bloque 1)
BLOCKED → (sin envío)
ARCHIVED → (sin envío)
```

## Estados de mensaje (mock)

```text
SENT → DELIVERED → READ
```

`markConversationRead` promueve mensajes entrantes a READ.

## Privacidad

Toda lectura pública pasa por `M20PrivacySanitizer`. Emails, teléfonos y markup peligroso se redactan.

## Rutas

| Ruta | Pantalla |
|------|----------|
| `m20/conversations` | Lista de conversaciones |
| `m20/conversations/{conversationId}` | Hilo + envío texto |

Entrada principal: **Comunidad → Mensajería (M20)**. M18 eventos permanece en Sumate → Eventos.

## Bloque 2 (implementado, no cerrado)

- Migración SQL `062` conversaciones/mensajes/bloqueos — **creada, no aplicada**
- `SupabaseM20MessagingRepository` + mapeadores remotos
- `DataProvider` mock vs Supabase

## Bloque 3 (pendiente)

- Adjuntos M05 upload
- Recibos READ remotos / `markConversationRead` SQL
- Retención y políticas
