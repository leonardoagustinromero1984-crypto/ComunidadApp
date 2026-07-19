# LEOVER — M02 Etapa 5: Validación staging, calidad y cierre final

**Módulo:** M02 — Usuarios, Roles y Permisos  
**Etapa:** 5 — Validación integral y cierre  
**Estado de entrada:** Etapa 4 consolidada en commit `87b8c07a0759696d8028f0583a86aa57c5333a15`  
**Rama base:** `m02/etapa-4-roles-permisos-administracion`  
**Backend oficial:** Supabase  
**Objetivo:** validar integralmente M02, documentar con honestidad el estado remoto y cerrar el módulo sin iniciar M03.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-auditoria-inicial.md`
5. `/docs/02-arquitectura/M02-etapa-2-cierre.md`
6. `/docs/02-arquitectura/M02-etapa-3-cierre.md`
7. `/docs/02-arquitectura/M02-etapa-4-cierre.md`
8. `/docs/03-modulos/M02-Usuarios-Roles-y-Permisos.md`
9. `/docs/03-modulos/M02-Etapa-2-Contratos-Perfil-y-Autorizacion.md`
10. `/docs/03-modulos/M02-Etapa-3-Perfil-Onboarding-Privacidad-y-RLS.md`
11. `/docs/03-modulos/M02-Etapa-4-Roles-Permisos-y-Administracion-Segura.md`
12. `/docs/04-calidad/M02-pruebas-perfil-privacidad-rls.md`
13. `/docs/04-calidad/M02-pruebas-roles-permisos-administracion.md`
14. `/docs/04-calidad/M02-bootstrap-superadmin.md`
15. Este documento.

---

## 2. Protección Git

Antes de trabajar:

1. Confirmar que el commit base es:

```text
87b8c07a0759696d8028f0583a86aa57c5333a15
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m02/etapa-5-validacion-cierre
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M03.

---

## 3. Alcance autorizado

### 3.1 Revisión estática integral

Auditar nuevamente:

- migraciones 014–018;
- orden y dependencias;
- triggers;
- RLS;
- RPC `SECURITY DEFINER`;
- `search_path`;
- grants/revokes;
- seeds idempotentes;
- Storage ownership;
- bootstrap SUPERADMIN;
- permisos Android;
- gates de navegación;
- account status;
- historial;
- exposición de PII.

Corregir únicamente defectos reales de M02.

No agregar funcionalidades nuevas.

### 3.2 Validación local obligatoria

Ejecutar:

```bash
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
```

Requisitos:

- build SUCCESS;
- todos los tests anteriores y nuevos pasan;
- lint 0 errores;
- no reducir cobertura eliminando tests;
- no ocultar errores con baseline nuevo o suppress global.

### 3.3 Validación de migraciones

Determinar con evidencia si 014–018 están aplicadas en un entorno compartido.

#### Si existe acceso autorizado a Supabase staging

Aplicar exclusivamente en staging, en orden:

```text
014 → 015 → 016 → 017 → 018
```

Antes de cada paso:

- backup o punto de recuperación;
- verificar historial de migraciones;
- no reejecutar migraciones ya aplicadas;
- no modificar producción;
- registrar resultado real.

#### Si no existe acceso

No simular el despliegue.

Crear un plan ejecutable y dejar el estado como:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

La falta de acceso remoto no impide cerrar el código de M02, pero sí debe quedar como condición previa de release.

### 3.4 Pruebas staging

Cuando exista staging autorizado, validar con cuentas técnicas:

- USER;
- MODERATOR;
- ADMIN;
- SUPERADMIN.

Casos mínimos:

#### Perfil y privacidad

- onboarding;
- username;
- username duplicado;
- reservado;
- perfil propio completo;
- perfil ajeno por allowlist;
- privacidad PUBLIC/FRIENDS/PRIVATE;
- avatar ownership;
- usuario A no modifica B.

#### Roles y permisos

- USER sin moderación;
- MODERATOR con moderación;
- ADMIN con permisos limitados;
- SUPERADMIN con control completo;
- autoasignación bloqueada;
- ADMIN no asigna SUPERADMIN;
- assignments revocados/expirados no otorgan permisos;
- cuenta SUSPENDED/BANNED sin permisos elevados;
- `has_permission` false ante error/código desconocido.

#### Administración

- cambio de estado;
- historial;
- asignación/revocación;
- protección del último SUPERADMIN;
- acceso directo a ruta administrativa denegado;
- PII privada solo con `users.view_private`.

No utilizar cuentas reales de usuarios.

### 3.5 Bootstrap SUPERADMIN

No incorporar UUID ni email reales al repositorio.

Si se ejecuta en staging:

- usar cuenta técnica;
- registrar operador, fecha y entorno;
- comprobar que existe al menos un SUPERADMIN;
- comprobar protección del último SUPERADMIN;
- documentar cómo revocar acceso de bootstrap.

Si no se ejecuta, mantenerlo como paso manual previo al uso administrativo.

### 3.6 Seguridad

Confirmar:

- ninguna clave service role en Android;
- actor derivado de `auth.uid()`;
- ningún `target_user_id` reemplaza la identidad del actor;
- funciones con `search_path` fijo;
- no hay autoelevación;
- deny-by-default;
- RLS sin recursión;
- UPDATE directo sensible revocado;
- Storage restringido por path;
- logs sin PII/secrets;
- UI administrativa no es la única barrera.

### 3.7 Pruebas Android adicionales

Agregar solo si faltan:

- cache de permisos invalidada al logout;
- error remoto niega;
- expiración de asignación;
- cuenta restringida;
- ruta administrativa profunda;
- protección último SUPERADMIN en ViewModel;
- PII oculta;
- loading/error/retry de administración.

---

## 4. Correcciones permitidas

Se permiten:

- migración correctiva consecutiva;
- fix de RLS;
- fix de RPC;
- fix de mapper;
- fix de gate;
- pruebas;
- documentación;
- mejora de mensajes técnicos seguros.

No se permite:

- editar migraciones aplicadas;
- crear organizaciones;
- introducir capacidades M03;
- agregar GPS, mapas o pagos;
- cambiar backend;
- agregar Hilt/Retrofit;
- refactor masivo;
- renombrar paquete;
- desplegar producción.

---

## 5. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M02-etapa-5-cierre.md
/docs/02-arquitectura/M02-cierre-final.md
/docs/04-calidad/M02-reporte-validacion-staging.md
```

### `M02-etapa-5-cierre.md`

Debe incluir:

- rama y commits;
- estado Git inicial/final;
- archivos creados/modificados/eliminados;
- correcciones;
- comandos;
- cantidad final de tests;
- build/lint;
- estado staging;
- seguridad;
- riesgos;
- checklist.

### `M02-reporte-validacion-staging.md`

Debe incluir una tabla por prueba:

```text
caso
entorno
fecha
actor técnico
resultado
evidencia
observaciones
```

Cuando no hubo acceso, dejar cada bloque como no ejecutado y explicar el motivo.

### `M02-cierre-final.md`

Debe resumir:

- perfil y onboarding;
- privacidad y proyección pública;
- Storage;
- roles y permisos;
- administración;
- estados de cuenta;
- auditoría;
- controles de calidad;
- validación remota real o pendiente;
- deuda aceptada;
- condiciones de release;
- estado de M03.

---

## 6. Criterios de aceptación de M02

M02 puede cerrarse a nivel código cuando:

- [ ] Commit base verificado.
- [ ] Rama limpia sin WIP.
- [ ] Migraciones 014–018 revisadas.
- [ ] No se editó una migración aplicada.
- [ ] Perfil y onboarding mantienen contratos.
- [ ] Proyección pública no filtra PII.
- [ ] Storage ownership correcto.
- [ ] Roles y permisos normalizados.
- [ ] Deny-by-default en Android y servidor.
- [ ] Autoelevación bloqueada.
- [ ] Jerarquía administrativa aplicada.
- [ ] Estados de cuenta aplicados.
- [ ] Historial obligatorio.
- [ ] Último SUPERADMIN protegido.
- [ ] PermissionRepository niega ante error.
- [ ] Moderación y administración usan permisos reales.
- [ ] Build aprobado.
- [ ] Todos los tests aprobados.
- [ ] Lint con 0 errores.
- [ ] CI sin secretos.
- [ ] Staging validado o marcado honestamente como pendiente.
- [ ] Release bloqueado si falta validación remota.
- [ ] No se inició M03.
- [ ] Tres documentos de salida creados.

---

## 7. Estado de M03

M03 queda habilitado para **auditoría y diseño solamente** después de aprobar `M02-cierre-final.md`.

No autorizar implementación de M03 mientras:

- M02 no esté formalmente cerrado;
- el árbol Git no esté limpio;
- build/tests/lint no estén verdes.

La validación staging pendiente puede mantenerse como condición de release si está documentada con claridad.

---

## 8. Instrucción de parada

No hacer merge a `main`.

No iniciar M03.

Detenerse cuando existan:

```text
/docs/02-arquitectura/M02-etapa-5-cierre.md
/docs/02-arquitectura/M02-cierre-final.md
/docs/04-calidad/M02-reporte-validacion-staging.md
```

Presentar el SHA del commit final y `git status`.
