# M20 Bloque 2 — Auditoría (Mensajería persistencia)

## Alcance auditado

Persistencia remota Supabase para conversaciones, mensajes y bloqueos M20.

## Migración 062

| Aspecto | Estado |
|---------|--------|
| Archivo | `supabase/migrations/062_m20_messaging_conversations_and_messages.sql` |
| Aplicada | **No** |
| Forward-only | Sobre 001–061 |
| Tablas | `m20_conversations`, `m20_messages`, `m20_user_blocks` |
| RLS | Deny direct table mutation; RPC SECURITY DEFINER |
| RPC autenticados | `m20_list_my_conversations`, `m20_get_conversation_messages`, `m20_send_message`, `m20_archive_conversation`, `m20_block_user`, `m20_unblock_user` |
| Anon | Denegado en tablas internas y RPCs |

## Kotlin remoto

- `SupabaseM20RemoteDataSource` — mapeadores JSON seguros (`toM20Conversation`, `toM20PublicConversation`, `toM20Message`, etc.)
- `SupabaseM20MessagingRepository` — implementa contrato Bloque 1 vía RPC
- `M20MessagingErrorMapper` — códigos alineados con mock
- `DataProvider` selecciona mock vs Supabase según `useSupabase`

## Seguridad

- JSON público sin `userId`, email ni teléfono
- `auth.uid()` en RPCs de participante
- Bloqueos bidireccionales vía `m20_user_blocks`
- Adjuntos `private://` rechazados en SQL y validadores Kotlin

## Excluido Bloque 2

- Aplicación SQL en entorno
- Upload adjuntos M05
- Retención / políticas avanzadas
- `markConversationRead` remoto (stub local en Supabase repo; recibos READ en Bloque 3)
- Realtime / suscripciones WebSocket

## Veredicto

```text
M20 BLOQUE 2 PERSISTENCIA DEFINIDA
MIGRACIÓN 062 CREADA NO APLICADA
MOCK CONSERVADO COMO FALLBACK
BLOQUE 3 NO CERRADO
```
