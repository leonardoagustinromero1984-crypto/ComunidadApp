# M28 — Portal Veterinario y Gestión Profesional de Salud

**Producto:** LeoVer  
**Módulo:** M28 — Portal Veterinario y Gestión Profesional de Salud  
**Versión:** 1.0  
**Fecha:** 2026-08-09  
**Fuente superior:** Documento Maestro Integral v1.1 · D01 v1.2 · Inventario real Mxx v1.0  
**Estado:**

```text
M28 — IMPLEMENTADO PILOT-MINIMUM
SQL — migración 080 (pendiente apply remoto)
ANDROID — responsable + clínica mínima
WEB — NO_APLICA (sin workspace oficial)
```

**Cierre técnico:** `docs/03-modulos/M28-cierre-implementacion.md`

**Ruta de repositorio:** `/docs/03-modulos/M28-portal-veterinario-y-gestion-profesional-salud.md`

---

## 1. Identificación

| Campo | Valor |
|-------|-------|
| ID | **M28** |
| Nombre | Portal Veterinario y Gestión Profesional de Salud |
| Release D01 | **R5 — Servicios confiables y comercial** |
| Superficies | **WEB** (principal), **VET** (portal profesional), integración **ANDROID** / **iOS** para responsables |
| Stack web estratégico | Next.js · React · TypeScript · Cloudflare Workers · OpenNext · Supabase |
| Backend | Supabase (PostgreSQL + RLS + RPC + Edge Functions donde aplique) |

---

## 2. Estado

| Aspecto | Estado |
|---------|--------|
| Código Android M28 | **PILOT-MINIMUM** (grants, propuestas, care clínica, export PDF) |
| Migraciones M28 | **080** creada (no aplicada remoto en cierre) |
| Portal web profesional | **NO_APLICA** — sin workspace Next.js en repo |
| M12 (directorio + turnos vet Android) | **IMPLEMENTADO** (046–047) — reutilizado, no reemplazado |
| M14 (Pasaporte) | **IMPLEMENTADO** (050–052) — integración por propuesta |
| Permisos | **`veterinary.care.*`** (DEC-M28-08 cerrado) |

M28 es la **siguiente evolución profesional** sobre identidad veterinaria y turnos ya modelados en M12, no un módulo paralelo de “otra veterinaria”.

---

## 3. Objetivo

Permitir que establecimientos y profesionales veterinarios **organicen operativamente** dentro de LeoVer:

- pacientes (mascotas LeoVer ya existentes);
- atenciones, vacunas, controles, procedimientos y estudios;
- documentos e indicaciones;
- seguimientos y recordatorios;
- equipos, permisos y trazabilidad;
- **propuestas selectivas** de actualización al Pasaporte (M14);
- **exportación portable** de información relevante.

**Valor operativo real** para la clínica sin obligarla a abandonar registros externos o regulatorios.

LeoVer **no** sustituye la conservación legal/profesional que corresponda al establecimiento fuera de la plataforma.

---

## 4. Alcance

### Incluido (M28)

- Portal web profesional (superficie principal de gestión).
- Relación profesional mascota ↔ establecimiento/profesional (**sin duplicar mascota**).
- Registro de atenciones profesionales trazables (`ProfessionalCare`).
- Vacunas, controles, procedimientos y estudios resumidos.
- Documentos adjuntos vía M05 con metadatos M28.
- Indicaciones profesionales (texto registrado por humano; no prescripción autónoma).
- Seguimientos y recordatorios integrados con M06.
- Consumo/extensión de agenda M12 (`veterinary_appointments`, 047) — **no agenda paralela**.
- Permisos revocables del responsable (`ProfessionalAccessGrant`).
- Propuestas de actualización al Pasaporte con aprobación del responsable.
- Corrección/versionado no destructivo de registros profesionales.
- Exportación PDF legible + exportación estructurada (diseño; formato definitivo pendiente).
- Auditoría M07 de eventos sensibles.
- Integración reputación M21 cuando exista atención/turno completado verificable.
- Suscripción comercial de establecimiento/profesional (conceptual; precio pendiente).
- Flujos Android/iOS mínimos para responsables (permisos, propuestas Pasaporte, recordatorios).

### Piloto territorial (referencia Maestro / D01)

Partido de **San Vicente** y **Almirante Brown**. M28 puede incorporarse **progresivamente**; no bloquea apertura del piloto social.

---

## 5. No objetivos

LeoVer / M28 **no es**:

- historia clínica oficial;
- registro sanitario estatal;
- sistema oficial SENASA;
- repositorio regulatorio obligatorio;
- custodio legal primario permanente de documentación clínica;
- garante de diagnóstico, tratamiento ni resultados clínicos.

M28 **no promete** en copy ni contratos: “historia clínica oficial”, “registro médico legal definitivo”, “archivo clínico obligatorio”, “custodia permanente”.

---

## 6. Relación M12 ↔ M28

### Qué resuelve M12 hoy (evidencia repo)

| Capacidad | Persistencia / código | Superficie |
|-----------|----------------------|------------|
| Directorio público de clínicas | `veterinary_clinic_profiles` (046) | Android |
| Profesionales y membresía clínica | `veterinary_professionals`, `veterinary_clinic_professionals` (046) | Android |
| Servicios y horarios de apertura | `veterinary_services`, `veterinary_opening_hours` (046) | Android |
| Agenda, disponibilidad, turnos | `veterinary_schedule_settings`, `veterinary_availability_*`, `veterinary_appointments` (047) | Android |
| Permisos org | `veterinary.*` vía M03 `has_org_permission` | Backend |
| Recordatorios preparados (sin push real) | Bloque 4 M12 | Android + hooks M06 |
| Reserva con autoridad M08 sobre mascota | RPC + `m08_actor_has_active_responsibility` | Backend |

**Límites explícitos M12:** sin pagos, sin historia clínica, sin diagnóstico, sin recetas, sin laboratorio, sin chat/video clínico.

### Qué agrega M28 (sin reemplazar M12)

