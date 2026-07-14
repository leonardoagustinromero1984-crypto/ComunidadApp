# Leover

Plataforma móvil para la comunidad animal: adopciones, refugios, avisos de perdidos/encontrados, servicios y publicaciones.

> **Backend oficial actual:** Supabase (Auth + PostgreSQL + Storage + Edge Functions).  
> **Push:** Firebase Cloud Messaging.  
> No hay servidor NestJS ni Docker Compose de base de datos en este repositorio.

Documentación índice: [`docs/README.md`](docs/README.md).

## Stack

| Capa | Tecnología |
|------|------------|
| App | Kotlin, Jetpack Compose, Material 3 |
| Arquitectura | MVVM, Navigation Compose, Coroutines / Flow |
| Datos | Repositorios + mock local **o** Supabase Kotlin |
| Backend | Supabase |
| Push | Firebase Cloud Messaging |
| Build | Gradle Kotlin DSL, AGP 9.x, JDK 17 |

`applicationId` actual: `com.comunidapp.app` (migración a `com.leover.app` documentada en [ADR-0006](docs/adr/ADR-0006-Migracion-del-paquete-a-Leover.md); **no** renombrar sin tarea aprobada).

## Requisitos

- Android Studio (Ladybug o superior)
- JDK 17
- minSdk 26, targetSdk 36
- (Opcional) cuenta Supabase Free para datos remotos

## Cómo ejecutar

### 1. Clonar y abrir

Abrí el proyecto en Android Studio y esperá el sync de Gradle.

### 2. Modo mock (sin backend)

Sin `SUPABASE_URL` / `SUPABASE_ANON_KEY` en `local.properties`, la app usa datos locales.

Credenciales mock:

- Email: `maria@email.com`
- Contraseña: `123456`

### 3. Modo Supabase

1. Copiá el ejemplo de configuración:

```bash
# Windows PowerShell
Copy-Item local.properties.example local.properties
```

2. Completá en `local.properties` (este archivo **nunca** se commitea):

```properties
sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://TU_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.EJEMPLO_FICTICIO
```

3. Ejecutá las migraciones SQL en orden bajo `supabase/migrations/` (guía: [`docs/supabase-setup.md`](docs/supabase-setup.md)).
4. Creá el bucket Storage `leover`.
5. Sync Gradle → Run.

Si las variables están vacías o incompletas, la app **cae a modo mock** (no debe crashear por falta de secretos).

### 4. Push (opcional)

FCM requiere `app/google-services.json` y, para envío desde servidor, secrets de la Edge Function `push` (ver `supabase/.env.example` y [`docs/leover-push-fcm-setup.md`](docs/leover-push-fcm-setup.md)).

## Compilar, tests y lint

```bash
# Debug APK
./gradlew.bat :app:assembleDebug

# Pruebas unitarias
./gradlew.bat :app:testDebugUnitTest

# Lint (objetivo: 0 errors)
./gradlew.bat :app:lintDebug
```

En Unix/macOS usá `./gradlew` en lugar de `./gradlew.bat`.

CI (push/PR a `main`): [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) — assemble + unit tests + lint en modo mock, sin inyectar secretos.

Configuración tipada: `AppConfigProvider` / `FeatureFlags` (`core/config`, `core/featureflags`). Ver [ADR-0005](docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md).

## Qué no commitear

| Archivo / patrón | Motivo |
|------------------|--------|
| `local.properties` | SDK path + claves Supabase |
| `supabase/.env` | Secrets de Edge Functions |
| `*.jks` / `*.keystore` | Firmas |
| APK/AAB generados | Artefactos de build |

`app/google-services.json` está versionado porque el repo es **privado**. Si el repo se hace público, sacarlo del Git.

## Documentación

| Recurso | Contenido |
|---------|-----------|
| [`docs/README.md`](docs/README.md) | Índice oficial |
| [`docs/01-producto/D01-Modulos-y-Orden.md`](docs/01-producto/D01-Modulos-y-Orden.md) | Orden de módulos |
| [`docs/02-arquitectura/arquitectura-inicial.md`](docs/02-arquitectura/arquitectura-inicial.md) | Arquitectura real |
| [`docs/adr/`](docs/adr/) | Decisiones aceptadas |
| [`docs/supabase-setup.md`](docs/supabase-setup.md) | Setup Supabase |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Cómo contribuir |

## Licencia

Proyecto privado — uso interno del equipo.
