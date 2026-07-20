# M08 — Matriz RLS y permisos (freeze 3A)

**Producto:** LeoVer  
**Estado:** diseño; sin policies SQL aplicadas.

Helpers referidos (nombres freeze):

- `m08_actor_can_read_pet(pet_id)`
- `m08_actor_has_capability(pet_id, capability)`
- `m08_actor_can_manage_pet(pet_id)` — shorthand update/manage
- `has_permission(code)` — staff M02

Leyenda resultado: **ALLOW** / **DENY** / **RPC-ONLY** (cliente no escribe directo).

---

## 1. `public.pets`

| Actor | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| Principal persona | ALLOW can_read | RPC-ONLY create | ALLOW has cap update (no owner_id col) | DENY (usar archive/deceased) |
| Principal org (miembro con cap vía org bridge futuro) | ALLOW can_read | RPC-ONLY | ALLOW manage | DENY |
| Co-responsable | ALLOW | DENY | ALLOW update/health/media según cap | DENY |
| Custodio temporal | ALLOW | DENY | ALLOW limitado | DENY |
| Autorizado | ALLOW si VIEW/READ cap | DENY | ALLOW solo caps explícitas | DENY |
| Miembro org sin custody | DENY | DENY | DENY | DENY |
| Staff ADMIN (pet.read) | ALLOW has_permission | DENY | DENY default | DENY |
| Staff SUPERADMIN | ALLOW | RPC-ONLY excepciones | RPC-ONLY | DENY |
| service_role | ALLOW | ALLOW | ALLOW | ALLOW (interno) |
| anon | DENY | DENY | DENY | DENY |

Notas:

- Reemplazar `pets_select_authenticated USING (true)`.  
- UPDATE policy **no** permite cambiar `owner_id`/`status` lifecycle → columnas sensibles solo RPC (o WITH CHECK que las preserve).  
- DELETE denegado a authenticated.

---

## 2. `public.pet_responsibilities`

| Actor | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| Actor con can_read pet | ALLOW filas del pet | RPC-ONLY | RPC-ONLY (revoke) | DENY |
| Staff pet.view_history / read | ALLOW | DENY | DENY | DENY |
| service_role | ALLOW | ALLOW | ALLOW | DENY prefer |
| anon | DENY | DENY | DENY | DENY |

Condición SELECT: `m08_actor_can_read_pet(pet_id)` OR staff permission.

---

## 3. `public.pet_authorizations`

| Actor | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| can_read | ALLOW | RPC-ONLY grant | RPC-ONLY revoke | DENY |
| grantee | ALLOW propias | DENY | DENY | DENY |
| staff | ALLOW si permission | DENY | DENY | DENY |
| service_role | ALLOW | ALLOW | ALLOW | DENY |
| anon | DENY | DENY | DENY | DENY |

---

## 4. `public.pet_transfers`

| Actor | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| can_read / involucrado from|to|requester | ALLOW | RPC-ONLY initiate | RPC-ONLY accept/reject/cancel | DENY |
| staff | ALLOW | DENY | DENY | DENY |
| service_role | ALLOW | ALLOW | ALLOW (expire job) | DENY |
| anon | DENY | DENY | DENY | DENY |

---

## 5. `public.pet_status_history`

| Actor | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| can_read + cap view_history (o principal) | ALLOW | RPC-ONLY | DENY | DENY |
| staff pet.view_history | ALLOW | DENY | DENY | DENY |
| service_role | ALLOW | ALLOW | DENY | DENY |
| anon | DENY | DENY | DENY | DENY |

Append-only estricto.

---

## 6. Matriz capability ↔ operación (resumen)

| Capability | Operaciones habilitadas (vía RPC) |
|---|---|
| pet.read | SELECT pets/grafo |
| pet.create | create_pet_with_principal |
| pet.update | update perfil básico |
| pet.manage_responsibilities | assign/revoke co/temp |
| pet.manage_authorizations | grant/revoke auth |
| pet.initiate_transfer | initiate |
| pet.accept_transfer | accept (destino) |
| pet.cancel_transfer | cancel pending |
| pet.mark_deceased | mark deceased |
| pet.archive / pet.restore | archive/restore |
| pet.manage_media | set avatar asset |
| pet.view_history | status_history SELECT |
| pet.manage_health | update health fields (RPC futuro o update acotado) |

Staff M02: `has_permission('pet.read')` etc. suma capacidades vía `m08_actor_has_capability` (bridge).

---

## 7. Grants EXECUTE (helpers/RPC)

| Objeto | PUBLIC | anon | authenticated | service_role |
|---|---|---|---|---|
| `m08_normalize_microchip` | REVOKE | REVOKE | GRANT | GRANT |
| `m08_actor_can_*` / `m08_actor_has_*` | REVOKE | REVOKE | GRANT | GRANT |
| `_m08_*` internos | REVOKE | REVOKE | REVOKE | GRANT |
| RPCs `m08_*` cliente | REVOKE | REVOKE | GRANT | GRANT |
| `m08_expire_pet_transfers` | REVOKE | REVOKE | REVOKE | GRANT |

---

## 8. Integración org (M03)

Miembro de organización con principal ORGANIZATION: capability efectiva si `is_org_member(org)` AND `has_org_permission(org, …)` **o** permiso dedicado futuro `organization.manage_pets` (pendiente seed org-matrix en 3B si se confirma).  

**Freeze 3A:** documentar hook; implementación mínima = `is_org_member` + rol OWNER/ADMIN org ⇒ treat as manage para pets con `organization_id` principal. Detalle exacto en 3B ADR corto si hace falta.