| Dominio | M12 | M28 |
|---------|-----|-----|
| Descubrimiento / directorio | Sí (Android) | Enlace desde web; no duplica directorio |
| Perfil clínica/profesional | Sí (046) | **Reutiliza** tablas; web gestiona operación |
| Turnos | Sí (047) | **Reutiliza** `veterinary_appointments`; web backoffice |
| Ficha clínica operativa | No | Sí — atenciones, vacunas, documentos |
| Pasaporte | No (solo emisor futuro en M14) | Propuestas controladas → M14 |
| Portal web profesional | No | **Superficie principal M28** |

### Frontera normativa

```
M12 = capa pública + operación de turnos (Android, implementada)
M28 = capa profesional de gestión clínica-operativa (web-first, por diseñar)
M08 = identidad mascota autoritativa
M14 = Pasaporte (vista integrada; no copia automática desde M28)
```

**Regla:** no crear `veterinary_clinic_profiles_bis`, `pets_clinical`, ni segundo profesional. Extender permisos (`veterinary.care.*` o `m28.*`) y tablas nuevas solo donde M12 no modela dominio clínico-operativo.

### Evolución sin ruptura

```
M12 existente (046–047)
    ↓ reutilizar clínica, profesional, turno
M28 agrega: patient_relationship, professional_care, documentos, propuestas M14
    ↓
Android M12 sigue operando directorio/turnos; web M28 opera backoffice clínico
```

---

## 7. Dependencias reales

Basado en D01 v1.2, inventario Mxx y código existente. **No inventar IDs.**

| Módulo | ID | Relación con M28 | Evidencia |
|--------|-----|------------------|-----------|
| Fundación | M00 | Supabase, convenciones RPC/RLS | Migraciones base |
| Identidad | M01 | `auth.uid()`, usuarios | M01 cierre |
| Roles globales | M02 | Perfil, privacidad usuario | `user_privacy_settings` |
| Organizaciones | M03 | Org ACTIVE, sedes, `has_org_permission`, membresías | 046 integra `organization_id` en clínicas |
| Moderación | M04 | Suspensión org/profesional, verificación clínica | `m12_review_*` |
| Archivos | M05 | PDF/imagen informe; refs `m05://` | Usado en M12 logos, M14 credenciales |
| Notificaciones | M06 | Recordatorios turno, propuesta Pasaporte, seguimiento | Hooks M12 B4 |
| Auditoría | M07 | Eventos sensibles append-only | `m07_best_effort_audit` |
| Mascotas | M08 | **`pets` autoritativo**; responsabilidades; estados | 035–036 |
| Veterinarias | M12 | **Base reutilizada** — perfiles, turnos | 046–047, `SupabaseVeterinaryM12*` |
| Pasaporte | M14 | Propuestas → credenciales/campos; no autoverificación | 050–052 |
| Reputación | M21 | Reseña verificada post-atención completada | 065, integración vet en SQL |
| Prestadores | M22 | Paralelo comercial genérico; **no sustituye M12 vet** | 066–067 |
| Agenda genérica | M23 | Reservas M22; **no es agenda veterinaria M12** | 068–069 |
| Pagos integrados | M24 | Posponido; suscripción M28 ≠ M24 checkout | Preauditoría |
| IA creativa | M26 | Futuro asistencia redacción; no diagnóstico | — |

**Nota M23 vs M12:** M23 modela `bookings` para **prestadores M22**. M12 modela **`veterinary_appointments`** para clínicas veterinarias. M28 **extiende M12** para agenda vet; solo referencia M23 si una org unifica prestador genérico y clínica vet (caso borde — decisión abierta).

---

## 8. Actores

| Actor | Descripción | Superficies típicas |
|-------|-------------|---------------------|
| Responsable de mascota | Titular/co/autorizado M08; otorga/revoca acceso profesional | Android, iOS |
| Profesional veterinario | Usuario vinculado a `veterinary_professionals`; registra atenciones | Web M28 |
| Establecimiento veterinario | Org M03 + `veterinary_clinic_profiles` | Web M28 |
| Administrador establecimiento | Miembro org con permisos gestión/equipo | Web M28 |
| Miembro equipo | Asistente, recepción, etc. — acceso mínimo | Web M28 |
| Personal administrativo | Agenda, datos no clínicos sensibles | Web M28 |
| Colaborador autorizado | Acceso puntual sin rol clínico pleno | Web M28 |
| Staff plataforma | M04 moderación/soporte | Admin |
| Sistema | RPC SECURITY DEFINER, M07, M06 | Backend |

**Principio:** no todos los miembros de la clínica ven todos los datos clínicos. Mínimo privilegio por capacidad M03 + grants M08.

---

## 9. Modelo de permisos

### 9.1 Capas (no mezclar)

| Capa | Autoridad | Ejemplos |
|------|-----------|----------|
| **A — M08 responsabilidad** | Grafo mascota | Ver/editar ficha según rol responsable |
| **B — ProfessionalAccessGrant** | Consentimiento responsable → clínica/profesional | Atención actual, histórico, documentos, propuesta Pasaporte |
| **C — M03 org / veterinary.*** | Membresía clínica activa | Gestionar turnos, ver pacientes de la clínica |
| **D — M14 passport.*** | Pasaporte | Aceptar/rechazar propuestas (responsable) |

### 9.2 ProfessionalAccessGrant (conceptual)

| Campo | Descripción |
|-------|-------------|
| `pet_id` | Mascota LeoVer |
| `grantee_type` | `CLINIC` \| `PROFESSIONAL` |
| `grantee_clinic_id` / `grantee_professional_id` | Destinatario |
| `purposes[]` | `CURRENT_CARE`, `HISTORICAL_READ`, `DOCUMENTS`, `PASSPORT_PROPOSAL` |
| `valid_from` / `valid_until` | Opcional — acceso temporal |
| `status` | `ACTIVE`, `REVOKED`, `EXPIRED` |
| `granted_by_user_id` | Responsable que autoriza |
| `revoked_at`, `revoked_by` | Trazabilidad revocación |

**No** asumir consentimiento global permanente al reservar un turno M12: el turno implica acceso **operativo acotado**; histórico ampliado requiere grant explícito (diseño).

### 9.3 Permisos internos establecimiento

**Reutilizar M03** — no hardcodear roles paralelos. Capacidades M28 propuestas (códigos estables; asignación vía roles org):

