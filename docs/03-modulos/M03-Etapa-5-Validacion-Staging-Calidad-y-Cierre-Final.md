# LEOVER — M03 Etapa 5: Validación staging, calidad y cierre final

**Módulo:** M03 — Organizaciones y Equipos  
**Etapa:** 5 — Validación integral y cierre  
**Estado de entrada:** Etapa 4 aprobada y consolidada  
**Commit base:** `11abd6b9a68dd15b1c27e2e0295958275cab7dd1`  
**Rama base:** `m03/etapa-4-equipos-invitaciones-sucursales`  
**Backend oficial:** Supabase  
**Objetivo:** revisar integralmente M03, validar calidad local, documentar staging real o pendiente y cerrar el módulo sin iniciar M04.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-auditoria-inicial.md`
6. `/docs/02-arquitectura/M03-etapa-2-cierre.md`
7. `/docs/02-arquitectura/M03-etapa-3-cierre.md`
8. `/docs/02-arquitectura/M03-etapa-4-cierre.md`
9. `/docs/03-modulos/M03-Organizaciones-y-Equipos.md`
10. `/docs/03-modulos/M03-Etapa-2-Contratos-Organizaciones-Membresias-y-Autorizacion.md`
11. `/docs/03-modulos/M03-Etapa-3-Persistencia-RLS-Perfil-y-Storage.md`
12. `/docs/03-modulos/M03-Etapa-4-Equipos-Invitaciones-Sucursales-y-Contexto.md`
13. `/docs/04-calidad/M03-pruebas-persistencia-rls-organizaciones.md`
14. `/docs/04-calidad/M03-pruebas-equipos-invitaciones-sucursales.md`
15. ADR-0001 a ADR-0005
16. Este documento.

---

## 2. Protección Git

Antes de trabajar:

1. Confirmar commit base:

```text
11abd6b9a68dd15b1c27e2e0295958275cab7dd1
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m03/etapa-5-validacion-cierre
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M04.

---

## 3. Alcance autorizado

### 3.1 Revisión estática integral

Auditar nuevamente:

- migraciones 014–020;
- orden y dependencias;
- tablas, constraints, índices y seeds;
- triggers;
- funciones `SECURITY DEFINER`;
- `search_path`;
- grants y revokes;
- RLS y riesgo de recursión;
- perfiles públicos allowlist;
- contactos institucionales;
- Storage y ownership;
- invitaciones y hash de token;
- membresías y roles;
- protección último OWNER;
- transferencia de ownership;
- sucursales;
- contexto organizacional Android;
- limpieza de contexto en logout;
- exposición de PII;
- logs y errores.

Corregir únicamente defectos reales de M03.

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
- todos los tests previos y nuevos aprobados;
- lint con 0 errores;
- no eliminar tests;
- no ocultar errores con baseline nuevo o suppress global;
- documentar cantidad total de tests.

### 3.3 Validación de migraciones

Determinar con evidencia si 014–020 están aplicadas en algún entorno compartido.

#### Si existe acceso autorizado a staging

Aplicar solo en staging y respetar el historial real:

```text
014 → 015 → 016 → 017 → 018 → 019 → 020
```

Antes de aplicar:

- revisar tabla de migraciones;
- no reejecutar archivos ya aplicados;
- no editar migraciones aplicadas;
- realizar backup o punto de recuperación;
- no usar producción;
- registrar cada resultado.

#### Si no existe acceso autorizado

No simular el despliegue.

Marcar:

```text
PENDIENTE DE VALIDACIÓN REMOTA
```

La falta de acceso no bloquea el cierre de código, pero sí bloquea release.

### 3.4 Pruebas staging de M03

Cuando exista acceso autorizado, utilizar cuentas y organizaciones técnicas.

#### Organización

- crear organización;
- OWNER inicial atómico;
- slug duplicado;
- tipo OTHER;
- update permitido/denegado;
- perfil público allowlist;
- contactos privados por defecto;
- contacto público por opt-in;
- solicitud de verificación solo PENDING;
- organización SUSPENDED/CLOSED bloqueada.

#### Membresías y permisos

- OWNER;
- ADMIN;
- MANAGER;
- MEMBER;
- VIEWER;
- usuario externo;
- cuenta M02 SUSPENDED/BANNED;
- AccountType no concede membresía;
- platform role M02 no concede rol interno;
- error remoto → deny.

#### Invitaciones

- token persistido solo como hash;
- aceptación única;
- expiración;
- revocación;
- rechazo;
- no invitar OWNER;
- no crear membresía antes de aceptar;
- token inválido no enumera información;
- invitación de otra organización no funciona.

#### Ownership

- último OWNER protegido;
- ADMIN no se autoeleva;
- transferencia atómica;
- antiguo OWNER conserva el rol definido por contrato;
- fallo intermedio no deja organización sin OWNER;
- cierre revoca invitaciones pendientes.

#### Sucursales

- crear/editar;
- contacto privado por defecto;
- perfil público allowlist;
- sin coordenadas;
- organización A no modifica sucursal B.

#### Storage

