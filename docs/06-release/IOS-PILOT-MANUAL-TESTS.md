# IOS-PILOT — Manual test matrix (iPhone real)

All RESULT = NOT_RUN until executed on device. Do not mark PASS without evidence.

| ID | Case | PRECONDITION | STEPS | EXPECTED | RESULT | EVIDENCE |
| -- | ---- | ------------ | ----- | -------- | ------ | -------- |
| P-01 | Cold launch | App installed | Kill app → open | Splash/login or restored session; no crash | NOT_RUN | |
| P-02 | Login | Unauth | Email/password | Home authenticated | NOT_RUN | |
| P-03 | Session restore | Logged in | Kill → reopen | Session restored or clear login | NOT_RUN | |
| P-04 | Logout | Logged in | Cerrar sesión | Login; no private screens; no pending private nav | NOT_RUN | |
| P-05 | Profile edit | Auth | Edit name/city → save | Updated profile | NOT_RUN | |
| P-06 | Avatar | Auth | Pick image → save | Avatar shown; no raw errors | NOT_RUN | |
| P-07 | Pet create | Auth | Create pet | Detail with new pet | NOT_RUN | |
| P-08 | Pet edit | Own pet | Edit fields → save | Detail refreshed | NOT_RUN | |
| P-09 | Pet health | Own ACTIVE pet | Edit vaccines/health → save | Health section updated | NOT_RUN | |
| P-10 | Archive/restore | Own ACTIVE | Archive → (detail) Reactivar | Status transitions; list ACTIVE only | NOT_RUN | |
| P-11 | Lost create | Auth | Publish LOST | Detail + public code | NOT_RUN | |
| P-12 | Found create | Auth | Publish FOUND | Detail | NOT_RUN | |
| P-13 | L/F edit | Owner ACTIVE | Edit desc/location/photo | Updated | NOT_RUN | |
| P-14 | Resolve | Owner ACTIVE | Marcar resuelto | Status RESOLVED | NOT_RUN | |
| P-15 | Adoption browse | Auth | Open list → detail | Content / empty / error sane | NOT_RUN | |
| P-16 | Publish adoption | Auth | Publish | Detail | NOT_RUN | |
| P-17 | Apply | Auth | Apply | Success / conflict sanitized | NOT_RUN | |
| P-18 | Withdraw | Own app | Withdraw if allowed | Status updated | NOT_RUN | |
| P-19 | Shelter review | Owner pub | Accept/reject | Status updated | NOT_RUN | |
| P-20 | Public pet link | Any | Open https pet or leover deep link | Public content; no PII | NOT_RUN | |
| P-21 | Public adoption | Any | Public adoption URL | Public content | NOT_RUN | |
| P-22 | Public lost | Any | Public lost URL | Public content | NOT_RUN | |
| P-23 | Public found | Any | Public found URL | Public content | NOT_RUN | |
| P-24 | Notif permission | Auth NotDetermined | Activar | Prompt → Authorized/Denied UX | NOT_RUN | |
| P-25 | Push prefs | Auth | Toggle category push | Saved remotely | NOT_RUN | |
| P-26 | Quiet hours | Auth | Set window + days + TZ | Saved; reload persists | NOT_RUN | |
| P-27 | Custom scheme | Device | `leover://passport/CODE` | Landing/content | NOT_RUN | |
| P-28 | Apple Sign In blocker | Device | Tap Continuar con Apple | ConfigRequired or native sheet; no secrets crash | NOT_RUN | |
| P-29 | APNs registration | Device + capability | After permission | Register success or MissingToken without fake ACTIVE | NOT_RUN | |
| P-30 | Multi-user privacy | Two accounts | A load private → logout → B login | B never sees A profile/pets/apps/media/pending | NOT_RUN | |

## REAL DEVICE EXTERNAL CAPABILITIES

All RESULT = **NOT_RUN** until executed on a physical iPhone with signed build. Do not mark PASS without evidence.

| ID | Case | PRECONDITION | STEPS | EXPECTED | RESULT | EVIDENCE |
| -- | ---- | ------------ | ----- | -------- | ------ | -------- |
| RD-01 | install signed app | Signed build / TestFlight | Install on iPhone | App icon; launches | NOT_RUN | |
| RD-02 | cold launch | Installed | Kill → open | Splash/login or restored session; no crash | NOT_RUN | |
| RD-03 | email/password login | Unauth | Login | Home authenticated | NOT_RUN | |
| RD-04 | session restore | Logged in | Kill → reopen | Session restored or clear login | NOT_RUN | |
| RD-05 | logout | Logged in | Cerrar sesión | Login; installation revoke attempted; no private UI | NOT_RUN | |
| RD-06 | Sign in with Apple | Apple capability + Supabase apple enabled | Tap Continuar con Apple | Native sheet → session OR honest ConfigurationRequired | NOT_RUN | |
| RD-07 | profile read/edit | Auth | Open profile → edit → save | Updated; no raw errors | NOT_RUN | |
| RD-08 | pet create/edit | Auth | Create then edit | Detail refreshed | NOT_RUN | |
| RD-09 | pet health | Own ACTIVE pet | Edit health → save | Health updated | NOT_RUN | |
| RD-10 | lost publish | Auth | Publish LOST | Detail + public code | NOT_RUN | |
| RD-11 | found publish | Auth | Publish FOUND | Detail | NOT_RUN | |
| RD-12 | adoption | Auth | Browse / publish / apply as available | Sane success/error | NOT_RUN | |
| RD-13 | media upload/read | Auth | Upload avatar or case photo; reopen | Media shown; no signed URL leak in UI logs | NOT_RUN | |
| RD-14 | custom scheme | Device | Open `leover://passport/CODE` (valid code) | Landing/content or safe unsupported | NOT_RUN | |
| RD-15 | universal link mascota | AASA + entitlement live | Tap `https://leover.com.ar/mascota/{code}` | Opens app public pet (not only Safari) | NOT_RUN | |
| RD-16 | universal link adopción | Same | Tap `/adopciones/{code}` | Opens app public adoption | NOT_RUN | |
| RD-17 | universal link perdido | Same | Tap `/perdidos/{code}` | Opens app lost case | NOT_RUN | |
| RD-18 | universal link encontrado | Same | Tap `/encontrados/{code}` | Opens app found case | NOT_RUN | |
| RD-19 | notification permission | Auth NotDetermined | Request permission | Authorized/Denied UX honest | NOT_RUN | |
| RD-20 | APNs token registration | Push capability + permission | After authorize | Register success via fingerprint OR MissingToken (no fake ACTIVE) | NOT_RUN | |
| RD-21 | notification tap deep link | Push delivered | Tap notification with extras | Typed deep link; no crash | NOT_RUN | |
| RD-22 | quiet hours | Auth | Set window + days + TZ; reload | Persists remotely | NOT_RUN | |
| RD-23 | User A logout → User B privacy | Two accounts | A private data → logout → B login | B never sees A private state | NOT_RUN | |
| RD-24 | background/foreground | Auth | Background → foreground | Session/UI stable | NOT_RUN | |
| RD-25 | offline/error UX | Airplane / bad network | Trigger network call | Sanitized error; no secrets/stack in UI | NOT_RUN | |

RESULT column remains **NOT_RUN** until real-device evidence is attached.