| Código propuesto | Capacidad |
|------------------|-----------|
| `veterinary.care.read` | Ver pacientes/atenciones de la clínica |
| `veterinary.care.write` | Crear/editar atenciones (con grant M08) |
| `veterinary.care.correct` | Registrar correcciones versionadas |
| `veterinary.care.document.upload` | Adjuntar documentos |
| `veterinary.care.export` | Solicitar exportaciones |
| `veterinary.care.passport.propose` | Crear propuestas M14 |
| `veterinary.care.settings` | Catálogo tipos atención, plantillas |
| `veterinary.schedule.*` | **Ya existen** (047) |
| `veterinary.appointment.*` | **Ya existen** (047) |

Mapeo sugerido (configurable en org, no fijo en código):

| Rol operativo | Capacidades típicas |
|---------------|---------------------|
| Admin/Owner org | Todas `veterinary.*` de la clínica |
| Veterinario | care.read/write/correct/document/propose |
| Asistente | care.read + document.upload limitado |
| Recepción | schedule.*, appointment.*, care.read básico (sin notas clínicas sensibles) |
| Solo lectura | care.read acotado |

### 9.4 Matriz resumen lectura

| Dato | Responsable | Profesional con grant | Recepción | Público |
|------|-------------|----------------------|-----------|---------|
| Identidad mascota básica | Sí | Sí (grant) | Parcial | No |
| Notas clínicas M28 | Sí (propias) | Sí (autor) | No | No |
| Documentos M28 | Según grant | Según grant | No | No |
| Contacto responsable | Sí | Mínimo necesario | Tel. turno si policy | No |
| Ubicación exacta | No default | No | No | No |
| Pasaporte completo | Según M14 | Solo propuesta | No | Redactado M14 |

---

## 10. Establecimientos

### Reutilización

| Entidad conceptual | Persistencia | Clasificación |
|--------------------|--------------|---------------|
| VeterinaryEstablishment | `veterinary_clinic_profiles` + `organizations` (M03) | **REUTILIZAR EXISTENTE** |
| Sede / branch | `organization_branches` (M03) si aplica | **REUTILIZAR** |

### Extensión posible (sin romper 046)

| Campo / concepto | Clasificación |
|------------------|---------------|
| Preferencias M28 (timezone ya en schedule settings) | **EXTENDER** `veterinary_schedule_settings` o tabla `veterinary_clinic_m28_settings` |
| Estado suscripción comercial | **NUEVA POSIBLE** `professional_subscription` (org-scoped) |
| Política retención/exportación | Config documental; plazos numéricos **PENDIENTES** |

Un establecimiento puede operar con perfil M12 activo antes de habilitar M28; flag `m28_enabled` (conceptual) en settings.

---

## 11. Profesionales y equipos

### Reutilización

| Entidad | Persistencia | Clasificación |
|---------|--------------|---------------|
| VeterinaryProfessional | `veterinary_professionals` | **REUTILIZAR** |
| ProfessionalMembership | `veterinary_clinic_professionals` | **REUTILIZAR** |
| Especialidades | `veterinary_professional_specialties` | **REUTILIZAR** |

### Reglas

- Un profesional puede pertenecer a **varias** clínicas (filas en `veterinary_clinic_professionals`).
- Baja de membresía → `status` inactivo; **no borrar** atenciones históricas ni autoría.
- Registros históricos conservan `professional_id` y `clinic_id` originales aunque el miembro se retire.
- Equipos = membresías M03 + permisos `veterinary.*` / `veterinary.care.*`.

---

## 12. Pacientes

### Identidad

```
Pet (M08 / public.pets)  — identidad persistente única
        ↓
PatientRelationship — vínculo operativo clínica/profesional ↔ pet
        ↓
ProfessionalCare, VaccinationRecord, etc.
```

**NO** crear `clinical_pets`, `m28_patients` duplicados.

### PatientRelationship (conceptual — NUEVA POSIBLE)

| Campo | Descripción |
|-------|-------------|
| `pet_id` | FK `pets` |
| `clinic_id` | FK `veterinary_clinic_profiles` |
| `primary_professional_id` | Opcional |
| `status` | `ACTIVE`, `INACTIVE`, `CLOSED` |
| `access_grant_id` | FK grant M08 si aplica |
| `first_seen_at`, `last_care_at` | Operativos |
| `notes` | Alertas profesionales no clínicas |

### Escenarios de vinculación

| Caso | Comportamiento |
|------|----------------|
| Mascota registrada; responsable LeoVer | Grant + relación tras consentimiento o turno confirmado |
| Responsable aún no en LeoVer | Atención puede iniciarse con identificación mínima + invitación; relación **provisional** hasta claim M08 |
| Presentada por autorizado M08 | Validar `m08_actor_has_active_responsibility` o rol autorizado |
| Múltiples responsables | Grant puede requerir acción del principal o política co-responsable (M08) |
| Atención temporal | Grant con `valid_until` |
| Cambio responsable | Relación clínica persiste; grants revocados/reemitidos vía M08 |
| Mascota fallecida (`DECEASED`) | Solo lectura histórica; no nuevas atenciones |
| Duplicados potenciales | Señalar conflicto; **no** resolver titularidad automáticamente |

### Ficha profesional del paciente (vista)

Según permisos, mostrar:

- identidad mascota (nombre, especie, raza, sexo, edad);
- peso (último registrado + procedencia);
- características y alertas;
- responsables/contactos **autorizados** (mínimo);
- alergias **declaradas**;
- vacunas/controles/antecedentes **en LeoVer**;
- documentos;
- últimas atenciones;
- seguimientos pendientes;
- estado Pasaporte (resumen M14, no copia total).

### Procedencia del dato (etiquetado UI obligatorio)

| Código | Significado |
|--------|-------------|
| `DECLARED_BY_RESPONSIBLE` | Cargado por familia |
| `LOADED_BY_PROFESSIONAL` | Registro M28 |
| `VERIFIED` | Verificado M14 / credencial |
| `FROM_PASSPORT` | Proyección Pasaporte |
| `SYSTEM_GENERATED` | Derivado (edad, recordatorio) |

---

## 13. Atenciones

### Entidad: ProfessionalCare / VeterinaryEncounter

