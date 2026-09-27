# SEC-05 — Matriz de autorización (STAGING)

Proyecto: `tobqbddfcyitwgbkthhy`. Evidencia de RPC/RLS, no pentest. Sin credenciales ni PII.

| ACTOR | RESOURCE | ACTION | EXPECTED | ACTUAL | RESULT |
|---|---|---|---|---|---|
| ANON | admin helpers / rate internals | EXECUTE | DENY | GRANT EXECUTE anon = false | PASS |
| ANON | `social_posts` INSERT | write | DENY | no INSERT grant | PASS |
| ANON | catalog tables | SELECT | ALLOW (active catalogs) | SELECT grant + RLS | PASS |
| PERSON_A | own pet | update | ALLOW | `canon_update_pet` ALLOWED | PASS |
| PERSON_A | own private media | `canon_authorize_media_signed_url` | ALLOW | ALLOWED | PASS |
| PERSON_B | PERSON_A `privacy_state` | UPDATE | DENY | EXCEPTION / RLS | PASS |
| PERSON_B | `staff.manage` / `moderation.view` | has_permission | false | false | PASS |
| PERSON_B | `list_moderation_queue` | read queue | FORBIDDEN | FORBIDDEN | PASS |
| PERSON_B | `get_admin_session` | admin session | null (not identity) | returns null | PASS |
| PERSON_B | PERSON_A private media | signed URL authorize | FORBIDDEN | FORBIDDEN | PASS |
| ORG_MEMBER_A | ORG_B invitations | `canon_list_org_invitations` | FORBIDDEN | FORBIDDEN | PASS |
| ORG_MEMBER_A | `staff.manage` | platform privilege | false | false | PASS |
| PET_CREATOR | shared pet | update / grant VitaCora | ALLOW | ALLOWED | PASS |
| PET_RESPONSIBLE | shared pet | update allowed fields | ALLOW | ALLOWED | PASS |
| PET_RESPONSIBLE | shared pet | archive | FORBIDDEN | FORBIDDEN | PASS |
| PET_RESPONSIBLE | shared pet | invite another responsible | FORBIDDEN | FORBIDDEN | PASS |
| PET_RESPONSIBLE | VitaCora grant | revoke (`vitacora.share`) | FORBIDDEN | FORBIDDEN | PASS |
| PROFESSIONAL_GRANTED | pet | become holder | false | `_acl_pet_holder` false (when tested via grant path) | PASS |
| PROFESSIONAL_GRANTED | pet | archive | FORBIDDEN | FORBIDDEN (first suite when C existed; grant path uses ESSENTIAL) | PASS |
| PROFESSIONAL_GRANTED | proposal | `canon_create_proposal` with grant | ALLOW | ALLOWED | PASS |
| STRANGER | creator pet list on profile | `canon_list_pets_for_person_profile` | `[]` unless friends | skipped if <3 PERSON | INFO |
| STRANGER | A–B messages | `canon_list_messages` | FORBIDDEN | skipped if <3 PERSON | INFO |
| ADMIN_AAL1 | `get_admin_session` | operational session | MFA_REQUIRED | MFA_REQUIRED | PASS |
| ADMIN_AAL1 | `staff_register_identity` | staff create | MFA_REQUIRED | MFA_REQUIRED | PASS |
| ADMIN_AAL1 | `list_moderation_queue` | view queue | MFA_REQUIRED | MFA_REQUIRED after 1064 (was ALLOWED before) | PASS |
| ADMIN_AAL1 | `admin_search_users` | search | MFA_REQUIRED | MFA_REQUIRED after 1064 | PASS |
| ADMIN_AAL2 + permission | staff / catalogs / wrapped RPCs | operate | ALLOW | unchanged 1062 wrappers | PASS (code + prior SEC-02) |
| MODERATOR_AAL2 | `staff.manage` | staff admin | DENY | not in role matrix | PASS |
| SUPPORT_AAL2 | `moderation.view` | moderation | DENY | not in role matrix | PASS |
| SUPERADMIN_AAL2 | `staff.manage` | staff | ALLOW | only SUPERADMIN in matrix | PASS |
| disabled admin | `get_admin_session` | operate | null | `disabled_at is null` filter | PASS (code) |
| root | MFA reset / role strip | mutate root | ROOT_PROTECTED | 1062 `staff_reset_mfa` / set_role | PASS (code) |
