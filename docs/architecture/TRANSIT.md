# Transit (hogar de tránsito)

FOSTER is a PERSON capability. It does **not** receive FOUND waves.

Verification is human and lighter than an organization, but required for matching.

Profile: available flag, capacity 1 / 2 / 3+, optional preferences (dogs/cats, age, treatments, other animals, notes). Exact pin is private; public view is zone / approximate distance.

Request: `canon_request_foster_for_pet` on the pet. Eligible homes (`VERIFIED` + available + base location) apply with `canon_apply_to_foster_request`. The responsible chooses one via `canon_select_foster_applicant`. First applicant does not win.

Same `pet_id` and VitaCora. Temporary care permissions only: moments, photos, allowed care. Cannot change owner, transfer, delete, or manage permanent responsibles.

States (conceptual / existing foster): REQUESTED → MATCHED → ACTIVE → COMPLETED / CANCELLED.

Completion writes start/end/home/next destination into VitaCora care history. Destination: responsible, another transit, owner, adoption.
