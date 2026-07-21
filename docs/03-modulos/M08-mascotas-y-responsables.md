# M08 — Mascotas y responsables

**Producto:** LeoVer
**Módulo:** M08 — Mascotas y responsables
**Versión:** 1.6 (Etapa 4D — preparación staging; apply 035/036 pendiente)
**Fecha:** 2026-07-20
**Fuente superior:** D01 Mapa de Módulos · Documento Maestro Integral
**Estado:**

```text
M08 ETAPA 4D — PREPARACIÓN STAGING LISTA
APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL
APK DISTRIBUIBLE — PENDIENTE DEL APPLY
```

Marcadores de calidad de etapas anteriores (conservados):
```text
M08 ETAPA 4B — REPOSITORIOS Y ADAPTADOR LEGACY LISTOS LOCALMENTE
STAGING NO AUTORIZADO
REQUIERE ETAPA 4C
```
(4C/4D prep cerraron la integración local; el apply remoto 035/036 sigue pendiente de confirmación humana.)

SQL: `035_m08_pets_responsibilities_and_rls.sql` + `036_m08_pet_repository_compatibility_rpcs.sql`
Detalle 4B: `docs/02-arquitectura/M08-etapa-4b-repositorios-adaptador-legacy.md`
Detalle 4C: `docs/02-arquitectura/M08-etapa-4c-integracion-local-smoke-apk.md`
Detalle 4D: `docs/02-arquitectura/M08-etapa-4d-staging-apk-distribuible.md`
Checklist smoke: `docs/04-calidad/M08-checklist-smoke-apk-local.md`
Checklist smoke staging: `docs/04-calidad/M08-checklist-smoke-apk-staging.md`
Plan despliegue staging: `docs/04-calidad/M08-plan-despliegue-staging-035-036.md`
Android: `LegacyPetRepositoryAdapter` + package `data/remote/supabase/m08/` (DataProvider).
Perfil público: mascotas ajenas ocultas (sin SELECT-all / sin vitrina pública).

---

## 1. PropÃ³sito

