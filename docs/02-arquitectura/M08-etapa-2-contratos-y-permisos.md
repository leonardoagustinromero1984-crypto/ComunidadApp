# M08 Etapa 2 — Contratos y permisos

**Producto:** LeoVer  
**Rama:** `m08/etapa-2-contratos-permisos`  
**Fecha:** 2026-07-19  
**Base Etapa 1:** `b3d4710`  
**Tipo:** dominio puro + documentación (sin persistencia)

```text
M08 ETAPA 2 — CONTRATOS Y PERMISOS LISTOS
```

---

## 1. Convenciones reutilizadas

| Convención | Origen |
|---|---|
| Paquete `domain/<módulo>/` + `authorization/` | M03 / M05 |
| `@JvmInline value class` para IDs de agregado | `OrganizationId` |
| Users como `String` | M02/M03 |
| `Result` + `IllegalArgumentException("CODE")` | files `fileFailure` |
| Capacidades `dotted.snake` + enum `code` | `PermissionCode` / `OrganizationPermissionCode` |
| Matrices rol → permisos deny-by-default | `RolePermissionMatrix` |
| Epoch ms en dominio | organization / files |
| Repos legacy en `data`; contratos M08 Etapa 2 en `domain/pets` | decisão Etapa 2 (interfaces sin impl) |
| Tests en `app/src/test/.../domain/pets` | patrón domain tests |

No se creó un segundo framework de permisos: se añadió `PetCapability` + entradas `PermissionCode.PET_*` alineadas.

---

## 2. Paquetes y archivos

```text
app/src/main/java/com/comunidapp/app/domain/pets/
  PetIds.kt
  PetEnums.kt
  PetModels.kt
  PetMediaAndMicrochip.kt
  PetAggregateRules.kt
  PetTransferAndAuthorizationRules.kt
  PetCapabilityMatrix.kt
  PetDomainRepositories.kt
  authorization/PetAuthorizationBridge.kt

app/src/main/java/.../authorization/Authorization.kt  # PermissionCode PET_*
app/src/test/.../domain/pets/PetDomainStage2Test.kt
scripts/ci/m08_stage2_quality_checks.sh
docs/02-arquitectura/M08-etapa-2-contratos-y-permisos.md
(+ actualización docs Etapa 1)
```

---

## 3. Modelos

- `PetAggregate` (no reemplaza `data.model.Pet`)
- `PetPrincipalHolder.Person | Organization`
- `PetResponsibility` / `PetAuthorization` / `PetTransfer` / `PetStatusHistoryEntry`
- `PetMediaBundle` + refs M05
- `MicrochipNormalizer`

---

## 4. Invariantes clave

- Exactamente un PRINCIPAL ACTIVE.
- Principal ≠ co-responsable concurrente.
- TEMPORARY_CUSTODIAN requiere `validTo`.
- DECEASED/ARCHIVED: sin transferencias ni nuevas autorizaciones.
- Autorización explícita no otorga transfer / deceased / archive / manage_responsibilities.
- Una sola transferencia PENDING por mascota.
- Aceptación reescribe principal + proyección `legacyOwnerUserId`.

---

## 5. Máquina de transferencias

`PENDING → ACCEPTED | REJECTED | CANCELLED | EXPIRED`  
Sin retornos desde terminales a PENDING/ACCEPTED.

---

## 6. Capacidades M08

Códigos: `pet.read|create|update|manage_responsibilities|manage_authorizations|initiate_transfer|accept_transfer|cancel_transfer|mark_deceased|archive|restore|manage_media|view_history|manage_health`.

Defaults: `PetRoleCapabilityMatrix`. Efectivas: `PetEffectiveCapabilities` (+ staff vía `PetAuthorizationBridge`).

M02: mismos strings en `PermissionCode`; ADMIN → read+view_history; SUPERADMIN → set staff amplio.

---

## 7. Integración futura

| Módulo | Uso |
|---|---|
| M02 | `has_permission` seed Etapa 3 desde PermissionCode PET_* |
| M03 | `OrganizationId` como principal; invites patrón conceptual M03 |
| M05 | avatar/gallery por fileAssetId |
| M06/M07 | Etapas 5+ (notifs/eventos) |
| Legacy | `data.model.Pet` + UI sin cambios; `owner_id` proyección |

---

## 8. Decisiones resueltas (desde pendientes Etapa 1)

1. Principal Persona|Organización; `owner_id` proyección; nullable en schema futuro.  
2. Invitaciones: patrón conceptual M03; sin sistema token paralelo ahora.  
3. Capabilities catálogo explícito; roles aportan defaults; auth no supera política.  
4. Bridge adopciones/perdidos: `pet_id` opcional futuro; no obligatorio ahora.

---

## 9. Decisiones → Etapa 3

- DDL tablas/columnas y número final de migración (035 tentative).  
- Trigger sync `owner_id`.  
- RLS rewrite.  
- Seed SQL permisos.  
- Invite persistence concreta.  
- Soft-unique microchip en SQL.

---

## 10. Limitaciones

- Sin implementaciones de repos.  
- Sin UI / Supabase / migración 035.  
- Capacidades org-member sobre pet custody org: Etapa 3–5 (bridge M03 membership).

---

## 11. Quality

- Suite: `PetDomainStage2Test`  
- Gate: `scripts/ci/m08_stage2_quality_checks.sh`  
- Highest migration debe permanecer **034**

---

## 12. Siguiente etapa

**Etapa 3A — freeze esquema/RLS:** ver `docs/02-arquitectura/M08-etapa-3-freeze-esquema-rls.md` (aprobable).  
**Etapa 3B — SQL 035 ejecutable:** solo tras aprobación explícita del freeze. **No iniciada.**
