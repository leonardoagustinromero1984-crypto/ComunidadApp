# RC1 — Cierre de consolidación

**Etapa:** Consolidación transversal M00–M27 — Release Candidate 1  
**Fecha cierre:** 2026-08-02

## Entregables

| # | Entregable | Archivo / evidencia |
|---|------------|---------------------|
| 1 | Verificación inicial | `RC1-verificacion-inicial.md` |
| 2 | Auditoría navegación | `RC1-auditoria-navegacion.md` |
| 3 | Fix navegación NAV-001 | `ComunidappNavGraph.kt` |
| 4 | Auditoría DataProvider | `RC1-auditoria-dataprovider.md` |
| 5 | Matriz integraciones | `RC1-matriz-integraciones-M00-M27.md` |
| 6 | Auditoría permisos | `RC1-auditoria-permisos.md` |
| 7 | Auditoría PII | `RC1-auditoria-privacidad-pii.md` |
| 8 | Auditoría resiliencia | `RC1-auditoria-resiliencia.md` |
| 9 | Auditoría migraciones | `RC1-auditoria-migraciones-001-077.md` |
| 10 | Deuda 039–052 | `RC1-deuda-migraciones-039-052.md` |
| 11 | Tests transversales | `PlatformCrossModuleIntegrationTest.kt` |
| 12 | Auditoría UI/textos | `RC1-auditoria-ui-textos.md` |
| 13 | Backlog hallazgos | `RC1-backlog-hallazgos.md` |
| 14 | Resumen ejecutivo | `RC1-resumen-ejecutivo.md` |
| 15 | Matriz módulos | `RC1-matriz-modulos.md` |
| 16 | Plan prueba manual | `RC1-plan-prueba-manual.md` |
| 17 | Criterios APK | `RC1-criterios-apk.md` |

## Cambios de código

1. **NAV-001:** `popUpTo(NavRoutes.ADOPTIONS)` → `popUpTo(NavRoutes.SUMATE)` en flujo post-solicitud adopción.
2. **Tests:** 21 casos en `PlatformCrossModuleIntegrationTest`.

## Restricciones respetadas

- Sin SQL aplicado; sin migración 078.
- Sin staging/producción modificados.
- Sin APK; sin lint; sin JaCoCo.
- Un commit; push al final.
- M24 no desarrollado; M28 no creado.

## Resultados de validación

Completar tras ejecución Parte O:

| Validación | Resultado |
|------------|-----------|
| Tests `PlatformCrossModuleIntegrationTest` | **20/20 PASS** |
| `compileLocalDebugKotlin --max-workers=1` | **PASS** |
| `git diff --check` | Sin conflictos |

## Veredicto final

Ver `RC1-resumen-ejecutivo.md`.

```
CONSOLIDACIÓN M00–M27 COMPLETADA
M24 PAGOS CONTINÚA POSPUESTO
M28 NO EXISTE
NAVEGACIÓN TRANSVERSAL VALIDADA
PERMISOS Y PRIVACIDAD AUDITADOS
MIGRACIONES 001–077 AUDITADAS
DEUDA 039–052 DOCUMENTADA SIN APLICAR
RELEASE CANDIDATE 1 DOCUMENTADA
APK TODAVÍA NO GENERADA
PRODUCCIÓN NO AFECTADA
```
