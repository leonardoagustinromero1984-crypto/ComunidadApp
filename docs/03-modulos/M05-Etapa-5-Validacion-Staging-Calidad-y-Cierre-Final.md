# LEOVER — M05 Etapa 5: Validación, Staging, Calidad y Cierre Final

**Módulo:** M05 — Archivos, Media y Documentos  
**Etapa:** 5 — Validación integral y cierre final  
**Estado de entrada:** Etapa 4 aprobada y consolidada  
**Commit base:** `0e57c1c3f1482235e10a78565aad0daf4bc3c05c`  
**Rama base:** `m05/etapa-4-ui-flujos-operativos-archivos`  
**Calidad de entrada:** 353 tests, 0 failures, 0 errors; `assembleDebug` y `lintDebug` en SUCCESS  
**Staging heredado:** migraciones `014`–`024` pendientes de validación remota  
**Objetivo:** validar integralmente M05, corregir únicamente defectos reales, documentar staging y cerrar formalmente el módulo sin iniciar M06.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/02-arquitectura/M05-auditoria-inicial.md`
2. `/docs/02-arquitectura/M05-etapa-2-cierre.md`
3. `/docs/02-arquitectura/M05-etapa-3-cierre.md`
4. `/docs/02-arquitectura/M05-etapa-4-cierre.md`
5. `/docs/04-calidad/M05-pruebas-persistencia-storage-rls-rpc.md`
6. `/docs/04-calidad/M05-pruebas-ui-flujos-operativos-archivos.md`
7. `/docs/03-modulos/M05-Archivos-Media-y-Documentos.md`
8. `/docs/03-modulos/M05-Etapa-2-Contratos-Assets-Ownership-Validacion-y-Rutas.md`
9. `/docs/03-modulos/M05-Etapa-3-Persistencia-Storage-RLS-y-RPC.md`
10. Este documento.

---

## 2. Protección Git

1. Confirmar el commit base:

```text
0e57c1c3f1482235e10a78565aad0daf4bc3c05c
```

2. Confirmar working tree limpio.
3. Crear la rama:

```text
m05/etapa-5-validacion-cierre
```

4. No mezclar GPS, mapas ni pagos.
5. No hacer merge a `main`.
6. No iniciar M06.
7. No usar producción.
8. No afirmar validación remota sin evidencia.
9. No modificar autenticación, username ni migraciones de M01/M02.

---

## 3. Alcance de la Etapa 5

### 3.1 Auditoría estática integral

Revisar:

- migración `024`;
- que `001`–`023` no hayan sido editadas;
- hardening real de `leover`;
- buckets tipados y límites;
- tablas `file_*`;
- constraints e índices;
- RLS de tablas;
- policies de `storage.objects`;
- funciones `SECURITY DEFINER`;
- `search_path`;
- actor desde `auth.uid()`;
- signed URLs no persistidas;
- paths generados server-side;
- ownership y permisos;
- retención y legal hold;
- auditoría de accesos;
- compatibilidad legacy;
- repositorios Supabase;
- `DataProvider`;
- coordinador de upload;
- reemplazo seguro;
- cancelación y retry;
- limpieza en logout;
- adjuntos sensibles;
- mensajes INTERNAL;
- deep links;
- errores seguros;
- logs y PII.

Corregir únicamente defectos reales de M05.

No agregar funcionalidades nuevas.

### 3.2 Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Requisitos:

- build SUCCESS;
- todos los tests aprobados;
- lint con 0 errores;
- no eliminar pruebas;
- no crear baseline nuevo;
- no usar suppress global;
- documentar cantidad final de tests.

### 3.3 Validación remota

Determinar con evidencia si las migraciones `014`–`024` están aplicadas en un entorno autorizado.

#### Si existe acceso autorizado a staging

Aplicar respetando el historial real:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020 → 021 → 022 → 023 → 024
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

## 4. Checklist remoto de M05

### 4.1 Bucket legacy `leover`

- lectura pública de objetos existentes;
- URLs legacy sin cambio;
- INSERT autenticado denegado;
- UPDATE autenticado denegado;
- DELETE autenticado denegado;
- objetos existentes no movidos ni borrados;
- nuevos uploads M05 nunca resuelven a `leover`.

### 4.2 Buckets tipados

- `public-media` público solo para assets PUBLIC/READY;
- paths ajenos denegados;
- MIME y tamaño inválidos denegados;
- `organization-documents` privado;
- `moderation-evidence` privado;
- `support-attachments` privado;
- sin listado global sensible;
- `profile-avatars` y `organization-media` sin regresiones.

### 4.3 Assets y upload

- ownership dual bloqueado;
- sensible PUBLIC bloqueado;
- path arbitrario bloqueado;
- sesión expirada bloqueada;
- doble sesión activa bloqueada;
- progreso válido;
- cancelación idempotente;
- complete marca versión y asset READY;
- signed URL no persistida;
- URL pública solo para PUBLIC/READY.

### 4.4 Acceso sensible

- org A no accede archivos de org B;
- staff sin permiso no accede evidencia;
- verificación requiere permiso correcto;
- requester no ve adjuntos INTERNAL;
- PRIVACY/SAFETY requiere permiso sensible;
- deep link revalida permiso;
- logout limpia URI, sesiones, previews y signed URLs.

### 4.5 Retención y eliminación

- unlink no borra físico;
- links activos bloquean physical delete;
- retención futura bloquea delete;
- legal hold bloquea delete;
- reemplazo no elimina el anterior antes de READY;
- fallos parciales quedan recuperables;
- auditoría sensible sin tokens ni paths completos.

---

## 5. Seguridad obligatoria

Confirmar:

- sin service role en Android;
- sin base64;
- sin persistencia de `content://`;
- sin persistencia de signed URLs;
- sin elección arbitraria de bucket/path;
- `auth.uid()` como actor;
- `search_path` fijo;
- RLS deny-by-default;
- sensibles nunca PUBLIC;
- roles M03 limitados a su organización;
- AccountType y `active_modules` sin autoridad;
- UI no es única barrera;
- errores no exponen SQL, tokens, paths o stack traces;
- no se afirma antivirus, EXIF strip, thumbnails o sniffing profundo si no existen.

