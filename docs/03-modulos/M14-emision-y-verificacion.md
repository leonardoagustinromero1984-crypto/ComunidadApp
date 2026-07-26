# M14 — Emisión y verificación humana (Bloque 3)

## Migración

```text
supabase/migrations/052_m14_credential_verification_and_public_access.sql
```

Forward-only sobre 050/051. **No aplicada** remotamente en este bloque.

## Capacidades

- Abrir revisión (`PENDING` → `UNDER_REVIEW`)
- Aprobar / rechazar (solo desde `UNDER_REVIEW`)
- Expirar (`PENDING`/`UNDER_REVIEW` → `EXPIRED`)
- Emisión directa verificada (sin solicitud ficticia)
- Revocación de credencial `VERIFIED`
- Rotación de `public_code` (sin tocar `passport_number`)
- Historial tipificado (metadatos; estado de pasaporte sin cambio en eventos de credencial)
- QR/deep link: `leover://passport/{publicCode}` sin PII

## Autoridad

Actor = `auth.uid()`. Decisión vía `_m14_can_decide_request` (emisor objetivo / M12 / moderador).  
Anti-autoverificación: responsable M08 y solicitante no deciden (salvo moderador).

## Concurrencia

`FOR UPDATE` + decisión única (`request_id` unique) + idempotencia / `CONFLICT`.

## 10 RPC nuevas

`m14_open_verification_review`, `m14_approve_verification_request`, `m14_reject_verification_request`, `m14_expire_verification_request`, `m14_get_verification_decision`, `m14_list_verification_decisions`, `m14_issue_verified_credential`, `m14_revoke_verified_credential`, `m14_rotate_public_code`, `m14_list_passport_status_history`.

## Seguridad

- SECURITY DEFINER + `search_path = public`
- authenticated EXECUTE; anon sin EXECUTE en las 10
- Helpers `_m14_*` revocados
- SELECT authenticated + RLS; sin DML directo
- Sin historia clínica; sin `service_role` en Android

## CI

Guard highest migration: **052**.
