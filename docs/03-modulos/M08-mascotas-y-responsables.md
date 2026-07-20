# M08 — Mascotas y responsables

**Producto:** LeoVer  
**Módulo:** M08 — Mascotas y responsables  
**Versión:** 1.2 (Etapa 3B — migración 035 validada localmente)
**Fecha:** 2026-07-19  
**Fuente superior:** D01 Mapa de Módulos · Documento Maestro Integral  
**Estado:**

```text
M08 ETAPA 3B — MIGRACIÓN 035 VALIDADA LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4 — REPOSITORIOS Y COMPATIBILIDAD LEGACY
```

SQL: `supabase/migrations/035_m08_pets_responsibilities_and_rls.sql`
Detalle: `docs/02-arquitectura/M08-etapa-3b-migracion-035-local.md`
UI / `SupabasePetRepository`: **sin cambios** hasta Etapa 4.

---

## 1. Propósito

Definir la identidad animal de LeoVer como entidad independiente del responsable humano y de las publicaciones (regla D01 #2), con un grafo de responsabilidad, autorizaciones, custodia (personal u organizacional), transferencias auditables y ciclo de vida (activa, archivada, fallecida).

M08 es el fundamento de M09 (pasaporte), M12/M13 (reencuentro) y M14–M16 (adopción/rescate).

---

## 2. Alcance

### Incluido (M08)

- Identidad de mascota (ficha base, especie, raza, microchip, fotos).
- Responsable principal, co-responsables y personas autorizadas.
- Custodia temporal y custodia por organización (diseño; detalle organizativo vía M03).
- Transferencias de responsabilidad (estados y aceptación).
- Fallecimiento y baja lógica / archivo.
- Historial inmutable de cambios de responsabilidad.
- Prevención/gestión de duplicados (microchip y reglas).
- Contratos de permisos, eventos M07 y ganchos M06.
- Compatibilidad con legado `public.pets` / UI MyPets–PetForm–PetDetail.

### Fuera de alcance (otros módulos)

| Tema | Módulo |
|---|---|
| Pasaporte, QR, documentos sanitarios formales | M09 |
| Geoservicios / mapas | M10 |
| Alertas perdidos/encontrados productivas | M12–M13 |
| Flujo completo de adopción | M14 |
| Hogares de tránsito / gestión de refugio | M15–M16 |
| Chat, feed social avanzado | M19–M20 |
| Pagos | M24 |

Etapa 1 **solo documentación**. No código, no migración 035, no cambios Supabase.

---

## 3. Actores

| Actor | Descripción |
|---|---|
| Responsable principal | Persona con custody primaria y máxima autoridad sobre la ficha |
| Co-responsable | Persona con custody compartida (edición / gestión según matriz) |
| Autorizado | Persona con facultades limitadas y vigentes (p. ej. ver, llevar a vet) |
| Miembro de organización en custodia | Opera bajo rol M03 sobre mascota en custody org |
| Staff plataforma (M02/M04) | Moderación / soporte; no ownership automático |
| Sistema | Writers SECURITY DEFINER, eventos M07, notifs M06 |

---

## 4. Casos de uso (núcleo)

1. Crear mascota y asignar responsable principal (= creador autenticado).
2. Invitar co-responsable o autorizado; aceptar / rechazar / revocar.
3. Transferir responsabilidad principal (solicitud → aceptación).
4. Delegar custodia temporal con vigencia.
5. Vincular custodia a organización (refugio / transit) con regla de exclusividad.
6. Registrar fallecimiento (estado terminal; historial conservado).
7. Archivar / baja lógica sin borrar histórico clínico ni auditoría.
8. Detectar posible duplicado por microchip.
9. Gestionar fotos vía M05 (`PET_AVATAR` / `PET_GALLERY`), deprecar `photo_url` en etapas posteriores.
10. Consultar “mis mascotas” según grafo de responsabilidad (no solo `owner_id`).

---

## 5. Reglas de negocio (numeradas)

1. La mascota es entidad independiente; no se confunde con posts, adopciones ni usuarios.
2. Exactamente **un** responsable principal activo por mascota en estado operable.
3. Co-responsables: N ≥ 0; no sustituyen al principal salvo transferencia completada.
4. Autorizados: N ≥ 0; no confieren transferencia ni cierre de ficha.
5. Custodia personal y organizacional son **mutuamente exclusivas** como custody primaria (alineado a resource links M03).
6. Transferencia de principal requiere aceptación del destinatario (o regla staff documentada).
7. Fallecimiento es irreversible vía producto; solo compensación administrativa auditada (fuera Etapa 1).
8. Baja lógica no elimina historial de responsables ni registros clínicos vinculados.
9. Microchip, cuando presente, es único entre mascotas **no fallecidas** (decisión: soft-unique activo).
10. Cambios sensibles (transferencia, fallecimiento, cambio de principal) emiten evento M07 y, si aplica, notificación M06.
11. Permisos se evalúan en backend (RPC / RLS); Android no autodeclara staff.
12. El legado `owner_id` se interpreta como **responsable principal actual** hasta migrar el grafo.

---

## 6. Estados de mascota (propuesta)

| Estado | Significado |
|---|---|
| `ACTIVE` | Operable; visible según reglas de privacidad |
| `ARCHIVED` | Baja lógica; no operable en flujos nuevos |
| `DECEASED` | Fallecida; ficha histórica |
| `TRANSFER_LOCK` | (opcional) Bloqueo temporal durante transferencia pendiente |

Estados de **responsabilidad** (vínculo persona↔pet) y de **transferencia** se modelan aparte (ver modelo de custodia).

---

## 7. Responsables

- **Principal:** obligatorio; mapea legacy `pets.owner_id`.
- **Co-responsable:** custody compartida; puede editar ficha según matriz Etapa 2.
- Histórico append-only en `pet_status_history` / historial de responsabilidades (nombres conceptuales).

---

## 8. Autorizados

- Facultades tipadas (`VIEW`, `EDIT_HEALTH_DECLARED`, `TRANSPORT`, …) — catálogo cerrado en Etapa 2.
- Vigencia (`valid_from` / `valid_to`), revocación y aceptación si el diseño lo exige.
- No pueden iniciar transferencia de principal ni marcar fallecimiento (salvo decisión explícita futura).

---

## 9. Custodia organizacional

**Decisión Etapa 1 (propuesta a confirmar antes de 035):**

- Permitir `organization_id` opcional en el vínculo de custody primaria.
- Si custody es ORGANIZATION: el “principal operativo” se resuelve vía membresía M03 + permiso dedicado (p. ej. `pet.manage_org`).
- No dual-primary PERSONAL+ORGANIZATION.

Alternativa descartada: solo personal hasta M16 — **rechazada** porque M03 ya existe y refugios necesitan custody temprana.

---

## 10. Transferencias

Estados: `PENDING` → `ACCEPTED` | `REJECTED` | `CANCELLED` | `EXPIRED`.

- Solo el principal (o staff) inicia.
- Un solo PENDING de principal a la vez por mascota.
- Aceptación reescribe principal + historial; notifica actor/origen/destino.

---

## 11. Fallecimiento

- Transición a `DECEASED` con motivo opcional, fecha, actor, correlation M07.
- Revoca autorizaciones vigentes.
- Cancela transferencias PENDING.
- Conserva ficha e historial (no hard-delete).

---

## 12. Duplicados

- Señales: mismo `microchip_id` activo; similitud nombre+especie+owner (heurística, no merge automático).
- Merge automático: **prohibido** en M08 Etapa 1–7 sin ADR.
- Resolución: revisión manual / staff en etapas posteriores.

---

## 13. Fotos

- Canónico: M05 `FileAssetPurpose.PET_AVATAR` / `PET_GALLERY` + paths `users/{userId}/pets/{petId}/…`.
- Legacy: `pets.photo_url` se mantiene de lectura hasta Etapa 6; escritura nueva preferir M05.
- Borrado de pet: planificar limpieza de assets (hoy: riesgo conocido M05 auditoría).

---

## 14. Auditoría (M07)

Event keys futuros (nombres tentativos, catalogar en Etapa 2/3):

- `pet.created` / `pet.updated` / `pet.archived` / `pet.deceased`
- `pet.responsibility.assigned` / `revoked`
- `pet.transfer.requested` / `accepted` / `rejected` / `cancelled`
- `pet.authorization.granted` / `revoked`

Sin secretos ni PII completa en metadata (allowlist).

---

## 15. Permisos (catálogo Etapa 2)

Códigos tipados (`PetCapability` / `PermissionCode`):

- `pet.read` / `pet.create` / `pet.update`
- `pet.manage_responsibilities` / `pet.manage_authorizations`
- `pet.initiate_transfer` / `pet.accept_transfer` / `pet.cancel_transfer`
- `pet.mark_deceased` / `pet.archive` / `pet.restore`
- `pet.manage_media` / `pet.view_history` / `pet.manage_health`

No reutilizar `AccountType` ni `active_modules` como autorización.
Seed SQL / `has_permission` remoto: **Etapa 3**.

---

## 16. Notificaciones (M06)

Reutilizar categoría/ruta `PET` ya existente.

Eventos de producto a cablear (Etapa 5+): transferencia pendiente/aceptada, co-responsabilidad, autorización, fallecimiento informado a co-responsables.

---

## 17. Criterios de aceptación (módulo, no solo Etapa 1)

- Spec D01 completa en este doc + arquitectura de etapas.
- Matriz de no regresión publicada.
- Modelo de custodia sin ambigüedad de unicidad de principal.
- Plan de coexistencia legacy documentado.
- Etapas 2–7 no iniciadas sin aprobación.

### Criterios específicos Etapa 1

- [x] Cuatro documentos de Etapa 1 publicados en rama `m08/etapa-1-auditoria-diseno`.
- [x] Sin migración 035.
- [x] Sin cambios Android/Supabase/main.

### Etapa 3A (freeze)

Diseño físico y RLS congelados en:

- `docs/02-arquitectura/M08-etapa-3-freeze-esquema-rls.md`
- `docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md`
- `docs/04-calidad/M08-matriz-rls-y-permisos.md`
- `docs/04-calidad/M08-plan-validacion-migracion-035.md`

**Migración 035 aún no creada.**

---

## 18. Documentos relacionados

- `docs/02-arquitectura/M08-etapa-1-auditoria-diseno.md`
- `docs/02-arquitectura/M08-modelo-responsabilidad-y-custodia.md`
- `docs/04-calidad/M08-matriz-impacto-y-no-regresion.md`
- `docs/02-arquitectura/BASE-PRE-M08-INTEGRADA.md`