**Clasificación persistencia:** **NUEVA POSIBLE** `veterinary_professional_cares` (nombre tentativo; ADR al implementar).

| Campo | Descripción |
|-------|-------------|
| `id` | UUID |
| `pet_id` | Mascota M08 |
| `clinic_id` | Establecimiento |
| `professional_id` | Autor principal |
| `appointment_id` | Opcional — FK `veterinary_appointments` |
| `patient_relationship_id` | Opcional |
| `occurred_at` | Fecha/hora atención |
| `care_type_code` | Código estable catálogo configurable |
| `care_type_label_snapshot` | Etiqueta al momento del registro |
| `reason` | Motivo |
| `observations` | Texto libre profesional |
| `weight_kg` | Opcional |
| `findings_summary` | Hallazgos resumidos |
| `status` | `DRAFT`, `FINALIZED`, `CORRECTED`, `VOID` |
| `version` | Entero incremental |
| `supersedes_care_id` | Corrección → apunta al original |
| `correction_reason` | Si aplica |
| `created_by`, `finalized_by` | Usuarios |
| `created_at`, `updated_at` | Timestamps |

**No** diseñar historia clínica regulatoria completa. Sí **trazabilidad** completa.

### Relación con turno M12

```
veterinary_appointment (CONFIRMED/COMPLETED)
    → opcionalmente abre ProfessionalCare (DRAFT)
    → finalize vincula appointment_id
```

Idempotencia: un turno `COMPLETED` no debe generar dos atenciones finalizadas (constraint + RPC).

---

## 14. Vacunas

### Entidad: VaccinationRecord (NUEVA POSIBLE)

Puede ser sub-recurso de `ProfessionalCare` o entidad hija.

| Campo | Descripción |
|-------|-------------|
| `care_id` | Atención contenedora |
| `vaccine_code` | Catálogo configurable |
| `vaccine_label_snapshot` | Etiqueta |
| `administered_at` | Fecha |
| `dose` | Texto/código |
| `batch_number` | Opcional |
| `manufacturer` | Opcional |
| `next_due_at` | Próxima dosis/control |
| `document_asset_id` | M05 opcional |
| `provenance` | `LOADED_BY_PROFESSIONAL` |
| `verification_status` | `UNVERIFIED` \| propuesta Pasaporte pendiente |

**Copy legal:** registro operativo LeoVer; **no reemplaza** certificados oficiales ni libreta sanitaria estatal.

---

## 15. Procedimientos y estudios

### CareProcedure / StudyRecord (NUEVA POSIBLE)

Estructura ligera vinculada a `ProfessionalCare`:

| Tipo | Campos clave |
|------|--------------|
| Control | `description`, `result_summary`, `next_control_at` |
| Procedimiento | `procedure_code`, `notes`, `complication_flag` |
| Estudio | `study_type_code`, `result_summary`, `performed_at` |
| Intervención | Similar procedimiento |

Archivos asociados → M05 + metadatos M28. **No** LIS/hospital completo.

---

## 16. Documentos

### ProfessionalDocument (metadatos — NUEVA POSIBLE)

| Campo | Descripción |
|-------|-------------|
| `asset_id` | Referencia M05 |
| `care_id` | Opcional |
| `pet_id` | Mascota |
| `clinic_id`, `uploaded_by` | Trazabilidad |
| `document_type_code` | Catálogo: PDF, IMAGE, REPORT, CERTIFICATE, STUDY, INDICATION, OTHER |
| `title`, `description` | Metadatos |
| `visibility` | `CLINIC_STAFF`, `RESPONSIBLE_SHARED`, `PROFESSIONAL_ONLY` |
| `provenance` | Origen |
| `linked_passport_proposal_id` | Opcional |

Formatos: PDF, imagen, informe escaneado. Minimización: no almacenar duplicados innecesarios; una ref M05 por archivo.

Exportación y descarga auditadas (M07).

---

## 17. Indicaciones

`ProfessionalIndication` — texto registrado por profesional humano, vinculado a atención.

| Regla | Detalle |
|-------|---------|
| Plataforma | **No** autogenera diagnóstico, prescripción ni dosis |
| IA futura (M26) | Solo borrador; profesional confirma antes de `FINALIZED` |
| Separación | Indicación M28 ≠ credencial M14 hasta propuesta aceptada |

---

## 18. Seguimientos

### FollowUp (NUEVA POSIBLE)

| Estado | Descripción |
|--------|-------------|
| `PENDING` | Creado, sin fecha |
| `SCHEDULED` | Con `due_at` |
| `COMPLETED` | Cerrado |
| `CANCELLED` | Anulado |
| `OVERDUE` | Calculado si vencido |

| Campo | Ejemplo |
|-------|---------|
| `care_id` | Atención origen |
| `follow_up_type_code` | `POST_CONTROL`, `VACCINE_BOOSTER`, `SUTURE_REMOVAL`, `STUDY_REPEAT`, `RECOVERY` |
| `due_at` | Fecha objetivo |
| `assigned_professional_id` | Opcional |
| `notes` | Texto |

Genera recordatorio M06 (§20).

---

## 19. Agenda

### Auditoría de infraestructura existente

| Sistema | Tablas | Uso |
|---------|--------|-----|
| **M12 vet** | `veterinary_appointments`, availability 047 | **Autoridad agenda veterinaria** |
| M23 genérico | `m23_*` bookings 068–069 | Prestadores M22 — **no duplicar para vet** |

### Diseño M28

- Portal web **consume y gestiona** la misma agenda M12.
- RPC existentes M12 (`m12_list_managed_appointments`, confirm, complete, etc.) + extensiones web.
- Vínculo `ProfessionalCare.appointment_id` cuando la atención nace de un turno.
- **No** crear `m28_appointments`.
- Pago de consulta: **fuera de alcance** — turno sin checkout.

---

## 20. Notificaciones

Integración **M06** (best-effort; preferencias M02).

| Evento | Destinatario | Prioridad |
|--------|--------------|-----------|
| Turno próximo (24h/2h) | Responsable | Media — reutilizar M12 B4 hooks |
| Seguimiento vencido | Responsable / profesional | Media |
| Vacuna próxima | Responsable | Media |
| Propuesta Pasaporte pendiente | Responsable | Alta |
| Documento compartido | Responsable | Baja |
| Grant revocado | Profesional/clínica | Alta |

