# LEOVER — M04 Etapa 3: Persistencia, RLS, RPC y Colas Administrativas

**Módulo:** M04 — Administración, Moderación y Soporte  
**Etapa:** 3 — Persistencia segura y acceso remoto  
**Estado de entrada:** Etapa 2 aprobada y consolidada  
**Commit base:** `9908b8bcb98ddc9ad26743ed4ce17180d7d72422`  
**Backend oficial:** Supabase  
**Objetivo prioritario:** cerrar la exposición crítica de `content_reports` antes de habilitar colas administrativas reales.  
**Sin UI nueva:** la interfaz operativa completa pertenece a Etapa 4.

---

## 1. Resultado esperado

Implementar la persistencia server-side de M04 con:

- endurecimiento inmediato de `content_reports`;
- evolución compatible del reporte legacy;
- casos de moderación;
- medidas y apelaciones;
- revisión de verificación de organizaciones;
- tickets y mensajes de soporte;
- asignaciones y auditoría administrativa;
- permisos M04 en M02;
- RPC y RLS `deny-by-default`;
- repositorios Supabase compatibles con los contratos de Etapa 2;
- pruebas SQL y Android;
- sin crear pantallas nuevas.

---

## 2. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/02-arquitectura/M04-auditoria-inicial.md`
7. `/docs/02-arquitectura/M04-etapa-2-cierre.md`
8. `/docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md`
9. `/docs/03-modulos/M04-Etapa-2-Contratos-Moderacion-Verificacion-y-Soporte.md`
10. ADR-0001 a ADR-0005
11. Este documento.

---

## 3. Protección Git

Antes de modificar código:

1. Confirmar el commit base:

```text
9908b8bcb98ddc9ad26743ed4ce17180d7d72422
```

2. Confirmar working tree limpio.
3. Crear la rama:

