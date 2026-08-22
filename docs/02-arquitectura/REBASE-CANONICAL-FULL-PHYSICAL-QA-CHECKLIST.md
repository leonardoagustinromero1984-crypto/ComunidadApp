# LeoVer — Canonical Staging full physical QA checklist

APK: `apk/LeoVer-CANONICAL-STAGING-FULL-QA.apk`  
Environment: STAGING `tobqbddfcyitwgbkthhy`  
Mark each: PASS / FAIL / N/A / OBSERVATION

Physical Auth QA is **blocking**. Do not continue the rest of physical QA until **AUTH01–AUTH12** are PASS. Recovery tests (AUTH14–AUTH17) can follow immediately after.

## AUTH (AUTH-01) — do these first

- [ ] AUTH01 — Crear cuenta con email y contraseña
- [ ] AUTH02 — Pantalla "Verificá tu correo"
- [ ] AUTH03 — Email muestra código de 6 dígitos
- [ ] AUTH04 — Ingresar código válido
- [ ] AUTH05 — Cuenta queda confirmada
- [ ] AUTH06 — PERSON se crea una sola vez
- [ ] AUTH07 — Logout
- [ ] AUTH08 — Login con email/password
- [ ] AUTH09 — Reenviar código
- [ ] AUTH10 — Código incorrecto muestra error usable
- [ ] AUTH11 — Link del email NO va a null/anull
- [ ] AUTH12 — Link abre/retorna correctamente a LeoVer
- [ ] AUTH13 — Link vencido muestra mensaje usable
- [ ] AUTH14 — Recuperar contraseña
- [ ] AUTH15 — Email de recuperación abre callback correcto
- [ ] AUTH16 — Cambiar contraseña
- [ ] AUTH17 — Login con contraseña nueva

Dashboard paste for templates + Redirect URLs: `AUTH-01-canonical-email-verification-and-deeplink.md`

## AUTH / ONBOARDING (AUTH-02 + ONBOARDING-01)

Do **after** AUTH01–AUTH12. Existing Staging QA account (confirmed PERSON, signup username already stored) is the preferred fixture. Do not delete it.

- [ ] AUTH/ONB01 — Account created ≠ verified: auth user may exist while email is still pending; LeoVer does not enter Home without a confirmed session
- [ ] AUTH/ONB02 — Verified account can login
- [ ] AUTH/ONB03 — Existing signup username is reused from PERSON (shown as `@usuario`)
- [ ] AUTH/ONB04 — Onboarding does NOT ask username twice; re-entering the same username is not required
- [ ] AUTH/ONB05 — Display name requested only if missing; otherwise prefilled/read-only
- [ ] AUTH/ONB06 — Province list loads all 24 Argentine jurisdictions from canonical Staging
- [ ] AUTH/ONB07 — Locality list filters by selected province; changing province clears locality; every selectable province has official localities
- [ ] AUTH/ONB08 — Profile save persists display name + province/locality; username unchanged; reload matches
- [ ] AUTH/ONB09 — Logout / login does not repeat onboarding when profile is complete
- [ ] AUTH/ONB10 — Visibility is only Público / Privado (no “Solo amigos”); PRIVATE is social profile only

See `AUTH-02-onboarding-profile-geography-fix.md`.

## ACCOUNT / LOGIN

- [ ] Create account (PERSON, username, birth date)
- [ ] Username uniqueness rejected when taken
- [ ] Logout / login
- [ ] Session survives app restart

## PERSON / PROFILE

- [ ] Profile shows display name from PERSON
- [ ] Edit display name, reload, name remains
- [ ] No account-type picker

## NAVIGATION / CONTEXT

- [ ] Personal bottom bar: Inicio / Sumate / Publicar / Comunidad / Perfil
- [ ] Back navigation does not crash
- [ ] Organization context (if member) does not grant extra security

## PET