Definir la identidad animal de LeoVer como entidad independiente del responsable humano y de las publicaciones (regla D01 #2), con un grafo de responsabilidad, autorizaciones, custodia (personal u organizacional), transferencias auditables y ciclo de vida (activa, archivada, fallecida).

M08 es el fundamento de M09 (pasaporte), M12/M13 (reencuentro) y M14â€“M16 (adopciÃ³n/rescate).

---

## 2. Alcance

### Incluido (M08)

- Identidad de mascota (ficha base, especie, raza, microchip, fotos).
- Responsable principal, co-responsables y personas autorizadas.
- Custodia temporal y custodia por organizaciÃ³n (diseÃ±o; detalle organizativo vÃ­a M03).
- Transferencias de responsabilidad (estados y aceptaciÃ³n).
- Fallecimiento y baja lÃ³gica / archivo.
- Historial inmutable de cambios de responsabilidad.
- PrevenciÃ³n/gestiÃ³n de duplicados (microchip y reglas).
- Contratos de permisos, eventos M07 y ganchos M06.
- Compatibilidad con legado `public.pets` / UI MyPetsâ€“PetFormâ€“PetDetail.

### Fuera de alcance (otros mÃ³dulos)

| Tema | MÃ³dulo |
|---|---|
| Pasaporte, QR, documentos sanitarios formales | M09 |
| Geoservicios / mapas | M10 |
| Alertas perdidos/encontrados productivas | M12â€“M13 |
| Flujo completo de adopciÃ³n | M14 |
| Hogares de trÃ¡nsito / gestiÃ³n de refugio | M15â€“M16 |
| Chat, feed social avanzado | M19â€“M20 |
| Pagos | M24 |

Etapa 1 **solo documentaciÃ³n**. No cÃ³digo, no migraciÃ³n 035, no cambios Supabase.

---

## 3. Actores

| Actor | DescripciÃ³n |
|---|---|
| Responsable principal | Persona con custody primaria y mÃ¡xima autoridad sobre la ficha |
| Co-responsable | Persona con custody compartida (ediciÃ³n / gestiÃ³n segÃºn matriz) |
| Autorizado | Persona con facultades limitadas y vigentes (p. ej. ver, llevar a vet) |
| Miembro de organizaciÃ³n en custodia | Opera bajo rol M03 sobre mascota en custody org |
| Staff plataforma (M02/M04) | ModeraciÃ³n / soporte; no ownership automÃ¡tico |
| Sistema | Writers SECURITY DEFINER, eventos M07, notifs M06 |

---

## 4. Casos de uso (nÃºcleo)

1. Crear mascota y asignar responsable principal (= creador autenticado).
2. Invitar co-responsable o autorizado; aceptar / rechazar / revocar.
3. Transferir responsabilidad principal (solicitud â†’ aceptaciÃ³n).
4. Delegar custodia temporal con vigencia.
5. Vincular custodia a organizaciÃ³n (refugio / transit) con regla de exclusividad.
6. Registrar fallecimiento (estado terminal; historial conservado).
7. Archivar / baja lÃ³gica sin borrar histÃ³rico clÃ­nico ni auditorÃ­a.
8. Detectar posible duplicado por microchip.
9. Gestionar fotos vÃ­a M05 (`PET_AVATAR` / `PET_GALLERY`), deprecar `photo_url` en etapas posteriores.
10. Consultar â€œmis mascotasâ€ segÃºn grafo de responsabilidad (no solo `owner_id`).

---

## 5. Reglas de negocio (numeradas)

1. La mascota es entidad independiente; no se confunde con posts, adopciones ni usuarios.
2. Exactamente **un** responsable principal activo por mascota en estado operable.
3. Co-responsables: N â‰¥ 0; no sustituyen al principal salvo transferencia completada.
4. Autorizados: N â‰¥ 0; no confieren transferencia ni cierre de ficha.
5. Custodia personal y organizacional son **mutuamente exclusivas** como custody primaria (alineado a resource links M03).
6. Transferencia de principal requiere aceptaciÃ³n del destinatario (o regla staff documentada).
7. Fallecimiento es irreversible vÃ­a producto; solo compensaciÃ³n administrativa auditada (fuera Etapa 1).
8. Baja lÃ³gica no elimina historial de responsables ni registros clÃ­nicos vinculados.
9. Microchip, cuando presente, es Ãºnico entre mascotas **no fallecidas** (decisiÃ³n: soft-unique activo).
10. Cambios sensibles (transferencia, fallecimiento, cambio de principal) emiten evento M07 y, si aplica, notificaciÃ³n M06.
11. Permisos se evalÃºan en backend (RPC / RLS); Android no autodeclara staff.
12. El legado `owner_id` se interpreta como **responsable principal actual** hasta migrar el grafo.

---

## 6. Estados de mascota (propuesta)

| Estado | Significado |
|---|---|
| `ACTIVE` | Operable; visible segÃºn reglas de privacidad |
| `ARCHIVED` | Baja lÃ³gica; no operable en flujos nuevos |
| `DECEASED` | Fallecida; ficha histÃ³rica |
| `TRANSFER_LOCK` | (opcional) Bloqueo temporal durante transferencia pendiente |

Estados de **responsabilidad** (vÃ­nculo personaâ†”pet) y de **transferencia** se modelan aparte (ver modelo de custodia).

---

## 7. Responsables

- **Principal:** obligatorio; mapea legacy `pets.owner_id`.
- **Co-responsable:** custody compartida; puede editar ficha segÃºn matriz Etapa 2.
- HistÃ³rico append-only en `pet_status_history` / historial de responsabilidades (nombres conceptuales).

---

## 8. Autorizados

- Facultades tipadas (`VIEW`, `EDIT_HEALTH_DECLARED`, `TRANSPORT`, â€¦) â€” catÃ¡logo cerrado en Etapa 2.
- Vigencia (`valid_from` / `valid_to`), revocaciÃ³n y aceptaciÃ³n si el diseÃ±o lo exige.
- No pueden iniciar transferencia de principal ni marcar fallecimiento (salvo decisiÃ³n explÃ­cita futura).

---

## 9. Custodia organizacional

**DecisiÃ³n Etapa 1 (propuesta a confirmar antes de 035):**

- Permitir `organization_id` opcional en el vÃ­nculo de custody primaria.
- Si custody es ORGANIZATION: el â€œprincipal operativoâ€ se resuelve vÃ­a membresÃ­a M03 + permiso dedicado (p. ej. `pet.manage_org`).
- No dual-primary PERSONAL+ORGANIZATION.

Alternativa descartada: solo personal hasta M16 â€” **rechazada** porque M03 ya existe y refugios necesitan custody temprana.

---

## 10. Transferencias

Estados: `PENDING` â†’ `ACCEPTED` | `REJECTED` | `CANCELLED` | `EXPIRED`.

- Solo el principal (o staff) inicia.
- Un solo PENDING de principal a la vez por mascota.
- AceptaciÃ³n reescribe principal + historial; notifica actor/origen/destino.

---

## 11. Fallecimiento

- TransiciÃ³n a `DECEASED` con motivo opcional, fecha, actor, correlation M07.
- Revoca autorizaciones vigentes.
- Cancela transferencias PENDING.
- Conserva ficha e historial (no hard-delete).

---

## 12. Duplicados

- SeÃ±ales: mismo `microchip_id` activo; similitud nombre+especie+owner (heurÃ­stica, no merge automÃ¡tico).
- Merge automÃ¡tico: **prohibido** en M08 Etapa 1â€“7 sin ADR.
- ResoluciÃ³n: revisiÃ³n manual / staff en etapas posteriores.

---

## 13. Fotos

- CanÃ³nico: M05 `FileAssetPurpose.PET_AVATAR` / `PET_GALLERY` + paths `users/{userId}/pets/{petId}/â€¦`.
- Legacy: `pets.photo_url` se mantiene de lectura hasta Etapa 6; escritura nueva preferir M05.
- Borrado de pet: planificar limpieza de assets (hoy: riesgo conocido M05 auditorÃ­a).

---

## 14. AuditorÃ­a (M07)

Event keys futuros (nombres tentativos, catalogar en Etapa 2/3):

- `pet.created` / `pet.updated` / `pet.archived` / `pet.deceased`
- `pet.responsibility.assigned` / `revoked`
- `pet.transfer.requested` / `accepted` / `rejected` / `cancelled`
- `pet.authorization.granted` / `revoked`

Sin secretos ni PII completa en metadata (allowlist).

---

## 15. Permisos (catÃ¡logo Etapa 2)

CÃ³digos tipados (`PetCapability` / `PermissionCode`):

- `pet.read` / `pet.create` / `pet.update`
- `pet.manage_responsibilities` / `pet.manage_authorizations`
- `pet.initiate_transfer` / `pet.accept_transfer` / `pet.cancel_transfer`
- `pet.mark_deceased` / `pet.archive` / `pet.restore`
- `pet.manage_media` / `pet.view_history` / `pet.manage_health`

No reutilizar `AccountType` ni `active_modules` como autorizaciÃ³n.
Seed SQL / `has_permission` remoto: **Etapa 3**.

---

## 16. Notificaciones (M06)

Reutilizar categorÃ­a/ruta `PET` ya existente.

Eventos de producto a cablear (Etapa 5+): transferencia pendiente/aceptada, co-responsabilidad, autorizaciÃ³n, fallecimiento informado a co-responsables.

---

## 17. Criterios de aceptaciÃ³n (mÃ³dulo, no solo Etapa 1)

- Spec D01 completa en este doc + arquitectura de etapas.
- Matriz de no regresiÃ³n publicada.
- Modelo de custodia sin ambigÃ¼edad de unicidad de principal.
- Plan de coexistencia legacy documentado.
- Etapas 2â€“7 no iniciadas sin aprobaciÃ³n.

### Criterios especÃ­ficos Etapa 1

- [x] Cuatro documentos de Etapa 1 publicados en rama `m08/etapa-1-auditoria-diseno`.
- [x] Sin migraciÃ³n 035.
- [x] Sin cambios Android/Supabase/main.

### Etapa 3A (freeze)

DiseÃ±o fÃ­sico y RLS congelados en:

- `docs/02-arquitectura/M08-etapa-3-freeze-esquema-rls.md`
- `docs/02-arquitectura/M08-esquema-propuesto-migracion-035.md`
- `docs/04-calidad/M08-matriz-rls-y-permisos.md`
- `docs/04-calidad/M08-plan-validacion-migracion-035.md`

**MigraciÃ³n 035 aÃºn no creada.**

---

## 18. Documentos relacionados

- `docs/02-arquitectura/M08-etapa-1-auditoria-diseno.md`
- `docs/02-arquitectura/M08-modelo-responsabilidad-y-custodia.md`
- `docs/04-calidad/M08-matriz-impacto-y-no-regresion.md`
- `docs/02-arquitectura/BASE-PRE-M08-INTEGRADA.md`
