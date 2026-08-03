# RC1 — Auditoría de resiliencia y errores

## Escenarios revisados

| Escenario | Comportamiento esperado | Evidencia |
|-----------|------------------------|-----------|
| Timeout / red perdida | Error tipado; UI Error state | Supabase repos → mappers |
| Sesión expirada | Re-auth flow M01 | AuthProvider |
| Token inválido | Mock safe mode | AppConfigProvider |
| Respuesta vacía | Empty states en ViewModels | pantallas M17–M27 |
| Recurso eliminado | Failure tipado | repo Result |
| Permiso denegado | Código *PERMISSION_DENIED* | M23/M27 tests |
| Supabase no disponible | Fallback mock si creds inválidas | AppConfigProvider |
| M05 no disponible | storage null; uploads fallan gracefully | DataProvider |
| M06 no disponible | Hook `available=false`; no bloquea M23/M25 | tests |
| M20/M21/M26/M27 caídos | Operación principal continúa | adapter pattern |

## Checklist transversal

| # | Criterio | Estado |
|---|----------|--------|
| 1 | Sin loops infinitos | OK |
| 2 | Sin polling agresivo | OK |
| 3 | Sin delays artificiales en prod | OK |
| 4 | Sin retries ilimitados | OK (idempotencia M23/M25/M27) |
| 5 | Refresh fallido no borra datos actuales | OK (StateFlow) |
| 6 | Estados Empty/Error/PartialData | OK en módulos M16–M27 |
| 7 | Dependencia opcional no rompe flujo | OK (M06 hooks) |
| 8 | Notificación fallida no revierte op | OK |
| 9 | Webhook fallido no revierte fuente | OK (M27 simulado) |
| 10 | IA fallida no modifica entidad | OK (M26 stub) |
| 11 | UI no carga eternamente | OK (ViewModel phases) |
| 12 | Coroutines cancelables | OK (viewModelScope) |
| 13 | Filtros cancelan trabajo previo | OK en búsquedas |

## Hallazgos

| ID | Severidad | Descripción | Acción |
|----|-----------|-------------|--------|
| RES-001 | BAJO | Legacy screens sin Empty state uniforme | Backlog UI |
| RES-002 | INFO | M07 metrics mock no reflejan caída remota | By design |

## Veredicto

Resiliencia **aceptable** para RC1. Patrón de hooks opcionales (M06) verificado.
