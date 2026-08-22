# RESCUE-01 — Canonical personal Rescuer capability

**Estado:** CANONICAL DOMAIN (minimum)  
**Fecha:** 16 de agosto de 2026  
**Producto:** LeoVer  

## Model

```
RESCUER_MODEL = PERSON + CAPABILITY
VISIBLE_LABEL = Rescatista
STORAGE_CODE = RESCUER
ACCOUNT_TYPE_CREATED = NO
ORGANIZATION_TYPE_CREATED = NO
ACTIVE_CONTEXT_SECURITY_AUTHORITY = NO
```

Selecting Rescatista in onboarding does **not** grant privileged operations. It makes rescue UX/context available. Protected actions still require their own RLS/RPC rules.

## Storage

There was **no** generic PERSON capability table. Foster uses `foster_profiles` (capacity/locality). Organizations use `organization_capabilities`.

RESCUE-01 adds the smallest reusable table:

`public.person_capabilities (user_id, capability, active, …)`

First allowed code: `RESCUER`. Not a rescue operations mega-table.

```
EXISTING_PERSON_CAPABILITY_MODEL = NO (before 1028)
RESCUER_CAPABILITY_STORAGE_OPTION = person_capabilities
```

## Deactivation

Full rescue operations are not implemented. There are no open rescue-domain responsibilities to orphan.

```
RESCUER_DEACTIVATION_ALLOWED_WHILE_NO_RESCUE_RESPONSIBILITIES = YES
```

When rescue cases exist, deactivation must be blocked if any remain open.

## Navigation (UX only)

Inicio · Rescates (`lost_found`) · ＋ Publicar · Ayuda (`sumate`) · Perfil

No new rescue workflow screens.
