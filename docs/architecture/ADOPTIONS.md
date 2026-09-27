# Adoptions

One pet, one VitaCora. Publication does not clone identity.

General profile (`adoption_general_profiles`) is per PERSON, reusable. Optional fields may be empty. Do not re-ask data already on PERSON.

“Quiero adoptar” uses that profile and opens the existing messaging channel between applicant and responsible. No per-shelter dynamic questionnaires.

Existing application statuses plus PAUSED/COMPLETED/CLOSED where the current module maps them. Accepting one application sets it ACCEPTED and pauses others. It does **not** transfer the pet.

Transfer is `1071` care transfers. `m09_finalize_adoption` now raises `ADOPTION_USE_CANONICAL_TRANSFER` and does not change owner. A completed care transfer marks the chosen application COMPLETED and archives the rest.

Post-adoption follow-up stays on the same VitaCora under a specific grant. The previous responsible is not OWNER and cannot transfer/delete through that grant. Dates/requirements are configured by the delivering party (existing M09 follow-up), not hardcoded.
