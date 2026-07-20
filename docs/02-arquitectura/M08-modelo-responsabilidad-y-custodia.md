# M08 — Modelo de responsabilidad y custodia

**Producto:** LeoVer  
**Fecha:** 2026-07-19  
**Estado:** diseño conceptual Etapa 1  
**Aviso:** los nombres de tablas/columnas son **propuestas**. Se confirmarán antes de la migración futura (tentativa `035_…`). **No existe migración en esta etapa.**

---

## 1. Principios

1. Un agregado `pets` como identidad animal.
2. Custody y facultades viven en tablas satélite (grafo), no solo en `owner_id`.
3. `pets.owner_id` permanece como **proyección** del responsable principal activo (compat legacy).
4. Historial append-only para cambios sensibles.
5. Backend como fuente de verdad (RPC + RLS).

---

## 2. Entidades conceptuales

### 2.1 `pets` (existente, extendido conceptualmente)

Campos nuevos propuestos (confirmar en 035):

| Campo conceptual | Uso |
|---|---|
| `status` | `ACTIVE` \| `ARCHIVED` \| `DECEASED` |
| `deceased_at` / `archived_at` | timestamps |
| `primary_organization_id` | nullable; custody org (alternativa: solo en responsibilities) |

**Alternativa A (preferida Etapa 1):** organization solo en `pet_responsibilities` / custody record.  
**Alternativa B:** columna en `pets`. Decisión final en Etapa 3.

---

### 2.2 `pet_responsibilities` (propuesta)

Vínculo persona (o subject org) ↔ mascota con rol de custody.

| Columna conceptual | Notas |
|---|---|
| `id` | uuid |
| `pet_id` | FK pets |
| `subject_user_id` | nullable si subject es org-operado vía membership |
| `organization_id` | nullable; custody org |
| `role` | `PRINCIPAL` \| `CO_RESPONSIBLE` \| `TEMPORARY_CUSTODIAN` |
| `status` | `ACTIVE` \| `REVOKED` \| `EXPIRED` \| `SUPERSEDED` |
| `valid_from` / `valid_to` | vigencia |
| `granted_by` | actor |
| `accepted_at` | si requiere aceptación |
| `revoked_at` / `revoke_reason` | |
| `created_at` | |

**Unicidad:**

- Exactamente un `PRINCIPAL` + `ACTIVE` por `pet_id`.
- No dual primary PERSONAL+ORGANIZATION.
- Índice parcial único sugerido: `(pet_id) WHERE role=PRINCIPAL AND status=ACTIVE`.

**Reglas:**

- Crear pet ⇒ insert PRINCIPAL (usuario creador) + set `pets.owner_id`.
- Transferencia ACCEPTED ⇒ SUPERSEDE old PRINCIPAL + ACTIVE new + update `owner_id`.
- CO_RESPONSIBLE no puede existir sin PRINCIPAL activo.

---

### 2.3 `pet_authorizations` (propuesta)

Facultades limitadas ≠ custody.

| Columna conceptual | Notas |
|---|---|
| `id` | uuid |
| `pet_id` | FK |
| `grantee_user_id` | |
| `capability` | enum/text controlado |
| `status` | ACTIVE/REVOKED/EXPIRED |
| `valid_from` / `valid_to` | |
| `granted_by` | suele ser PRINCIPAL o CO_RESPONSIBLE con permiso |
| `accepted_at` | opcional |
| `revoked_at` | |

**Reglas:**

- No otorga `transfer_principal` ni `mark_deceased` por defecto.
- Revocación masiva al pasar a DECEASED.
- Unicidad razonable: `(pet_id, grantee_user_id, capability) WHERE status=ACTIVE`.

---

### 2.4 `pet_transfers` (propuesta)

| Columna conceptual | Notas |
|---|---|
| `id` | uuid |
| `pet_id` | FK |
| `from_user_id` | principal origen |
| `to_user_id` | destinatario |
| `status` | PENDING/ACCEPTED/REJECTED/CANCELLED/EXPIRED |
| `requested_at` / `resolved_at` | |
| `message` | opcional, sanitizado |
| `correlation_id` | M07 |

**Reglas:**

- Máximo un PENDING por pet.
- Solo PRINCIPAL (o staff) crea.
- ACCEPTED ejecuta en transacción: responsibilities + owner_id + history + events + notifs.
- EXPIRED por job/cron futuro (no Etapa 1).

---

### 2.5 `pet_status_history` (propuesta)

