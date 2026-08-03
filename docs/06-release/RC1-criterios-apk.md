# RC1 — Criterios para generación de APK

RC1 **no genera APK**. Este documento define cuándo proceder.

## Pre-requisitos obligatorios

| # | Criterio | RC1 |
|---|----------|-----|
| 1 | main = origin/main, working tree limpio | ✓ |
| 2 | Compilación Kotlin PASS | ✓ (ver cierre) |
| 3 | Tests transversales PASS | ✓ |
| 4 | Documentación RC1 completa | ✓ |
| 5 | Sin hallazgos CRÍTICOS abiertos | ✓ |
| 6 | M24 fuera de alcance confirmado | ✓ |
| 7 | Staging no modificado en RC1 | ✓ |

## Criterios recomendados antes de APK

| # | Criterio | Estado |
|---|----------|--------|
| 1 | Decisión producto sobre dual legacy (chat/feed) | Pendiente |
| 2 | Plan reconciliación SQL-001 (039–052) | Documentado |
| 3 | M17 tabs in-kind/volunteer (NAV-002) | Pendiente |
| 4 | Credenciales staging en `local.properties` / CI secrets | Operativo |
| 5 | Plan manual RC1 revisado por QA | Pendiente |

## Comando APK (futuro — no ejecutar en RC1)

```powershell
.\gradlew.bat assembleLocalDebug --no-configuration-cache --max-workers=1 --console=plain
```

## Variantes

| Variante | Uso |
|----------|-----|
| `localDebug` | Mock o staging local |
| `stagingDebug` | Si flavor staging configurado |

## Exclusiones RC1

- No `assembleRelease`
- No firma producción
- No ProGuard/R8 release tuning
- No JaCoCo
- No lint gate

## Veredicto

RC1 **documenta** criterios. APK **todavía no generada** por decisión de etapa.