**Anti-spam:** deduplicación por `(user, event_type, entity_id, window)`; respetar preferencias; no duplicar recordatorio turno M12 + M28.

---

## 21. Integración M14 Pasaporte

### Principio crítico

La información profesional M28 **NO se copia automáticamente** al Pasaporte.

### Flujo

```
Profesional registra dato en M28 (vacuna, peso, control, documento)
        ↓
Opcional: "Proponer actualización al Pasaporte"
        ↓
PassportUpdateProposal (NUEVA POSIBLE)
        ↓
Responsable (Android/iOS/web) recibe notificación
        ↓
ACEPTA | RECHAZA | SOLICITA CORRECCIÓN
        ↓
Si ACEPTA → RPC M14 autorizado actualiza credencial/campo Pasaporte
```

### PassportUpdateProposal (conceptual)

| Campo | Descripción |
|-------|-------------|
| `proposal_type` | `VACCINATION`, `WEIGHT`, `CONTROL_EVENT`, `HEALTH_DOCUMENT`, `OTHER` |
| `source_care_id` | Atención origen |
| `source_vaccination_id` / etc. | Recurso M28 |
| `passport_id` | FK M14 |
| `previous_value_snapshot` | JSON |
| `proposed_value_snapshot` | JSON |
| `status` | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `SUPERSEDED` |
| `proposed_by_professional_id`, `clinic_id` | Origen |
| `decided_by_user_id`, `decided_at`, `decision_note` | Decisión responsable |

### Distinción vs M14 existente

| Flujo M14 actual | Flujo M28 propuesto |
|------------------|---------------------|
| `pet_passport_verification_requests` — verificar credencial emitida | Propuesta desde registro profesional M28 |
| Emisor org/profesional verifica credencial | Responsable **aprueba** reflejo en Pasaporte |
| Sin tabla `passport_update_proposals` hoy | **Nueva** en implementación futura (053+ o m28 migration) |

Al aceptar: puede crear `pet_passport_credentials` tipo `VACCINATION_ATTESTATION` u actualizar campo permitido vía RPC M14 — **sin autoverificación**.

---

## 22. Correcciones y versionado

| Regla | Implementación conceptual |
|-------|---------------------------|
| No edición silenciosa | `FINALIZED` → corrección crea nueva fila o versión con `supersedes_care_id` |
| Motivo obligatorio | `correction_reason` |
| Autor | `corrected_by` + timestamp |
| Original | Permanece legible para auditoría |
| Estados | Original `CORRECTED`; nueva versión `FINALIZED` |

Aplica a atenciones, vacunas y documentos metadatos (asset M05 inmutable; reemplazo = nuevo asset + enlace).

---

## 23. Exportación y portabilidad

**Obligatorio** por estrategia Maestro v1.1 / D01 v1.2.

### 23.1 Exportación PDF legible

- Resumen mascota + atenciones + vacunas + documentos listados.
- Cabecera: establecimiento, profesional, rango fechas, fecha exportación.
- Marca de agua: “Registro operativo LeoVer — no constituye historia clínica oficial”.
- Incluye procedencia y correcciones.

### 23.2 Exportación estructurada

Diseño mínimo (formato definitivo **PENDIENTE**):

- **JSON** o **CSV** de registros tabulares;
- **ZIP** con JSON + carpeta `documents/` (refs M05 resueltas en Edge Function con auth).

Preservar: mascota, fechas, profesionales, establecimiento, procedencia, correcciones, hashes documentos.

### 23.3 Reglas

- Profesional/establecimiento con permiso `veterinary.care.export` puede solicitar.
- Pérdida de suscripción **no bloquea** exportación de información propia pendiente (grace period conceptual — plazos numéricos **PENDIENTES**).
- Cada exportación → evento M07 `m28.export.requested` / `completed` / `failed`.

---

## 24. Privacidad

Alinear con M02/M05/M08/M12 sanitizer patterns.

| Clasificación | Ejemplos M28 |
|---------------|--------------|
| PUBLIC | Nada clínico por defecto |
| INTERNAL | Metadatos operativos clínica |
| PERSONAL | Contacto responsable |
| PROFESSIONAL | Notas atención, indicaciones |
| SENSITIVE / HIGH_RISK | Documentos salud, alergias, estudios |

**Minimización:** no exponer teléfono/domicilio/coordenadas exactas salvo necesidad operativa del turno y policy explícita.

**Microchip:** infraestructura M08 puede existir; **búsqueda operativa por microchip fuera de V1/piloto** — M28 no incluye búsqueda por chip en diseño inicial.

---

## 25. Seguridad y RLS

### Principios

- Deny by default.
- `auth.uid()` en RPC; actor nunca confiado desde cliente.
- Membresía org ACTIVE + permiso `veterinary.care.*`.
- Grant M08 vigente para acceder a `pet_id`.
- **Sin service role en cliente** (web incluido).

### Matriz RLS conceptual

| Recurso | SELECT | INSERT | UPDATE | DELETE |
|---------|--------|--------|--------|--------|
| `veterinary_professional_cares` | Miembro clínica + grant | Profesional con write + grant | Corrección vía RPC | No hard delete — VOID |
| `passport_update_proposals` | Responsable + proponente | Profesional propose | Responsable decide | Cancel |
| `professional_access_grants` | Responsable + grantee | Responsable | Revoke | No |
| Documentos metadatos | Según visibility + grant | Upload perm | Metadatos | Soft archive |

Profesional **fuera** del establecimiento tras baja: pierde SELECT salvo registros donde sigue siendo autor histórico (solo lectura propia si policy lo permite — default: solo vía export admin clínica).

---

## 26. Auditoría

**M07** — separado de analytics y observability.

| Evento | Código sugerido |
|--------|-----------------|
| Creación atención | `m28.care.created` |
| Finalización | `m28.care.finalized` |
| Corrección | `m28.care.corrected` |
| Documento cargado | `m28.document.uploaded` |
| Acceso sensible | `m28.sensitive.access` |
| Grant otorgado/revocado | `m28.grant.changed` |
| Propuesta Pasaporte | `m28.passport.proposal.created` |
| Decisión Pasaporte | `m28.passport.proposal.decided` |
| Exportación | `m28.export.*` |
| Baja miembro | `m28.membership.deactivated` |

