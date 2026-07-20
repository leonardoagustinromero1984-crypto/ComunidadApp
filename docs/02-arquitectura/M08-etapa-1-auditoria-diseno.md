# M08 Etapa 1 — Auditoría y diseño

**Producto:** LeoVer  
**Rama:** `m08/etapa-1-auditoria-diseno`  
**Fecha:** 2026-07-19  
**Base:** `main` @ `2d5d0a8`  
**Tipo:** documental (sin implementación)  
**GitHub Action:** “Assembly, unit tests, lint” puede permanecer en cola por runner; **no** se interpreta como fallo de código.

---

## 1. Objetivo de la etapa

Formalizar auditoría del legado pets, decisiones de dominio M08, modelo conceptual de custodia y plan de etapas posteriores **sin** crear migración 035 ni modificar `app/` / `supabase/migrations`.

---

## 2. Inventario completo (matriz)

| Componente | Estado actual | Dependencia | Riesgo | Reutilizable | Cambio requerido | Etapa |
|---|---|---|---|---|---|---|
| `public.pets` | LEGACY con `owner_id` único | users | RLS select-all | Sí como núcleo ficha | Extender estados + vistas sobre grafo | 3 |
| RLS pets | insert/update/delete own; **select authenticated all** | auth | Sobreexposición | Parcial | Policies por responsabilidad | 3 |
| `003` / `005` campos salud/perfil | Presentes | pets | Bajo | Sí | Conservar columnas | — |
| `pet_clinical_records` | FK `pet_id` cascade | pets | Borrado hard elimina clínica | Sí | Soft-archive preferido | 3–6 |
| `adoptions` / `lost_found_posts` | **Sin FK pet**; ficha duplicada | M14/M12 | Drift de identidad | Contenido | Bridge opcional `pet_id` futuro | post-M08 / M12–14 |
| `adoption_pet_ids` (eventos) | text[] | M06/eventos | Semántica débil | Parcial | Tipar / correlacionar | 4+ |
| Kotlin `data.model.Pet` | ownerId único | repo | No multi-responsable | Sí | Dominio tipado paralelo | 2–4 |
| `PetRepository` / Mock / Supabase | CRUD owner | DataProvider | API insuficiente | Sí | Ampliar contratos | 2–4 |
| UI MyPets / Form / Detail | Operativa | nav | Asume owner | Sí | UI responsables/transfers | 5 |
| Perfil lista pets | Filtra ownerId | PetRepository | Incompleto post-grafo | Sí | Filtrar por responsabilidad | 4–5 |
| M05 PET_AVATAR/GALLERY | Paths tipados | file_assets | Híbrido photo_url | Sí | Preferir M05 en escritura | 6 |
| `StoragePaths.petPhoto` | Legacy jpg path | storage | Doble canal | Parcial | Deprecar gradual | 6 |
| M06 PET category/route | Presente | notifs | Sin eventos M08 | Sí | Encolar en transfers | 5 |
| M07 catalogs | Sin event keys pet.* | obs | Hueco | Sí | Catalogar keys | 2–3 |
| Permisos plataforma | Sin pet.* | M02 | Autorización solo RLS owner | No | Seed permisos | 2–3 |
| Custodia org | Ausente | M03 | Refugios | Roles M03 | Diseño + RPC | 3–5 |
| Transferencias / fallecimiento / autorizados | Ausentes | — | Producto | — | Modelo + UI | 3–6 |
| Spec `docs/03-modulos/M08-*` | Este trabajo | D01 | — | — | Etapa 1 | 1 |

---

## 3. Modelo legacy actual

```text
public.pets
  id, owner_id → users
  name, photo_url, species, sex, age_*, size, description
  vaccinations jsonb, reminders jsonb
  health fields (003), weight/color/breed/... (005)
  RLS: SELECT true (authenticated); CUD owner_id = auth.uid()
```

Android espeja esto en `Pet` + CRUD. No hay tabla de responsibilities.

**Implicación:** `owner_id` = responsable principal actual (compatibilidad obligatoria).

---

## 4. Brechas vs D01 M08

| Requisito D01 | Brecha |
|---|---|
| Entidad independiente | Parcial: existe tabla, pero custody = columna usuario |
| Responsables / autorizados | Ausente |
| Transferencias | Ausente |
| Organización | Ausente en pets (sí en M03) |
| Auditoría cambios sensibles | Ausente eventos dedicados |
| Privacidad por defecto | Select-all pets contradice espíritu | 

---

## 5. Decisiones (Etapa 1)

