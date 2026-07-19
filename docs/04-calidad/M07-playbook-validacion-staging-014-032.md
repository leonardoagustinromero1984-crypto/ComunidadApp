# LeoVer — M07 Playbook: Validación Staging 014–032

**Versión:** 1.0  
**Producto:** LeoVer  
**Módulo:** M07 — Auditoría, Analítica y Observabilidad  
**Commit de referencia (Etapa 6):** `1ee50d3711e6fe20266251df76dd7f1ec810b7d1`  
**Rama de referencia:** `m07/etapa-6-validacion-staging-cierre-final`  
**Entorno permitido:** staging no productivo únicamente  
**Entorno prohibido:** producción  
**Estado de entrada:** foundation M07 cerrado localmente · **RELEASE BLOQUEADO** · **PENDIENTE DE VALIDACIÓN REMOTA**

---

## 0. Propósito y límites

Este playbook guía la validación remota de migraciones `014`–`032` y la matriz M07 en staging.

**Incluye**

- preparar staging;
- aplicar solo migraciones pendientes;
- ejecutar matriz de validación;
- revalidar username (sin corregirlo en M07);
- actualizar evidencia y decidir release técnico.

**No incluye / no permite**

- tocar producción;
- iniciar M08;
- merge a `main` (solo evaluar después de PASS);
- editar o reejecutar migraciones ya aplicadas;
- simular resultados remotos;
- implementar exportación de archivo o integración M06 solo para “cerrar deuda”;
- modificar `AuthRepository`, `domain/auth` o `UsernameValidators`;
- corregir el bug de username en la rama M07;
- activar Crashlytics / Firebase Analytics / Sentry / OpenTelemetry;
- analítica comercial, marketing o tracking individual.

Deudas honestas que pueden permanecer documentadas tras PASS de foundation:

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

---

## 1. Orden de ejecución (obligatorio)

```text
1. Preparar staging
2. Aplicar solo migraciones pendientes (014→032 según historial real)
3. Ejecutar matriz de validación
4. Revalidar username
5. Cerrar release técnico (quitar RELEASE BLOQUEADO solo si todo PASS)
6. Evaluar merge controlado a main
7. Recién entonces comenzar M08
```

No saltar pasos. Sin backup no hay apply. Sin matriz PASS no hay release.

---

## 2. Preparar staging

### 2.1 Confirmar project ref

| Campo | Registrar |
|---|---|
| Project ref | (UUID / ref real de Supabase staging) |
| URL API | (sin secrets en docs públicos del repo si es sensible) |
| Entorno | staging no productivo |
| Actor técnico | nombre / herramienta |
| Fecha/hora (UTC−3 o UTC) | |

**Criterio PASS:** project ref distinto de producción y autorizado explícitamente.

**STOP si:** no hay project ref, hay duda de que sea producción, o no hay autorización escrita.

### 2.2 Verificar acceso autorizado

Evidencias mínimas (al menos una real):

- `supabase link` / `config.toml` apuntando al project ref de staging; **o**
- acceso Dashboard + SQL editor / CLI con rol suficiente; **o**
- credenciales staging en secret store autorizado (no commitear al repo).

Registrar:

| Campo | Valor |
|---|---|
| caso | Acceso autorizado staging |
| entorno | staging |
| fecha | |
| actor técnico | |
| resultado | PASS / FAIL / BLOQUEADO |
| evidencia | |
| observaciones | |

**STOP si:** acceso ausente → mantener `PENDIENTE DE VALIDACIÓN REMOTA` y `RELEASE BLOQUEADO`. No inventar PASS.

### 2.3 Revisar historial remoto de migraciones

Consultar el historial real (p. ej. `supabase_migrations.schema_migrations` / CLI `migration list`).

Construir la lista:

| Versión local | ¿Existe remoto? | Acción |
|---|---|---|
| 014 … 032 | sí / no | skip si sí; aplicar si no, en orden |

Reglas:

- no editar archivos `014`–`032` en disco para “arreglar” remoto;
- no reejecutar una versión ya registrada;
- si hay drift (remoto distinto al archivo local), **STOP** y escalar — no forzar.

### 2.4 Crear backup o punto de recuperación

Antes del primer `apply` pendiente:

- snapshot / backup de DB staging; **o**
- punto de recuperación documentado (provider + ID + timestamp).

Registrar actor, fecha, entorno, ID de backup.

**STOP si:** no hay backup. No aplicar migraciones.

---

## 3. Aplicar solo migraciones pendientes