```text
m04/etapa-3-persistencia-rls-rpc-colas
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M05.

---

## 4. Migraciones

Las migraciones `001–021` son históricas. No editarlas.

Crear una migración consecutiva desde:

```text
supabase/migrations/022_m04_administration_moderation_support_foundation.sql
```

Si una única migración resulta riesgosa o difícil de revisar, dividir en `022`, `023`, etc., manteniendo orden, atomicidad y documentación. Nunca renumerar ni reescribir migraciones previas.

### Primer bloque obligatorio

La primera operación de seguridad debe corregir `content_reports`:

- eliminar políticas `SELECT using (true)` y `UPDATE using (true)`;
- revocar escritura directa sensible a `authenticated`;
- permitir al reportante crear un reporte con `reporter_id = auth.uid()`;
- permitir al reportante leer una proyección limitada de sus propios reportes;
- permitir lectura staff solo mediante permiso M02;
- permitir mutaciones staff solo mediante RPC autorizadas;
- no exponer `reporter_id`, notas o evidencia a quien no tenga permiso sensible;
- mantener compatibilidad con `POST`, `USER` y `COMMENT`.

No dejar una ventana intermedia fail-open dentro de la migración.

---

## 5. Evolución compatible de `content_reports`

Mantener la tabla como entrada de reportes. Puede ampliarse con columnas compatibles, constraints o índices, pero no reemplazarse por una tabla duplicada.

Alinear con los contratos de Etapa 2:

```text
priority
case_id
reason_code
reason_detail
duplicate_of_report_id
updated_at
```

Reglas:

- conservar filas legacy;
- migrar valores antiguos de forma determinista;
- mapear `USER` a `USER_PROFILE` en la capa de dominio sin romper datos existentes;
- `ACTIONED` legacy no equivale automáticamente a una medida real;
- `DUPLICATE` requiere referencia válida;
- índices para cola, estado, prioridad, target y fechas;
- no almacenar contenido sensible del recurso reportado en la fila.

---

## 6. Persistencia M04

Crear solo las tablas justificadas por los contratos.

### 6.1 Moderación

```text
moderation_cases
moderation_case_reports
moderation_actions
moderation_appeals
moderation_evidence_refs
moderation_case_notes
```

Reglas:

- un reporte activo no pertenece a dos casos activos;
- notas internas separadas de proyecciones públicas;
- evidencia guarda referencias lógicas, no binarios ni URLs permanentes;
- toda acción tiene actor, motivo y timestamp;
- medidas temporales tienen expiración;
- medidas de cuenta reutilizan M02;
- medidas de organización reutilizan M03;
- no duplicar estados de cuenta u organización;
- apelación activa única por medida;
- quien aplicó la medida no resuelve la apelación.

### 6.2 Verificación de organizaciones

```text
organization_verification_reviews
organization_verification_document_refs
```

Reglas:

- M03 solicita; M04 revisa;
- solo `PENDING` puede aprobarse o rechazarse;
- `REQUEST_MORE_INFORMATION` no marca `VERIFIED`;
- revocación solo desde `VERIFIED`;
- miembro de la organización no puede revisar;
- documentos son referencias lógicas;
- notas de revisión son internas;
- actualización de `organizations.verification_status` e historial debe ser atómica.

### 6.3 Soporte

```text
support_tickets
support_ticket_messages
```

Reglas:

- solicitante crea ticket propio;
- prioridad operativa solo staff;
- mensajes `INTERNAL` nunca visibles al solicitante;
- `CLOSED` requiere resolución o motivo;
- categorías PRIVACY y SAFETY se consideran sensibles;
- no reutilizar chat social;
- sin tiempo real en esta etapa.

### 6.4 Asignaciones y auditoría

```text
administrative_assignments
administrative_audit_log
```

Puede utilizarse una tabla de asignación genérica solo si mantiene constraints claros por tipo de recurso. Si complica RLS o integridad, usar asignación específica en cada entidad.

Toda asignación, reasignación, cambio de estado, decisión o medida debe auditarse.

---

## 7. Permisos M04

Reutilizar el sistema M02. Agregar únicamente los códigos faltantes:

```text
moderation.manage_cases
moderation.apply_actions
moderation.view_sensitive
moderation.review_appeals
organizations.review_verification
organizations.revoke_verification
support.view
support.manage
support.view_sensitive
```

Ya existentes y reutilizados:

```text
moderation.view
moderation.manage_reports
audit.view
users.view_private
users.change_status
```

### Matriz mínima aprobada

| Rol plataforma | Permisos M04 mínimos |
|---|---|
| MODERATOR | `moderation.view`, `moderation.manage_reports`, `moderation.manage_cases`, `moderation.review_appeals` |
| ADMIN | permisos MODERATOR + `moderation.apply_actions`, `moderation.view_sensitive`, `organizations.review_verification`, `support.view`, `support.manage`, `support.view_sensitive`, `audit.view` |
| SUPERADMIN | todos los permisos anteriores + `organizations.revoke_verification` |

Reglas:

- respetar asignaciones existentes;
- seeds idempotentes;
- no usar el nombre del rol como chequeo en Android;
- la fuente de verdad son permisos efectivos;
- roles M03 no conceden permisos M04;
- `AccountType` y `active_modules` no conceden permisos;
- acciones sobre estado de cuenta requieren además el permiso M02 correspondiente;
- revocación de verificación requiere permiso específico.

---

## 8. RPC obligatorias

Los nombres pueden adaptarse a la convención del repositorio, pero deben existir contratos equivalentes.

### Reportes y cola

```text
create_content_report
get_my_content_reports
list_moderation_queue
get_moderation_report_for_staff
triage_content_report
mark_content_report_duplicate
```

### Casos

```text
create_moderation_case
attach_report_to_moderation_case
list_moderation_cases
get_moderation_case
assign_moderation_case
change_moderation_case_status
add_moderation_internal_note
```

### Medidas y apelaciones

```text
apply_moderation_action
submit_moderation_appeal
list_moderation_appeals
assign_moderation_appeal
review_moderation_appeal
```

### Verificación

```text
list_organization_verification_queue
get_organization_verification_review
assign_organization_verification_review
record_organization_verification_decision
```

### Soporte

```text
create_support_ticket
get_my_support_tickets
get_support_ticket_for_requester
list_support_queue
get_support_ticket_for_staff
assign_support_ticket
change_support_ticket_status
add_support_requester_message
add_support_internal_message
```

### Auditoría

La auditoría debe escribirse dentro de las RPC privilegiadas. No confiar en que Android invoque una segunda operación de auditoría.

---

## 9. Reglas SQL y seguridad

Todas las funciones privilegiadas:

- actor desde `auth.uid()`;
- `SECURITY DEFINER` solo cuando sea necesario;
- `search_path = public` fijo;
- permiso verificado server-side con M02;
- cuenta del actor activa;
- deny ante error o dato faltante;
- IDs recibidos no sustituyen identidad;
- sin service role en Android;
- sin exposición de token, secreto o PII en errores;
- grants mínimos;
- evitar recursión RLS;
- transacciones atómicas para cambios relacionados;
- timestamps server-side;
- notas internas y reporter protegidos;
- reporter no puede mutar estado, prioridad, caso o asignación;
- soporte interno no visible al solicitante;
- organización no puede autoaprobarse;
- conflicto de apelación bloqueado;
- acciones irreversibles con razón obligatoria.

### Acciones sobre cuentas y organizaciones

No duplicar lógica de M02/M03.

- `ACCOUNT_SUSPENDED` y `ACCOUNT_BANNED` deben actualizar el estado M02 y su historial de manera segura;
- `ORGANIZATION_SUSPENDED` y cambios de verificación deben actualizar M03 y su historial;
- validar permisos adicionales;
- una falla debe revertir acción, estado e historial completos.

---

## 10. RLS esperada

### Reportante / solicitante

Puede:

- crear reportes propios;
- leer una proyección segura de sus reportes;
- crear tickets propios;
- leer sus tickets y mensajes visibles;
- agregar mensajes visibles en tickets habilitados;
- presentar apelación cuando sea parte afectada.

No puede:

- ver reportes ajenos;
- ver `reporter_id` ajeno;
- ver notas internas;
- ver mensajes internos;
- cambiar prioridad, asignación o decisión;
- aplicar medidas;
- revisar verificación.

### Staff

Acceso por permisos efectivos, no por simple autenticación.

Separar:

- lectura básica;
- lectura sensible;
- mutación;
- aplicación de medidas;
- revisión de apelaciones;
- verificación;
- soporte.

No usar políticas globales `using (true)` para recursos administrativos.

---

## 11. Supabase y Android

Crear implementaciones dedicadas:

```text
SupabaseModerationRepository
SupabaseOrganizationVerificationRepository
SupabaseSupportRepository
SupabaseAdministrativeAuditRepository
```

Reglas:

- usar RPC para operaciones privilegiadas;
- mapear errores a `AppError`;
- no loguear reporter, evidencia, notas, tokens o PII;
- preservar mocks;
- `DataProvider` usa Supabase cuando `useSupabase` y mocks en modo local;
- adaptar el acceso legacy de `AdminModerationScreen` para que deje de depender de SELECT/UPDATE directo;
- no crear pantallas nuevas;
- no ampliar navegación;
- UI existente debe continuar compilando y negar ante error.

Si `AdministrativeAuditRepository` no necesita escritura directa desde cliente, dejarlo solo lectura o interno. No crear una RPC pública redundante para registrar auditoría.

---

## 12. Evidencia y M05

En Etapa 3 solo persistir referencias lógicas y metadatos mínimos.

No crear bucket administrativo todavía salvo que exista un bloqueo técnico imposible de resolver sin él. En ese caso, detenerse y documentar la decisión antes de implementarlo.

Prohibido:

- usar bucket público `leover`;
- guardar base64;
- guardar URL firmada como dato permanente;
- guardar binarios en tablas.

---

## 13. Pruebas SQL

Crear exactamente:

```text
/docs/04-calidad/M04-pruebas-persistencia-rls-rpc-colas.md
```

Cubrir como mínimo:

### `content_reports`

- autenticado no lee reportes ajenos;
- autenticado no actualiza reportes;
- reporter crea y lee proyección propia;
- MODERATOR ve cola básica;
- solo permiso sensible ve reporter;
- legacy POST/USER/COMMENT sigue funcionando;
- duplicate requiere referencia válida.

### Casos y acciones

- attach único;
- asignación con permiso;
- conflicto bloqueado;
- acción sin permiso bloqueada;
- acción de cuenta exige permiso M02;
- acción org exige permiso correspondiente;
- historial atómico;
- error revierte todo.

### Apelaciones

- afectado puede presentar;
- tercero no puede;
- una activa por medida;
- aplicador no revisa;
- decisión exige motivo.

### Verificación

- org PENDING revisable;
- miembro propio bloqueado;
- aprobación/rechazo atómico;
- revocación solo con permiso y estado válido;
- notas/documentos no públicos.

### Soporte

- usuario crea ticket propio;
- no lee ticket ajeno;
- mensaje interno oculto;
- staff sin permiso no ve cola;
- prioridad y asignación solo staff;
- cierre requiere resolución o motivo.

---

## 14. Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Requisitos:

- conservar las 224 pruebas;
- agregar pruebas de repositorios/mappers/autorización necesarias;
- 0 fallos y 0 errores;
- lint sin errores;
- no crear baseline nuevo;
- no usar suppress global;
- documentar cantidad final.

---

## 15. Validación remota

Las migraciones `014–021` continúan pendientes de validación remota.

La nueva migración M04 también debe declararse:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

salvo que exista evidencia real y acceso autorizado a staging.

No aplicar producción.

No afirmar despliegue sin evidencia.

---

## 16. Documento de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M04-etapa-3-cierre.md
```

