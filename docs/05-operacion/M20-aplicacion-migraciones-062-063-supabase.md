# Operación — migraciones 062 y 063 (M20 mensajería)

**LeoVer** · Supabase staging/pruebas · **no producción**.

## Archivos canónicos

```text
supabase/migrations/062_m20_messaging_conversations_and_messages.sql
supabase/migrations/063_m20_messaging_operations_and_privacy.sql
```

## Estado actual (post cierre M20)

```text
062 — APLICADA EN STAGING NO PRODUCTIVO
063 — APLICADA EN STAGING NO PRODUCTIVO (imprescindible para paridad Bloque 3)
```

Registro en `supabase_migrations.schema_migrations`: 062, 063.

Validación: `scripts/ops/m20_remote_validation_062_063.sql` — **125/125 PASS**  
Smoke: `scripts/ops/m20_smoke_remote_01_25.sql` — **25/25 PASS**

## Entorno

- Proyecto: `wystsapjfpdtoprlmizz`
- **No producción**

## Procedimiento (referencia)

1. Confirmar entorno **no productivo**.
2. Aplicar **062** completo una sola vez (`supabase db query --linked -f ...`).
3. Aplicar **063** completo una sola vez.
4. `supabase migration repair 062 --status applied --linked`
5. `supabase migration repair 063 --status applied --linked`
6. Ejecutar scripts de validación y smoke.

## Orden obligatorio

```text
062 → 063
```

## Verificación tablas

```sql
select table_name from information_schema.tables
where table_schema = 'public' and table_name like 'm20_%'
order by 1;
-- m20_conversations, m20_messages, m20_user_blocks, m20_participant_state
```

## Referencias

- `docs/03-modulos/M20-cierre-oficial.md`
- `docs/03-modulos/M20-Bloque-4-validacion.md`
