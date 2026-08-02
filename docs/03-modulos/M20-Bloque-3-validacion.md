# M20 Bloque 3 — Validación

**Fecha cierre local:** 2026-08-02  
**Commit:** `744118a` — `feat(m20): add messaging operations and privacy`  
**Veredicto local:** PASS

## Incidencia de tests (documentada)

Durante la validación local se observó lo siguiente:

| Hecho | Detalle |
|-------|---------|
| Intentos interrumpidos | Varios runs de Gradle quedaron colgados **>9 minutos** |
| Exit code | `4294967295` (proceso interrumpido; **no** indica assertion fallida) |
| Evidencia de FAIL funcional | **Ninguna** en los intentos interrumpidos |
| Acción correctiva infra | Proceso Gradle/Java bloqueado **terminado correctamente** |
| Causa funcional encontrada | `validateReplyTarget` se invocaba con `replyToMessageId = null` y rechazaba envíos normales |
| Corrección | Validación de reply **solo** cuando `replyToMessageId != null` (`M20MessagingRepositories.kt`) |
| Validación final | **46/46 PASS** |

Los intentos interrumpidos **no** se registran como FAIL funcional.

### Comando final exitoso

```bat
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m20.*" --no-configuration-cache --max-workers=1 --console=plain
```

**Duración aproximada final:** ~47 s (suite completa, sin cuelgue).

**Tests pendientes:** ninguno.

## Tests focalizados

| Suite | Casos | Resultado |
|-------|-------|-----------|
| `M20MessagingFoundationTest` | 11 | PASS |
| `M20MessagingRemoteMapperTest` | 10 | PASS |
| `M20MessagingOperationsTest` | 25 | PASS |
| **Total** | **46** | **46/46 PASS** |

## Compilación

```bat
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

**Resultado:** PASS

## Navegación

- Comunidad → Mensajería → `m20/inbox` ✓
- Hilo → `m20/conversations/{conversationId}` ✓
- Alias legacy `m20/conversations` ✓

## Confirmaciones al cierre Bloque 3

| Item | Estado |
|------|--------|
| Mock ampliado Bloque 3 | Sí |
| Fix `validateReplyTarget` | Sí |
| 062 no aplicada (correcto en B3) | Sí |
| 063 no creada aún en B3 | Sí |
| Supabase repo stubs edit/delete/create (pre-B4) | Sí |
| Moderación M04 adapter | Sí |
| SQL staging | Pendiente Bloque 4 |

## Veredicto

```text
M20 BLOQUE 3 CERRADO LOCALMENTE
TESTS 46/46 PASS
COMPILACIÓN KOTLIN PASS
BLOQUE 4 — PARIDAD REMOTA Y STAGING (siguiente paso)
```
