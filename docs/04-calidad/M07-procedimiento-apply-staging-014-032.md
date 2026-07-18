# M07 — Procedimiento de apply staging (migraciones 014–032)

**Versión:** 1.0  
**Fecha:** 2026-07-18  
**Producto:** LeoVer  
**Commit local validado:** `80379b1201b3a31e94a572130a44cf07304a87ac`  
**Rama de trabajo:** `m07/validacion-staging-014-032`  
**CLI de referencia:** Supabase CLI 2.109.1  
**Entorno permitido:** staging no productivo  
**Entorno prohibido:** producción  

```text
VALIDACIÓN SUPABASE LOCAL PASS
PENDIENTE DE VALIDACIÓN REMOTA
RELEASE BLOQUEADO
USERNAME NO REVALIDADO — STAGING PENDIENTE
EXPORTACIÓN DE ARCHIVO PENDIENTE
INTEGRACIÓN M06 PENDIENTE
```

**Estado de acceso actual (2026-07-18):** clasificación **B** — configurado pero no verificado.  
Este documento prepara el apply; **no lo ejecuta**.

---

## 1. Prerrequisitos

| # | Prerrequisito | Obligatorio |
|---|---|---|
| 1 | Commit local `80379b1` (o successor FF) con PASS local | Sí |
| 2 | Autorización escrita: project ref es **staging** | Sí |
| 3 | Confirmación escrita: staging **≠** producción | Sí |
| 4 | Backup/snapshot previo registrado | Sí |
| 5 | Acceso CLI (`login`/`token`) del propietario | Sí |
| 6 | `supabase link` solo al ref staging verificado | Sí |
| 7 | Docker/CLI no requeridos en remoto; sí para smoke local | Según caso |
| 8 | Ventana de mantenimiento acordada | Recomendado |

**STOP** si falta cualquiera de 1–6.

---

## 2. Responsables

| Rol | Responsabilidad |
|---|---|
| Propietario del proyecto Supabase | Confirmar ref staging ≠ prod; emitir token; autorizar link |
| Operador técnico | Ejecutar checklist, registrar evidencias sanitizadas |
| Revisor | Verificar que no se usó producción ni repair injustificado |

---

## 3. Verificación de entorno

### 3.1 Confirmación staging ≠ producción

Registrar (sin secrets):

| Campo | Valor |
|---|---|
| Project ref staging (últimos 4) | |
| Project ref producción (últimos 4) o “N/A declarado” | |
| Declaración escrita actor/fecha | |
| ¿Refs distintos? | Sí / No |

**FAIL / STOP** si no se puede demostrar desigualdad o hay duda.

### 3.2 Herramientas

```text
supabase --version          # esperado ≥ 2.109.1
git rev-parse HEAD          # debe incluir fixes 020/029/031
git status --short          # limpio recomendado
```

### 3.3 Prohibiciones absolutas

- No usar producción.
- No `db push` sin link staging verificado.
- No `migration repair` sin evidencia + autorización explícita.
- No reset remoto de staging sin autorización (destructivo).
- No editar migraciones 001–032 durante el apply.
- No modificar AuthRepository / domain/auth / UsernameValidators.
- No iniciar M08.
- No merge a `main` hasta PASS remoto + decisión explícita.
- No imprimir tokens, passwords, anon/service_role, JWT ni URLs con secrets en reportes.
- No simular resultados.

---

## 4. Backup

Antes de cualquier apply:

1. Crear snapshot/backup de la DB staging (Dashboard o procedimiento del plan Supabase).  
2. Registrar: fecha UTC, actor, ID de backup, entorno=staging.  
3. Verificar que el backup es restaurable (o PITR disponible).

**Sin backup → DETENER.**

---

## 5. Historial previo (read-only)

Solo con link staging ya verificado:

```text
supabase migration list --linked
```

Registrar:

| Campo | Valor |
|---|---|
| Versión máxima remota | |
| Versiones remotas presentes | |
| Local 014–032 | 014…032 en repo |
| Pendientes reales | intersección “en local y no en remoto” |
| Remotas desconocidas (no en local) | |
| Duplicados / desorden | |

### Reglas

- Confirmar estado de **001–013** en remoto (esperadas aplicadas).  
- Si alguna de **014–032** ya está aplicada con **contenido anterior distinto**, **NO** reaplicar; escalar (puede requerir migración correctiva nueva autorizada, no repair ciego).  
- Los fixes L01–L04 viven **dentro** de 020/029/031 del commit validado: solo aplican si esas versiones **aún no** están en el historial remoto.

---

## 6. Dry-run

Intentar:

```text
supabase db push --linked --dry-run
```

| Resultado | Acción |
|---|---|
| Soportado | Revisar plan de migraciones a aplicar; capturar salida sanitizada |
| No soportado en CLI 2.109.1 | Documentar “dry-run N/D”; **no** sustituir con apply real; usar `migration list` + revisión manual del diff de pendientes |

---

## 7. Apply

Solo pendientes confirmadas:

```text
supabase db push --linked
```

Alternativa controlada (si el equipo usa otra vía autorizada): aplicar SQL de pendientes vía pipeline aprobado — siempre sin producción.

Registrar por cada migración pendiente: PASS/FAIL, SQLSTATE si falla, objeto afectado.

**Ante el primer error SQL remoto:** detener, no forzar, no inventar 033 sin defecto real reproducible.

---

## 8. Validaciones SQL post-apply

Ejecutar en staging (SQL editor o `db query --linked` si aplica) evidencias equivalentes al PASS local:

### 8.1 Catálogos

