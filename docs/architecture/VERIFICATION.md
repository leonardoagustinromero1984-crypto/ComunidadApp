# LeoVer verification

Human review of PERSON / organization functions. Not a second account.

Statuses: `NOT_REQUESTED`, `PENDING`, `REQUIRES_CORRECTION`, `VERIFIED`, `REJECTED`, `SUSPENDED`, `EXPIRED`.

Table: `leover_verification_requests`. Capability row: `person_capabilities.verification_status`. Orgs: `organizations.verification_status`.

Required to request (enforced by `canon_request_leover_verification`): complete PERSON profile, contact (phone or verified email), base location for RESCUER/FOSTER and org address for SHELTER/NGO/VET/BUSINESS, terms_accepted in evidence. Evidence documents remain optional. Independent rescuers are not required to have legal personhood.

Android: `LeoverVerificationRequestScreen` llama `canon_request_leover_verification` con `p_function_code` + `p_organization_id` (SHELTER/NGO/VET/BUSINESS). El perfil de organización ya no usa `NOT_IMPLEMENTED_PRODUCT`. Copy humano: “Aún no solicitaste la verificación” / “Verificación pendiente” / “Necesitamos que corrijas información” / “Verificado por LeoVer” / “Solicitud de verificación rechazada” / “Verificación suspendida”. Si faltan requisitos, la UI lista exactamente qué falta (Ubicación *, Teléfono/email *, términos *) y no envía. Evidence institucional sigue opcional.

Admin/staff with `staff.manage`: VERIFY / REQUIRES_CORRECTION / REJECT / SUSPEND. Store reviewer, time, note.

Badge: verified → “Verificado por LeoVer”. Otherwise show “Aún no verificado”.

`SUSPENDED` never enters FOUND fanout even if `receive_nearby_cases` is true.

`VERIFIED` ≠ available. Availability is `persons.receive_nearby_cases` / org equivalent.