| # | Decisión | Elección |
|---|---|---|
| D1 | Independencia de la mascota | Mantener `pets` como agregado raíz; grafo satélite |
| D2 | Principal | Exactamente uno activo; sync con `owner_id` en etapa de persistencia |
| D3 | Co-responsables | Tabla/vínculos; no N principals |
| D4 | Autorizados | Separados de co-responsables; facultades tipadas |
| D5 | Custodia temporal | Authorization o responsibility con `valid_to` |
| D6 | Custodia org | Permitida; exclusiva vs personal |
| D7 | Transferencias | Máquina de estados PENDING/ACCEPTED/REJECTED/CANCELLED/EXPIRED |
| D8 | Fallecimiento | Estado `DECEASED`; no hard-delete |
| D9 | Archivo | `ARCHIVED` distinto de DECEASED |
| D10 | Histórico | Append-only responsibilities/status |
| D11 | Duplicados | Soft-unique microchip activos; sin auto-merge |
| D12 | Adopciones/perdidos | No FK obligatoria en M08; bridge opcional posterior |
| D13 | Fotos | M05 canónico; `photo_url` compat lectura |
| D14 | M07/M06 | Catalogar y notificar en etapas 2–5 |
| D15 | Migración | **Futura 035** (o número siguiente al freeze); **no creada en Etapa 1** |
| D16 | `owner_id` org pura | Proyección legacy nullable en schema futuro (Etapa 2 formaliza; Etapa 3 DDL) |
| D17 | Invitaciones | Patrón conceptual M03; sin token system paralelo (Etapa 2) |
| D18 | Capabilities | Catálogo `pet.*` tipado; roles ≠ permisos directos (Etapa 2) |
| D19 | Bridge adopt/LF | `pet_id` opcional futuro; no obligatorio en M08 inicial (Etapa 2) |


---

## 6. Alternativas descartadas

| Alternativa | Motivo de descarte |
|---|---|
| Reemplazar `pets` por modelo nuevo desde cero | Quiebra UI y clínica existentes |
| Solo ampliar `owner_id` a array | Pierde historial, roles y vigencia |
| Ignorar org hasta M16 | M03 ya cerrado; refugios necesitan custody |
| Auto-merge por microchip | Riesgo legal/datos; requiere staff |
| Hard-delete en fallecimiento | Pierde auditoría y clínica |
| Select-all permanente | Incompatible con privacidad D01 |

---

## 7. Plan por etapas

| Etapa | Contenido | Estado |
|---|---|---|
| **1** | Auditoría y diseño (estos docs) | **CERRADA documentalmente** (`b3d4710`) |
| **2** | Contratos dominio Kotlin + permisos + catálogo eventos | **Cerrada en contratos/permisos** — ver `M08-etapa-2-contratos-y-permisos.md` |
| **3** | Esquema/migración + RLS + RPC | No iniciada · **sin 035 aquí** |
| **4** | Repos + compatibilidad legacy (`owner_id` sync) | No iniciada |
| **5** | UI responsables y transferencias | No iniciada |
| **6** | Fallecimiento, duplicados, fotos M05 | No iniciada |
| **7** | Integración, pruebas, cierre | No iniciada |

---

## 8. Propuesta conceptual migración futura (NO crear ahora)

Nombre tentativo: `035_m08_pets_responsibilities_foundation.sql` (confirmar número al freeze).

Contenido esperado (alto nivel):

- Columnas de ciclo de vida en `pets` (`status`, timestamps de fallecimiento/archivo) **o** tabla satélite.
- Tablas conceptuales: `pet_responsibilities`, `pet_authorizations`, `pet_transfers`, `pet_status_history`.
- Backfill: insert responsibility PRINCIPAL desde `owner_id`.
- Trigger/RPC que mantenga `owner_id` = principal activo.
- RLS rewrite: visible si usuario tiene responsibility/authorization vigente o permiso staff.
- Grants mínimos; writers DEFINER con `search_path=public`.
- Seed permisos `pet.*` y links roles.
- Event keys en `observability_event_catalog` (si patrón M07 lo exige en misma migración o 036).

**Prohibido en Etapa 1:** crear el archivo SQL.

---

## 9. Archivos Kotlin futuros (orientativos)

```text
domain/pets/...
  PetIdentity.kt / PetStatus.kt / PetResponsibility.kt
  PetAuthorization.kt / PetTransfer.kt
  authorization/PetAuthorizationService.kt
data/repository/... PetResponsibilityRepository.kt
ui/screens/pets/... ResponsibilitiesScreen / TransferScreen
```

Etapa 2 define nombres finales.

---

## 10. RLS / RPC futuras (orientativas)

- `m08_list_my_pets()`, `m08_request_pet_transfer()`, `m08_accept_pet_transfer()`, …
- Helpers `m08_is_pet_principal`, `m08_has_pet_capability`.
- SECURITY DEFINER + search_path public; revoke PUBLIC/anon donde corresponda.

---

## 11. Estrategia de compatibilidad

1. Leer/escribir legado vía `owner_id` hasta Etapa 4.
2. Introducir grafo con backfill 1:1.
3. UI Etapa 5 consume grafo; MyPets filtra por “mis vínculos”.
4. Deprecar escritura `photo_url` en Etapa 6.
5. No romper `pet_clinical_records`.
6. Adopciones/perdidos siguen independientes hasta su módulo o ADR bridge.

---

## 12. Criterio de cierre Etapa 1

- Cuatro documentos publicados.
- Decisiones D1–D15 registradas.
- Sin código, sin 035, sin push a main.
- Rama `m08/etapa-1-auditoria-diseno` lista para revisión.
