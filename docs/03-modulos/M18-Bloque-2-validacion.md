# M18 Bloque 2 — Validación

## Implementado

- Migración `058_m18_community_events_and_registrations.sql`
- Permisos `event.view`, `event.manage` (SQL + Kotlin)
- `SupabaseM18RemoteDataSource` — mapeadores JSON seguros
- `SupabaseM18EventRepository` — contratos Bloque 1 vía RPC
- `M18EventModerationAdapter` — reportes M04
- `DataProvider` selecciona mock vs Supabase
- Tests `M18EventRemoteMapperTest`

## SQL

```text
MIGRACIÓN 058 CREADA — NO APLICADA
```

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Tests focalizados

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "*M18Event*Test" --no-configuration-cache --max-workers=1 --console=plain
```

## Confirmaciones

| Item | Estado |
|------|--------|
| Sin SQL aplicado | Sí |
| Sin pagos / entradas | Sí |
| Mock operativo | Sí |
| M06 allowlist sin cambio | Sí |
| JSON público sin PII | Sí |
| Bloque 3 pendiente al cierre | Sí |

## Veredicto

```text
M18 BLOQUE 2 PERSISTENCIA REMOTA IMPLEMENTADA
MIGRACIÓN 058 CREADA Y NO APLICADA
```