Secuencia canónica:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023
→ 024 → 025 → 026 → 027 → 028 → 029 → 030 → 031 → 032
```

Notas:

- Empezar desde la **primera que no exista remotamente**.
- Incluir **032** (hardening D1–D3 de permisos M07). No edita 029–031.
- Aplicar de a una o por lote ordenado según herramienta; fallar = STOP + no continuar.
- Tras apply, reconsultar historial y adjuntar evidencia (lista de versiones remotas).

Comando típico (solo si linkeado a staging; **nunca** a producción):

```bash
# Verificar proyecto linkeado ANTES
supabase projects list
# migration list / db push solo contra staging autorizado
```

Plantilla de evidencia por migración aplicada:

| Caso | entorno | fecha | actor | resultado | evidencia | observaciones |
|---|---|---|---|---|---|---|
| Apply NNN | staging | | | PASS/FAIL | version list / log | |

---

## 4. Matriz de validación remota

Para cada caso: `caso · entorno · fecha · actor · resultado · evidencia · observaciones`.  
Resultados válidos: `PASS` | `FAIL` | `NO EJECUTADO` | `BLOQUEADO`.  
No simular.

### 4.1 RLS y grants

| ID | Caso | Criterio PASS |
|---|---|---|
| S01 | Cliente authenticated sin DML directo en tablas M07 | INSERT/UPDATE/DELETE denegados |
| S02 | Writers internos sin PUBLIC/anon/authenticated EXECUTE | solo roles previstos |
| S03 | `m07_record_metric` solo `service_role` | authenticated denegado |
| S04 | RLS deny-by-default en tablas M07 | SELECT cliente sin política denegado o vacío seguro |
| S05 | SECURITY DEFINER con `search_path = public` | inspección remota de funciones M07 |

### 4.2 Permisos M07

Permisos dedicados a verificar:

```text
observability.view
observability.manage
audit.view_sensitive
security.events.view
export.audit_data
alert.manage
retention.manage
health.check.execute
```

| ID | Caso | Criterio PASS |
|---|---|---|
| P01 | Usuario común | denegado en RPC/UI M07 |
| P02 | `AccountType` / `active_modules` | no otorgan autoridad |
| P03 | Deep links internos | no otorgan acceso |
| P04 | Staff plataforma | permisos globales según matriz |
| P05 | Rol M03 org | scope organizacional |
| P06 | `audit.view` (M02) | **no** autoriza RPC M07 (`m07_list_audit_events`, health manual, evaluate alerts) |
| P07 | Health MANUAL | requiere `health.check.execute` |
| P08 | Evaluate alerts | `observability.manage` \| `alert.manage` |

### 4.3 Catálogos 118 / 28 / 14

| ID | Caso | Criterio PASS |
|---|---|---|
| C01 | Event keys SQL | exactamente **118**, sin duplicados |
| C02 | Metric keys SQL | exactamente **28** |
| C03 | Health check keys SQL | exactamente **14** |
| C04 | Drift Kotlin↔SQL | 0 (comparar con catálogo app del commit de referencia) |
| C05 | Event key desconocido | denegado |
| C06 | `m07.incident.staff_notification` | catalogado; envío **no** simulado |

### 4.4 Auditoría, security events y errores

| ID | Caso | Criterio PASS |
|---|---|---|
| A01 | Append-only auditoría | sin UPDATE/DELETE cliente |
| A02 | Actor server-side | actor coherente con JWT/sesión |
| A03 | Metadata allowlist | claves prohibidas rechazadas |
| A04 | Sin PII/tokens/SQL/stack/signed URL/INTERNAL | respuesta y filas sanitizadas |
| A05 | Correlation ID | presente en escrituras/lecturas auditadas |
| A06 | Una auditoría por request paginado | no por fila |
| A07 | Denegaciones relevantes | en `security_events` |
| A08 | Errores técnicos | en `application_errors` con fingerprint/dedup |
| A09 | Sin loops de observabilidad | fallo de write no dispara cascada |

### 4.5 Métricas, health e incidentes

| ID | Caso | Criterio PASS |
|---|---|---|
| M01 | Métricas agregadas | sin user_id/email/IP/coords/fingerprint |
| M02 | Dimensions/units/ventanas | allowlisted |
| M03 | Health sin evidencia | **UNKNOWN** |
| M04 | TTL health | expirados no se tratan como HEALTHY fresco |
| M05 | Health manual | con permiso; sin mutación de dominio |
| M06 | Incidentes | OPEN→ACK→RESOLVED; inválidas denegadas; idempotencia |
| M07 | SUPPRESSED | según política |
| M08 | Sin DELETE incidente cliente | denegado |

### 4.6 Retención y legal hold

| ID | Caso | Criterio PASS |
|---|---|---|
| R01 | Preview | no muta datos de negocio |
| R02 | Execute | exige preview válido y reciente |
| R03 | Preview consumido | no reutilizable |
| R04 | Execute | idempotente / lotes limitados |
| R05 | Legal hold | bloquea purge |
| R06 | `LEGAL_REVIEW_REQUIRED` | no se purga |
| R07 | Incidentes abiertos / exports en proceso | protegidos |
| R08 | Silos M02–M06 | no eliminados |
| R09 | Respuestas | sin contenido eliminado |
| R10 | Deep link | no ejecuta retención |
| R11 | UI | preview / confirm / execute separados |

### 4.7 Exportaciones (deuda aceptable)

| ID | Caso | Criterio PASS |
|---|---|---|
| E01 | Request + authorize | auditados |
| E02 | Estado | `AUTHORIZED` + `filePending` |
| E03 | `READY_SIMULATED` | no alcanzable en flujos nuevos; no mostrado como archivo real |
| E04 | Android | sin signed URL / bucket path / CSV-JSONL simulado |
| E05 | Deuda | permanece **EXPORTACIÓN DE ARCHIVO PENDIENTE** si no hay implementación real |

### 4.8 UI administrativa y deep links

Rutas:

```text
observability_overview
observability_metrics
observability_health
observability_incidents
observability_audit
observability_errors
observability_exports
observability_retention
observability_permissions_info
```

| ID | Caso | Criterio PASS |
|---|---|---|
| U01 | Rutas ocultas sin permiso | |
| U02 | Permisos revalidados en pantalla | |
| U03 | Org incorrecta | denegado |
| U04 | Deep links | sin autoridad |
| U05 | Loading / empty / error / retry | |
| U06 | Logout / cambio de cuenta | limpia estado |
| U07 | Permissions info | no edita roles |
| U08 | Sin contenido sensible en UI | |

### 4.9 Edge push y delete-account

| ID | Caso | Criterio PASS |
|---|---|---|
| G01 | Push | sin secretos/token raw en respuesta cliente |
| G02 | delete-account | logs/respuestas sanitizados |
| G03 | Correlation | presente cuando aplica |
| G04 | Métricas/health Edge | sin PII |

### 4.10 Integración M06

| ID | Caso | Criterio PASS |
|---|---|---|
| N01 | Notificación staff incidente | **INTEGRACIÓN M06 PENDIENTE** (no simular éxito) |
| N02 | Ack/resolve incidente | no depende de M06 |

---

## 5. Revalidar username

**Después** de apply remoto exitoso.

| Campo | Valor |
|---|---|
| caso | Revalidación verificación username |
| entorno | staging |
| fecha | |
| actor | |
| resultado | PASS (ok) / FAIL (sigue) / NO EJECUTADO |
| evidencia | pasos + capturas/logs sanitizados |
| observaciones | |

Reglas:

1. No corregir en M07.  
2. Si **desaparece**: documentar qué dependencia remota (migración/función/config) lo resolvió.  
3. Si **continúa**: abrir rama separada de autenticación; no mezclar con M07.  
4. `AuthRepository`, `domain/auth`, `UsernameValidators` intactos en ramas M07.

Si staging no se aplicó:

```text
USERNAME NO REVALIDADO — STAGING PENDIENTE
```

---

## 6. Cerrar release técnico

### 6.1 Actualizar evidencia

Actualizar:

- `docs/04-calidad/M07-reporte-validacion-staging.md` con resultados **reales**;
- este playbook (sección bitácora) o anexo de ejecución;
- opcional: versión docx oficial si el proceso lo exige.

### 6.2 Quitar RELEASE BLOQUEADO

Solo si **todas** las condiciones:

- [ ] Project ref staging confirmado (no producción)  
- [ ] Backup registrado  
- [ ] Historial remoto coherente; pendientes `014`–`032` aplicadas sin re-run  
- [ ] Matriz S/P/C/A/M/R/E/U/G sin FAIL bloqueante  
- [ ] Username revalidado y documentado (o FAIL → rama auth, sin bloquear foundation si política lo acepta — registrar decisión)  
- [ ] Deudas export/M06 documentadas honestamente  
- [ ] Quality local del commit de referencia sigue verde  

Entonces reemplazar:

```text
RELEASE BLOQUEADO
```

por:

```text
STAGING PASS — RELEASE TÉCNICO M07 HABILITADO
```

Si cualquier FAIL bloqueante: mantener **RELEASE BLOQUEADO**.

### 6.3 Después del PASS

1. Evaluar **merge controlado** a `main` (PR, no force).  
2. **Recién entonces** iniciar M08.  
3. No iniciar M08 con release bloqueado.

---

## 7. Bitácora de ejecución (llenar en vivo)

| Paso | Fecha | Actor | Resultado | Evidencia |
|---|---|---|---|---|
| Project ref | | | | |
| Acceso | | | | |
| Historial remoto | | | | |
| Backup | | | | |
| Apply 014–032 | | | | |
| Matriz seguridad | | | | |
| Matriz permisos | | | | |
| Catálogos | | | | |
| Auditoría/security/errores | | | | |
| Métricas/health/incidentes | | | | |
| Retención | | | | |
| Export | | | | |
| UI/deep links | | | | |
| Edge | | | | |
| Username | | | | |
| Decisión release | | | | |

---

## 8. Prompt de Cursor (copiar/pegar)

Usar el siguiente prompt en una sesión Cursor **solo** cuando exista acceso autorizado a staging. No ejecutar contra producción.

```text
LeoVer — Validación staging M07 (014–032). Producto: LeoVer.