Payload sanitizado — sin contenido clínico completo en audit log.

---

## 27. Moderación e incidentes

| Escenario | vía M04 |
|-----------|---------|
| Profesional/establecimiento reportado | Caso moderación |
| Documentación falsa | Suspensión credenciales M12/M14 |
| Acceso indebido | Revocación grants + audit |
| Incidente privacidad | Playbook M04 + notificación según legal |

**No mezclar** decisión moderación comercial con juicio clínico.

---

## 28. IA

| Prohibido (M28 y futuro M26 clínico) | Permitido (asistencia) |
|--------------------------------------|------------------------|
| Diagnóstico autónomo | Resumir notas existentes |
| Prescripción / dosis | Sugerir redacción borrador |
| Urgencia clínica definitiva | Organizar documentos |
| | Dictado → borrador |
| | Sugerir recordatorio operativo |

Siempre: **BORRADOR + confirmación profesional** antes de persistir como registro M28.

---

## 29. UX Android (responsables)

Flujos críticos piloto (no backoffice clínico completo):

| Flujo | Pantalla / ruta sugerida |
|-------|--------------------------|
| Otorgar/revocar acceso profesional | Extensión `PetDetail` / permisos M08 |
| Ver propuestas Pasaporte pendientes | `m14/passports/{id}/proposals` (nueva) |
| Aceptar / rechazar / corregir propuesta | Detalle propuesta |
| Recordatorios vacuna/control | Notificaciones + resumen mascota |
| Turnos vet existentes | Rutas M12 actuales (`my_veterinary_appointments`) |
| Documentos compartidos por clínica | Vista read-only autorizada |

Orientación: claridad, mínimo pasos, etiquetas de procedencia visibles.

---

## 30. UX Web profesional

### Stack

Next.js · React · TypeScript · Cloudflare Workers · OpenNext · Supabase Auth.

### Mapa de navegación sugerido

```
/dashboard          — Inicio operativo
/agenda             — Turnos M12 (día/semana)
/pacientes          — Lista + búsqueda autorizada
/pacientes/:petId   — Ficha profesional
/atenciones         — Lista reciente / borradores
/atenciones/:id     — Detalle / editar / finalizar
/atenciones/nueva    — Desde turno o walk-in
/seguimientos       — Pendientes / vencidos
/documentos         — Biblioteca clínica
/equipo             — Membresías M03 (vista acotada)
/establecimiento    — Perfil clínica M12
/configuracion      — Catálogos, preferencias, exportación
/exportaciones      — Historial solicitudes
```

Desktop/tablet first; operación cotidiana en pocas pantallas.

### Dashboard (útil, no vanity)

- Turnos de hoy (M12).
- Seguimientos pendientes/vencidos.
- Vacunas próximas (7/30 días).
- Pacientes vistos recientemente.
- Borradores de atención sin finalizar.
- Propuestas Pasaporte pendientes de envío.
- Alertas operativas (grant expirando, export lista).

**No** incluir métricas clínicas de “calidad médica” ni datos inexistentes.

### Búsqueda pacientes

Por nombre mascota, responsable autorizado, ID interno — **solo** con permiso. Sin búsqueda por microchip en V1.

---

## 31. iOS

Paridad con Android en flujos **críticos de responsable** durante piloto:

- propuestas Pasaporte;
- permisos profesionales;
- recordatorios;
- turnos (si superficie M12 disponible).

**No** requerir backoffice profesional nativo iOS en fase inicial — web M28 cubre gestión clínica.

---

## 32. Modelo conceptual de datos

| Entidad | Clasificación | Notas |
|---------|---------------|-------|
| VeterinaryEstablishment | **REUTILIZAR** | `veterinary_clinic_profiles` + org M03 |
| VeterinaryProfessional | **REUTILIZAR** | `veterinary_professionals` |
| ProfessionalMembership | **REUTILIZAR** | `veterinary_clinic_professionals` |
| Pet | **REUTILIZAR** | `pets` (M08) |
| Appointment | **REUTILIZAR** | `veterinary_appointments` (M12) |
| PatientRelationship | **NUEVA POSIBLE** | Vínculo clínica ↔ pet |
| ProfessionalCare | **NUEVA POSIBLE** | Atención |
| CareProcedure / StudyRecord | **NUEVA POSIBLE** | Hijos o JSON estructurado |
| VaccinationRecord | **NUEVA POSIBLE** | Hija de care o standalone |
| ProfessionalDocument | **NUEVA POSIBLE** | Metadatos + M05 |
| ProfessionalIndication | **NUEVA POSIBLE** | Parte de care o separada |
| FollowUp | **NUEVA POSIBLE** | Seguimiento |
| PassportUpdateProposal | **NUEVA POSIBLE** | Puente M28→M14 |
| ProfessionalAccessGrant | **NUEVA POSIBLE** | Consentimiento responsable |
| CareTypeCatalog | **NUEVA POSIBLE** | Códigos + labels configurables |
| PetPassport / Credentials | **REUTILIZAR** | M14 tablas existentes |
| Audit events | **REUTILIZAR** | M07 pipeline |
| File assets | **REUTILIZAR** | M05 |

**No asumir** una tabla por entidad hasta ADR de implementación; algunas pueden consolidarse (p. ej. procedimientos como JSONB en care).

---

## 33. Contratos backend (conceptual)

### Patrón LeoVer vigente

| Tipo | Uso M28 |
|------|---------|
| **Lecturas** | RPC `m28_*` / extensión `m12_*` con RLS |
| **Operaciones atómicas** | RPC SECURITY DEFINER: finalize care, propose passport, decide proposal, grant access |
| **Edge Functions** | Export ZIP grande, generación PDF, URLs firmadas M05 temporales |
| **Repositories (web/mobile)** | Mock + Supabase; sin SQL en cliente |

### RPC propuestos (nombres tentativos)

