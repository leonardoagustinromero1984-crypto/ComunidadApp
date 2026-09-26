# INCIDENT RESPONSE — LeoVer

Documento mínimo operable. No incluye secretos reales.  
Owner de decisión: Leonardo. Cursor no rota secretos ni acepta riesgos.

Por cada escenario: detect → contain → revoke/rotate → recover → verify → document.

## Contacto y canales

- Owner: Leonardo  
- Evidencia: tickets internos + `docs/security/` (sin PII, sin JWT, sin bodies de chat, sin datos de salud).  
- No commitear dumps. No pegar service_role en chat.

## Escenarios

### 1. Sospecha de account compromise (PERSON)

- **Detect:** reportes, login inesperado, mensajes/posts no reconocidos, reset no pedido.  
- **Contain:** Auth → sign-out global de esa sesión/usuario; deshabilitar cuenta si hay abuso (`change_user_account_status` AAL2).  
- **Revoke/rotate:** reset password; no rotar service_role por una cuenta PERSON.  
- **Recover:** no restore de proyecto. Revisar posts/media del usuario.  
- **Verify:** el dueño entra; sesiones ajenas muertas.  
- **Document:** user_id (no password), timestamps, acciones.

### 2. Staff / admin compromise

- **Detect:** AAL2 fallido repetido, staff_audit anómalo, cambios de roles no pedidos, `admin_begin_login` throttle.  
- **Contain:** `staff_set_disabled` (AAL2 + staff.manage); sign-out Auth; no tocar root salvo otro SUPERADMIN.  
- **Revoke/rotate:** reset password Edge `admin-staff`; `staff_reset_mfa` (nunca root). Si cayó el root: restore Auth + rotar service_role.  
- **Recover:** revisar `platform_admin_identities`, assignments, Edge secrets.  
- **Verify:** disabled no opera; último SUPERADMIN intacto; root `is_root` único.  
- **Document:** quién deshabilitó, motivo, no TOTP secrets.

### 3. service_role exposure

- **Detect:** key en log, ticket, APK, repo, captura.  
- **Contain:** asumir bypass de RLS desde el momento de exposición. Kill switches: `imports.enabled`, `reels.create.enabled`, `media.video.upload.enabled`.  
- **Revoke/rotate:** rotar `SUPABASE_SERVICE_ROLE_KEY` en Dashboard; redeploy `admin-staff`; invalidar copias.  
- **Recover:** auditar cambios recientes; no “arreglar” recreando backdoors.  
- **Verify:** Android/APK sigue sin service_role; Edge usa el secret nuevo.  
- **Document:** cuándo se rotó (no el valor).

### 4. OAuth secret exposure

- **Detect:** client secret de Google en Git, log o chat.  
- **Contain:** deshabilitar el client comprometido en Google Cloud.  
- **Revoke/rotate:** crear secret/client nuevo; actualizar Auth → Providers → Google. Redirects solo Production.  
- **Recover:** sesiones OAuth existentes según política de Google; pedir re-login.  
- **Verify:** redirects no apuntan a STAGING en PROD.  
- **Document:** client_id (público) + fecha de rotación. Nunca el secret.

### 5. Data leak

- **Detect:** URL pública de objeto privado, dump, screenshot de VitaCora/salud, lista de usuarios.  
- **Contain:** hacer privado / borrar objeto; revocar signed URLs (TTL corto); kill switch media si es masivo.  
- **Revoke/rotate:** si hubo service_role o JWT de staff, rotar esos secretos.  
- **Recover:** no re-publicar; notificar owner. Restore solo si hubo borrado + destino aislado.  
- **Verify:** objeto ya no es listable anónimo; RLS sigue deny.  
- **Document:** bucket/path, no el contenido médico.

### 6. Abusive user

- **Detect:** quota events, reports, spam.  
- **Contain:** rate limits 1063; status de cuenta; no ban silencioso de historial ajeno.  
- **Revoke/rotate:** n/a salvo tokens de ese usuario.  
- **Recover:** triage `list_moderation_queue` AAL2.  
- **Verify:** quotas siguen fail-closed en media/imports.  
- **Document:** operation_key, used_units (ya sanitizado).

### 7. Media abuse (bytes / video)

- **Detect:** `UPLOAD_QUOTA_EXCEEDED`, picos Storage, videos masivos.  
- **Contain:** `media.video.upload.enabled` / `reels.create.enabled` = false.  
- **Revoke/rotate:** n/a.  
- **Recover:** borrar objetos abusivos; quotas diarias se resetan por ventana.  
- **Verify:** register media fail-closed.  
- **Document:** unidades, no URLs firmadas.

### 8. Unexpected cost spike

- **Detect:** Billing/Usage dashboard; Storage egress; Edge invocations.  
- **Contain:** kill switches; bajar policies; no desplegar `push` / `klipy-proxy`.  
- **Revoke/rotate:** keys de Maps/Firebase si hay abuso de API externa.  
- **Recover:** no contratar extras sin autorización.  
- **Verify:** spend alert disparó (cuando esté configurada — hoy MANUAL).  
- **Document:** métrica y acción.

### 9. Compromised Edge Function

- **Detect:** logs anómalos, deploy no reconocido, secret leído.  
- **Contain:** redeploy revisión anterior del repo; pausar función en Dashboard si hace falta.  
- **Revoke/rotate:** `SUPABASE_SERVICE_ROLE_KEY` si `admin-staff` filtró; JWT no se loguean.  
- **Recover:** código canónico = Git (`infra/supabase-canonical/supabase/functions/`).  
- **Verify:** `verify_jwt: true`; no hay funciones extra (`push` / `delete-account` / `klipy-proxy` siguen sin deploy).  
- **Document:** versión desplegada.

### 10. Lost production database

- **Detect:** proyecto inaccesible / drop.  
- **Contain:** no restaurar encima de STAGING. No usar `wystsapjfpdtoprlmizz`.  
- **Revoke/rotate:** si el incidente es compromiso, rotar service_role y OAuth.  
- **Recover:** Dashboard Restore o dump hacia **destino aislado autorizado**; luego cutover. Ver SEC-04.  
- **Verify:** schema, FKs, RLS, RPC AAL2, sequence VitaCora, catalogs, rate-limit tables, Auth linkage.  
- **Document:** RPO alcanzado vs esperado.

### 11. Storage loss

- **Detect:** 404 masivo, bucket vacío.  
- **Contain:** DB restore **no** recupera bytes.  
- **Revoke/rotate:** n/a.  
- **Recover:** backup de objetos o re-upload; metadata `media_assets` puede quedar huérfana.  
- **Verify:** 1 objeto public-media + 1 private-media + 1 vitacora-import.  
- **Document:** buckets afectados, no listar PII de paths privados.

## Qué no loguear nunca

passwords, JWT, `SUPABASE_SERVICE_ROLE_KEY`, bodies de mensajes privados, datos médicos innecesarios, TOTP secrets, signed URL tokens.
