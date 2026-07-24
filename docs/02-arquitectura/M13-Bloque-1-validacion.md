# M13 Bloque 1 — Validación

## Estado

```text
M13 BLOQUE 1 CERRADO LOCALMENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

## Criterios DoD (spec §13)

| # | Criterio | Resultado |
|---|---|---|
| 1 | Legacy conservado | PASS |
| 2 | Dominio M13 completo | PASS |
| 3 | Matching determinista/explicable | PASS |
| 4 | Sin autoconfirmación | PASS |
| 5 | Contratos y fakes | PASS |
| 6 | Navegación básica | PASS |
| 7 | Vistas públicas redactadas | PASS |
| 8 | Media refs seguras `m05:` | PASS |
| 9 | Pruebas focalizadas | PASS |
| 10 | `compileLocalDebugKotlin` | PASS |
| 11 | Sin migración `048` | PASS |
| 12 | Sin SQL aplicado | PASS |
| 13 | Un commit/push | PASS |
| 14 | M12 pendiente externo documentado | PASS |

## Archivos principales

- `data/model/M13SightingModels.kt`
- `data/repository/M13*.kt`
- `data/remote/supabase/m13/M13ErrorMapper.kt`
- `viewmodel/M13SightingViewModels.kt`
- `ui/screens/m13/M13SightingScreens.kt`
- `navigation/NavRoutes.kt` + `ComunidappNavGraph.kt`
- `data/provider/DataProvider.kt`
- tests `M13FoundationTest` / `M13StaticGuardsTest`

## Pendientes

- Bloque 2 persistencia (`048` solo con aprobación).
- Autoridad M08/M03/M04 completa.
- Push M06.
- Smoke funcional M12 externo.