| RPC | Propósito |
|-----|-----------|
| `m28_upsert_patient_relationship` | Alta vínculo clínica-pet |
| `m28_create_care_draft` | Idempotente por appointment |
| `m28_finalize_care` | Cierra atención + hijos |
| `m28_correct_care` | Versión superseding |
| `m28_create_passport_proposal` | Propuesta M14 |
| `m28_decide_passport_proposal` | Responsable acepta/rechaza |
| `m28_grant_professional_access` | Consentimiento M08 |
| `m28_revoke_professional_access` | Revocación |
| `m28_request_export` | Encola export |
| `m28_list_patient_timeline` | Vista agregada autorizada |

Reutilizar: todos los `m12_*` appointment existentes; `m14_*` passport read/update post-aceptación; `m08_actor_has_active_responsibility`.

---

## 34. Estados, errores e idempotencia

### Idempotencia obligatoria

| Operación | Clave idempotencia |
|-----------|-------------------|
| Finalizar atención | `care_id` + estado |
| Crear atención desde turno | `appointment_id` unique finalized |
| Subir documento | `client_upload_id` |
| Propuesta Pasaporte | `(care_id, proposal_type, target_field)` pending unique |
| Decidir propuesta | `proposal_id` + estado terminal |
| Exportación | `export_request_id` |
| Recordatorio M06 | `(entity, type, due_window)` M12 pattern |

### Catálogo de errores (UX)

| Código | Escenario |
|--------|-----------|
| `M28_PET_NOT_FOUND` | Mascota inexistente |
| `M28_GRANT_REVOKED` | Permiso responsable revocado |
| `M28_PROFESSIONAL_SUSPENDED` | Profesional/clínica suspendida M04 |
| `M28_ESTABLISHMENT_SUSPENDED` | Org no ACTIVE |
| `M28_CARE_DUPLICATE` | Atención ya finalizada para turno |
| `M28_UPLOAD_FAILED` | M05 error |
| `M28_EXPORT_FAILED` | Export job fallido |
| `M28_MEMBER_INACTIVE` | Miembro dado de baja |
| `M28_PROPOSAL_ALREADY_RESOLVED` | Propuesta terminal |
| `M28_EDIT_CONFLICT` | Versión obsoleta |
| `M28_NETWORK_INTERRUPTED` | Reintento seguro |

Web: estados de carga claros, borrador local opcional para atención `DRAFT`, prevención doble submit.

---

## 35. Métricas

Métricas de **utilidad operativa** — no calidad médica.

| Métrica | Uso |
|---------|-----|
| Profesionales activos semanales | Adopción M28 |
| Establecimientos activos | Adopción |
| Pacientes con relación activa | Volumen gestionado |
| Atenciones finalizadas / semana | Uso core |
| Vacunas/controles registrados | Feature uso |
| Documentos cargados | |
| Seguimientos completados vs vencidos | |
| Propuestas Pasaporte creadas | Integración M14 |
| Tasa aceptación/rechazo propuestas | Producto |
| Exportaciones completadas | Portabilidad |
| Retención establecimiento 4 semanas | Comercial |
| WAU establecimiento | |

Analytics separado de audit log (M07).

---

## 36. M28 Pilot Minimum

Alcance mínimo recomendado para primeras clínicas piloto (San Vicente / Brown):

| Incluir | Excluir del mínimo |
|---------|-------------------|
| Web: login + dashboard + agenda M12 | Suscripción automatizada enforcement |
| Pacientes: relación + ficha básica | Catálogo admin avanzado multi-tenant |
| Atención básica finalize + peso/motivo | IA asistida |
| Vacuna simple + próxima dosis | Export ZIP estructurado complejo |
| 1 documento por atención (M05) | Roles granulares más allá de vet/admin/recepción |
| Seguimiento manual + recordatorio M06 | Integración M21 reseñas |
| Permisos grant/revoke responsable (Android) | iOS paridad completa |
| Propuesta Pasaporte vacuna/peso | Telemedicina |
| Export PDF básica | Pagos consulta |
| Auditoría eventos core | Microchip búsqueda |

**Criterio:** una clínica puede operar un día real con turnos + atención + documento + propuesta Pasaporte + export PDF.

---

## 37. M28 Post-Pilot

| Capacidad | Descripción |
|-----------|-------------|
| Export estructurada ZIP/JSON | Portabilidad completa |
| Suscripción Mercado Pago | Enforcement + grace export |
| Roles finos recepción/asistente | Matriz permisos completa |
| IA redacción borrador | M26 integrado |
| Reseñas M21 post-atención | Verificadas |
| iOS flujos responsable ampliados | |
| Métricas dashboard establecimiento | |
| Catálogos configurables multi-clínica | |
| Walk-in sin responsable LeoVer | Invitación claim |
| Integración org M22 dual | Si org es vet + prestador |

---

## 38. Fuera de alcance M28 inicial

Explícitamente **excluido** del diseño inicial (evaluable futuro):

- historia clínica oficial / registro regulatorio;
- facturación médica / obras sociales / prepagas / aseguradoras;
- pagos de consultas / split / comisión LeoVer;
- receta electrónica oficial;
- integración SENASA / gubernamental;
- hospitalización compleja / inventario clínico avanzado;
- laboratorio completo (LIS);
- diagnóstico o prescripción por IA;
- microchip funcional / búsqueda pública por chip;
- marketplace M25;
- video consulta / telemedicina clínica completa;
- farmacia integrada.

---

## 39. Secuencia futura de implementación

Preferir **bloques cohesivos** (no micro-etapas).

| Etapa | Contenido | Entregables |
|-------|-----------|-------------|
| **A — Contratos y compatibilidad** | ADR M28; extensión permisos; mocks; frontera M12 | ADR, contratos Kotlin/TS, tests contrato |
| **B — Persistencia RLS/RPC** | Tablas care, grant, proposal; RPC core; sin UI | Migración `070+_m28_*`, guards SQL |
| **C — Portal web profesional** | Next/OpenNext CF; agenda M12; pacientes; atención | Web deploy staging |
| **D — Integraciones** | Pasaporte proposals; M06 recordatorios; M08 grants Android | Flujos E2E piloto |
| **E — Exportación, auditoría, cierre** | PDF export; M07 completo; DoD; smoke | Docs cierre M28 |

Depende de cierre operativo M12 smoke externo **recomendado** pero no bloqueante para Etapa A–B documental ya hecha.

