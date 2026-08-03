# RC1 — Auditoría de navegación

**Alcance:** pantallas M01–M27, shell principal, ausencia M24  
**Archivos clave:** `NavRoutes.kt`, `ComunidappNavGraph.kt`, `M17NavGraph.kt`–`M27NavGraph.kt`, `ComunidappBottomBar.kt`

## Arquitectura

Tres niveles: sesión (login/legal/onboarding) → `RootNavHost` → `MainScreen` con bottom bar (`home`, `sumate`, `publish`, `comunidad`, `profile`).

Módulos M14–M27 registrados en `mainAppRoutes()` vía extensiones por módulo.

## Matriz de acceso por área

| Área | Rutas principales | Módulos |
|------|-------------------|---------|
| Sumate | `m17/hub`, `m18/events`, `m16/shelters`, legacy foster/shelter/vet | M16, M17, M18 |
| Comunidad | `m19/feed`, `m20/inbox`, `m21/hub`, `m22/hub`, `m23/home`, `m25/hub`, `m26/hub`, `m27/hub` | M19–M23, M25–M27 |
| Perfil | mascotas, chat legacy, administración | M08, M20 legacy, M04 |
| Publicar | adopción, lost/found, eventos, donaciones | M09, M12–M13, M18, M17 |

## M24 — Pagos

- Sin constantes `M24_*` en `NavRoutes.kt`.
- Sin `M24NavGraph.kt`.
- M25/M26/M27 documentados como *sin pagos*.
- **Veredicto:** ausencia intencional; alineado con D01.

## Hallazgos

### Corregido en RC1

| ID | Severidad | Descripción | Corrección |
|----|-----------|-------------|------------|
| NAV-001 | ALTO | `AdoptionApplyScreen.onSubmitted` usaba `popUpTo(NavRoutes.ADOPTIONS)` pero la ruta `adoptions` no tiene composable registrado | Cambiado a `popUpTo(NavRoutes.SUMATE)` |

### Pendientes (no bloqueantes RC1)

| ID | Severidad | Descripción |
|----|-----------|-------------|
| NAV-002 | MEDIO | M17 hub: tabs Bienes/Voluntariado sin rutas de detalle (`onInKindDetail` / `onVolunteerDetail` vacíos) |
| NAV-003 | MEDIO | Dual legacy/modern: `CHAT` vs `M20_INBOX`, `FOSTER_*` vs `M15_*`, `SHELTER_*` vs `M16_*` |
| NAV-004 | BAJO | `ADMINISTRATIVE_OPS_HUB` definida sin composable |
| NAV-005 | BAJO | `M20_CONVERSATIONS` alias duplicado de inbox; nunca navegado |
| NAV-006 | BAJO | Deep links de notificación apuntan a `CHAT` legacy, no M20 |
| NAV-007 | BAJO | M23 provider/availability con IDs mock hardcodeados en nav graph |

## Cobertura por flujo solicitado

| # | Flujo | Estado |
|---|-------|--------|
| 1 | Pantalla inicial / sesión | OK |
| 2–4 | Login, registro, recuperación | OK (session gate) |
| 5 | Comunidad | OK |
| 6–7 | Perfil usuario / mascota | OK |
| 8–9 | Adopciones / perdidas | OK (legacy + M13) |
| 10 | Refugios | OK (M11 + M16) |
| 11 | Donaciones sin pagos | OK (M17) |
| 12 | Eventos | OK (M18) |
| 13–14 | Red social / mensajería | OK (M19/M20; legacy chat paralelo) |
| 15 | Reputación | OK (M21) |
| 16–17 | Prestadores / reservas | OK (M22/M23) |
| 18 | Marketplace sin pagos | OK (M25) |
| 19–20 | IA / integraciones | OK (M26/M27) |
| 21–24 | Admin / moderación / soporte / config | OK (M04, pantallas perfil) |
| 25 | Cerrar sesión | OK |

## Veredicto

Navegación transversal **validada** para RC1. Un defecto demostrado (NAV-001) corregido. Dual legacy documentado como deuda conocida.
