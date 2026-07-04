# Leover

Plataforma móvil para la comunidad animal: adopciones, refugios, avisos de perdidos/encontrados y publicaciones. Conecta personas, mascotas, refugios y organizaciones en un único ecosistema.

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Navigation Compose
- Firebase Auth, Firestore y Storage (opcional con `google-services.json`)
- Datos mock locales para desarrollo sin Firebase

## Requisitos

- Android Studio (Ladybug o superior)
- JDK 17
- minSdk 26, targetSdk 36

## Cómo ejecutar

1. Clonar el repositorio.
2. Abrir la carpeta del proyecto en Android Studio.
3. Sincronizar Gradle (File → Sync Project with Gradle Files).
4. Ejecutar en emulador o dispositivo (`app` → Run).

### Modo mock (sin Firebase)

Sin `app/google-services.json`, la app usa datos en memoria:

- Email: `maria@email.com`
- Contraseña: `123456`

Los datos no persisten entre reinicios. Ideal para desarrollo de UI.

### Modo Firebase (producción / staging)

1. Seguir [`docs/firebase-setup.md`](docs/firebase-setup.md).
2. Colocar `google-services.json` en `app/`.
3. Desplegar reglas: `firebase deploy --only firestore:rules,storage`
4. Registrarse con email real y verificar la cuenta desde el enlace del correo.

La app recuerda la sesión: si ya iniciaste sesión, al reabrir entrás directo al inicio. Podés cerrar sesión desde **Mi perfil**.

## Funcionalidades (Fase 0)

| Área | Qué incluye |
|------|-------------|
| Sesión | Login, registro, verificación email, recuperar contraseña, sesión persistente |
| Perfil | Ver/editar perfil, avatar, tipo de cuenta, perfil público |
| Mascotas | CRUD completo, foto, salud (vacunas, desparasitación), FAB agregar |
| Feed | Publicaciones generales con imagen, adopciones y perdidos/encontrados |
| Adopciones / Refugios | Listados y detalle (mock en Fase 0) |

## Estructura

```
app/src/main/java/com/comunidapp/app/
├── navigation/     # Rutas y grafo de navegación
├── ui/             # Pantallas, componentes y tema
├── data/           # Modelos, mock, repositorios y capa Firebase
└── viewmodel/      # ViewModels por pantalla
```

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [`docs/leover-requirements.md`](docs/leover-requirements.md) | Requisitos funcionales |
| [`docs/leover-phase0-implementation.md`](docs/leover-phase0-implementation.md) | Plan de implementación Fase 0 |
| [`docs/leover-firestore-model.md`](docs/leover-firestore-model.md) | Modelo de datos Firestore |
| [`docs/firebase-setup.md`](docs/firebase-setup.md) | Configuración Firebase paso a paso |
| [`docs/qa-checklist-phase0.md`](docs/qa-checklist-phase0.md) | Checklist QA manual |

## Tests

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Licencia

Proyecto privado — uso interno del equipo.
