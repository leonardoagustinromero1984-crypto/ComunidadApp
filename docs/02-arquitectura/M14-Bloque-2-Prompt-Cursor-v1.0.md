# Cursor — M14 Bloque 2: persistencia segura del pasaporte y credenciales

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `526ab0f80fbd46c815ba888de7358d80fde92850`.
- `origin/main` alineada.
- M14 Bloque 1: CERRADO LOCALMENTE.
- M14 técnico: Pasaporte e identidad verificable de mascotas.
- Producto M14 Adopciones: cubierto por M09 técnico.
- M13 cierre técnico local: COMPLETADO.
- M13 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- M12 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- Migraciones existentes: 001–049.
- Próxima migración permitida: 050.

## Objetivo

Implementar **M14 Bloque 2 — Persistencia, seguridad y repositorios Supabase** para pasaportes, credenciales y solicitudes de verificación.

El bloque debe:

- crear la migración 050;
- preservar M08 como autoridad de mascota y responsables;
- implementar RLS/RPC;
- generar número de pasaporte y código público en servidor;
- persistir credenciales y solicitudes;
- preparar decisiones e historial para el Bloque 3;
- integrar repositorios Supabase;
- cerrar localmente sin aplicar SQL remoto.

## Lectura obligatoria

Leer completos antes de modificar código:

```text
@docs/03-modulos/M14-pasaporte-identidad-verificable.md
@docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md
@docs/03-modulos/M14-auditoria-inicial.md
@docs/03-modulos/M14-plan-funcional-y-tecnico.md
@docs/02-arquitectura/M14-Bloque-1-validacion.md
@docs/03-modulos/M08-mascotas-y-responsables.md
@docs/03-modulos/M09-adopciones.md
@docs/03-modulos/M12-veterinarias.md
@docs/01-producto/D01-Modulos-y-Orden.md
```

Auditar también:

```text
supabase/migrations/001*
supabase/migrations/012*
supabase/migrations/046*
supabase/migrations/047*
supabase/migrations/048*
supabase/migrations/049*
scripts/ci/m07_quality_checks.sh
.github/workflows/android-ci.yml
```

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–049.
- Crear únicamente la migración 050.
- No crear migración 051.
- No aplicar SQL remotamente.
- No generar APK.
- No usar emulador.
- No ejecutar lint, JaCoCo ni toda la suite repetidamente.
- Ejecutar pruebas focalizadas.
- Ejecutar una única compilación Kotlin final.
- No debilitar Android CI.
- No declarar M12 ni M13 cerrados.
- No iniciar M15.
- No implementar historia clínica.
- No implementar resolución remota de verificaciones todavía.
- No implementar QR real ni lookup con datos privados.
- No implementar autoverificación.
- No transferir responsabilidad M08.
- No duplicar mascotas ni adopciones.
- No introducir `service_role` en Android.
- No permitir DML directo del cliente.
- No exponer PII, documentos completos ni número de microchip completo.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
git rev-parse origin/main
```

Esperado:

```text
main
HEAD = origin/main
working tree limpio
```

Si existen cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría de autoridad y esquema

Antes de crear SQL, identificar con precisión:

1. tabla real de mascotas M08;
2. PK y tipo de `pet_id`;
3. fuente real del responsable principal;
4. tabla o relación de responsables compartidos;
5. helper/RPC canónico para comprobar autoridad M08;
6. estados de mascota incompatibles con crear o activar pasaporte;
7. tablas reales M03/M04 de organizaciones, membresías y permisos;
8. tablas reales M12 de clínicas y profesionales;
9. formatos seguros M05 realmente aceptados;
10. helpers M07 disponibles;
11. soporte criptográfico ya habilitado para generar códigos aleatorios;
12. cualquier tabla, columna o modelo legacy de microchip, vacunas o documentos.

### Gate de autoridad

Si no puede comprobarse de forma segura la responsabilidad M08 desde SQL, detenerse con:

```text
M14 BLOQUE 2 BLOQUEADO — AUTORIDAD M08 NO RESUELTA
```

No confiar en `ownerId`, `responsibleUserId`, organización o rol enviados por parámetros.

### Gate de generación segura

El `public_code` debe ser criptográficamente no predecible.

Si el proyecto no tiene una función segura disponible y agregar una extensión contradice las reglas existentes, detenerse con:

```text
M14 BLOQUE 2 BLOQUEADO — GENERACIÓN SEGURA DE PUBLIC_CODE NO RESUELTA
```

## Paso 3 — Migración 050

Crear exactamente:

```text
supabase/migrations/050_m14_pet_passports_and_credentials.sql
```

### Tablas esperadas

Crear, ajustando únicamente FKs y tipos a la realidad auditada:

```text
pet_passports
pet_passport_credentials
pet_passport_verification_requests
pet_passport_verification_decisions
pet_passport_status_history
```

No crear simultáneamente modelos duplicados para la misma responsabilidad.

### `pet_passports`

Campos mínimos:

- `id`;
- `pet_id`;
- `passport_number`;
- `public_code`;
- `status`;
- `visibility`;
- snapshot o campos complementarios permitidos;
- `microchip_number_normalized` privado y opcional;
- `created_by`;
- `created_at`;
- `updated_at`;
- `activated_at` opcional;
- `archived_at` opcional.

Reglas:

- un único pasaporte no final por mascota;
- `passport_number` único y estable;
- `public_code` único, no predecible y distinto;
- estados `DRAFT|ACTIVE|SUSPENDED|REVOKED|ARCHIVED`;
- `REVOKED` y `ARCHIVED` finales;
- M08 sigue siendo autoridad;
- no guardar contacto del responsable;
- el número de microchip completo nunca aparece en la salida pública.

### `pet_passport_credentials`

Campos mínimos:

- `id`;
- `passport_id`;
- `type`;
- `title`;
- emisor opcional usando FKs reales cuando existan;
- `issued_at` opcional;
- `expires_at` opcional;
- `status`;
- `visibility`;
- `media_refs`;
- referencia externa enmascarada opcional;
- `created_by`;
- `created_at`;
- `updated_at`.

Tipos iniciales:

```text
IDENTITY
MICROCHIP
ADOPTION
OWNERSHIP
STERILIZATION_ATTESTATION
VACCINATION_ATTESTATION
TRAVEL_DOCUMENT
OTHER
```

Estados:

```text
DRAFT
PENDING_VERIFICATION
VERIFIED
REJECTED
EXPIRED
REVOKED
```

Reglas:

- expiración posterior a emisión;
- media solo M05 segura;
- documentos completos no públicos;
- no autoverificación;
- estados finales no se editan.

### `pet_passport_verification_requests`

Campos mínimos:

- `id`;
- `credential_id`;
- `requested_by`;
- organización o profesional objetivo opcional mediante FKs reales;
- `status`;
- `requested_at`;
- `resolved_at` opcional;
- `resolution_reason` opcional;
- `created_at`;
- `updated_at`.

Estados:

```text
PENDING
APPROVED
REJECTED
CANCELLED
EXPIRED
```

En este bloque:

- permitir crear y cancelar solicitudes propias;
- no resolverlas remotamente;
- no escribir decisiones finales desde cliente.

### `pet_passport_verification_decisions`

Preparar para Bloque 3:

- `id`;
- `request_id`;
- `decision`;
- `actor_user_id`;
- autoridad o contexto auditado;
- `reason_code`;
- `note_private`;
- `created_at`.

No exponer RPC de aprobación/rechazo final todavía.

### `pet_passport_status_history`

Historial append-only del pasaporte:

- `id`;
- `passport_id`;
- estado anterior;
- estado nuevo;
- actor;
- razón;
- timestamp;
- metadatos no sensibles.

No borrar ni actualizar historial.

### Constraints e índices

Incluir:

- PK;
- FKs reales;
- checks de estados y visibilidad;
- unicidad;
- índice único parcial para un pasaporte no final por mascota;
- índices por mascota, pasaporte, estado, emisor, solicitud y fechas;
- fechas coherentes;
- arrays/JSON seguros;
- timestamps;
- ninguna cascada destructiva injustificada.

## Paso 4 — Generadores server-side

Crear helpers internos seguros:

```text
_m14_generate_passport_number
_m14_generate_public_code
```

### Número de pasaporte

Formato conceptual:

```text
LV-AR-YYYY-XXXXXXXX
```

Reglas:

- generado en servidor;
- no contiene PII;
- no se reutiliza;
- unique constraint;
- reintento ante colisión;
- estable una vez creado.

### Código público

Formato conceptual no vinculante:

```text
PUB-<TOKEN_ALEATORIO_SEGURO>
```

Reglas:

- criptográficamente no predecible;
- unique constraint;
- no incluye `passport_number`, `pet_id`, usuario o fecha legible;
- puede rotarse en bloques posteriores;
- no es un secreto de autenticación;
- solo habilita una proyección redactada.

Los helpers `_m14_*` no deben ser ejecutables por clientes.

## Paso 5 — RPC cliente

Crear exactamente estas 18 RPC cliente, salvo incompatibilidad técnica documentada:

### Pasaportes

```text
m14_create_pet_passport
m14_get_pet_passport
m14_get_pet_passport_by_pet
m14_list_my_pet_passports
m14_update_my_pet_passport
m14_activate_my_pet_passport
m14_archive_my_pet_passport
m14_get_public_pet_passport
```

### Credenciales

```text
m14_create_passport_credential
m14_update_my_passport_credential
m14_withdraw_my_passport_credential
m14_get_passport_credential
m14_list_passport_credentials
```

### Solicitudes de verificación

```text
m14_create_verification_request
m14_cancel_my_verification_request
m14_get_verification_request
m14_list_my_verification_requests
m14_list_managed_verification_requests
```

Total esperado:

```text
18 RPC cliente
```

### Alcance de las RPC

#### Pasaporte

- crear solo sobre mascota con autoridad M08;
- impedir duplicado no final;
- activar solo con mínimos válidos;
- actualizar solo campos permitidos;
- archivar solo por responsable o autoridad;
- `get/list my` solo para actores autorizados;
- `get public` devuelve proyección redactada por `public_code`.

#### Credenciales

- crear o editar por responsable autorizado;
- emisor organizacional solo dentro de autoridad real;
- retirar solo estados permitidos;
- listar según visibilidad y autoridad;
- documentos y notas privadas nunca salen en proyección pública.

#### Solicitudes

- crear solo para credencial elegible;
- impedir solicitud duplicada `PENDING`;
- cancelar solo por solicitante autorizado;
- listar gestionadas solo por organización/profesional o moderador autorizado;
- no resolver en Bloque 2.

## Paso 6 — Proyección pública

`m14_get_public_pet_passport` debe devolver únicamente una estructura redactada.

Permitido:

- nombre visible;
- especie;
- raza opcional;
- sexo opcional;
- color;
- marcas distintivas permitidas;
- estado público compatible;
- microchip enmascarado;
- credenciales `PUBLIC_REDACTED`;
- emisor público permitido;
- actualización aproximada.

Prohibido:

- `pet_id`;
- `user_id`;
- IDs internos;
- contacto;
- dirección;
- notas;
- documento completo;
- media privada;
- microchip completo;
- organización interna;
- datos clínicos;
- número de pasaporte como identificador secreto.

La RPC pública puede recibir `public_code`, pero no debe revelar si existió y fue suspendido/revocado con detalles sensibles.

## Paso 7 — Seguridad SQL

### RPC privadas

Todas las RPC privadas:

- `SECURITY DEFINER`;
- `search_path = public`;
- actor derivado de `auth.uid()`;
- autoridad comprobada internamente;
- sin actor/rol/organización confiados desde parámetros;
- errores consistentes;
- sin SQL dinámico inseguro.

### RPC pública

`m14_get_public_pet_passport`:

- `SECURITY DEFINER`;
- `search_path = public`;
- devuelve solo proyección redactada;
- puede recibir EXECUTE para `anon` y `authenticated`;
- `PUBLIC` debe quedar revocado.

### Grants

- `authenticated`: EXECUTE sobre las 17 RPC privadas y la RPC pública;
- `anon`: EXECUTE únicamente sobre `m14_get_public_pet_passport`;
- `PUBLIC`: sin EXECUTE;
- tablas: sin DML directo para `authenticated` ni `anon`;
- preferir acceso por RPC;
- helpers `_m14_*`: sin EXECUTE para `PUBLIC`, `anon`, `authenticated`.

### RLS

Activar RLS en las cinco tablas.

Mantener policies de defensa en profundidad para:

- responsables M08;
- responsables compartidos;
- gestores M03/M04;
- emisores/verificadores autorizados;
- moderación;
- historial;
- decisiones.

No habilitar lectura pública directa de tablas.

## Paso 8 — Permisos reales

Registrar en el catálogo canónico real:

```text
passport.read
passport.create
passport.manage_own
passport.manage_organization
passport.verify
passport.moderate
passport.credential.issue
passport.credential.verify
passport.public.read
```

Total esperado:

```text
9 permisos
```

Usar `ON CONFLICT` según la clave real.

La presencia del permiso no reemplaza:

- responsabilidad M08;
- pertenencia organizacional;
- autoridad del emisor;
- comprobación de actor.

## Paso 9 — M05, M07 y M12

### M05

Aceptar únicamente formatos seguros realmente usados por el proyecto, por ejemplo:

```text
m05://
file_asset:
```

Rechazar:

- URLs HTTP/HTTPS arbitrarias;
- bucket público inseguro;
- rutas locales no canónicas;
- documentos completos en proyección pública.

### M07

Preparar auditoría para:

```text
M14_PASSPORT_CREATED
M14_PASSPORT_UPDATED
M14_PASSPORT_ACTIVATED
M14_PASSPORT_ARCHIVED
M14_CREDENTIAL_ADDED
M14_CREDENTIAL_UPDATED
M14_VERIFICATION_REQUESTED
M14_VERIFICATION_CANCELLED
```

Reutilizar helper/catálogo existente.

Si el catálogo tiene un techo canónico que no debe ampliarse en este bloque:

- mantener integración best-effort;
- documentar el pendiente;
- no romper quality gates.

### M12

Una clínica/profesional solo puede figurar como emisor u objetivo si la relación real existe.

No otorgar autoridad clínica por un ID enviado por cliente.

No persistir historia clínica.

## Paso 10 — Android Supabase

Crear o completar:

- DTOs M14;
- mappers DTO/dominio;
- `SupabaseM14RemoteDataSource`;
- `SupabaseM14PassportRepository`;
- `SupabaseM14CredentialRepository`;
- `SupabaseM14VerificationRepository`;
- parser de errores;
- paginación y filtros;
- switching en `DataProvider`.

Reglas:

- conservar mocks;
- Supabase cuando esté habilitado;
- mock cuando esté deshabilitado;
- sin secretos;
- sin red real en tests;
- no alterar auth hotfix;
- no romper UI/rutas B1;
- la proyección pública usa exclusivamente la RPC pública.

## Paso 11 — Errores

Mapear errores remotos a los códigos M14 existentes.

Agregar solo cuando sea necesario:

```text
PET_NOT_ELIGIBLE
PASSPORT_NUMBER_GENERATION_FAILED
PUBLIC_CODE_GENERATION_FAILED
PASSPORT_ALREADY_ACTIVE
CREDENTIAL_NOT_ELIGIBLE
VERIFICATION_REQUEST_ALREADY_PENDING
ISSUER_NOT_AUTHORIZED
PUBLIC_PASSPORT_NOT_AVAILABLE
CONFLICT
```

No filtrar existencia de recursos ajenos mediante errores.

## Paso 12 — Tests focalizados

### Migración estática

Cubrir al menos:

1. existe solo migración 050 nueva;
2. 001–049 intactas;
3. cinco tablas;
4. PK/FKs;
5. estados y checks;
6. índice único de pasaporte no final por mascota;
7. número y public code únicos;
8. 18 RPC;
9. `SECURITY DEFINER`;
10. `search_path=public`;
11. 17 RPC privadas para authenticated;
12. RPC pública para authenticated y anon;
13. sin EXECUTE para PUBLIC;
14. sin EXECUTE anon en RPC privadas;
15. sin DML directo;
16. RLS;
17. policies;
18. helpers protegidos;
19. actor desde `auth.uid()`;
20. autoridad M08;
21. nueve permisos;
22. proyección pública redactada;
23. media M05;
24. sin service_role;
25. sin secretos;
26. sin autoverificación;
27. sin resolución remota final;
28. sin historia clínica.

### Repositorios Android

Cubrir:

- DTO parsing;
- mappers;
- create/get/list/update/activate/archive;
- credenciales;
- solicitudes;
- proyección pública;
- microchip enmascarado;
- media;
- error mapper;
- switching mock/Supabase;
- sin red real.

### Regresión mínima

- M14 Bloque 1;
- M05;
- M08;
- M09;
- M12;
- auth hotfix;
- guards de CI y migraciones.

Ejecutar las suites con `--rerun-tasks` cuando corresponda para evitar falsos `UP-TO-DATE`.

## Paso 13 — Guard CI de migraciones

Actualizar deliberadamente:

```text
scripts/ci/m07_quality_checks.sh
```

de:

```text
049
```

a:

```text
050
```

Actualizar únicamente las guardas estáticas que controlan el highest migration.

El guard debe seguir siendo estricto y fallar ante una futura `051`.

Actualizar la documentación operativa del CI.

No hacer reemplazos globales.

## Paso 14 — Validaciones

Ejecutar:

```powershell
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Ejecutar pruebas focalizadas M14 y regresiones mínimas.

