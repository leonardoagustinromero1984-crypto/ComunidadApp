# M20 Bloque 4 — Auditoría de paridad

**Fecha:** 2026-08-02  
**Migraciones:** 062 (base), **063 requerida y creada**

## Matriz de paridad (Kotlin / mock / 062 / Supabase / solución)

| # | Función | Kotlin | Mock | 062 | Supabase | Brecha | Solución |
|---|---------|--------|------|-----|----------|--------|----------|
| 1 | Conversaciones DIRECT | Sí | Sí | Sí | Sí | — | 062 |
| 2 | Tipos conversación ampliados | Sí | Sí | No | Sí | conversation_type | **063** |
| 3 | Context CAMPAIGN / SOCIAL_POST | Sí | Sí | Parcial | Sí | enum context | **063** |
| 4 | clientMessageId idempotente | Sí | Sí | No | Sí | columna + RPC | **063** |
| 5 | Reply to message | Sí | Sí | No | Sí | reply_to + JSON | **063** |
| 6 | Editar mensaje propio | Sí | Sí | No | Sí | RPC edit | **063** |
| 7 | Eliminar lógico | Sí | Sí | No | Sí | deleted_at | **063** |
| 8 | Archivo per-participante | Sí | Sí | Global | Sí | participant_state | **063** |
| 9 | markRead cursor | Sí | Sí | No | Sí | RPC mark_read | **063** |
| 10 | Adjunto sin texto | Sí | Sí | No | Sí | CHECK content | **063** |
| 11 | message_type | Sí | Sí | No | Sí | columna enum | **063** |
| 12 | createDirectConversation | Sí | Sí | No | Sí | RPC create | **063** |
| 13 | Bloqueo / unblock | Sí | Sí | Sí | Sí | — | 062 |
| 14 | Paginación mensajes | Sí | Sí | Sí | Sí | — | 062 |
| 15 | JSON público sanitizado | Sí | Sí | Sí | Sí | campos B3 | **063** |
| 16 | Reportes M04 | Adapter | Sí | N/A | N/A | cola M04 | mock+adapter |
| 17 | Upload M05 real | No | No | No | No | Fuera alcance | documentado |
| 18 | Retención avanzada | No | No | No | No | Futuro | documentado |

## Conclusión 063

**063 REQUERIDA** — 062 no cubría operaciones Bloque 3 (edit/delete/reply, archivo per-participante, idempotencia, tipos, contextos extendidos).

## Staging

- Entorno: `wystsapjfpdtoprlmizz` (no producción)
- 062 aplicada vía `supabase db query --linked`
- 063 aplicada vía `supabase db query --linked`
- `schema_migrations`: 062, 063 registradas (`migration repair`)

## Navegación

Comunidad → Mensajería (M20). Rutas `m20/inbox`, `m20/conversations/{id}`.
