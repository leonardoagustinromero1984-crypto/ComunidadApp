# RC1.1 — Arquitectura del onboarding de primer ingreso

## Modelo de dominio

- `OnboardingStatus`: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`
- `OnboardingStep`: `WELCOME` → info (`IDENTITY`, `HELP_NETWORK`, `COMMUNITY_AND_CARE`) → `FIRST_INTENT` → `MINIMAL_SETUP` → `PRIVACY` → `COMPLETION`
- `OnboardingIntent`: ocho intenciones de primera acción (sin roles permanentes)
- `ContextualHelpId`: `PET_PASSPORT`, `ALERTS`, `ADOPTIONS`, `SHELTERS`
- `OnboardingProgress`: versión, estado, paso, intención, timestamps, ayudas vistas, borradores de nombre/zona aproximada

Versión actual: `ONBOARDING_VERSION = 1`.

## Persistencia

`DataStoreOnboardingPreferencesRepository` (producción) e `InMemoryOnboardingPreferencesRepository` (tests).

Reglas:
- Fallo al guardar no bloquea navegación (`persistFailed` informativo).
- `SKIPPED` / `COMPLETED` no se muestran automáticamente.
- Reinicio visual desde Configuración resetea paso a `WELCOME` y ayudas contextuales; no borra cuenta ni datos remotos.

## ViewModel

`FirstRunOnboardingViewModel`:
- Carga progreso al iniciar
- Avanza / omite / selecciona intención / setup mínimo / completa
- Emite efectos de navegación una sola vez (`navigationEmitted`)
- `restartTutorialVisual()` para “Ver tutorial de inicio”

## UI

`FirstRunOnboardingScreen`: Compose simple, scroll, botones grandes, indicador “N de 3”, marca **LeoVer**.

`ContextualFirstVisitHelp`: banner dismissible; persistencia vía repositorio.

## Cuándo se muestra

- **Sí:** sesión válida, antes del uso normal de tabs, `NOT_STARTED` o `IN_PROGRESS`
- **No:** login, recuperación de contraseña, `COMPLETED`, `SKIPPED`, cada apertura

## Permisos en contexto

Sin solicitud de cámara, ubicación, notificaciones, archivos, contactos ni micrófono durante el onboarding.

## Primera acción útil

Conceptual: completar mascota, alerta, hallazgo, postulación, tránsito, organización, voluntariado o exploración. Sin analítica externa nueva; sin eventos fuera de la app.

## Accesibilidad

Content descriptions, encabezados semánticos, texto ampliable con scroll, indicador numérico de progreso.

## Rendimiento

Sin video, Lottie pesado ni red obligatoria en el flujo.