Ejecutar una sola compilación:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

No aplicar 050 remotamente.

## Paso 15 — Documentación

Crear:

```text
docs/03-modulos/M14-persistencia-y-seguridad.md
docs/02-arquitectura/M14-Bloque-2-validacion.md
docs/05-operacion/M14-aplicacion-y-validacion-migracion-050.md
```

Actualizar:

```text
docs/03-modulos/M14-pasaporte-identidad-verificable.md
docs/03-modulos/M14-plan-funcional-y-tecnico.md
docs/03-modulos/M14-auditoria-inicial.md
docs/01-producto/D01-Modulos-y-Orden.md
docs/05-operacion/Android-CI-actualizacion-guard-migraciones-047.md
```

La guía 050 debe registrar:

- archivo exacto;
- aplicación manual una sola vez;
- validación estructural;
- smoke remoto;
- no editar 050 después de aplicada;
- toda corrección SQL posterior comienza en 051.

No inventar resultados remotos.

## Paso 16 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–049 intactas;
- solo 050 nueva;
- sin 051;
- sin secretos;
- sin binarios;
- workflow CI no debilitado;
- M12/M13 pendientes externos preservados;
- Bloque 3 no implementado;
- M15 no iniciado.

## Paso 17 — Git

Commit único:

```text
feat(m14): persist pet passports and credentials
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Auditoría M08.
3. Autoridad M08 resuelta.
4. Generación segura resuelta.
5. Archivo 050.
6. Tablas.
7. Constraints e índices.
8. Número de pasaporte.
9. Código público.
10. Dieciocho RPC y firmas.
11. Proyección pública.
12. Autoridad M08/M03/M04/M12.
13. RLS y policies.
14. Grants.
15. Permisos.
16. M05.
17. M07.
18. DTOs y mappers.
19. Remote data source.
20. Repositorios Supabase.
21. DataProvider.
22. Errores.
23. Tests ejecutados.
24. Total PASS.
25. `bash -n`.
26. Quality script.
27. Compilación.
28. Documentación.
29. Migraciones intactas.
30. Migración 050 creada y no aplicada.
31. Limitaciones.
32. Pendientes remotos.
33. Propuesta exacta del Bloque 3.
34. SHA.
35. Push.
36. `git status -sb`.

## Estado final permitido

```text
M14 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 050 PENDIENTE DE APLICACIÓN REMOTA
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

Si la autoridad o el código público no pueden resolverse de forma segura:

```text
M14 BLOQUE 2 BLOQUEADO — AUTORIDAD O GENERACIÓN SEGURA NO RESUELTA
```