Debe incluir:

- rama y commit base;
- migraciones creadas;
- corrección de `content_reports`;
- tablas/RPC/RLS;
- permisos y seeds;
- repositorios Supabase;
- compatibilidad legacy;
- seguridad y privacidad;
- pruebas SQL;
- cantidad de tests Android;
- build y lint;
- estado staging;
- deuda aceptada;
- checklist;
- parada.

---

## 17. Criterios de aceptación

- [ ] Working tree inicial limpio.
- [ ] Rama correcta.
- [ ] No se editaron migraciones 001–021.
- [ ] Migración nueva consecutiva.
- [ ] `content_reports` dejó de estar abierto a authenticated.
- [ ] Reporter conserva creación y lectura propia segura.
- [ ] Staff usa permisos M02.
- [ ] Datos sensibles requieren permiso adicional.
- [ ] Contratos Etapa 2 persistidos.
- [ ] Casos, medidas y apelaciones seguros.
- [ ] Verificación org segura y atómica.
- [ ] Soporte separa mensajes internos.
- [ ] Auditoría dentro de RPC privilegiadas.
- [ ] Roles M03 sin autoridad M04.
- [ ] AccountType/active_modules sin autoridad.
- [ ] Sin bucket público para evidencia.
- [ ] Repositorios Supabase dedicados.
- [ ] AdminModeration legacy ya no depende de acceso directo inseguro.
- [ ] Pruebas SQL documentadas.
- [ ] Tests Android verdes.
- [ ] Build y lint verdes.
- [ ] Staging declarado honestamente.
- [ ] Sin UI nueva.
- [ ] Sin M05.
- [ ] Sin merge a main.

---

## 18. Parada

No iniciar Etapa 4.

No crear pantallas nuevas.

No iniciar M05.

No hacer merge a `main`.

Detenerse cuando existan:

```text
/docs/02-arquitectura/M04-etapa-3-cierre.md
/docs/04-calidad/M04-pruebas-persistencia-rls-rpc-colas.md
```