Commit de referencia: 1ee50d3711e6fe20266251df76dd7f1ec810b7d1
Rama de referencia: m07/etapa-6-validacion-staging-cierre-final
Playbook: docs/04-calidad/M07-playbook-validacion-staging-014-032.md
Reporte a actualizar: docs/04-calidad/M07-reporte-validacion-staging.md

REGLAS OBLIGATORIAS
- Solo staging no productivo. NUNCA producción.
- No iniciar M08.
- No merge a main en esta sesión (solo documentar si staging PASS).
- No editar ni reejecutar migraciones ya aplicadas.
- No simular resultados remotos.
- No modificar AuthRepository, domain/auth ni UsernameValidators.
- No corregir username en ramas M07.
- No implementar analítica comercial, marketing, tracking individual.
- No agregar Crashlytics, Firebase Analytics, Sentry, OpenTelemetry.
- Conservar EXPORTACIÓN DE ARCHIVO PENDIENTE e INTEGRACIÓN M06 PENDIENTE si no hay implementación real segura.
- Quitar RELEASE BLOQUEADO solo con evidencia PASS de la matriz.

ORDEN
1) Confirmar project ref staging y acceso autorizado. Si no hay acceso: documentar PENDIENTE DE VALIDACIÓN REMOTA + RELEASE BLOQUEADO y DETENER.
2) Revisar historial remoto de migraciones.
3) Crear backup / punto de recuperación. Sin backup: DETENER.
4) Aplicar únicamente pendientes desde la primera faltante hasta 032, en orden 014→032.
5) Ejecutar matriz del playbook: RLS/grants, permisos M07, catálogos 118/28/14, auditoría/security/errores, métricas/health/incidentes, retención/legal hold, export, UI/deep links, Edge push y delete-account.
6) Revalidar username: registrar resultado; si falla, proponer rama auth separada; si ok, documentar dependencia remota.
7) Actualizar M07-reporte-validacion-staging.md con casos reales (caso/entorno/fecha/actor/resultado/evidencia/observaciones).
8) Si todo PASS: documentar STAGING PASS — RELEASE TÉCNICO M07 HABILITADO. Si no: mantener RELEASE BLOQUEADO.

ENTREGABLES
- Bitácora completa en el playbook o anexo.
- Reporte staging actualizado con evidencias.
- Resumen final: project ref (enmascarado si hace falta), migraciones aplicadas, PASS/FAIL por sección, username, decisión de release.
```

---

## 9. Referencias

- `docs/02-arquitectura/M07-etapa-6-cierre.md`
- `docs/02-arquitectura/M07-cierre-final.md`
- `docs/04-calidad/M07-reporte-validacion-staging.md`
- `docs/04-calidad/M07-pruebas-validacion-final.md`
- `docs/03-modulos/M07-Etapa-6-Validacion-Integral-Staging-y-Cierre-Final.md`
- `supabase/migrations/029_*.sql` … `032_m07_stage6_final_validation_hardening.sql`
- Oficial: `documentacion-oficial/07-auditoria-observabilidad/M07-Playbook-Validacion-Staging-014-032-v1.0.docx`

---

## 10. Changelog

| Versión | Fecha | Notas |
|---|---|---|
| 1.0 | 2026-07-17 | Playbook inicial post-cierre local M07 Etapa 6. Sin ejecución remota. |
