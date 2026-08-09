# M28 — Cierre implementación PILOT-MINIMUM

**Producto:** LeoVer  
**Módulo:** M28 — Portal Veterinario y Gestión Profesional de Salud  
**Fecha cierre:** 2026-08-09  
**Rama:** `main`  
**Migración:** `080_m28_veterinary_professional_health_management.sql`

---

## 1. Qué se implementó

| Área | Alcance PILOT-MINIMUM |
|------|------------------------|
| Vínculos profesionales | `veterinary_patient_relationships` + RPC listado clínica |
| Grants revocables | `veterinary_professional_access_grants` + grant/revoke/list |
| Atención profesional | `veterinary_professional_cares` ciclo DRAFT → FINALIZED + supersesión |
| Vacunas | `veterinary_vaccination_records` vinculadas a pet/care/clinic |
| Documentos | `veterinary_professional_documents` (metadatos; storage M05 reutilizable) |
| Seguimientos | `veterinary_follow_ups` + cambio de estado |
| Agenda M12 | `appointment_id` opcional en care; finalize idempotente por turno |
| Propuestas Pasaporte | `veterinary_passport_update_proposals` + decisión responsable |
| Exportación | `veterinary_export_requests` + snapshot JSON; PDF en Android |
| Auditoría | Eventos vía `m07_best_effort_audit` en RPC sensibles |
| Android responsable | Grants, propuestas, atención clínica mínima desde turno |
| Seguridad | RLS deny-by-default; acceso vía RPC SECURITY DEFINER + grants M08 |

---

## 2. Reutilización M12 / M08 / M14

| Autoridad | Reutilizado | No duplicado |
|-----------|-------------|--------------|
| **M12** | `veterinary_clinic_profiles`, `veterinary_professionals`, `veterinary_clinic_professionals`, `veterinary_appointments`, permisos org `veterinary.*` | Sin tablas `m28_*clinic*`, `m28_appointments` |
| **M08** | `pets` como identidad única del paciente; responsables para grants y resolución | Sin segunda tabla de pacientes |
| **M14** | `pet_passports`, credenciales; aplicación solo vía `_m28_apply_passport_proposal` tras ACCEPT | Sin UPDATE directo al Pasaporte; sin reutilizar `pet_passport_verification_requests` |
| **M03** | Membresías y `has_org_permission` | Sin roles M28 independientes |
| **M07** | Auditoría best-effort | Sin analytics mezclados |
| **M05** | Bucket/storage existente para documentos | Sin bucket nuevo en piloto |

---

## 3. Migraciones

| # | Archivo |
|---|---------|
| **080** | `supabase/migrations/080_m28_veterinary_professional_health_management.sql` |

Migraciones 001–079 intactas. **No aplicada a producción** en este cierre (revisión estática + tests locales).

---

## 4. Tablas nuevas

1. `veterinary_care_type_catalog`
2. `veterinary_patient_relationships`
3. `veterinary_professional_access_grants`
4. `veterinary_professional_cares`
5. `veterinary_vaccination_records`
6. `veterinary_professional_documents`
7. `veterinary_follow_ups`
8. `veterinary_passport_update_proposals`
9. `veterinary_export_requests`

---

## 5. RPC principales

- `m28_grant_professional_access` / `m28_revoke_professional_access`
- `m28_list_grants_for_responsible` / `m28_list_my_grants_for_pet`
- `m28_upsert_patient_relationship` / `m28_list_clinic_patients`
- `m28_create_care_draft` / `m28_update_care_draft` / `m28_finalize_care` / `m28_supersede_care`
- `m28_get_care` / `m28_list_pet_cares`
- `m28_create_vaccination_record`
- `m28_create_professional_document`
- `m28_create_follow_up` / `m28_update_follow_up_status`
- `m28_create_passport_update_proposal` / `m28_list_passport_update_proposals_for_responsible` / `m28_decide_passport_update_proposal`
- `m28_request_export` / `m28_get_export_snapshot`
- `m28_list_clinic_dashboard_summary` / `m28_list_clinic_proposals`

Helpers internos: `_m28_*` (grant activo, acceso clínico, apply proposal M14, audit).

---

## 6. Permisos (DEC-M28-08 cerrado)

**Decisión:** namespace **`veterinary.care.*`** (convención M12/M03), **no** `m28.*`.

Capacidades mínimas registradas:

- `veterinary.care.read`
- `veterinary.care.write`
- `veterinary.care.correct`
- `veterinary.care.document.upload`
- `veterinary.care.export`
- `veterinary.care.passport.propose`
- `veterinary.care.grant.manage`

Recepción (`RECEPTION_ONLY`) no recibe acceso automático a notas clínicas restringidas (filtrado en RPC/políticas + mock tests).

---

## 7. RLS

