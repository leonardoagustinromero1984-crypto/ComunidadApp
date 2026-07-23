# M09 — Persistencia de postulaciones de adopción

## Migración

`supabase/migrations/038_m09_adoption_applications.sql`

Forward-only sobre 001–037. **No reescribe** migraciones anteriores. Asume que **037** ya está aplicada. **No aplicada** en remoto en este bloque.

## Tabla `public.adoption_applications`

Nueva tabla (la legacy `adoption_requests` permanece intacta e incompatible con el modelo M09).

Columnas: `id`, `adoption_id`, `applicant_user_id`, `message`, `housing_type`, `has_other_pets`, `previous_experience`, `contact_phone`, `status`, `submitted_at`, `reviewed_at`, `reviewed_by`, `rejection_reason`, `created_at`, `updated_at`.

Estados: `SUBMITTED`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`.

## Índices

- `adoption_applications_adoption_id_idx`
- `adoption_applications_applicant_user_id_idx`
- `adoption_applications_status_idx`
- `adoption_applications_one_active_uidx` — unique parcial `(adoption_id, applicant_user_id)` donde status ∈ {SUBMITTED, UNDER_REVIEW, ACCEPTED}
- `adoption_applications_one_accepted_uidx` — unique parcial por `adoption_id` donde status = ACCEPTED

## Seguridad

- SELECT RLS: solo postulante o gestor de la publicación (`_m09_actor_can_manage_adoption`).
- INSERT/UPDATE/DELETE directo: denegado; mutaciones vía RPC `m09_*`.
- Las postulaciones **nunca** son públicas.
- Teléfono de contacto no se expone en listados públicos de Android.

## Aceptación transaccional

`m09_accept_application`:

1. Valida autenticación y permiso de gestor.
2. Exige publicación en `PUBLISHED`.
3. Marca la postulación elegida `ACCEPTED`.
4. Rechaza las demás activas (`SUBMITTED` / `UNDER_REVIEW`).
5. Pausa la publicación (`PAUSED`).
6. **No** marca la mascota como adoptada (eso sigue en finalización / `m09_mark_adoption_adopted`).

## RPCs

- `m09_submit_application`
- `m09_withdraw_application`
- `m09_mark_application_under_review`
- `m09_accept_application`
- `m09_reject_application`
- `m09_list_my_applications`
- `m09_list_received_applications`
- `m09_get_application`
