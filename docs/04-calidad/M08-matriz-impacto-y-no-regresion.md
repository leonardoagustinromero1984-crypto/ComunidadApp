# M08 — Matriz de impacto y no regresión

**Producto:** LeoVer  
**Fecha:** 2026-07-19  
**Etapa:** 1 (documental)  
**Base de código:** `main` @ `2d5d0a8`  
**Migración 035:** no creada

---

## 1. Propósito

Registrar impactos esperados de M08 sobre superficies existentes y pruebas futuras mínimas para no romper legacy pets / M02–M07.

---

## 2. Matriz de impacto

| Superficie | Impacto Etapa 2–7 | Riesgo si se ignora | Mitigación | Prueba futura |
|---|---|---|---|---|
| **MyPets** | Filtro por grafo vs solo `ownerId` | Lista incompleta/incorrecta | Adaptador legacy; feature flag | Lista = pets donde soy PRINCIPAL/CO |
| **PetForm** create | Crear + PRINCIPAL | owner_id desfasado | Transacción RPC create | create ⇒ owner + responsibility |
| **PetForm** edit | Quién puede editar | Escalada privilegios | Matriz auth server | co-responsable edita; autorizado no |
| **PetDetail** | Mostrar responsables | UX incompleta | Sección nueva Etapa 5 | UI muestra principal |
| **Perfil** | Pets del perfil | Solo owner legacy | Misma fuente MyPets | perfil ajeno no filtra mis co-custody |
| **Publicaciones** | Bajo (no FK pet) | Bajo | Sin cambio M08 | smoke feed |
| **Adopciones** | Sin FK pet hoy | Doble ficha | No forzar FK en M08 | adoption CRUD intacto |
| **Perdidos** | Sin FK pet hoy | Doble ficha | Diferir bridge | lost/found CRUD intacto |
| **Notificaciones** | Ruta PET existente | Notifs transfer faltan | M06 events Etapa 5 | deep link PET sigue parseando |
| **Historia clínica** | FK cascade | Delete pet borra clínica | Preferir archive | no hard-delete en DECEASED |
| **Fotos** | Híbrido photo_url / M05 | Huérfanos storage | Etapa 6 cleanup plan | upload PET_AVATAR path válido |
| **Organizaciones** | Custodia org | Dual primary | Regla exclusividad | org custody sin dual |
| **Auditoría M07** | Nuevos event keys | Huecos compliance | Catalog Etapa 2–3 | keys existen; unknown rejected |
| **Permisos M02** | Codes pet.* | Auth bypass vía RLS only | Seed + has_permission | deny sin permiso staff |
| **RLS pets** | Select-all hoy | Privacidad | Rewrite Etapa 3 | select limitado a grafo |
| **Datos legacy** | owner_id | Drift grafo | Trigger sync | backfill count = pets count |
| **Migraciones** | Futura 035+ | Aplicar sin backfill | Checklist apply | local reset 001–035×2 |
| **Rollback** | Difícil post-backfill | Datos mixtos | Forward-fix preferido; restore backup staging | documentar |

---

## 3. No regresión obligatoria (checklist previo a cierre M08)

### Android

- [ ] `MyPets` lista al menos las mascotas donde el usuario es `owner_id` (compat).
- [ ] Create/update/delete pet sigue funcionando para principal.
- [ ] Perfil muestra pets del owner.
- [ ] PetDetail / health section no crashea.
- [ ] Navegación add/edit pet.
- [ ] Suite unit tests ≥ baseline (559+) en verde.
- [ ] `assembleDebug` / `lintDebug` PASS.

### Supabase / staging (cuando haya 035+)

- [ ] Historial migraciones sin gaps/dups.
- [ ] `pets.owner_id` sync = PRINCIPAL ACTIVE.
- [ ] RLS: usuario sin vínculo no actualiza pets ajenas.
- [ ] Transfer ACCEPTED cambia principal una sola vez.
- [ ] DECEASED no elimina `pet_clinical_records` (policy elegida).
- [ ] db lint 0 errores.
- [ ] Matriz SQL ampliada o checks M08 PASS / deuda documentada.

### Observabilidad / notifs

- [ ] Event keys pet.* en catálogo (si introducidos).
- [ ] Deep link PET no roto.
- [ ] Metadata allowlist respetada.

---

## 4. Casos de prueba futuros (borrador Etapa 7)

1. Usuario A crea pet → es PRINCIPAL; aparece en MyPets.
2. A invita B co-responsable → B acepta → ambos ven pet; solo A transfiere.
3. Transfer A→C PENDING → C acepta → A ya no es PRINCIPAL; `owner_id=C`.
4. Autorizado D con VIEW no edita.
5. Marcar DECEASED → transferencias PENDING canceladas; autorizaciones revocadas.
6. Microchip duplicado en ACTIVE → rechazo o flag (según regla Etapa 6).
7. Legacy smoke: 10 pets existentes siguen listables post-backfill.
8. Org custody: sin dual primary.

---

## 5. Datos de prueba / fixtures

- No inventar PII real.
- Usar users de staging de prueba.
- No ejecutar SQL remoto en Etapa 1.

---

## 6. Criterio de liberación de etapas

| Antes de… | Condición |
|---|---|
| Etapa 2 | Etapa 1 merged o aprobada en rama |
| Etapa 3 | Contratos + permisos revisados; número migración freeze |
| Etapa 4 | 035 aplicada local ×2 + lint |
| Etapa 5 | Repos compat verdes |
| Etapa 7 / cierre M08 | Esta matriz 100% checkeada + staging PASS documentado |

---

## 7. Relación

- Spec: `docs/03-modulos/M08-mascotas-y-responsables.md`
- Auditoría: `docs/02-arquitectura/M08-etapa-1-auditoria-diseno.md`
- Modelo: `docs/02-arquitectura/M08-modelo-responsabilidad-y-custodia.md`
