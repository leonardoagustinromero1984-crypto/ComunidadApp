# PRODUCTION SECURITY DEPLOYMENT — LeoVer

Orden seguro. STAGING (`tobqbddfcyitwgbkthhy`) y PRODUCCIÓN **no** comparten credentials.  
Nunca copiar `SUPABASE_SERVICE_ROLE_KEY` dentro del APK.  
No usar el proyecto obsoleto `wystsapjfpdtoprlmizz`.

## 1. Crear / configurar proyecto Production

- Nuevo project ref.  
- Región documentada.  
- Plan con backup diario (PITR si el RPO lo exige).  
- Completar `PRODUCTION-MANUAL-CHECKLIST.md` (backups/PITR/retención/spend).

## 2. Secrets

Crear **nuevos** valores (nombres solamente):

- `SUPABASE_URL`  
- `SUPABASE_ANON_KEY` (publishable; Android + Edge)  
- `SUPABASE_SERVICE_ROLE_KEY` (solo Edge `admin-staff`)  
- Google OAuth client secret  
- Maps key (restringida)  
- Firebase/FCM solo si se despliega push (hoy no)

No reutilizar secrets de STAGING.

## 3. OAuth

- Google client de **producción**.  
- Redirects / package / SHA de release.  
- Auth URL configuration = Production, no STAGING.

## 4. Migrations

- Aplicar el árbol `infra/supabase-canonical/supabase/migrations/` en orden.  
- No editar migraciones ya aplicadas en STAGING.  
- Registrar `supabase_migrations.schema_migrations`.  
- Incluye 1061–1065.

## 5. Edge Functions

Desplegar solo las que ya están en el attack surface de STAGING, tras review:

- `admin-staff` (`verify_jwt: true`; service_role server-side)  
- `vitacora-import-analyze` (`verify_jwt: true`; JWT del caller; sin service_role)

**No** desplegar sin review: `push`, `delete-account`, `klipy-proxy`.

## 6. Storage

Crear buckets con la config live de STAGING:

| bucket | public | max size | MIME bucket |
|---|---|---|---|
| public-media | yes | 50 MiB | null (validar en `canon_register_media`) |
| private-media | no | 50 MiB | null |
| documents | no | 50 MiB | null |
| moderation-evidence | no | 50 MiB | null |
| vitacora-import | no | 5 MiB | null |

No crear `profile-avatars` / `organization-media` sin RLS y camino canónico.  
Signed URL privada: RPC `canon_authorize_media_signed_url` + Storage RLS. Cierre total del mint SDK = Edge futura (P1 abierto).

## 7. RLS / grants

- Confirmar `relrowsecurity` en todas las tablas public.  
- No reintroducir GRANT DML residual (1065).  
- Android no escribe `country_markets` / hours por tabla; usa RPC.

## 8. Admin root

- Crear root técnico `is_root` único.  
- No convertirlo en PERSON.  
- No backdoor MFA.

## 9. MFA

- Enroll TOTP nativo del root y de todo staff.  
- AAL2 obligatorio para operaciones administrativas (1062/1064).  
- `admin_clear_must_change_password` es self-only AAL1 (password-before-MFA).

## 10. Rate limits

- Aplicar propuesta `PRODUCTION-INITIAL-LIMITS.md` (más estricta o igual que STAGING; no subir).  
- Kill switches en `security_feature_flags`.

## 11. Feature flags

Decidir explícitamente video / reels / imports el día del launch.

## 12. Smoke tests (dirigidos)

- Login PERSON + logout (`SupabaseAuthRepository.signOut`).  
- Login admin + MFA AAL2.  
- Staff disabled no opera.  
- PERSON no llama staff/moderation.  
- Signed URL cruzada FORBIDDEN.  
- Media register respeta quota.  
- No ejecutar emulator ni suite completa en la PC de desarrollo salvo lo acordado.

## 13. Backup / PITR verification

- Completar checklist Dashboard.  
- Restore drill en destino **aislado** (SEC-04). Sin drill: gate BLOCKED salvo aceptación escrita de Leonardo.

## 14. Release Android

- Flavor Production: URL + anon key nuevos.  
- Gradle debe fallar si la key contiene `service_role`.  
- Una sola `assemble` de release cuando toque.  
- No incrustar Edge secrets.

## 15. Monitoring inicial

Ver `PRODUCTION-SECURITY-GATE.md` → OBSERVABILITY.  
Primeras 48 h: throttle admin, RATE_LIMIT_EXCEEDED, QUOTA, IMPORT_THROTTLED, errores 5xx Edge, spend.
