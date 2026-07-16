# LEOVER — M04 Etapa 5: Validación, Staging, Calidad y Cierre Final

**Módulo:** M04 — Administración, Moderación y Soporte  
**Etapa:** 5 — Validación integral y cierre final  
**Estado de entrada:** Etapa 4 aprobada y consolidada  
**Commit base:** `41e0d65cc366602959bd8b1292701f7633213a29`  
**Rama base:** `m04/etapa-4-ui-flujos-operativos`  
**Calidad de entrada:** 258 tests, 0 failures, 0 errors; `assembleDebug` y `lintDebug` en SUCCESS  
**Backend oficial:** Supabase  
**Staging heredado:** migraciones `014`–`022` pendientes de validación remota  
**Objetivo:** validar integralmente M04, corregir únicamente defectos reales, documentar staging y cerrar formalmente el módulo sin iniciar M05.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/02-arquitectura/M04-auditoria-inicial.md`
7. `/docs/02-arquitectura/M04-etapa-2-cierre.md`
8. `/docs/02-arquitectura/M04-etapa-3-cierre.md`
9. `/docs/02-arquitectura/M04-etapa-4-cierre.md`
10. `/docs/04-calidad/M04-pruebas-persistencia-rls-rpc-colas.md`
11. `/docs/04-calidad/M04-pruebas-ui-flujos-operativos.md`
12. `/docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md`
13. `/docs/03-modulos/M04-Etapa-2-Contratos-Moderacion-Verificacion-y-Soporte.md`
14. `/docs/03-modulos/M04-Etapa-3-Persistencia-RLS-RPC-y-Colas-Administrativas.md`
15. `/docs/03-modulos/M04-Etapa-4-UI-y-Flujos-Operativos.md`
16. ADR-0001 a ADR-0005
17. Este documento.

---

## 2. Protección Git

Antes de trabajar:

1. Confirmar commit base:

```text
41e0d65cc366602959bd8b1292701f7633213a29
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m04/etapa-5-validacion-cierre
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M05.
7. No usar producción.
8. No afirmar validación remota sin evidencia.

---

## 3. Alcance de la Etapa 5

### 3.1 Revisión estática integral

Auditar:

- migraciones `014`–`022`;
- que `001`–`021` no hayan sido editadas por M04;
- orden y dependencias de `022`;
- eliminación de políticas abiertas de `content_reports`;
- grants y revokes;
- RLS y riesgo de recursión;
- funciones `SECURITY DEFINER`;
- `search_path`;
- actor derivado de `auth.uid()`;
- permisos M02 usados por M04;
- separación de roles M03;
- protección de `reporter_id`;
- notas internas;
- mensajes `INTERNAL`;
- auditoría administrativa;
- medidas de cuenta y organización;
- apelaciones;
- verificación;
- soporte;
- navegación;
- deep links;
- limpieza al logout;
- logs;
- PII;
- errores y estados loading.

Corregir únicamente defectos reales de M04.

No agregar funcionalidades nuevas.

### 3.2 Calidad local obligatoria

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Requisitos:

- build SUCCESS;
- todas las pruebas aprobadas;
- lint con 0 errores;
- no eliminar pruebas;
- no crear baseline nuevo;
- no usar suppress global;
- documentar cantidad total de tests.

### 3.3 Validación de migraciones

Determinar con evidencia si las migraciones `014`–`022` están aplicadas en un entorno autorizado.

#### Si existe acceso autorizado a staging

