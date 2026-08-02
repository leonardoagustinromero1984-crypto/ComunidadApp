# M17 Bloque 4 — Validación

## HEAD inicial

`d56e9fd40c08d46b8a5bf0f9b0c0f121baba1d87`

## Implementado

### SQL (no aplicado)

- `supabase/migrations/055_m17_in_kind_volunteering_and_transparency.sql`
- Tablas: `m17_in_kind_needs`, `m17_in_kind_pledges`, `m17_volunteer_opportunities`, `m17_volunteer_applications`, `m17_campaign_transparency_reports`, `m17_fund_usage_items`, `m17_transparency_milestones`
- RLS en todas las tablas; mutaciones vía RPC SECURITY DEFINER
- RPCs públicas sanitizadas (bienes, voluntariado, transparencia)
- RPCs autenticadas (pledge, postulación, entrega, aceptación)

### Kotlin

- `SupabaseM17ExtendedRemoteDataSource.kt` — DTOs/mappers JSON
- `SupabaseM17ExtendedRepositories.kt` — bienes, voluntariado, transparencia
- `DataProvider` — mock vs Supabase según `useSupabase`
- `M17CampaignModerationAdapter` — reportes M04 extendidos

### Tests focalizados

Clases ejecutadas:

- `M17ExtendedRemoteMapperTest`
- `M17ExtendedFoundationTest`
- `M17DonationRemoteMapperTest` (regresión campañas)

Comando:

```text
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m17.M17ExtendedRemoteMapperTest" --tests "com.comunidapp.app.domain.m17.M17ExtendedFoundationTest" --tests "com.comunidapp.app.domain.m17.M17DonationRemoteMapperTest" --no-configuration-cache --max-workers=1 --console=plain
```

Resultado:

```text
BUILD SUCCESSFUL in 1m 12s
28 actionable tasks: 3 executed, 25 up-to-date
Tests: M17ExtendedRemoteMapperTest + M17ExtendedFoundationTest + M17DonationRemoteMapperTest — PASS
```

### Compilación Kotlin

```text
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Resultado:

```text
BUILD SUCCESSFUL in 8s
compileLocalDebugKotlin — PASS
```

## Confirmaciones

| Item | Estado |
|------|--------|
| Migración 054 creada, no aplicada | Sí |
| Migración 055 creada, no aplicada | Sí |
| Sin migración 056 | Sí |
| Sin Supabase local | Sí |
| Sin producción | Sí |
| Sin pagos reales | Sí |
| Sin M18 / M24 | Sí |
| Bloque 5 no iniciado | Sí |
| Mock operativo | Sí |
| Privacidad público sin PII | Sí |
| M06 allowlist no ampliada | Sí |

## Privacidad (PASS)

1. Modelos públicos bienes sin `contributorUserId`
2. Modelos públicos voluntariado sin `applicantUserId`
3. Postulaciones no públicas
4. Pledges no públicos
5. Sin emails/teléfonos en JSON público
6. Sin domicilio exacto
7. Sin `availability_summary` público
8. Sin notas internas
9. Comprobantes privados omitidos en superficie pública
10. `organizationId` interno no expuesto

## Veredicto

```text
M17 BLOQUE 4 PERSISTENCIA REMOTA EXTENDIDA IMPLEMENTADA
BIENES M17 CON REPOSITORIO SUPABASE
VOLUNTARIADO M17 CON REPOSITORIO SUPABASE
TRANSPARENCIA M17 CON REPOSITORIO SUPABASE
MIGRACIÓN 054 CREADA Y NO APLICADA
MIGRACIÓN 055 CREADA Y NO APLICADA
VALIDACIÓN REMOTA PENDIENTE
M17 CIERRE OPERATIVO GLOBAL PENDIENTE
M17 BLOQUE 5 NO INICIADO
PAGOS REALES DIFERIDOS A M24
```
