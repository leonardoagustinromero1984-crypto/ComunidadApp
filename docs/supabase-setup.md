# Configuración Supabase — Leover

Guía para conectar la app Android con tu proyecto Supabase **gratis** (plan Free).

## Lo que tenés que hacer vos (checklist)

Marca cada paso cuando lo completes:

- [ ] **1.** Crear proyecto en [supabase.com](https://supabase.com)
- [ ] **2.** Ejecutar SQL de tablas (`001_initial_schema.sql`)
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
3. **Authentication** → **Settings** (o URL Configuration):
   - **Confirm email**: recomendado ON (como Firebase)
   - Site URL: `com.comunidapp.app://login-callback` (opcional para deep links futuros)

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