- logo y portada;
- URL firmada;
- ownership entre organizaciones;
- usuario no miembro no sube ni elimina;
- reemplazo no borra archivos ajenos.

#### Android

- selector personal/organización;
- cambio de contexto refresca permisos;
- logout limpia contexto;
- deep link/ruta directa sin permiso denegada;
- loading/error niega acciones privilegiadas.

No utilizar usuarios ni organizaciones reales.

### 3.5 Seguridad

Confirmar:

- sin service role en Android;
- actor siempre desde `auth.uid()`;
- `organization_id` no sustituye identidad;
- token plano nunca persistido ni logueado;
- hash seguro y comparación controlada;
- `search_path` fijo;
- escritura directa sensible revocada;
- RLS sin recursión;
- deny-by-default;
- último OWNER protegido;
- AccountType/active_modules sin autoridad;
- roles M02 separados;
- contactos privados;
- logs sin PII/secrets;
- UI no es única barrera.

### 3.6 Regresión legacy

Confirmar:

- shelters sin organización siguen funcionando;
- service profile personal sigue funcionando;
- resource link no migra automáticamente;
- FOSTER_HOME sigue personal;
- PublishViewModel no asigna shelter por AccountType;
- M01/M02 no se rompen;
- WIP GPS/mapas/pagos permanece aislado.

---

## 4. Correcciones permitidas

Se permiten:

- migración correctiva consecutiva;
- corrección de constraint, índice, RLS, RPC o grant;
- corrección de mapper/repositorio;
- corrección de gate o contexto;
- pruebas;
- documentación;
- mensajes seguros.

No se permite:

- editar una migración aplicada;
- crear nuevas funcionalidades de M03;
- implementar verificación documental avanzada;
- iniciar M04;
- agregar mapas, GPS o pagos;
- cambiar backend;
- agregar Hilt/Retrofit;
- renombrar paquete;
- desplegar producción;
- hacer merge a `main`.

---

## 5. Documentación de salida

Crear exactamente:

```text
/docs/02-arquitectura/M03-etapa-5-cierre.md
/docs/02-arquitectura/M03-cierre-final.md
/docs/04-calidad/M03-reporte-validacion-staging.md
```

### `M03-etapa-5-cierre.md`

Debe incluir:

- rama y commits;
- estado Git inicial/final;
- archivos creados/modificados/eliminados;
- correcciones realizadas;
- comandos ejecutados;
- cantidad final de tests;
- resultado build/lint;
- estado staging;
- seguridad;
- riesgos;
- checklist.

### `M03-reporte-validacion-staging.md`

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

### `M03-cierre-final.md`

Debe resumir:

- organización y perfil institucional;
- membresías y autorización;
- invitaciones;
- ownership;
- sucursales;
- Storage;
- contexto Android;
- relación con recursos legacy;
- calidad local;
- validación remota real o pendiente;
- deuda aceptada;
- condiciones de release;
- estado de M04.

---

## 6. Criterios de aceptación de M03

M03 puede cerrarse a nivel código cuando:

- [ ] Commit base verificado.
- [ ] Rama limpia sin WIP.
- [ ] Migraciones 014–020 revisadas.
- [ ] No se editó una migración aplicada.
- [ ] Organization permanece entidad separada.
- [ ] OWNER inicial es atómico.
- [ ] Roles internos separados de M02.
- [ ] Deny-by-default server-side y Android.
- [ ] Perfil público no filtra PII.
- [ ] Contactos privados por defecto.
- [ ] Storage ownership correcto.
- [ ] Tokens persistidos solo como hash.
- [ ] Invitaciones expirables/revocables/un solo uso.
- [ ] Membresía solo al aceptar.
- [ ] Último OWNER protegido.
- [ ] Transferencia atómica.
- [ ] Sucursales sin GPS/mapas.
- [ ] OrganizationContext limpia en logout.
- [ ] Legacy shelters/services no se rompe.
- [ ] AccountType/active_modules sin autoridad.
- [ ] Build aprobado.
- [ ] Todos los tests aprobados.
- [ ] Lint con 0 errores.
- [ ] Staging validado o marcado honestamente como pendiente.
- [ ] Release bloqueado sin staging.
- [ ] No se inició M04.
- [ ] Tres documentos de salida creados.

---

## 7. Estado de M04

M04 queda habilitado únicamente para **auditoría y diseño** después de aprobar `M03-cierre-final.md`.

No autorizar implementación de M04 mientras:

- M03 no esté formalmente cerrado;
- el working tree no esté limpio;
- build/tests/lint no estén verdes.

La validación staging pendiente puede permanecer como condición de release si está documentada.

---

## 8. Instrucción de parada

No hacer merge a `main`.

No iniciar M04.

Detenerse cuando existan:

```text
/docs/02-arquitectura/M03-etapa-5-cierre.md
/docs/02-arquitectura/M03-cierre-final.md
/docs/04-calidad/M03-reporte-validacion-staging.md
```

Consolidar en un commit final e informar:

- SHA completo;
- `git status`;
- cantidad total de tests;
- build;
- lint;
- estado real de staging.
