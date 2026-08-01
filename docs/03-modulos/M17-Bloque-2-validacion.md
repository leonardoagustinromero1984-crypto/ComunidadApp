# M17 Bloque 2 — Validación

## Implementado

- Migración `054_m17_donation_campaigns_and_contributions.sql`
- Permisos `donation.view`, `donation.manage` (SQL + Kotlin)
- `SupabaseM17RemoteDataSource` — mapeadores JSON seguros
- `SupabaseM17DonationRepository` — contratos Bloque 1 vía RPC
- `M17CampaignModerationAdapter` — reportes M04
- `DataProvider` selecciona mock vs Supabase
- Tests `M17DonationRemoteMapperTest`

## SQL

```text
MIGRACIÓN 054 CREADA — NO APLICADA
```

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Tests focalizados

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "*M17Donation*Test" --no-configuration-cache --max-workers=1 --console=plain
```

## Confirmaciones

| Item | Estado |
|------|--------|
| Sin migración 055 | Sí |
| Sin SQL aplicado | Sí |
| Sin pagos reales | Sí |
| Mock operativo | Sí |
| M06 allowlist sin cambio | Sí |
| Bloque 3 pendiente al cierre | Sí |

## Veredicto

```text
M17 BLOQUE 2 PERSISTENCIA REMOTA IMPLEMENTADA
MIGRACIÓN 054 CREADA Y NO APLICADA
```
