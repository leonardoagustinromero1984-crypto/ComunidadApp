# RC1.1 — Auditoría inicial de onboarding

**Fecha:** 2026-08-02  
**HEAD base:** `d326498` — `build(rc1): generate debug apk and prepare manual testing`  
**Alcance:** flujo de inicio, sesión, navegación y almacenamiento local previo a RC1.1.

## Decisión de pantalla inicial

| Punto | Ubicación | Comportamiento previo |
|-------|-----------|------------------------|
| Splash / arranque | `ComunidappNavGraph` → `startDestination` según sesión | Login si no hay sesión; `MainScreen` si hay sesión válida |
| Post-login | NavHost principal | Llegada directa a tabs (Home / Comunidad / Sumate / Perfil) |
| Onboarding legacy perfil | `PROFILE_ONBOARDING` | Flujo separado de completar perfil remoto (M02) |

**RC1.1:** Tras sesión válida, `MainScreen` consulta `FirstRunOnboardingViewModel.shouldAutoShow()` y navega a `first_run_onboarding/false` si el progreso local es `NOT_STARTED` o `IN_PROGRESS`.

## Almacenamiento local disponible

| Mecanismo | Uso previo | Reutilizado en RC1.1 |
|-----------|------------|----------------------|
| DataStore Preferences | No existía para onboarding | **Sí** — `OnboardingPreferencesRepository` (`leover_first_run_onboarding`) |
| SharedPreferences / Room | Otros módulos | No duplicado |
| Perfil remoto (Supabase) | M02 | Solo lectura/escritura opcional en setup mínimo vía `UserRepository.updateMyProfile` |

## Rutas reutilizables (mapeo de intenciones)

| Intención | Ruta NavRoutes |
|-----------|-----------------|
| REGISTER_PET | `ADD_PET` |
| LOST_PET / FOUND_ANIMAL | `PUBLISH_LOST_FOUND` |
| ADOPT | `SUMATE` |
| OFFER_FOSTER | `PUBLISH_FOSTER` |
| ORGANIZATION | `MY_ORGANIZATIONS` |
| VOLUNTEER | `M17_HUB` |
| EXPLORE | `HOME` |
| Privacidad | `LEGAL_PRIVACY` |

## Permisos Android (auditoría previa)

Los permisos se solicitan en contexto desde pantallas de publicación, cámara, ubicación y notificaciones. **El onboarding RC1.1 no agrega permisos al Manifest ni invoca `ActivityResult` de permisos.**

## Brechas identificadas

1. No existía flujo de primer ingreso orientado a acción.
2. No había persistencia local de progreso de tutorial.
3. No había ayudas contextuales de primera visita reutilizables.
4. `ORGANIZATION` no ofrece sub-opciones (crear / invitación) — se usa `MY_ORGANIZATIONS` como ruta estable más cercana.
5. Documento Maestro integral aún es placeholder; párrafo de activación añadido en D01 y `docs/00-maestro/README.md`.

## Archivos modificados / creados (RC1.1)

**Nuevos:** `domain/onboarding/*`, `data/local/Onboarding*.kt`, `FirstRunOnboardingViewModel`, `FirstRunOnboardingScreen`, `ContextualFirstVisitHelp`, tests, docs RC1.1.

**Modificados:** `NavRoutes`, `ComunidappNavGraph`, `ProfileScreen`, `MyPetsScreen`, `PublishForms`, `M16ShelterScreens`, `AdoptionDetailScreen`, `gradle/libs.versions.toml`, `app/build.gradle.kts`.

## Restricciones respetadas

- Sin SQL, migraciones, staging ni producción.
- Sin M24, sin M28, sin APK en esta entrega.
