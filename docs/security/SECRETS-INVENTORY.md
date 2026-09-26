# Secrets inventory (nombres solamente)

No se imprimen valores. Distinción client/public vs server secret.  
Un backup de DB **no** es backup de secrets.

## Qué reconstruir tras desastre (LAYER 4)

| Nombre | Clase | Dónde se reconstruye | Owner rotación |
|---|---|---|---|
| `SUPABASE_URL` | public config | Dashboard → Settings → API; Android `local.properties` / flavor PROD; Edge secrets | ingeniería + Leonardo |
| `SUPABASE_ANON_KEY` / publishable | public config | misma pantalla; Android; Edge | ingeniería + Leonardo |
| `SUPABASE_SERVICE_ROLE_KEY` | **server secret** | Edge `admin-staff` only; secret manager. **Nunca Android, nunca Git** | Leonardo (rotar si leak) |
| Google OAuth client secret | **server secret** | Google Cloud + Supabase Auth provider | Leonardo |
| Google OAuth client IDs / redirect URIs | public-ish config | Auth URL config + consola Google | Leonardo |
| Firebase client `api_key` / `oauth client_id` / `mobilesdk_app_id` | client config | `app/google-services.json` (tracked; sin `private_key`) | Leonardo (restringir consola) |
| Firebase **server** `private_key` / FCM | **server secret** | no desplegado (push off); secret manager si se activa | Leonardo |
| Maps API key | client, restrict | Google Cloud credentials | Leonardo |
| Edge secrets (mismos nombres URL/anon/service_role) | mix | Dashboard Edge → Secrets en **cada** proyecto | ingeniería |

Rotación: cambiar valor en el proveedor → actualizar solo el destino que lo usa → redeploy Edge si aplica → no commitear el valor.  
Tras restore-to-new-project: **nuevos** URL/keys; no reutilizar service_role de STAGING en Android.

## Server secrets (nunca Android, nunca Git)

| Nombre | Dónde vive | Attack surface |
|---|---|---|
| `SUPABASE_SERVICE_ROLE_KEY` | Edge secret `admin-staff` | bypass RLS si se filtra |
| Google OAuth client secret | Supabase Auth / Google Cloud | account takeover OAuth |
| Firebase **server** credentials (`private_key` / FCM server) | no desplegado (push off) | push/admin Firebase |
| Cualquier signing secret futuro (klipy, webhooks) | no desplegar sin review | — |

## Client / public configuration (no son service secrets)

| Nombre | Dónde | Nota |
|---|---|---|
| `SUPABASE_URL` | Android BuildConfig / Edge | project URL |
| `SUPABASE_ANON_KEY` | Android BuildConfig / Edge | publishable; sujeta a RLS |
| Firebase client `api_key` / `oauth client_id` / `mobilesdk_app_id` | `app/google-services.json` tracked | restringir en consola; **no** hay `private_key` |
| Maps API key | consola / app config | restringir paquete+SHA |

## Inspección SEC-FINAL

- `app/src/main`: no `SUPABASE_SERVICE_ROLE_KEY`.  
- Gradle: falla el build si la key de staging/local contiene `service_role`.  
- `local.properties`: gitignored.  
- Migrations / docs / scripts versionados: no se encontraron valores de service_role ni OAuth secret.  
- `vitacora-import-analyze` no usa service_role.  
- No rotar ahora (no hay exposición de server secret confirmada).