---

## 40. Criterios de aceptación

### Bloque A — Contratos

- [ ] ADR publicado con frontera M12↔M28.
- [ ] Ninguna entidad duplica `pets` ni `veterinary_clinic_profiles`.
- [ ] Catálogo tipos atención: códigos estables + labels configurables.

### Bloque B — Persistencia

- [ ] RLS deny-by-default en tablas nuevas.
- [ ] RPC no confía en actor del payload.
- [ ] Finalize care idempotente por appointment.
- [ ] Grant revocado bloquea lectura inmediata.

### Bloque C — Web

- [ ] Dashboard muestra turnos reales M12.
- [ ] Crear/finalizar atención con trazabilidad autor/fecha.
- [ ] Procedencia visible en UI.

### Bloque D — Integraciones

- [ ] Propuesta Pasaporte requiere acción responsable.
- [ ] Aceptar propuesta refleja en M14 sin autoverificación.
- [ ] Rechazar no modifica Pasaporte.

### Bloque E — Cierre

- [ ] Export PDF legible con disclaimer.
- [ ] Eventos M07 para acciones sensibles.
- [ ] Copy sin “historia clínica oficial”.
- [ ] Sin regresión rutas M12 Android.

### Transversal

- [ ] Permisos mínimo privilegio verificados.
- [ ] Sin service role en cliente.
- [ ] Microchip no operativo en búsqueda M28.
- [ ] IA no decide clínica.

---

## 41. Definition of Done (M28)

M28 se considerará **DONE** cuando:

1. Portal web profesional operativo en staging con al menos **Pilot Minimum**.
2. **No duplicación** M12 ni mascota — evidencia ADR + tests guards.
3. RLS y permisos `veterinary.care.*` + grants M08 verificados.
4. Flujo propuesta Pasaporte E2E con responsable Android.
5. Export PDF funcional + solicitud auditada.
6. Corrección/versionado demostrable en tests.
7. Auditoría M07 eventos críticos registrados.
8. Documentación cierre en `docs/03-modulos/M28-cierre-*.md`.
9. Tests focalizados: RPC guards, idempotencia, permisos, proposal flow.
10. **Sin secretos** en web/mobile.
11. **Sin regresiones críticas** M12 turnos / M14 pasaporte.
12. Copy legal revisado (no custodia oficial).
13. Smoke piloto clínica real en San Vicente o Brown.

---

## 42. Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Confundir M28 con historia clínica oficial | Disclaimer persistente; separación M14; export |
| Duplicar agenda M12/M23 | Documentar autoridad 047; no `m28_appointments` |
| Copia automática a Pasaporte | Flujo propuesta obligatorio |
| Permiso excesivo equipo clínica | Mínimo privilegio + roles M03 |
| Pérdida datos al cancelar suscripción | Grace + export obligatorio |
| Responsable sin app para aprobar propuesta | Notificación + web responsable futuro |
| Conflicto M12 smoke pendiente | M28 Etapa C puede paralelizarse con guards |
| IA scope creep | Prohibiciones explícitas §28 |
| Reputación manipulable | M21 solo post-atención real |
| Legal retención | Plazos pendientes; categorías definidas §retención |

---

## 43. Decisiones abiertas (DECISIONES ABIERTAS M28)

Solo decisiones **no cerrables** con fuentes actuales:

| ID | Tema | Notas |
|----|------|-------|
| DEC-M28-01 | Plazos numéricos retención / grace export post-baja | Pendiente legal |
| DEC-M28-02 | Precio y tiers suscripción profesional | PEN abierto; Mercado Pago confirmado como proveedor |
| DEC-M28-03 | Formato export estructurado definitivo (JSON schema / FHIR subset) | PDF acordado; ZIP pendiente |
| DEC-M28-04 | Walk-in sin responsable LeoVer — política invitación | Producto + M08 |
| DEC-M28-05 | Org dual M22 prestador + M12 clínica — unificación UI | Caso borde |
| DEC-M28-06 | Consolidación tablas care vs JSONB procedimientos | ADR implementación |
| DEC-M28-07 | Web responsable para decidir propuestas (además Android) | Piloto vs post-pilot |
| DEC-M28-08 | Código permisos `veterinary.care.*` vs prefijo `m28.*` | **CERRADO:** `veterinary.care.*` (migración 080) |

**No reabrir:** microchip fuera V1; no pagos consulta; no marketplace; no IA diagnóstico; M12 no reemplazado; mascota única M08.

---

## 44. Fuentes de verdad

| # | Documento | Ruta |
|---|-----------|------|
| 1 | Documento Maestro v1.1 | `docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md` |
| 2 | D01 v1.2 | `docs/01-producto/D01-Modulos-y-Orden-v1.2.md` |
| 3 | Inventario Mxx | `docs/00-startup/LeoVer-Inventario-Real-Modulos-Mxx-v1.0.md` |
| 4 | Saneamiento documental | `docs/00-startup/LeoVer-Saneamiento-Documental-v1.1.md` |
| 5 | Cierre saneamiento | `docs/00-startup/LeoVer-Cierre-Saneamiento-Documental-v1.0.md` |
| 6 | M12 spec | `docs/03-modulos/M12-veterinarias.md` |
| 7 | M12 persistencia / agenda | `docs/02-arquitectura/M12-persistencia-perfiles-servicios.md`, `M12-agenda-disponibilidad-turnos.md` |
| 8 | M14 Pasaporte | `docs/03-modulos/M14-pasaporte-identidad-verificable.md` |
| 9 | M08 Mascotas | `docs/03-modulos/M08-mascotas-y-responsables.md` |
| 10 | M23 agenda genérica | `docs/02-arquitectura/M23-arquitectura-agenda-reservas.md` |
| 11 | Migraciones | `046`, `047` (M12), `050–052` (M14), `035–036` (M08) |

---

## 45. Historial de versión

| Versión | Fecha | Cambios |
|---------|-------|---------|
| **1.0** | **2026-08-09** | Especificación inicial M28 — diseño completo pre-implementación; frontera M12; integración M14 propuestas; Pilot Minimum; sin código/SQL |

---

**Fin del documento M28 v1.0**