- [ ] Create pet, reload, pet visible
- [ ] Edit text, reload, changes remain
- [ ] Birth / age display coherent
- [ ] No owner_id as the only responsible

## PET PHOTO

- [ ] Create with photo → reload → photo visible
- [ ] Edit text only → reload → same photo
- [ ] Replace photo → reload → new photo

## FAMILY / RESPONSIBILITY

- [ ] Holders list from canonical Staging
- [ ] Add person if exposed
- [ ] Revoke non-last owner if exposed
- [ ] Last OWNER cannot be removed

## VITACORA

- [ ] Opens from pet
- [ ] Not labelled as first-class Passport product
- [ ] Moment create if exposed

## HEALTH

- [ ] Add allergy → reload → visible
- [ ] Add medication → reload → visible
- [ ] Add vaccination → reload → visible
- [ ] Add weight → reload → visible

## LOST / FOUND

- [ ] Create LOST, appears in list after reload
- [ ] Resolve if exposed
- [ ] Public web `/perdidos` or `/encontrados` with public_code: valid renders, unknown 404, no exact coords

## ADOPTION

- [ ] Create publication for a pet
- [ ] List shows OPEN items
- [ ] Public `/adopciones/{code}`: valid renders, private/unknown 404

## ORGANIZATIONS

- [ ] Create organization
- [ ] My organizations lists it after reload

## FOSTER

- [ ] N/A unless foster profile UI is used (canonical upsert/list RPCs exist)

## SOCIAL

- [ ] Publish a post, reload feed, post visible
- [ ] Like / comment if exposed

## MESSAGING

- [ ] Start person conversation, send message, reload, message visible

## SERVICES / BOOKING / DAYCARE / VETERINARY / EVENTS / DONATIONS

- [ ] N/A for full portals still unmapped; do not trust leftover demo rows

## TEEN / LEGAL

- [ ] Legal screens remain DRAFT_PRE_LAUNCH
- [ ] No marketing checkbox

## PUBLIC WEB

- [ ] `/mascota/{code}` valid / unknown
- [ ] No PII, no exact location

## GENERAL VISUAL / CRASH

- [ ] No crash on rotate / back / cold start
- [ ] Reload after writes persists real Staging data

## UX / ONBOARDING (UX-03)

- [ ] UXONB-01 — Tutorial visual: skip arriba, composición, título, cuerpo, chips, dots, CTA grande
- [ ] UXONB-02 — Tras T00 se ve el catálogo de funciones (no una lista vacía)
- [ ] UXONB-03 — Se pueden marcar varias funciones; Perfil personal queda siempre activo

## FOSTER (personal)

- [ ] FOSTER-01 — Crear perfil personal de hogar de tránsito y verlo en Usar LeoVer como
- [ ] FOSTER-02 — El alta de tránsito no pide razón social, identificador público ni tipo de organización

## ORGANIZATION

- [ ] ORG-01 — Crear organización (Veterinaria / Refugio ONG / Tienda / Guardería / Otro)
- [ ] ORG-02 — El creador puede abrir/gestionar de inmediato
- [ ] ORG-03 — Reintentar crear no duplica ni muestra error SQL

## REFUGIO / ONG

- [ ] REFUGE-01 — Crear u abrir contexto Refugio/ONG
- [ ] REFUGE-02 — Navegar Inicio / Animales / Publicar / Gestión / Perfil sin crash ni formulario de prestador

## COMMUNITY

- [ ] COMM-01 — Comunidad: header, chips (incluye Guarderías y Peluquería), cards blancas, Ver perfil
- [ ] COMM-02 — Filtro por categoría (chip verde seleccionado)
- [ ] COMM-03 — Filtro provincia/localidad (CABA, Buenos Aires, Córdoba, Santa Fe si hay datos)
- [ ] COMM-04 — Ver perfil abre el detalle canónico

## ERROR UX

- [ ] ERR-01 — Ningún duplicate-key / SQLSTATE / PostgREST crudo en pantalla

