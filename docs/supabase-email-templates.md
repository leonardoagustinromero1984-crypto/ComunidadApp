# Emails de Leover en Supabase — guía paso a paso

Si el mail dice **"Supabase Auth"**, **"Confirm your email address"** o tiene el pie *"powered by Supabase"*, **todavía no aplicaste los templates de Leover** en el dashboard. Eso no se arregla desde el código de la app.

---

## Paso 1 — Personalizar asunto y cuerpo (gratis)

1. Entrá a [Supabase Dashboard](https://supabase.com/dashboard) → tu proyecto.
2. **Authentication** → **Email Templates**.
3. Elegí **Confirm sign up**.
4. **Subject:** pegá el contenido de `supabase/email-templates/confirm-signup-subject.txt`  
   → debe quedar: `Confirmá tu cuenta en Leover`
5. **Body (HTML):** pegá todo el contenido de `supabase/email-templates/confirm-signup-body.html`.
6. **Save**.

Repetí para **Reset password** con los archivos `reset-password-subject.txt` y `reset-password-body.html`.

> El template de Leover usa el **código de 6 dígitos** (`{{ .Token }}`). Es la forma más confiable en Android.

---

## Paso 2 — URLs para que el link no quede en blanco

1. **Authentication** → **URL Configuration**.
2. Configurá:

| Campo | Valor exacto |
|-------|----------------|
| **Site URL** | `com.comunidapp.app://login-callback` |
| **Redirect URLs** | `com.comunidapp.app://login-callback` |

3. **Save**.

### Por qué el link muestra página en blanco

1. Gmail/Chrome abren el link en un navegador.
2. Supabase confirma el email y redirige a `com.comunidapp.app://login-callback`.
3. El navegador **no puede mostrar** esa URL (es de la app) → pantalla blanca.
4. A veces la app se abre sola en segundo plano; muchas veces **no**.

**Recomendación:** usá siempre el **código de 6 dígitos** en la app (pantalla *Confirmar email* → *Confirmar con código*).

---

## Paso 3 — Cambiar remitente "Supabase Auth" → "Leover"

En el plan **Free**, el nombre **Supabase Auth** no se puede cambiar sin SMTP propio.

1. **Authentication** → **SMTP Settings**.
2. Activá **Enable Custom SMTP**.
3. Completá con tu proveedor (Resend, Brevo, SendGrid, etc.).
4. **Sender name:** `Leover`
5. **Sender email:** `noreply@tudominio.com` (o el que permita el proveedor).
6. **Save**.

Sin SMTP custom vas a seguir recibiendo mails desde `noreply@mail.app.supabase.io` con nombre Supabase, aunque el texto ya diga Leover.

---

## Paso 4 — Probar

1. Borrá el usuario de prueba en **Authentication → Users** (o usá otro email).
2. Registrate de nuevo en la app.
3. Verificá que el mail tenga:
   - Asunto en español: *Confirmá tu cuenta en Leover*
   - Código de 6 dígitos visible
   - Sin texto "Confirm your email address" en inglés
4. En la app: **Confirmar con código** (no dependas del link).
5. Iniciá sesión.

---

## Límite de emails (plan Free)

~3–4 emails por hora. Si ves `email rate limit exceeded`, esperá 30–60 minutos.

---

## Resumen rápido

| Problema | Solución |
|----------|----------|
| Mail en inglés / "Supabase Auth" | Pegar templates del paso 1 (+ SMTP paso 3 para el nombre) |
| Link → página en blanco | Normal en navegador; usá el código de 6 dígitos |
| Link no abre la app | Verificar Site URL y Redirect URLs (paso 2) |