Aplicar respetando el historial real:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022
```

Antes de aplicar:

- revisar tabla de migraciones remotas;
- no reejecutar migraciones ya aplicadas;
- no editar migraciones aplicadas;
- realizar backup o punto de recuperación;
- no usar producción;
- registrar cada resultado.

#### Si no existe acceso autorizado

No simular despliegue ni resultados.

Marcar:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

La falta de acceso no bloquea el cierre de código, pero sí bloquea release.

---

## 4. Checklist remoto de M04

Ejecutar solo con cuentas y datos técnicos.

### 4.1 Reportes

- usuario crea reporte propio;
- `reporter_id` forzado desde `auth.uid()`;
- usuario no crea reporte por otro actor;
- usuario lee solo sus reportes;
- usuario no actualiza directamente;
- staff sin permiso no ve cola;
- staff con `moderation.view` ve proyección segura;
- `reporter_id` oculto sin `moderation.view_sensitive`;
- targets legacy `POST`, `USER`, `COMMENT`;
- marcar duplicado;
- triage;
- ningún acceso `using(true)` permanece.

### 4.2 Casos y medidas

- crear caso;
- adjuntar reporte;
- impedir doble caso activo;
- asignar y reasignar;
- nota interna;
- medida temporal con vencimiento;
- medida permanente;
- cuenta M02 actualizada con historial;
- organización M03 actualizada con historial;
- fallo intermedio produce rollback;
- auditoría en la misma transacción.

### 4.3 Apelaciones

- afectado presenta apelación;
- usuario no afectado bloqueado;
- segunda apelación activa bloqueada;
- aplicador no revisa su propia medida;
- decisión con motivo;
- `OVERTURNED` no restaura automáticamente sin acción posterior;
- proyección pública sin notas internas.

### 4.4 Verificación de organizaciones

- cola requiere permiso;
- miembro de la organización no revisa;
- APPROVE;
- REJECT;
- REQUEST_MORE_INFORMATION conserva PENDING;
- REVOKE requiere permiso;
- referencias documentales sin URL permanente;
- notas internas privadas.

### 4.5 Soporte

- usuario crea ticket;
- usuario solo lee sus tickets;
- usuario ve solo mensajes `REQUESTER_VISIBLE`;
- mensaje `INTERNAL` nunca se expone;
- staff sin permiso no ve cola;
- asignación;
- prioridad;
- estados;
- cierre válido;
- datos sensibles solo con permiso.

### 4.6 UI Android

- entradas staff según permisos efectivos;
- ruta directa sin permiso → AccessDenied;
- loading de permisos no habilita acciones;
- error de permisos → deny;
- doble toque no duplica operación;
- post-mutación refresca servidor;
- logout limpia estado administrativo;
- reporter sensible oculto;
- mensajes internos ocultos;
- auditoría de solo lectura.

---

## 5. Seguridad

Confirmar:

- sin service role en Android;
- sin `AccountType`, `active_modules` o roles M03 como autoridad;
- `auth.uid()` como actor;
- `search_path` fijo;
- escritura directa sensible revocada;
- RLS deny-by-default;
- reporter y PII protegidos;
- notas internas protegidas;
- soporte interno protegido;
- auditoría no escribible desde Android;
- logs sin tokens, secretos, evidencia o PII completa;
- UI no es única barrera;
- no se usa bucket público `leover`;
- no existe Storage administrativo físico todavía.

---

## 6. Regresión

Confirmar:

- M01 autenticación y sesión;
- M02 administración de usuarios y roles;
- M03 organizaciones;
- ruta legacy `admin_moderation`;
- `PlatformAdminScreen`;
- Profile;
- logout;
- navegación general;
- mocks cuando `useSupabase = false`;
- Supabase cuando `useSupabase = true`;
- WIP GPS/mapas/pagos aislado.

---

## 7. Correcciones permitidas

Se permiten:

- corrección Kotlin;
- corrección de ViewModel;
- corrección de gate;
- corrección de mapper o repositorio;
- pruebas;
- documentación;
- migración correctiva consecutiva únicamente si existe un defecto real de `022`.

Si hace falta migración correctiva:

- debe ser `023` o posterior;
- no editar `022`;
- alcance mínimo;
- prueba de regresión;
- documentar el defecto y el motivo.

No se permite:

- funcionalidad nueva;
- Storage administrativo;
- M05;
- IA de moderación;
- nuevo backend;
- Hilt;
- Retrofit;
- producción;
- merge a `main`.

---

## 8. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M04-etapa-5-cierre.md
/docs/02-arquitectura/M04-cierre-final.md
/docs/04-calidad/M04-reporte-validacion-staging.md
```

### `M04-etapa-5-cierre.md`

Debe incluir:

- rama y commits;
- estado Git inicial y final;
- archivos creados/modificados/eliminados;
- defectos encontrados;
- correcciones;
- migraciones nuevas, si existen;
- comandos ejecutados;
- cantidad final de pruebas;
- build;
- lint;
- estado staging;
- seguridad;
- riesgos;
- checklist.

### `M04-reporte-validacion-staging.md`

Tabla mínima:

```text
caso
entorno
fecha
actor técnico
resultado
evidencia
observaciones
```

Si no hubo acceso, marcar cada bloque como no ejecutado y explicar el motivo.

### `M04-cierre-final.md`

Debe resumir:

- reportes;
- casos;
- medidas;
- apelaciones;
- verificación de organizaciones;
- soporte;
- auditoría;
- permisos;
- protección de datos sensibles;
- UI y navegación;
- compatibilidad legacy;
- calidad local;
- staging real o pendiente;
- deuda aceptada;
- condiciones de release;
- estado de M05.

---

## 9. Criterios de aceptación

- [ ] Commit base verificado.
- [ ] Rama limpia sin WIP.
- [ ] Migraciones `014`–`022` revisadas.
- [ ] No se editó una migración aplicada.
- [ ] `content_reports` sin políticas abiertas.
- [ ] Mutaciones sensibles solo por RPC.
- [ ] Reporter protegido.
- [ ] Casos, medidas y apelaciones consistentes.
- [ ] Conflictos de interés protegidos.
- [ ] Verificación organizacional protegida.
- [ ] Soporte interno protegido.
- [ ] Mensajes INTERNAL ocultos.
- [ ] Auditoría de solo lectura desde Android.
- [ ] Permisos M02 como autoridad.
- [ ] Roles M03 sin autoridad global.
- [ ] AccountType/active_modules sin autoridad.
- [ ] Deep links denegados sin permiso.
- [ ] Loading/error niegan acciones.
- [ ] Logout limpia estado.
- [ ] Build aprobado.
- [ ] Todos los tests aprobados.
- [ ] Lint aprobado.
- [ ] Staging validado o marcado honestamente como pendiente.
- [ ] Release bloqueado sin staging.
- [ ] Tres documentos de salida creados.
- [ ] Sin iniciar M05.
- [ ] Sin merge a main.

---

## 10. Estado de M05

M05 — Archivos, Media y Documentos queda habilitado únicamente para **auditoría y diseño** después de aprobar `M04-cierre-final.md`.

No autorizar implementación de M05 mientras:

- M04 no esté formalmente cerrado;
- el working tree no esté limpio;
- build/tests/lint no estén verdes.

La validación staging pendiente puede permanecer como condición de release si está documentada.

---

## 11. Parada

No hacer merge a `main`.

No iniciar M05.

Detenerse cuando existan:

```text
/docs/02-arquitectura/M04-etapa-5-cierre.md
/docs/02-arquitectura/M04-cierre-final.md
/docs/04-calidad/M04-reporte-validacion-staging.md
```

Consolidar en un commit final e informar:

- SHA completo;
- rama;
- `git status`;
- cantidad total de tests;
- `assembleDebug`;
- `lintDebug`;
- estado real de staging.