- RLS **habilitado** en las 9 tablas nuevas.
- Políticas **deny-by-default** para `authenticated` (SELECT/INSERT/UPDATE/DELETE = false).
- Lectura/escritura exclusivamente vía RPC `SECURITY DEFINER` con validación de grant + membresía + rol.
- `service_role`: grants de tabla estándar Supabase; **no** expuesto en clientes Android/web.

---

## 8. Portal web profesional

**Estado:** **NO_APLICA** en PILOT-MINIMUM.

No existe workspace web oficial (Next.js/React) en el repositorio. Superficie profesional mínima implementada en **Android** (registrar atención desde turno M12). Portal web queda como prerrequisito Post-Pilot.

---

## 9. Android responsable / clínica

| Superficie | Ubicación |
|------------|-----------|
| Acceso profesional (grants) | `M28PetGrantsScreen` — desde detalle mascota |
| Propuestas Pasaporte | `M28PetProposalsScreen` — aceptar/rechazar |
| Atención desde turno | `M28ClinicCareScreen` — desde gestión turnos M12 |
| Capa datos | `M28Repository`, `SupabaseM28RemoteDataSource`, `MockM28Repository` |
| PDF export | `M28ExportPdfGenerator` (snapshot RPC → PDF local) |
| Navegación | `NavRoutes` + `ComunidappNavGraph` |

---

## 10. Integración M14

- Tabla separada `veterinary_passport_update_proposals`.
- Flujo: profesional propone → responsable decide → solo `ACCEPT` ejecuta operación M14 vía helper SQL.
- Rechazo/corrección no modifica Pasaporte.
- Android: resolución en `M28PetProposalsScreen` / `M28ProposalsViewModel`.

---

## 11. Exportación

- RPC genera snapshot JSON con disclaimer legal obligatorio.
- PDF PILOT generado en cliente Android (`M28ExportPdfGenerator`).
- Abstracción preparada para JSON/CSV/ZIP Post-Pilot.

---

## 12. Auditoría

Eventos registrados (best-effort M07): grant creado/revocado, care creado/finalizado/corregido, vacuna, documento, follow-up, propuesta creada/resuelta, export solicitado.

---

## 13. Tests

| Suite | Resultado |
|-------|-----------|
| `M28PilotMinimumRulesTest` | PASS |
| `M28Migration080StaticGuardsTest` | PASS |
| `testDebugUnitTest` (completo) | PASS |
| `assembleDebug` | PASS |
| Web typecheck/build | NO_APLICA |

Escenarios cubiertos (mock + guardas estáticas): grant/revoke, acceso clínica cruzada, recepción sin notas, care draft/finalize/supersede idempotente, propuestas M14, export permisos, compatibilidad M12 sin duplicar tablas.

---

## 14. Decisiones técnicas

1. Permisos `veterinary.care.*` — alineado a M12/M03.
2. Sin portal web hasta existir workspace oficial.
3. PDF en Android por ausencia de web en piloto.
4. Propuestas Pasaporte como entidad distinta de verificación M14.
5. Corrección de atenciones por supersesión (original `CORRECTED`, nueva versión `FINALIZED`).

---

## 15. Pendientes Post-Pilot

- Portal web profesional (Next.js/OpenNext) completo: dashboard, pacientes, documentos upload UI, seguimientos.
- Notificaciones M06 (propuesta, follow-up, vacuna próxima).
- Export ZIP / JSON schema / CSV.
- Walk-in sin responsable LeoVer (DEC-M28-04).
- Suscripción Mercado Pago profesional (DEC-M28-02).
- IA clínica (explícitamente fuera de alcance).
- Web responsable para propuestas (DEC-M28-07).

---

## 16. Decisiones abiertas conservadas

| ID | Tema |
|----|------|
| DEC-M28-01 | Plazos retención post-baja |
| DEC-M28-02 | Precio/tiers suscripción |
| DEC-M28-03 | Formato export estructurado definitivo |
| DEC-M28-04 | Walk-in sin responsable |
| DEC-M28-05 | Org dual M22 + M12 |
| DEC-M28-06 | Consolidación care vs JSONB |
| DEC-M28-07 | Web responsable propuestas |

**Cerrada en piloto:** DEC-M28-08 → `veterinary.care.*`.

---

## 17. Riesgos conocidos

1. Migración 080 no aplicada en staging/prod — requiere ventana de deploy SQL.
2. Portal profesional ausente — operación clínica completa depende de Android mínimo o futuro web.
3. Upload documentos: metadatos SQL listos; UI upload Android/web pendiente.
4. Tests de guardas de migración legacy actualizados para convivir con 053–079 y 080.

---

## 18. Validaciones ejecutadas

```text
gradlew.bat testDebugUnitTest --tests "com.comunidapp.app.viewmodel.M28*"  → PASS
gradlew.bat testDebugUnitTest                                              → PASS
gradlew.bat assembleDebug                                                  → PASS
Revisión estática SQL 080 (RLS, RPC, permisos)                             → OK
```

Commit: `feat(m28): add veterinary professional health management`