- exactamente **118** event keys  
- exactamente **28** metric keys  
- exactamente **14** health checks  
- **8** permisos M07  
- sin duplicados  
- igualdad SQL ↔ Kotlin  

### 8.2 Permisos

- `audit.view` no autoriza list/health/evaluate M07  
- list audit: `observability.view` \| `audit.view_sensitive`  
- health MANUAL: `health.check.execute`  
- evaluate alerts: `observability.manage` \| `alert.manage`  
- usuario común denegado  
- AccountType / active_modules no conceden autoridad  

### 8.3 RLS / grants / DEFINER

- RLS en tablas M07  
- authenticated sin INSERT/UPDATE/DELETE directo en tablas core M07  
- writers sin PUBLIC/anon EXECUTE indebido  
- `m07_record_metric` solo `service_role`  
- funciones `m07_%` SECURITY DEFINER con `search_path = public`  
- tipos de extensions calificados donde aplique (`extensions.citext`)  

### 8.4 Auditoría / errores

- append-only; actor server-side; correlation ID  
- metadata / event key desconocidos denegados  
- sin PII/JWT/Bearer/tokens/service_role/signed URL/SQL/stack/body INTERNAL  
- fingerprint / dedup; sin loops  

### 8.5 Métricas / health

- agregadas; dimensions/units allowlisted  
- sin evidencia → UNKNOWN; TTL; MANUAL con permiso; sin mutación de dominio  

### 8.6 Incidentes

- idempotencia; OPEN → ACKNOWLEDGED → RESOLVED  
- transición inválida denegada; sin DELETE cliente; sin PII  

### 8.7 Retención

- preview no purga; execute sin preview denegado  
- preview consumido no reutilizable; lotes limitados  
- legal hold / `LEGAL_REVIEW_REQUIRED` bloquean  
- incidentes abiertos / exports en proceso / silos M02–M06 protegidos  
- respuesta sin contenido eliminado  

### 8.8 Exportación

- request → `AUTHORIZED` + `filePending`  
- `READY_SIMULATED` no alcanzable en flujo nuevo  
- sin CSV/JSONL/signed URL simulados  

Mantener:

```text
EXPORTACIÓN DE ARCHIVO PENDIENTE
```

### 8.9 Integración M06

- `m07.incident.staff_notification` catalogado  
- sin envío simulado  

Mantener:

```text
INTEGRACIÓN M06 PENDIENTE
```

---

## 9. Smoke tests Android

Contra staging **solo** con URL/anon de staging verificado (no prod):

```text
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Opcional: lint/jacoco/quality locales (no sustituyen matriz SQL remota).

---

## 10. Validación username (post-staging)

```text
USERNAME NO REVALIDADO — STAGING PENDIENTE
```

Tras apply remoto: revalidar comportamiento de username **sin** modificar AuthRepository / domain/auth / UsernameValidators en esta rama.  
Si el defecto persiste → documentar y abrir rama de autenticación separada.  
Si desaparece → documentar dependencia remota.

---

## 11. Rollback / recuperación

| Situación | Acción |
|---|---|
| Fallo a mitad de apply | Detener; no repair ciego; restaurar backup |
| Apply completo con defecto funcional | Evaluar restore snapshot; no “revert SQL” ad hoc |
| Duda de entorno | Abortar inmediatamente |

Registrar ID de backup usado y hora de restore si aplica.

---

## 12. Evidencias a archivar (sanitizadas)

- project ref últimos 4 + declaración staging ≠ prod  
- ID backup  
- salida `migration list` (sin secrets)  
- resultado dry-run o “N/D”  
- log apply por versión (PASS/FAIL)  
- resultados SQL catálogos/permisos/RLS  
- smoke Android  
- actualización de `M07-reporte-validacion-staging.md`  

---

## 13. Criterios PASS / FAIL

### PASS remoto (futuro)

- historial 014–032 coherente con pendientes aplicadas  
- catálogos 118/28/14 + 8 permisos  
- matriz SQL PASS  
- deudas filePending/M06 documentadas honestamente  
- username revalidado (resultado documentado)  
- producción intacta  

Solo entonces se puede evaluar quitar `RELEASE BLOQUEADO`.

### FAIL

- cualquier apply a entorno no verificado  
- SQLSTATE bloqueante sin corrección autorizada  
- divergencia de historial no resuelta  
- ausencia de backup  
- secrets expuestos en docs/commits  

---

## 14. Checklist operativo

```text
[ ] Autorización escrita staging
[ ] staging ≠ producción confirmado
[ ] Backup/snapshot ID registrado
[ ] Link CLI solo a staging verificado
[ ] migration list --linked capturado
[ ] Pendientes 014–032 determinadas
[ ] Sin reaplicar ya existentes
[ ] dry-run ejecutado o documentado N/D
[ ] db push --linked (solo pendientes)
[ ] Historial post-apply OK
[ ] SQL catálogos 118/28/14
[ ] SQL 8 permisos + gates 032
[ ] SQL RLS/grants/DEFINER
[ ] SQL incidentes/retención/export/M06
[ ] Smoke Android
[ ] Username revalidado (sin fix auth en M07)
[ ] Reporte staging actualizado
[ ] RELEASE sigue bloqueado hasta PASS completo
[ ] Producción no tocada
[ ] M08 no iniciado
[ ] main no mergeado
```

---

## 15. Comandos exactos pendientes (no ejecutar hasta prerrequisitos)

```text
supabase --version
git rev-parse HEAD
supabase migration list --linked
supabase db push --linked --dry-run
supabase db push --linked
```

Sustituir credenciales solo vía entorno seguro del propietario; **nunca** committear tokens ni passwords.
