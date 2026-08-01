# M17 Bloque 1 — Validación

## Alcance implementado

- Auditoría inicial, matriz funcional, arquitectura
- Modelos interno/público, validadores, errores, repositorio mock
- Rutas `m17/campaigns/*`
- Pantallas directorio, detalle, administración, creación/edición
- ViewModels con estados Loading/Content/Empty/Error
- DataProvider `m17DonationRepository`
- Acceso Sumate → Donaciones → "Campañas solidarias (M17)"
- Tests focalizados `M17DonationFoundationTest`

## HEAD inicial

`79e2a36f59e2176cebf3fa26b3e8762f98faf21e`

## Archivos código creados

- `app/src/main/java/com/comunidapp/app/data/model/M17DonationModels.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M17DonationValidators.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M17DonationRepositories.kt`
- `app/src/main/java/com/comunidapp/app/data/remote/supabase/m17/M17DonationErrorMapper.kt`
- `app/src/main/java/com/comunidapp/app/viewmodel/M17DonationViewModels.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt`
- `app/src/main/java/com/comunidapp/app/navigation/M17NavGraph.kt`
- `app/src/test/java/com/comunidapp/app/domain/m17/M17DonationFoundationTest.kt`

## Archivos código modificados

- `app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt`
- `app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt`
- `app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/sumate/SumateScreen.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/sumate/tabs/SumateTabContent.kt`

## Validación focalizada (01–25)

| # | Criterio | Resultado |
|---|----------|-----------|
| 01 | Solo PUBLISHED en directorio público activo | PASS — `searchPublicCampaigns` filtra `isPublic` |
| 02 | DRAFT no aparece públicamente | PASS |
| 03 | PAUSED representado correctamente | PASS — seed + estado visible |
| 04 | COMPLETED terminal | PASS |
| 05 | CANCELLED terminal | PASS |
| 06 | Publicar idempotente | PASS — `recordIdempotentRetry` |
| 07 | Pausar idempotente | PASS |
| 08 | Completar idempotente | PASS |
| 09 | Cancelar idempotente | PASS |
| 10 | Objetivo inválido rechazado | PASS — test + validador |
| 11 | Contribución negativa rechazada | PASS — test |
| 12 | Solo CONFIRMED suma al total | PASS — test |
| 13 | REFUNDED no suma | PASS — test |
| 14 | Anónima no revela identidad | PASS — test |
| 15 | Privada no aparece públicamente | PASS — test |
| 16 | Mascota usa referencia pública | PASS — `petPublicName` |
| 17 | Refugio usa referencia pública M16 | PASS — `shelterPublicName` |
| 18 | Usuario ajeno no administra | PASS — `canManageOrganization` |
| 19 | Organización autorizada administra | PASS — mock managers |
| 20 | Terminal no reabre | PASS — test |
| 21 | No datos de tarjeta | PASS — no campos en modelos |
| 22 | No integración pago real | PASS — mock only |
| 23 | M16 continúa funcionando | PASS — sin cambios M16 core |
| 24 | No migración 054 | PASS |
| 25 | Bloque 2 no iniciado | PASS |

## Compilación Kotlin

Comando único:

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Resultado:

```text
BUILD SUCCESSFUL in 56s
compileLocalDebugKotlin PASS
```

## Pruebas automáticas

Comando focalizado:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "*M17DonationFoundationTest" --no-configuration-cache --max-workers=1 --console=plain
```

Resultado:

```text
BUILD SUCCESSFUL — 6 tests PASS
M17DonationFoundationTest ejecutado
```

## Confirmaciones de cierre

| Item | Estado |
|------|--------|
| Sin migración 054 | Sí |
| Sin SQL aplicado | Sí |
| Sin Supabase local | Sí |
| Sin emulador | Sí |
| Sin APK | Sí |
| Sin lint | Sí |
| Sin JaCoCo | Sí |
| Sin pagos reales | Sí |
| Sin M18 | Sí |
| Sin M24 | Sí |
| M17 Bloque 2 no iniciado | Sí |

## Smoke físico

```text
NO EJECUTADO — fuera de alcance Bloque 1
```

## Pendientes Bloque 2

- Evaluar y crear migración 054
- `SupabaseM17DonationRepository` + RLS
- Permisos M03 reales (`donation.view`, `donation.manage`)
- Allowlist M06 explícita
- Validación remota / smoke staging
- Integración moderación M04 para campañas publicadas

## Veredicto

```text
M17 BLOQUE 1 FUNDACIÓN FUNCIONAL IMPLEMENTADA
CAMPAÑAS VINCULADAS A ORGANIZACIONES M03
SIN DUPLICACIÓN DE USUARIOS, MASCOTAS O REFUGIOS
MODELOS PÚBLICOS SIN PII FINANCIERA
REPOSITORIO LOCAL/MOCK OPERATIVO
PAGOS REALES DIFERIDOS
SIN MIGRACIÓN 054
SIN SQL APLICADO
COMPILACIÓN KOTLIN PASS
M17 BLOQUE 2 NO INICIADO
```
