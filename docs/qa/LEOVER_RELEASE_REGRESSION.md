# LeoVer release regression

Automated unit tests **do not** replace physical QA on a real phone.

Last automated run: 2026-08-19 localDebug unit tests — **2618 tests, 0 failures**.
A PASS automatizado **no** equivale a PASS físico.

## AUTH — AUTOMATED + PHYSICAL QA REQUIRED

- Google chooser, no auto-select
- One email → one PERSON
- Password linking
- Custom Auth Domain still pending (not an Android regression)

## MEDIA — AUTOMATED + PHYSICAL QA REQUIRED

Automated: decode math, crop pan at scale 1, TUS threshold, HEIC ingest flag, corrupt JPEG, processed size cap.

Physical required:

- Pick a camera JPEG, drag, pinch, confirm
- Preview equals saved avatar
- 12MP / 15–30MB photo processes without user size error
- Portrait EXIF 90
- Failure stays on step (Reintentar / Cambiar / Continuar sin foto)
- Reopen app: avatar still visible

## TUTORIAL — AUTOMATED + PHYSICAL QA REQUIRED

Automated: Omitir skips remaining tutorials; center tap is ignore.

Physical required: left/right zones, Siguiente, Omitir, safe area.

## PERSON BASE — AUTOMATED

Persona always selected, not removable.

## ADD FUNCTION TAXONOMY — AUTOMATED + PHYSICAL QA REQUIRED

Automated: 5 groups, Foster present, pro/business not flat.

Physical required: scroll to Hogar de tránsito on a device.

## FOSTER — AUTOMATED + PHYSICAL QA REQUIRED

## ACTIVE CONTEXT — AUTOMATED + PHYSICAL QA REQUIRED

## PERSON / RESCUER / REFUGE / PROFESSIONAL / BUSINESS — PHYSICAL QA REQUIRED

Inicio is social feed in every context.

Rescuer Gestión holds operational tools. Animales shows Agregar + Importar.

## PET CREATE — AUTOMATED + PHYSICAL QA REQUIRED

## VITACORA IMPORT — AUTOMATED + PHYSICAL QA REQUIRED

## LOST/FOUND MAP — AUTOMATED + PHYSICAL QA REQUIRED

## SOCIAL FEED — PHYSICAL QA REQUIRED

## BACKGROUND RESTORE — AUTOMATED + PHYSICAL QA REQUIRED
