# Leover

Plataforma móvil para la comunidad animal: adopciones, refugios, avisos de perdidos/encontrados y publicaciones.

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Navigation Compose
- **Supabase** (Auth + Postgres + Storage) — backend principal
- Datos mock locales si no hay credenciales configuradas

## Requisitos

- Android Studio (Ladybug o superior)
- JDK 17
- minSdk 26, targetSdk 36

## Cómo ejecutar

### 1. Clonar y abrir en Android Studio

### 2. Configurar Supabase (recomendado)

Seguí la guía completa: [`docs/supabase-setup.md`](docs/supabase-setup.md)

Resumen rápido:

1. Crear proyecto en [supabase.com](https://supabase.com) (plan Free)
2. Ejecutar SQL en `supabase/migrations/`
3. Crear bucket Storage `leover` (público)
4. Agregar a `local.properties`:

```properties
SUPABASE_URL=https://TU_PROJECT.supabase.co
SUPABASE_ANON_KEY=eyJ...
```

5. Sync Gradle → Run

### 3. Modo mock (sin Supabase)

Sin credenciales en `local.properties`:

- Email: `maria@email.com`
- Contraseña: `123456`

## Tests

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [`docs/supabase-setup.md`](docs/supabase-setup.md) | **Setup Supabase (empezá acá)** |
| [`docs/leover-requirements.md`](docs/leover-requirements.md) | Requisitos funcionales |
| [`docs/qa-checklist-phase0.md`](docs/qa-checklist-phase0.md) | Checklist QA manual |

## Licencia

Proyecto privado — uso interno del equipo.