Append-only.

| Columna conceptual | Notas |
|---|---|
| `id` | uuid |
| `pet_id` | |
| `from_status` / `to_status` | |
| `reason_code` | |
| `actor_user_id` | |
| `metadata` | jsonb allowlisted |
| `created_at` | |
| `correlation_id` | |

También (o en tabla hermana) historial de cambios de PRINCIPAL.

---

## 3. Roles de responsabilidad (resumen)

| Rol | Editar ficha | Invitar | Transferir | Fallecer | Ver clínica |
|---|---|---|---|---|---|
| PRINCIPAL | sí | sí | sí | sí | sí |
| CO_RESPONSIBLE | sí (matriz) | limitado | no | no | sí |
| TEMPORARY_CUSTODIAN | limitado | no | no | no | limitado |
| AUTHORIZED (capability) | según capability | no | no | no | según capability |
| ORG member + `pet.manage_org` | según org policy | según org | según org | según org | sí |

Matriz fina: Etapa 2 (`PetAuthorizationService`).

---

## 4. Vigencia, aceptación, revocación

- Invitaciones a co-responsable/autorizado pueden nacer `PENDING_ACCEPTANCE` (campo status o tabla invites — decisión Etapa 3).
- `valid_to` pasado ⇒ status EXPIRED (job o check en RPC).
- Revocación: solo PRINCIPAL / grantor / staff; audit event.

---

## 5. Trazabilidad

- Toda mutación de grafo: fila history + evento M07.
- Correlation ID obligatorio en transfers y deceased.
- Sanitización metadata (sin tokens, emails crudos innecesarios).

---

## 6. Reglas de autorización (esperadas)

Evaluación server-side:

```text
can_view(pet, user) =
  has ACTIVE responsibility
  OR has ACTIVE authorization with VIEW
  OR staff permission
  OR (legacy interim) owner_id = user   -- solo hasta Etapa 4 complete

can_update(pet, user) =
  PRINCIPAL OR CO_RESPONSIBLE OR capability EDIT_* OR org manage
```

Android consume RPC; no confiar en cliente.

---

## 7. RLS esperada (conceptual)

- `pets`: SELECT si `can_view`; CUD según rol (idealmente vía RPC DEFINER, policies restrictivas para write directo).
- Satélites: SELECT/INSERT/UPDATE solo actores relacionados; sin PUBLIC write.
- Preferencia M07: writes sensibles en SECURITY DEFINER con revoke EXECUTE de anon/authenticated donde sea writer interno.

Detalle SQL: Etapa 3.

---

## 8. Sincronización legacy

```text
AFTER INSERT/UPDATE responsibility PRINCIPAL ACTIVE
  => UPDATE pets SET owner_id = subject_user_id

BACKFILL 035:
  INSERT pet_responsibilities (pet_id, subject_user_id, role=PRINCIPAL, status=ACTIVE)
  FROM pets
```

Si custody ORGANIZATION sin user principal “dueño” físico: REQUIERE DECISIÓN Etapa 3 (service account vs membership-only + owner_id nullable). **Pendiente.**

---

## 9. Decisiones pendientes (Etapa 1 → resueltas en Etapa 2)

| # | Tema | Decisión Etapa 2 |
|---|---|---|
| 1 | `owner_id` bajo custody org puro | Principal canónico Persona\|Organización; `owner_id` es proyección legacy y **podrá ser nullable** en migración futura; invariante de principal único vive en el grafo. Sin DDL aún. |
| 2 | Invitaciones co-responsable | Reutilizar **patrón conceptual M03** (no sistema de tokens paralelo). Adaptador/persistencia en etapas posteriores. |
| 3 | Capabilities | Catálogo explícito `PetCapability` / `PermissionCode.PET_*`; rol aporta defaults; autorización explícita no supera política ni otorga ownership. |
| 4 | Bridge adopciones/perdidos | `pet_id` **opcional** en transición; no obligatorio mientras existan registros legacy sin mascota formal. Sin cambios M12/M14 ahora. |

Detalle de contratos: `docs/02-arquitectura/M08-etapa-2-contratos-y-permisos.md`.

---

## 10. Relación con documentos

- Spec producto: `docs/03-modulos/M08-mascotas-y-responsables.md`
- Auditoría etapas: `docs/02-arquitectura/M08-etapa-1-auditoria-diseno.md`
- No regresión: `docs/04-calidad/M08-matriz-impacto-y-no-regresion.md`
