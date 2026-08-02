# M20 Bloque 1 — Validación

## Alcance implementado

- Auditoría, matriz funcional, arquitectura
- Modelos interno/público, validadores, errores, repositorio mock
- Rutas `m20/conversations`, `m20/conversations/{id}`
- Pantallas lista e hilo
- ViewModels con estados Loading/Content/Empty/Error
- DataProvider `m20MessagingRepository` (mock only)
- Acceso Comunidad → "Mensajería (M20)"
- Tests focalizados `M20MessagingFoundationTest`

## Archivos código creados

- `app/src/main/java/com/comunidapp/app/data/model/M20MessagingModels.kt`
- `app/src/main/java/com/comunidapp/app/domain/m20/M20PrivacySanitizer.kt`
- `app/src/main/java/com/comunidapp/app/domain/m20/M20MessagingResilience.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M20MessagingValidators.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M20ContextHintResolver.kt`
- `app/src/main/java/com/comunidapp/app/data/repository/M20MessagingRepositories.kt`
- `app/src/main/java/com/comunidapp/app/data/remote/supabase/m20/M20MessagingErrors.kt`
- `app/src/main/java/com/comunidapp/app/viewmodel/M20MessagingViewModels.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/m20/M20MessagingScreens.kt`
- `app/src/main/java/com/comunidapp/app/navigation/M20NavGraph.kt`
- `app/src/test/java/com/comunidapp/app/domain/m20/M20MessagingFoundationTest.kt`

## Archivos código modificados

- `app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt`
- `app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt`
- `app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt`
- `app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt`

## Validación focalizada

| # | Criterio | Resultado |
|---|----------|-----------|
| 01 | Lista conversaciones con seeds | PASS |
| 02 | Context hints pet/org/event | PASS |
| 03 | Envío texto en ACTIVE | PASS |
| 04 | BLOCKED rechaza envío | PASS |
| 05 | Archivar conversación | PASS |
| 06 | Bloqueo idempotente (stub) | PASS |
| 07 | PII redactada en textos públicos | PASS |
| 08 | Sin userId en modelos públicos | PASS |
| 09 | Estados mensaje SENT/DELIVERED/READ | PASS |
| 10 | Adjunto solo referencia (sin upload) | PASS |
| 11 | private:// adjunto rechazado | PASS |
| 12 | markConversationRead → READ | PASS |
| 13 | Sin SQL | PASS |
| 14 | DataProvider mock only | PASS |
| 15 | Entrada Comunidad (no Eventos) | PASS |

## Pruebas automáticas

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m20.*" --no-configuration-cache --max-workers=1 --console=plain
```

## Confirmaciones de cierre

| Item | Estado |
|------|--------|
| Sin SQL aplicado | Sí |
| Sin upload adjuntos | Sí |
| Sin PII pública | Sí |
| M20 Bloque 2 pendiente | Sí |

## Veredicto

```text
M20 BLOQUE 1 FUNDACIÓN FUNCIONAL IMPLEMENTADA
CONVERSACIONES, HILO, ENVÍO TEXTO Y BLOQUEO STUB
CONTEXTO M08/M03/M18 EN MOCK
REPOSITORIO MOCK OPERATIVO
SIN SQL APLICADO
M20 BLOQUE 2 PENDIENTE
```
