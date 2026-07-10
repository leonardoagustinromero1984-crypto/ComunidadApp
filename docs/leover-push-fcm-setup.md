# LeoVer — Push FCM (retención)

Flujo: la app inserta en `notifications` → Database Webhook → Edge Function `push` → FCM → dispositivo.

## 1. Migración

En el SQL Editor de Supabase, ejecutar (después de 011–012):

```
supabase/migrations/013_device_tokens_fcm.sql
```

## 2. Firebase

1. Proyecto Firebase ya ligado (`app/google-services.json`, package `com.comunidapp.app`).
2. Cloud Messaging API (V1) habilitada en Google Cloud.
3. Crear **Service Account** con rol *Firebase Cloud Messaging Admin* (o usar la default de Firebase).
4. Descargar JSON de la cuenta y anotar:
   - `project_id`
   - `client_email`
   - `private_key`

## 3. Secrets de la Edge Function

En Supabase → Edge Functions → Secrets:

| Secret | Valor |
|--------|--------|
| `FIREBASE_PROJECT_ID` | `project_id` del JSON |
| `FIREBASE_CLIENT_EMAIL` | `client_email` |
| `FIREBASE_PRIVATE_KEY` | `private_key` (mantener `\n` o pegar con saltos reales) |

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` ya están disponibles en el runtime.

## 4. Deploy de la función

```bash
supabase functions deploy push --project-ref <tu-ref>
```

O subir `supabase/functions/push/index.ts` desde el Dashboard.

## 5. Database Webhook

Supabase → Database → Webhooks → Create:

- **Table:** `notifications`
- **Events:** `INSERT`
- **Type:** HTTP Request
- **URL:** `https://<project-ref>.supabase.co/functions/v1/push`
- **HTTP Headers:**
  - `Authorization` = `Bearer <SUPABASE_ANON_KEY o SERVICE_ROLE_KEY>`
  - `Content-Type` = `application/json`

## 6. App Android

- Permiso `POST_NOTIFICATIONS` (Android 13+): se pide al abrir.
- Canal `leover_default`.
- Token FCM se guarda en `device_tokens` al login y en `onNewToken`.

Eventos que ya crean notificación in-app (y por tanto push si el webhook está activo):

- Solicitud / aceptación de amistad
- Mensaje de chat
- Solicitud de adopción
- Solicitud de tránsito
- Reserva de turno

## 7. Prueba rápida

1. Dos usuarios logueados en dos dispositivos (o emulador + físico).
2. Enviar solicitud de amistad o un mensaje.
3. Verificar fila en `notifications` y en `device_tokens`.
4. Logs de la función `push` en Dashboard → Edge Functions.

Sin webhook/secrets, las notificaciones **in-app** siguen funcionando; solo falta el push en background.
