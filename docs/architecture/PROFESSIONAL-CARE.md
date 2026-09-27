# Professional care (Android V1)

One pet. The professional keeps a **private** history keyed by pet + organization/professional. Vet A does not see Vet B.

Writing to the shared VitaCora is only via `vitacora_update_proposals`. Owner accepts or rejects. Reject does not delete the professional record.

Professionals do not edit pet identity (name, species, breed, sex, declared age).

Attachments follow the same propose → accept/reject path.

Creating a patient: `canon_create_vet_patient` + mandatory owner email. Invite writes `email_outbox` template `vet_pending_owner_invite` (idempotent). Delivery needs STAGING Auth SMTP / Edge drain — no provider secret in repo. Verified email auto-links via `on_auth_user_link_pending_pets` → OWNER on the same pet. No duplicate pet.

Agenda reuses veterinary appointments: PENDING / CONFIRMED / ATTENDED / CANCELLED.

Membership: commercial/professional functions have a documented 90-day trial (`membership_entitlements`). `paywall_enforced = false`. No payments in this block.

Web clinic/ERP is not implemented. Same tables must be reused later.
