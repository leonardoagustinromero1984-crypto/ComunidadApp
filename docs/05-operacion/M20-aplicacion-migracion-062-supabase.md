# Operación — migración 062 (M20 mensajería)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivo canónico

```text
supabase/migrations/062_m20_messaging_conversations_and_messages.sql
```

## Estado actual (post Bloque 2)

```text
062 — CREADA, NO APLICADA
Validación remota M20 — PENDIENTE
```

## Prerrequisitos

- Migraciones 001–061 aplicadas en orden en entorno **no productivo**.
- Usuarios de prueba con filas en `public.users` vinculadas a `auth.users`.
- Operador confirma proyecto Supabase de staging/pruebas.

## Procedimiento

1. Confirmar entorno **no productivo** (no producción).
2. Abrir SQL Editor en Supabase Dashboard.
3. Ejecutar **062 completo** una sola vez.
4. Verificar tablas M20:

```sql
select table_name from information_schema.tables
where table_schema = 'public' and table_name like 'm20_%'
order by 1;
-- esperado: m20_conversations, m20_messages, m20_user_blocks
```

5. Verificar RPCs:

```sql
select proname from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname like 'm20_%'
order by 1;
```

6. Smoke RLS (usuario autenticado participante → listar conversaciones):

```sql
select public.m20_list_my_conversations();
```

7. Activar `useSupabase=true` en build de prueba y validar bandeja desde app.

## Orden obligatorio

```text
061 → 062
```

No aplicar 062 antes de 061 (forward-only sobre cadena M19).

## Límites

- No resetear base ni borrar datos de producción.
- No usar service role desde Android.
- Upload adjuntos M05 permanece en Bloque 3.

## Validación remota pendiente

Tras aplicación en staging:

- Listar conversaciones vía `m20_list_my_conversations`
- Obtener mensajes paginados vía `m20_get_conversation_messages`
- Enviar mensaje vía `m20_send_message`
- Archivar vía `m20_archive_conversation`
- Bloquear / desbloquear vía `m20_block_user` / `m20_unblock_user`
- Confirmar JSON sin `user_id`, email ni teléfono

## Rollback

No hay rollback automático. Ante fallo parcial, restaurar desde backup de staging o re-ejecutar en entorno limpio.

## Referencias

- `docs/03-modulos/M20-Bloque-2-auditoria.md`
- `docs/03-modulos/M20-Bloque-2-validacion.md`
