# Configuración Supabase — Leover

Guía para conectar la app Android con tu proyecto Supabase **gratis** (plan Free).

## Lo que tenés que hacer vos (checklist)

Marca cada paso cuando lo completes:

- [ ] **1.** Crear proyecto en [supabase.com](https://supabase.com)
- [ ] **2.** Ejecutar SQL de tablas (`001_initial_schema.sql`)
- [ ] **2b.** Ejecutar trigger de registro (`004_auth_user_profile_trigger.sql`)
- [ ] **2c.** Ejecutar módulos Fase 1 (`005_fase1_community.sql`) — adopciones, perdidos, módulos activos
- [ ] **2d.** Ejecutar Sprints A–D (`006_sprints_abcd.sql`) — likes, comentarios, solicitudes adopción, tránsito, eventos, donaciones, insignias
- [ ] **2e.** Ejecutar Fase 3 chat (`007_fase3_chat.sql`) — mensajería entre usuarios
- [ ] **3.** Crear bucket Storage `leover` (público)
- [ ] **4.** Ejecutar SQL de storage (`002_storage.sql`)
- [ ] **5.** Configurar Auth (email + confirmación)
- [ ] **6.** Copiar URL y anon key a `local.properties`
- [ ] **7.** Sync Gradle y Run en Android Studio

---

## Paso 1 — Crear proyecto Supabase

1. Entrá a [https://supabase.com/dashboard](https://supabase.com/dashboard)
2. **New project**
3. Nombre sugerido: `leover-dev`
4. Elegí contraseña de base de datos (guardala)
5. Región: la más cercana (ej. South America)
6. Plan: **Free** (no pide tarjeta)

Esperá a que termine de provisionarse (~2 min).

---

## Paso 2 — Crear tablas y políticas RLS

1. En el proyecto → **SQL Editor** → **New query**
2. Copiá y ejecutá todo el contenido de:

   `supabase/migrations/001_initial_schema.sql`

3. Debería decir **Success** sin errores.

Esto crea las tablas `users`, `pets`, `posts` con Row Level Security.

4. Ejecutá también (en orden):

   - `supabase/migrations/004_auth_user_profile_trigger.sql`
   - `supabase/migrations/005_fase1_community.sql` — adopciones, perdidos/encontrados, módulos activos
   - `supabase/migrations/006_sprints_abcd.sql` — likes, comentarios, solicitudes de adopción, refugios, tránsito, eventos, donaciones, reputación
   - `supabase/migrations/007_fase3_chat.sql` — chat 1:1 (conversaciones, mensajes, función `create_direct_conversation`)
   - `supabase/migrations/008_friendships.sql` — solicitudes de amistad y privacidad de perfiles persona
   - `supabase/migrations/009_profile_privacy.sql` — columna `profile_private` configurable por usuario
   - `supabase/migrations/010_fix_chat_function.sql` — fix función `create_direct_conversation` (error peer_user_id ambiguous)

---

## Paso 3 — Crear bucket de imágenes

1. **Storage** → **New bucket**
2. Name: `leover`
3. **Public bucket**: activado ✅
4. Create bucket

---

## Paso 4 — Políticas de Storage

1. Volvé a **SQL Editor**
2. Ejecutá:

   `supabase/migrations/002_storage.sql`

---

## Paso 5 — Configurar Authentication

1. **Authentication** → **Providers** → **Email**
2. Activá **Email** provider
3. **Authentication** → **URL Configuration**:
   - **Site URL**: `com.comunidapp.app://login-callback`
   - **Redirect URLs** (Additional): agregá también `com.comunidapp.app://login-callback`
   - **Confirm email**: recomendado ON

> **Importante:** si Site URL queda en `http://localhost:3000`, el link del mail de verificación no abrirá la app.

4. **Personalizá los emails de Leover** (asunto, cuerpo en español, código de 6 dígitos):
   - Guía: [`docs/supabase-email-templates.md`](supabase-email-templates.md)
   - Plantillas HTML: carpeta `supabase/email-templates/`
   - Dashboard → **Authentication** → **Email Templates** → *Confirm sign up*

5. **SQL Editor** → ejecutá también `004_auth_user_profile_trigger.sql` (crea el perfil en `public.users` al registrarse)

Para pruebas rápidas podés desactivar "Confirm email" temporalmente; la app funciona igual pero sin verificación.

---

## Paso 6 — Credenciales en la app

1. **Project Settings** → **API**
2. Copiá:
   - **Project URL** → `SUPABASE_URL`
   - **anon public** key → `SUPABASE_ANON_KEY`

3. En la raíz del repo, editá `local.properties` (está en `.gitignore`):

```properties
sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk

SUPABASE_URL=https://TU_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Podés usar `local.properties.example` como plantilla.

4. **Sync Project with Gradle Files** en Android Studio
5. Verificá que `BuildConfig.SUPABASE_ENABLED = true` (se activa solo si ambas keys existen)

---

## Paso 7 — Probar la app

1. Run en emulador/dispositivo
2. **Registrate** con email real
3. Confirmá el email (si está activado)
4. Editá perfil + subí avatar
5. Agregá mascota con foto
6. Publicá en el feed con imagen

---

## Modo mock (sin Supabase)

Si **no** configurás `SUPABASE_URL` y `SUPABASE_ANON_KEY` en `local.properties`:

- La app usa datos locales en memoria
- Usuario demo: `maria@email.com` / `123456`
- No persiste entre reinicios

---

## Troubleshooting

| Error | Solución |
|-------|----------|
| `SUPABASE_ENABLED` false | Completar `local.properties` y sync Gradle |
| `relation "users" does not exist` | Ejecutar `001_initial_schema.sql` |
| Error al subir imagen | Bucket `leover` público + `002_storage.sql` |
| `Email not confirmed` | Revisá spam o desactivá confirmación en Auth settings |
| Proyecto pausado (Free) | Dashboard → **Restore project** (inactividad 7 días) |
| Error al registrarse pero llega el mail | Ejecutar `004_auth_user_profile_trigger.sql` y configurar Site URL (no localhost) |
| Link del mail va a localhost | Site URL = `com.comunidapp.app://login-callback` en Auth → URL Configuration |
| Link del mail → página en blanco | Normal en Gmail: usá el **código de 6 dígitos** en la app. Ver [`docs/supabase-email-templates.md`](supabase-email-templates.md) |
| Remitente dice "Supabase Auth" | Personalizá template (gratis) o configurá **Custom SMTP** con nombre "Leover" |
| **`email rate limit exceeded`** | Supabase Free limita ~3–4 emails/hora. Esperá 30–60 min, probá otro email, o desactivá **Confirm email** en Auth → Providers → Email mientras desarrollás |
| RLS permission denied | Verificar que estás logueado y las policies están aplicadas |

---

## Firebase (legacy)

La app **ya no usa Firebase** como backend activo. Los archivos `firestore.rules`, `firebase.json` y `.firebaserc` quedaron como referencia histórica; podés ignorarlos o borrarlos del repo.

---

## Costos

| Etapa | Costo |
|-------|-------|
| Desarrollo / beta chica | **USD 0** (plan Free) |
| Producción estable | Pro ~USD 25/mes cuando escale |

Límites Free: 500 MB DB, 1 GB Storage, 50k MAU — suficiente para el periodo de prueba.
