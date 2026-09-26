# ANDROID-COMMUNITY-CARE-02 — GAP AUDIT

Physical Round 3. PASS funcional = UI alcanzable + acción real + backend conectado.
Una tabla o RPC sola no es PASS.

Última migration de este bloque: **1096**.
APK de esta ronda: ver informe final.
Web: NO. UI Foundation: NO.

## Verification

| Función | Estado | Evidencia |
|---|---|---|
| Solicitar verificación LeoVer | IMPLEMENTED AND REACHABLE | `LeoverVerificationRequestScreen` → `canon_request_leover_verification`. STAGING row `de09f427-…` PENDING (SHELTER / org `prueba`). |
| Refresh inmediato PENDING | PARTIAL → fix Round 3 | Antes: estado en `remember`; al remount volvía NOT_REQUESTED. Ahora ViewModel + CTA oculto en PENDING. |
| Idempotencia PENDING | BACKEND ONLY → 1096 | Unique pending + return existing. |
| Badge / listado propio | IMPLEMENTED AND REACHABLE | `canon_list_my_leover_verifications`. |
| Review admin | IMPLEMENTED BUT UNREACHABLE | `OrganizationVerificationQueueScreen` / M16 review existen; no son entry de usuario Community. |

## Transit

| Función | Estado | Evidencia |
|---|---|---|
| Ofrecer tránsito | IMPLEMENTED AND REACHABLE | Sumate → Ofrecer hogar de tránsito. |
| Solicitar tránsito para pet | PARTIAL | Routes `FOSTER_CARE_REQUEST` / open requests. |
| Elegir postulante | PARTIAL | `ChooseFosterApplicantScreen` wired. |
| Inbox tránsito | IMPLEMENTED AND REACHABLE | Foster context bottom nav. |

## Adoption

| Función | Estado | Evidencia |
|---|---|---|
| Sumate → Adopción | IMPLEMENTED AND REACHABLE | Route `ADOPTIONS`. Round 3: copy “Adopción” + perfil / publicar / postulaciones. |
| Explorar publicaciones | IMPLEMENTED AND REACHABLE | `canon_list_adoptions`. |
| Mi perfil de adopción | IMPLEMENTED AND REACHABLE | `ADOPTION_GENERAL_PROFILE`. |
| Publicar en adopción | IMPLEMENTED AND REACHABLE | `ADOPTION_FORM` (visible según contexto). |
| Postulaciones | IMPLEMENTED AND REACHABLE | MY / RECEIVED applications. |

## Community

| Función | Estado | Evidencia |
|---|---|---|
| Cerca mío | IMPLEMENTED AND REACHABLE | `canon_list_community_nearby` radio **25000 m**, limit **80**, `ORDER BY meters`. Sin lat/lng → `LOCATION_INVALID`. |
| Horarios en nearby | BACKEND ONLY | RPC devuelve `hours_json` null (1095). Horarios viven en `provider_weekly_hours`. |
| Ficha pública / publicar | PARTIAL | `MY_BUSINESS` publica; after success navega a Comunidad. |
| QA dataset | BACKEND ONLY | `infra/supabase-canonical/qa/seed_community_care_02.sql` (no migration permanente). |

## Professional Android

| Función | Estado | Evidencia |
|---|---|---|
| Consultorio workspace | PARTIAL → fix Round 3 | Antes `MY_BUSINESS` (ficha). Ahora tab → `PROFESSIONAL_HUB`. Ficha = “Ver mi ficha en Comunidad”. |
| Agenda | PARTIAL | Hub → `MY_VETERINARY_APPOINTMENTS`. |
| Pacientes + búsqueda | PARTIAL → fix Round 3 | `PROFESSIONAL_PATIENTS` + `canon_search_professional_patients` (ACL / creados / org / grants). No global. |
| Paciente creado por vet | PARTIAL | `VET_CREATE_PATIENT` + `canon_create_vet_patient`. Email invite; no owner hasta claim. |
| QR / atención clínica | PARTIAL | M28 clinic care routes existen; no todos tienen tab propio. |

## Membership (sin paywall)

| Función | Estado | Evidencia |
|---|---|---|
| Trial / capability contract | BACKEND ONLY | 1089 membership trial SQL. Sin paywall UI. |
| Usar LeoVer como | IMPLEMENTED AND REACHABLE | Round 3: select → Home/Feed, `popUpTo(HOME)`. |

## FOUND / LOST

| Función | Estado | Evidencia |
|---|---|---|
| Publicar FOUND/LOST | IMPLEMENTED AND REACHABLE | `canon_create_lost_found` (1095 geo + 1096 label). |
| Ubicación humana FOUND | PARTIAL → fix Round 3 | Antes note dummy. Ahora reverse geocode + `location_label`. Legacy sin label → “Zona cercana”. |
| Selector LOST | PARTIAL → fix Round 3 | Filtra `FOUND_CASE` / archivados. “Sin nombre” real STANDARD se conserva. |
| Match candidate | BACKEND ONLY → 1096 | Round 3 audit: candidate=0 porque `ST_Distance` bajo `search_path=public` se tragaba. Rematch OPEN FOUNDs. |
| Notificación owner | PARTIAL → fix Round 3 | M06 ausente. 1096 escribe `notifications` + outbox. Android: `m06_get_inbox` fallback `canon_list_my_notifications`. |
| Claim 2s | IMPLEMENTED AND REACHABLE | 1091. |

## VitaCora / QR

| Función | Estado | Evidencia |
|---|---|---|
| Info animal → Compartir QR | PARTIAL → fix Round 3 | Antes VitaCora intermedio. Ahora `m14/pets/{petId}/share`. |

## Conteos (funciones de contrato auditadas)

- IMPLEMENTED AND REACHABLE: 12
- IMPLEMENTED BUT UNREACHABLE: 1
- PARTIAL: 12
- BACKEND ONLY: 5
- MISSING: 0 en el perímetro Community Care 02 (no se inventaron features nuevas)

## No iniciado

- LEOVER-UI-FOUNDATION-01
- Web
