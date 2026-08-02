# M20 Bloque 2 — Validación

## Implementado

- Migración `062_m20_messaging_conversations_and_messages.sql`
- Tablas `m20_conversations`, `m20_messages`, `m20_user_blocks`
- RPCs SECURITY DEFINER con JSON público sanitizado
- `SupabaseM20RemoteDataSource` — mapeadores JSON seguros
- `SupabaseM20MessagingRepository` — contratos Bloque 1 vía RPC
- `M20MessagingErrorMapper`
- `DataProvider` selecciona mock vs Supabase
- Tests `M20MessagingRemoteMapperTest`

## SQL

```text
MIGRACIÓN 062 CREADA — NO APLICADA
```

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Tests focalizados

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m20.*" --no-configuration-cache --max-workers=1 --console=plain
```

## Confirmaciones

| Item | Estado |
|------|--------|
| Sin SQL aplicado | Sí |
| Mock operativo | Sí |
| JSON público sin PII | Sí |
| Sin tocar M19 | Sí |
| M20 Bloque 3 pendiente | Sí |

## Veredicto

```text
M20 BLOQUE 2 PERSISTENCIA REMOTA IMPLEMENTADA
MIGRACIÓN 062 CREADA Y NO APLICADA
BLOQUE 3 NO CERRADO
```
