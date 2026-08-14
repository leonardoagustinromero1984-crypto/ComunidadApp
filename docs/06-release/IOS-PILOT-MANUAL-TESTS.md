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
