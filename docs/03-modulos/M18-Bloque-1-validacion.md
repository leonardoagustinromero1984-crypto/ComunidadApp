# M18 Bloque 1 — Validación

## Alcance implementado

- Auditoría inicial, matriz funcional, arquitectura
- Modelos interno/público, validadores, errores, repositorio mock
- Rutas `m18/events/*`
- Pantallas directorio, detalle, administración, creación/edición
- ViewModels con estados Loading/Content/Empty/Error
- DataProvider `m18EventRepository` (mock only)
- Acceso Sumate → Eventos → "Eventos comunitarios (M18)"
- Tests focalizados `M18EventFoundationTest`

## Archivos código creados

- `app/src/main/java/com/comunidapp/app/data/model/M18EventModels.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M18EventValidators.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M18EventRepositories.kt`
- `app/src/main/java/com/comunidapp/app/data/remote/supabase/m18/M18EventErrorMapper.kt`
- `app/src/main/java/com/comunidapp/app/viewmodel/M18EventViewModels.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/m18/M18EventScreens.kt`
- `app/src/main/java/com/comunidapp/app/navigation/M18NavGraph.kt`
- `app/src/test/java/com/comunidapp/app/domain/m18/M18EventFoundationTest.kt`

## Archivos código modificados

- `app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt`
- `app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt`
- `app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/sumate/SumateScreen.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/sumate/tabs/SumateTabContent.kt`

## Validación focalizada

| # | Criterio | Resultado |
|---|----------|-----------|
| 01 | Solo PUBLISHED en directorio activo | PASS — `searchPublicEvents` filtra `activeOnly` |
| 02 | DRAFT no aparece públicamente | PASS |
| 03 | PAUSED representado | PASS — seed + estado visible |
| 04 | COMPLETED terminal | PASS |
| 05 | CANCELLED terminal | PASS |
| 06 | Publicar idempotente | PASS — test + `recordIdempotentRetry` |
| 07 | Cupo inválido rechazado | PASS — validador + test |
| 08 | Terminal no reabre | PASS — test |
| 09 | Inscripción idempotente | PASS — test |
| 10 | Check-in idempotente | PASS — test |
| 11 | PII redactada en textos públicos | PASS — test privacy |
| 12 | Stats públicos sin nombres | PASS — `M18PublicRegistrationStats` |
| 13 | Recordatorio requiere infra M06 | PASS — test |
| 14 | Usuario ajeno no administra | PASS — test |
| 15 | Org manager administra | PASS — mock managers |
| 16 | Sin pagos / entradas | PASS — no campos de pago |
| 17 | Sin SQL | PASS |
| 18 | Mock determinista | PASS — seeds en store |
| 19 | M17/M16 no rotos | PASS — sin cambios core |
| 20 | DataProvider mock only | PASS — sin rama Supabase |

## Compilación Kotlin

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Resultado:

```text
BUILD SUCCESSFUL
compileLocalDebugKotlin PASS
```

## Pruebas automáticas

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "*M18EventFoundationTest" --no-configuration-cache --max-workers=1 --console=plain
```

Resultado:

```text
BUILD SUCCESSFUL — 11 tests PASS
M18EventFoundationTest ejecutado
```

## Confirmaciones de cierre

| Item | Estado |
|------|--------|
| Sin SQL | Sí |
| Sin Supabase M18 | Sí |
| Sin pagos | Sí |
| Sin PII pública | Sí |
| M18 Bloque 2 no iniciado | Sí |

## Veredicto

```text
M18 BLOQUE 1 FUNDACIÓN FUNCIONAL IMPLEMENTADA
EVENTOS VINCULADOS A ORGANIZACIONES M03
CUPOS, INSCRIPCIÓN, RECORDATORIOS MOCK Y CHECK-IN LOCAL
REPOSITORIO MOCK OPERATIVO
SIN SQL
SIN PAGOS
M18 BLOQUE 2 NO INICIADO
```
