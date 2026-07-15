# M01 — Matriz de eliminación de cuenta

**Fecha:** 2026-07-14  
**Regla:** borrado duro / cascade para datos actuales de M01 (D-M01-14).  
**Service role:** solo Edge Function `delete-account`.  
**Release legal:** borradores no habilitan release publicable (D-M01-15).

Orden global sugerido en la función:

1. Registrar/actualizar `account_deletion_requests` (pending)  
2. Borrar objetos Storage del usuario  
3. Borrar filas owned (si no cascade automático basta)  
4. Borrar `auth.users` (cascade a `public.users` y dependientes)  
5. Marcar request completed / failed  

---

## Storage

| Entidad | Propietario | Acción | Orden | Razón | Prueba | Rollback |
|---------|-------------|--------|------:|-------|--------|----------|
| `storage.objects` bucket `leover` paths `users/{uid}/**` | path prefix | **delete** | 2 | Avatars; no borrar paths ajenos | list+delete por prefix | no; re-upload |
| `storage.objects` `pets/{uid}/**` | path prefix | **delete** | 2 | Fotos mascota del owner | idem | no |
| `storage.objects` `posts/**`, `lost_found/**` | URL en fila | **delete** best-effort por URL parseada + **delete** filas | 2–3 | Evitar basura huérfana | staging | no |

Paths reales (`StoragePaths`): revisar `userAvatar`, `petPhoto`, `postImage`, `lostFoundImage`.

---

## Auth / perfil

| Entidad | FK | Acción | Orden | Razón |
|---------|-----|--------|------:|-------|
| `auth.users` | — | **delete** (admin API) | último | Identidad |
| `public.users` | `auth.users` ON DELETE CASCADE | **cascade** | tras Auth | Perfil |
| `public.user_consents` | `auth.users` CASCADE | **cascade** | — | Consentimientos |
| `public.account_deletion_requests` | `auth.users` SET NULL | **retain** row + `completed` | 1/5 | Auditoría sin PII |
| `public.device_tokens` | `users` CASCADE | **cascade** | — | FCM |

---

## Contenido / social (cascade vía `public.users`)

| Entidad | Acción | Nota |
|---------|--------|------|
| `pets` | cascade | Incluye health fields |
| `posts`, `post_likes`, `post_comments`, `post_saves` | cascade | |
| `friend_connections` | cascade | Ambas direcciones |
| `user_blocks` | cascade | |
| `user_badges` | cascade | |
| `conversations` / participants / `messages` | cascade participant; mensajes del sender cascade | Conversaciones 1:1 pueden quedar vacías — documentado; OK M01 |
| `notifications` | cascade | |
| `content_reports` (reporter) | cascade | `reviewed_by` SET NULL si aplica |

## Comunidad / adopción / servicios

| Entidad | Acción | Nota |
|---------|--------|------|
| `adoptions`, `adoption_requests`, `adoption_matches` | cascade / set null shelter | Hard delete M01; módulos futuros revisan retención formal |
| `lost_found_posts`, `lost_found_sightings` | cascade | |
| `shelters`, `foster_homes`, `foster_requests` | cascade | No borrar org ajena: solo si `owner_id`/`host_id` = uid |
| `community_events`, `event_interests` | cascade | |
| `donation_campaigns` | cascade | Sin política de facturación inventada |
| `service_profiles`, `service_bookings`, `service_reviews` | cascade | |
| `shop_products` | cascade | |
| `payment_intents` | cascade | **Deuda:** M24/pagos debe redefinir retención legal; hoy cascade |
| `pet_clinical_records` | cascade | **Deuda:** salud futura puede exigir retain; hoy hard delete M01 |

---

## Bloqueos

Ninguna FK conocida sin estrategia: todas apuntan a `public.users`/`auth.users` con cascade/set null revisado en migraciones 001–014.

**No** eliminar organizaciones/perfiles de **otros** usuarios.

---

## Compensación / fallo parcial

Si falla Storage → marcar `failed` + `failure_code=storage` y **no** borrar Auth.  
Si falla Auth tras Storage/DB parcial → `failed` + compensación manual staging.  
Reintento: idempotente (Storage delete ignore missing; Auth delete ignore already gone).