---

## 6. Regresión

Confirmar:

- M01 autenticación y sesión sin cambios;
- M02 perfiles, permisos y estados;
- M03 organizaciones;
- M04 moderación, verificación y soporte;
- avatar y onboarding;
- logo/cover/documentos de organización;
- mascotas;
- publicaciones;
- adopciones;
- perdidos/encontrados;
- logout;
- mocks cuando `useSupabase = false`;
- Supabase cuando `useSupabase = true`;
- lectura legacy;
- WIP GPS/mapas/pagos aislado.

El error de username queda fuera de esta rama.  
No corregirlo ni modificar AuthRepository, `domain/auth`, validadores de username o migraciones de autenticación.

---

## 7. Correcciones permitidas

Se permiten:

- correcciones Kotlin de M05;
- correcciones de RLS/RPC/Storage de `024`;
- pruebas;
- documentación;
- migración correctiva `025` únicamente si existe un defecto real y bloqueante de `024`.

Si se requiere `025`:

- no editar `024`;
- alcance mínimo;
- prueba de regresión;
- defecto y motivo documentados.

No se permite:

- funcionalidad nueva;
- M06;
- producción;
- nuevo backend;
- cambios de username/auth;
- merge a `main`.

---

## 8. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M05-etapa-5-cierre.md
/docs/02-arquitectura/M05-cierre-final.md
/docs/04-calidad/M05-reporte-validacion-staging.md
```

### `M05-etapa-5-cierre.md`

Debe incluir:

- rama y commits;
- estado Git;
- archivos modificados;
- defectos encontrados;
- correcciones;
- migración correctiva, si existe;
- comandos ejecutados;
- cantidad final de tests;
- build;
- lint;
- seguridad;
- regresión;
- estado staging;
- riesgos;
- checklist.

### `M05-reporte-validacion-staging.md`

Debe registrar:

```text
caso
entorno
fecha
actor técnico
resultado
evidencia
observaciones
```

Si no hubo acceso, marcar los casos como `NO EJECUTADO` y explicar el motivo.

### `M05-cierre-final.md`

Debe resumir:

- assets y versiones;
- ownership;
- propósitos y visibilidad;
- validación;
- rutas y buckets;
- hardening de `leover`;
- RLS/RPC;
- signed URLs;
- upload y reemplazo;
- retención y eliminación;
- adjuntos sensibles;
- compatibilidad legacy;
- UI y logout;
- calidad local;
- staging real o pendiente;
- deuda aceptada;
- condiciones de release;
- estado de M06;
- username/auth fuera de alcance.

---

## 9. Criterios de aceptación

- [ ] Commit base verificado.
- [ ] Rama limpia.
- [ ] Migración `024` auditada.
- [ ] `001`–`023` sin ediciones.
- [ ] `leover` read-only para escrituras nuevas.
- [ ] URLs legacy intactas.
- [ ] Buckets tipados correctos.
- [ ] Ownership único.
- [ ] Sensibles nunca PUBLIC.
- [ ] Paths server-side.
- [ ] RLS deny-by-default.
- [ ] RPC con `auth.uid()`.
- [ ] Signed URLs no persistidas.
- [ ] `content://` no persistido.
- [ ] Reemplazo seguro.
- [ ] Logout limpia estado.
- [ ] INTERNAL protegido.
- [ ] Retención/legal hold respetados.
- [ ] Build aprobado.
- [ ] Todos los tests aprobados.
- [ ] Lint aprobado.
- [ ] Staging validado o marcado honestamente pendiente.
- [ ] Release bloqueado sin staging PASS.
- [ ] Tres documentos de salida creados.
- [ ] Username/auth sin cambios.
- [ ] Sin M06.
- [ ] Sin merge a main.

---

## 10. Parada

No iniciar M06.

No hacer merge a `main`.

No corregir username/auth en esta rama.

Detenerse al crear:

```text
/docs/02-arquitectura/M05-etapa-5-cierre.md
/docs/02-arquitectura/M05-cierre-final.md
/docs/04-calidad/M05-reporte-validacion-staging.md
```

Consolidar en un commit final e informar:

- SHA completo;
- rama;
- `git status`;
- cantidad final de tests;
- `assembleDebug`;
- `lintDebug`;
- migración correctiva o ausencia;
- estado real de staging;
- confirmación de username/auth intactos.
