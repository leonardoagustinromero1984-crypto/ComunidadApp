# SECURITY RISK REGISTER — LeoVer

Cursor **no** autoacepta riesgos. Las filas “accepted by Leonardo” son **decisión expresa del propietario**, no de Cursor.

| ID | risk | severity | likelihood | impact | mitigation | status | owner | production blocker | accepted by | date |
|---|---|---|---|---|---|---|---|---|---|---|
| R-SEC04-BACKUP | STAGING Free no tiene scheduled backups; PROD debe nacer en Pro con daily ON | P0 operacional | certain (Free) | sin backup físico no hay Auth DR | Crear PROD Pro; confirmar daily backup | OPEN | Leonardo | yes (release) | | 2026-09-04 |
| R-SEC04-PITR | PITR no contratado al launch | P1 operacional | certain (deferred) | RPO hasta ~24 h; no rewind intra-día | Daily backup + reevaluar al crecer usuarios/datos/ingresos | DEFERRED FOR INITIAL LAUNCH | Leonardo | no al launch | Leonardo | 2026-09-04 |
| R-SEC04-DRILL | Drill lógico no recupera Auth hosted | P0 Auth | certain (SEC-04C) | clone no login-capable | Physical restore-to-new-project en PROD | PRODUCTION-CONDITIONAL | Leonardo | yes (Auth en PROD) | | 2026-09-04 |
| R-SEC04-STORAGE | DB dump ≠ bytes | P1 | mitigated (SEC-04D) | media huérfana si no corre el job | `backup-storage.ps1` diario a destino privado; sample restore PASS | OPEN (job PROD) | Leonardo | yes para media durable en PROD | | 2026-09-04 |
| R-SIGNED-URL | Storage SDK mint signed URL sin `canon_authorize_media_signed_url` | P1 | mitigated | bypass de throttle/coste | 1066: Edge mint + sin SELECT privado; consume `signed_url.request` | FIXED | ingeniería | no | | 2026-09-04 |
| R-FEED-JSON | Feed/messages jsonb sin cursor | P1 | mitigated | CPU/memoria/latencia/costo | 1066 limit + cursor `(created_at, id)` | FIXED | ingeniería | no | | 2026-09-04 |
| R-MIME-BUCKET | `allowed_mime_types` null en buckets de register | P2 | mitigated | upload MIME no nativo de Storage | 1066 allowlist = `canon_register_media`; evidence null | FIXED (4 buckets) | ingeniería | no | | 2026-09-04 |
| R-MIME-EVIDENCE | `moderation-evidence` sin MIME de bucket | P2 | n/a (no client upload) | n/a en superficie actual | null a propósito hasta contrato de evidencias; checklist en `SEC-P1-CLOSURE.md` | NOT APPLICABLE TO CURRENT UPLOAD SURFACE / FUTURE DEPLOYMENT REQUIREMENT | ingeniería | no | | 2026-09-04 |
| R-LEGACY-BUCKET | Código Android apunta a buckets inexistentes | INFO | low hoy | si se crean sin RLS, superficie nueva | No crear `profile-avatars`/`organization-media` sin diseño canónico | OPEN | ingeniería | no | | 2026-09-02 |
| R-FIREBASE-CLIENT | `google-services.json` tracked (client api_key / oauth client_id) | INFO | low si restricted | abuso de APIs Google si la key no está restringida | Restringir por paquete+SHA en consola PROD; no es service account | OPEN | Leonardo | no si restricted | | 2026-09-02 |
| R-UNDEPLOYED-FN | `push` / `delete-account` / `klipy-proxy` en repo | INFO | n/a | superficie futura | NOT IN CURRENT ATTACK SURFACE; security review antes de deploy | OPEN | ingeniería | n/a | | 2026-09-02 |
| R-STAGING-LIMITS | Limits actuales son de STAGING | P1 | certain al copiar ciego | costo o UX de bloqueo | Aplicar `PRODUCTION-INITIAL-LIMITS.md` (no subir) | OPEN | Leonardo | yes al crear PROD | | 2026-09-02 |
| R-SPEND | Spend/billing alerts no verificadas | P1 | unknown | factura inesperada | Dashboard billing alerts | BLOCKED | Leonardo | yes (presupuesto bajo) | | 2026-09-02 |

P1 signed-URL / feed / messages / MIME register: **FIXED** (1066 + 1067 window). Ver `SEC-P1-CLOSURE.md`.  
`moderation-evidence` MIME: **no OPEN RISK** — FUTURE DEPLOYMENT REQUIREMENT.  
SEC-04 PROD-only permanece PRODUCTION-CONDITIONAL.
