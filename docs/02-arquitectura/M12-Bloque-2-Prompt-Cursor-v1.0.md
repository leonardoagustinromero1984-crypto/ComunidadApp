# Cursor — M12 Bloque 2: persistencia, RLS/RPC y gestión de veterinarias

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD esperado: `ffb52cb746a0228f5f0d714a0240470163d81200`.
- M12 Bloque 1: cerrado.
- Calidad del Bloque 1: 70/70 pruebas focalizadas y `compileLocalDebugKotlin` exitoso.
- Working tree esperado: limpio y alineado con `origin/main`.
- Migraciones 040–045 aplicadas en Supabase de pruebas.
- Siguiente migración libre: `046`.

## Objetivo

Implementar **M12 Bloque 2 — persistencia del núcleo veterinario, seguridad RLS/RPC y repositorios Supabase**.

El bloque debe habilitar:

1. Perfiles reales de clínicas veterinarias vinculados a organizaciones/sedes M03.
2. Gestión de profesionales y su vinculación con clínicas.
3. Especialidades profesionales.
4. Gestión de servicios veterinarios.
5. Gestión transaccional de horarios semanales.
6. Publicación y consulta remota del directorio veterinario.
7. Solicitud y revisión de verificación reutilizando autoridad M04.
8. Repositorios Android conectados a RPC Supabase.
9. Migración 046, guardas estáticas, pruebas focalizadas y documentación operativa.

## Flujo obligatorio

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y un único push.
- No modificar migraciones 040–045.
- Crear exactamente una nueva migración 046.
- No aplicar SQL remotamente desde Cursor.
- No iniciar Supabase local.
- No ejecutar emulador.
- No generar APK.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- Ejecutar pruebas focalizadas durante el desarrollo.
- Ejecutar una sola compilación Kotlin al final.
- No implementar agenda, turnos, disponibilidad, historia clínica, recetas, vacunación clínica ni pagos.
- No utilizar Supabase real en tests.
- No eliminar ni reescribir destructivamente `service_profiles`, `ServiceProfile` ni bookings legacy.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Resultado esperado:

```text
main
ffb52cb746a0228f5f0d714a0240470163d81200
```

Si hay archivos untracked intencionales del bloque:

- identificarlos;
- preservarlos;
- incluirlos en el único commit final.

Si existen cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría focalizada previa

Leer:

```text
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-auditoria-y-contratos-iniciales.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Auditar patrones reales de:

- M03 organizaciones, sedes, membresías y `has_org_permission`;
- M04 verificación y autoridad administrativa;
- M05 referencias seguras;
- M06 hooks;
- M07 auditoría y errores;
- migraciones recientes 042–045;
- repositorios Supabase existentes;
- DataProvider;
- mapeo JSON/RPC;
- guardas estáticas;
- grants y revokes;
- legacy `service_profiles` y bookings.

Antes de modificar código, documentar:

```text
REUTILIZADO
ADAPTADO
PRESERVADO_LEGACY
NO_APLICA
AUSENTE
```

No inventar nombres de tablas o permisos M03/M04: usar los objetos reales encontrados.

## Paso 3 — Confirmar dominio del Bloque 1

Reutilizar, sin duplicar:

```text
VeterinaryClinicProfile
VeterinaryProfessional
VeterinaryService
VeterinaryOpeningHours
VeterinaryDirectoryFilter
VeterinarySpecialty
VeterinaryServiceCategory
VeterinaryClinicStatus
VeterinaryVerificationStatus
VeterinaryProfessionalStatus
```

Corregir solamente inconsistencias comprobadas necesarias para persistencia.

Mantener:

- contactos públicos opt-in;
- `logoAssetRef` y `coverAssetRef` como M05;
- website/redes como enlaces públicos normales, no como media;
- matrícula declarada y jurisdicción;
- especies tipadas;
- ausencia de precios;
- ausencia de turnos e historia clínica.

## Paso 4 — Migración 046

Crear exactamente:

```text
supabase/migrations/046_m12_veterinary_profiles_and_services.sql
```

Debe comenzar con:

```sql
begin;
```

y finalizar con:

```sql
commit;
```

Crear tablas equivalentes a:

```text
veterinary_clinic_profiles
veterinary_professionals
veterinary_clinic_professionals
veterinary_professional_specialties
veterinary_services
veterinary_opening_hours
```

No modificar `service_profiles` ni bookings.

### veterinary_clinic_profiles

Campos mínimos equivalentes:

```text
id uuid
organization_id uuid
branch_id uuid nullable
display_name text
description text nullable
status text
verification_status text
public_zone_text text
public_address_text text nullable
public_contact_enabled boolean
public_phone text nullable
public_email text nullable
website_url text nullable
social_links jsonb
logo_asset_ref text nullable
cover_asset_ref text nullable
offers_emergency_care boolean
is_open_24_hours boolean
created_by uuid
created_at timestamptz
updated_at timestamptz
archived_at timestamptz nullable
```

Reglas:

- organización obligatoria y ACTIVE;
- sede, si existe, pertenece a la organización;
- evitar dos perfiles activos para la misma organización/sede;
- `display_name` y `public_zone_text` obligatorios;
- estados válidos:
  `DRAFT`, `ACTIVE`, `PAUSED`, `SUSPENDED`, `ARCHIVED`;
- verificación:
  `UNVERIFIED`, `PENDING`, `VERIFIED`, `REJECTED`, `SUSPENDED`;
- contacto público se redacta cuando `public_contact_enabled = false`;
- M05 segura para logo y portada;
- website/redes pueden ser HTTPS válidos, pero no se tratan como asset refs;
- no hard-delete.

Usar índices parciales para unicidad organización/sede cuando corresponda, evitando expresiones frágiles o UUID centinela si el proyecto tiene un patrón mejor.

### veterinary_professionals

Campos mínimos:

```text
id uuid
user_id uuid nullable
display_name text
license_number text nullable
license_jurisdiction text nullable
verification_status text
biography text nullable
public_contact_enabled boolean
avatar_asset_ref text nullable
status text
created_by uuid
created_at timestamptz
updated_at timestamptz
archived_at timestamptz nullable
```

Reglas:

- nombre obligatorio;
- matrícula vacía se normaliza a null;
- no afirmar verificación automática;
- no exponer matrícula completa en proyección pública por defecto;
- media M05 segura;
- no hard-delete.

No imponer una unicidad global de matrícula sin considerar jurisdicción y datos incompletos. Si se implementa unicidad, debe ser parcial, normalizada y documentada.

### veterinary_clinic_professionals

Campos mínimos:

```text
id uuid
clinic_id uuid
professional_id uuid
role_title text nullable
active boolean
linked_by uuid
linked_at timestamptz
unlinked_at timestamptz nullable
```

Reglas:

- una vinculación activa por clínica/profesional;
- preservar historial al desvincular;
- profesional vinculado no recibe autoridad administrativa automáticamente.

### veterinary_professional_specialties

Campos mínimos:

```text
id uuid
professional_id uuid
specialty text
created_at timestamptz
```

Reglas:

- especialidades del dominio M12;
- unicidad profesional/especialidad;
- no hard-delete necesario si se reemplazan transaccionalmente y se conserva auditoría M07; seguir el patrón real del proyecto.

### veterinary_services

Campos mínimos:

```text
id uuid
clinic_id uuid
name text
category text
description text nullable
species text[]
active boolean
requires_appointment boolean
emergency_available boolean
created_by uuid
created_at timestamptz
updated_at timestamptz
archived_at timestamptz nullable
```

Reglas:

- nombre y categoría obligatorios;
- especies limitadas al enum real de M12;
- no precios;
- no pagos;
- `emergency_available` coherente con la clínica;
- evitar duplicados activos por clínica/nombre/categoría.

### veterinary_opening_hours

Campos mínimos:

```text
id uuid
clinic_id uuid
day_of_week integer
closed boolean
opens_at time nullable
closes_at time nullable
emergency_only boolean
updated_by uuid
created_at timestamptz
updated_at timestamptz
```

Reglas:

- un registro por clínica y día;
- día 1–7 o patrón real adoptado por el proyecto;
- cuando `closed = true`, horas nulas;
- cuando `closed = false`, ambas horas obligatorias;
- `closes_at > opens_at`;
- 24 horas se representa de forma coherente y documentada; no usar `00:00–00:00` si contradice el check;
- reemplazo semanal transaccional.

## Paso 5 — Constraints, índices e idempotencia

Agregar:

- primary keys;
- foreign keys;
- checks de estados, categorías, especies y horarios;
- índices por organización, sede, estado, verificación y zona;
- índices por clínica/profesional;
- índices por servicio/categoría;
- índices por especialidad;
- índices por día;
- unicidades parciales necesarias.

La migración debe ser segura frente a un fallo parcial:

- `CREATE TABLE IF NOT EXISTS`;
- `CREATE INDEX IF NOT EXISTS`;
- `CREATE OR REPLACE FUNCTION`;
- `DROP POLICY IF EXISTS` antes de `CREATE POLICY`;
- `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`;
- sin `DROP TABLE`;
- sin truncados;
- sin borrado de datos;
- ningún parámetro obligatorio después de uno con `DEFAULT`.

## Paso 6 — Permisos

Sembrar o reutilizar, siguiendo el patrón real de permisos:

```text
veterinary.profile.read
veterinary.profile.manage
veterinary.professional.read
veterinary.professional.manage
veterinary.service.read
veterinary.service.manage
```

Reglas:

- autoridad por organización M03, membresía activa y permiso;
- `AccountType` y `active_modules` no conceden autoridad;
- profesional vinculado no obtiene `manage`;
- voluntario M11 no obtiene autoridad veterinaria;
- deny-by-default ante organización inválida, permiso desconocido o error.

No crear un sistema paralelo de roles.

## Paso 7 — RLS y grants

Habilitar RLS en las seis tablas.

Política:

- negar acceso directo por defecto;
- las operaciones de cliente se hacen mediante RPC;
- no conceder INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER a `anon` ni `authenticated`;
- preferir no conceder SELECT directo si los repositorios usan proyecciones RPC;
- cualquier excepción debe estar documentada y protegida por RLS.

Para tablas:

```sql
revoke all privileges on table ... from public;
revoke all privileges on table ... from anon;
revoke all privileges on table ... from authenticated;
```

Para RPC cliente:

```sql
revoke all on function public.nombre(firma...) from public;
revoke all on function public.nombre(firma...) from anon;
grant execute on function public.nombre(firma...) to authenticated;
```

Para helpers internos:

```sql
revoke all on function public.nombre(firma...) from public;
revoke all on function public.nombre(firma...) from anon;
revoke all on function public.nombre(firma...) from authenticated;
```

No usar `service_role` desde Android.

## Paso 8 — RPC

Crear funciones equivalentes, adaptando nombres solo si existe una convención real mejor.

### Clínicas

```text
m12_create_veterinary_clinic_draft
m12_update_veterinary_clinic_profile
m12_change_veterinary_clinic_status
m12_request_veterinary_clinic_verification
m12_review_veterinary_clinic_verification
m12_get_public_veterinary_clinic
m12_get_managed_veterinary_clinic
m12_list_public_veterinary_clinics
m12_list_managed_veterinary_clinics
```

### Profesionales

```text
m12_create_veterinary_professional
m12_update_veterinary_professional
m12_link_veterinary_professional
m12_unlink_veterinary_professional
m12_replace_veterinary_professional_specialties
m12_request_veterinary_professional_verification
m12_review_veterinary_professional_verification
m12_list_public_veterinary_professionals
m12_list_managed_veterinary_professionals
```

### Servicios y horarios

```text
m12_create_veterinary_service
m12_update_veterinary_service
m12_change_veterinary_service_status
m12_list_public_veterinary_services
m12_list_managed_veterinary_services
m12_replace_veterinary_opening_hours
m12_list_public_veterinary_opening_hours
m12_list_managed_veterinary_opening_hours
```

Total orientativo: 26 RPC cliente. El número final puede variar solo si la auditoría demuestra una combinación más segura y queda documentada.

Todas las funciones sensibles deben:

- derivar actor de `auth.uid()`;
- usar `SECURITY DEFINER`;
- fijar `SET search_path = public`;
- validar permisos M03/M12;
- validar autoridad M04 para decisiones de verificación;
- no confiar en IDs de actor recibidos desde Android;
- usar errores estables y mapeables;
- no filtrar datos privados en proyecciones públicas.

## Paso 9 — Transiciones

### Clínica

Permitir únicamente transiciones equivalentes:

```text
DRAFT → ACTIVE
DRAFT → ARCHIVED
ACTIVE → PAUSED
ACTIVE → SUSPENDED (autoridad M04/admin)
ACTIVE → ARCHIVED
PAUSED → ACTIVE
PAUSED → ARCHIVED
SUSPENDED → ACTIVE (solo después de revisión autorizada)
SUSPENDED → ARCHIVED
```

Activar exige:

- organización ACTIVE;
- perfil mínimo válido;
- zona pública;
- horarios válidos o 24 h;
- al menos un servicio activo;
- no exige VERIFIED, pero la proyección muestra claramente el estado de verificación.

### Verificación

```text
UNVERIFIED → PENDING
PENDING → VERIFIED
PENDING → REJECTED
VERIFIED → SUSPENDED
REJECTED → PENDING
SUSPENDED → PENDING o VERIFIED según autoridad real
```

Solo el propietario/gestor solicita. Solo autoridad M04 revisa.

### Profesional

- ACTIVE/INACTIVE/SUSPENDED/ARCHIVED;
- vincular/desvincular preserva historial;
- verificación separada del estado operativo.

## Paso 10 — Proyección pública

El directorio remoto debe devolver solamente:

- ID;
- nombre;
- descripción;
- zona pública;
- dirección pública opcional;
- estado de verificación;
- guardia;
- 24 horas;
- logo/portada M05;
- contacto público solo si opt-in;
- web/redes validadas;
- especialidades;
- servicios activos;
- horarios públicos;
- profesionales activos con datos redactados.

No devolver:

- IDs internos innecesarios;
- notas internas;
- contacto privado;
- matrícula completa por defecto;
- datos de membresía;
- motivos de rechazo;
- evidencia privada;
- datos médicos;
- información de pagos.

## Paso 11 — Integración M04

Auditar el contrato real de M04 antes de programar.

Preferencia:

- reutilizar casos/decisiones de verificación existentes;
- vincular la solicitud M12 a la organización o profesional;
- auditar solicitud y decisión;
- no crear una autoridad paralela.

Si el M04 actual no ofrece una integración segura:

- mantener `PENDING`;
- crear un adapter/hook explícito;
- documentar la limitación;
- no autoaprobar;
- no crear un flujo falso.

Las RPC `review_*` deben exigir el permiso administrativo real encontrado en M04.

## Paso 12 — Integraciones M05, M06 y M07

### M05

Para `logo_asset_ref`, `cover_asset_ref` y `avatar_asset_ref` aceptar únicamente referencias seguras equivalentes a:

```text
m05://...
file_asset:...
```

Rechazar:

```text
http://
https://
bucket público leover
rutas arbitrarias
```

Esta restricción no aplica a `website_url` ni a enlaces de redes, que deben validarse como HTTPS y nunca interpretarse como media.

### M06

Preparar hooks equivalentes:

```text
VETERINARY_CLINIC_VERIFICATION_REQUESTED
VETERINARY_CLINIC_STATUS_CHANGED
VETERINARY_PROFESSIONAL_LINKED
VETERINARY_PROFESSIONAL_VERIFICATION_REQUESTED
```

No implementar push real.

### M07

Registrar eventos auditables equivalentes:

```text
VETERINARY_CLINIC_CREATED
VETERINARY_CLINIC_UPDATED
VETERINARY_CLINIC_STATUS_CHANGED
VETERINARY_CLINIC_VERIFICATION_CHANGED
VETERINARY_PROFESSIONAL_CREATED
VETERINARY_PROFESSIONAL_LINKED
VETERINARY_PROFESSIONAL_UNLINKED
VETERINARY_SERVICE_CHANGED
VETERINARY_OPENING_HOURS_REPLACED
```

Reutilizar el mecanismo existente; no crear un subsistema paralelo.

## Paso 13 — Repositorios Supabase

Implementar o completar:

```text
SupabaseVeterinaryClinicRepository
SupabaseVeterinaryProfessionalRepository
SupabaseVeterinaryDirectoryRepository
```

Requisitos:

- usar RPC, no DML directo;
- mapear JSON snake_case;
- mapear errores técnicos a M12 tipados;
- tolerar enums desconocidos;
- resolver por ID;
- mapear horarios y listas;
- redacción de contactos;
- no usar Supabase desde ViewModels;
- no usar service role;
- mantener fakes para tests.

Actualizar `DataProvider` con la selección real de implementaciones según el patrón existente.

No eliminar el `M12VeterinaryMemoryStore`; conservarlo para tests y previews.

## Paso 14 — UI y flujos

Conectar el directorio y detalle existentes a repositorios remotos.

Completar gestión real para usuarios autorizados:

```text
my_veterinary_clinics
veterinary_clinic_draft/{clinicId?}
veterinary_clinic_professionals/{clinicId}
veterinary_clinic_services/{clinicId}
veterinary_clinic_hours/{clinicId}
```

Permitir:

- crear borrador remoto;
- editar perfil;
- solicitar verificación;
- activar/pausar/archivar;
- crear/editar/desactivar servicio;
- crear/editar/vincular/desvincular profesional;
- reemplazar especialidades;
- reemplazar horarios semanales.

Estados UI:

```text
LOADING
CONTENT
EMPTY
ERROR
SAVING
SUCCESS
```

Prevenir doble envío.

No mostrar disponibilidad de turnos, precios ni historia clínica.

## Paso 15 — Errores

Completar errores equivalentes:

```text
VETERINARY_ORGANIZATION_REQUIRED
VETERINARY_BRANCH_INVALID
VETERINARY_CLINIC_ALREADY_EXISTS
VETERINARY_CLINIC_INVALID_TRANSITION
VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS
VETERINARY_VERIFICATION_FORBIDDEN
VETERINARY_VERIFICATION_INVALID_TRANSITION
VETERINARY_PROFESSIONAL_ALREADY_LINKED
VETERINARY_PROFESSIONAL_NOT_LINKED
VETERINARY_SPECIALTY_INVALID
VETERINARY_SERVICE_DUPLICATE
VETERINARY_SERVICE_INVALID
VETERINARY_OPENING_HOURS_INCOMPLETE
VETERINARY_OPENING_HOURS_DUPLICATE_DAY
VETERINARY_PUBLIC_PROJECTION_FORBIDDEN
```

No mostrar errores PostgreSQL/Supabase al usuario.

## Paso 16 — Tests focalizados

Crear o completar:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryPersistenceTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinarySupabaseMappingTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryMigrationStaticGuardsTest.kt
```

Cubrir como mínimo:

### Persistencia y reglas

1. crear clínica DRAFT;
2. organización obligatoria;
3. organización inactiva rechazada;
4. sede ajena rechazada;
5. duplicado organización/sede rechazado;
6. editar perfil;
7. solicitar verificación;
8. gestor no puede auto-VERIFIED;
9. autoridad M04 puede revisar;
10. transición de verificación inválida;
11. activar clínica válida;
12. activación sin servicio rechazada;
13. activación sin horario/24 h rechazada;
14. pausar;
15. archivar sin hard-delete;
16. clínica suspendida;
17. contacto opt-in;
18. contacto redactado;
19. crear profesional;
20. matrícula opcional;
21. vincular profesional;
22. duplicado de vínculo rechazado;
23. desvincular preserva historial;
24. profesional vinculado sin autoridad automática;
25. reemplazar especialidades;
26. crear servicio;
27. servicio duplicado rechazado;
28. desactivar servicio;
29. reemplazar horarios;
30. día duplicado rechazado;
31. horario inválido;
32. clínica 24 h;
33. guardia coherente;
34. directorio remoto con filtros;
35. detalle público redactado;
36. error técnico mapeado;
37. enum desconocido;
38. ID vacío/inexistente;
39. doble envío;
40. no Supabase real en tests.

### Mapeo

41. snake_case clínica;
42. profesionales;
43. especialidades;
44. servicios y especies;
45. horarios;
46. social_links;
47. contacto nulo;
48. referencias M05;
49. website HTTPS separado de asset;
50. error RPC estable.

### Guardas 046

51. migración 046 presente;
52. migraciones 040–045 intactas;
53. seis tablas;
54. RLS habilitado;
55. policies idempotentes;
56. no DML directo al cliente;
57. RPC cliente solo authenticated;
58. helpers revocados;
59. SECURITY DEFINER;
60. search_path seguro;
61. auth.uid actor;
62. sin obligatorio después de DEFAULT;
63. sin DROP TABLE;
64. sin TRUNCATE;
65. sin service_role Android;
66. sin pagos/precios;
67. sin turnos;
68. sin historia clínica;
69. legacy service_profiles intacto;
70. permisos veterinary.* sembrados.

Ejecutar únicamente:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M12VeterinaryFoundationTest" `
  --tests "*M12VeterinaryPersistenceTest" `
  --tests "*M12VeterinarySupabaseMappingTest" `
  --tests "*M12VeterinaryStaticGuardsTest" `
  --tests "*M12VeterinaryMigrationStaticGuardsTest" `
  --tests "*M05SupabaseMappingTest"
```

Agregar una suite focalizada real de M03/M04 solo si es necesaria para probar autoridad y no dispara una regresión amplia.

No ejecutar toda la suite.

## Paso 17 — Compilación

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar:

```text
assembleDebug
lint
jacoco
connectedAndroidTest
```

## Paso 18 — Documentación

Actualizar:

```text
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-auditoria-y-contratos-iniciales.md
```

Crear:

```text
docs/02-arquitectura/M12-persistencia-perfiles-servicios.md
docs/05-operacion/M12-aplicacion-migracion-046-supabase.md
```

La guía operativa debe incluir:

- precondiciones 040–045;
- aplicación íntegra de 046;
- verificación de seis tablas;
- RLS;
- policies;
- constraints;
- índices;
- inventario de RPC m12_*;
- SECURITY DEFINER;
- search_path;
- grants;
- cero DML directo;
- permisos veterinary.*;
- smoke de clínica, profesional, servicio, horarios, solicitud de verificación, publicación y directorio;
- aclaración de que agenda/turnos no forman parte de 046.

No afirmar que 046 está aplicada.

## Paso 19 — Revisión final

Ejecutar:

```powershell
git status
git diff --stat
git diff --check
```

Verificar:

- migraciones 040–045 intactas;
- migración nueva exactamente 046;
- sin APK;
- sin secretos;
- sin logs;
- sin temporales;
- sin service role;
- sin pagos;
- sin precios;
- sin turnos;
- sin historia clínica;
- legacy preservado.

Agregar:

```powershell
git add app supabase docs
git diff --cached --stat
git diff --cached --check
```

## Paso 20 — Git

Crear un único commit:

```powershell
git commit -m "feat(m12): persist veterinary profiles and services"
git push origin main
```

Si el push falla:

- no crear rama;
- no hacer rebase;
- no crear otro commit;
- entregar SHA;
- dejar pendiente únicamente `git push origin main`.

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría y patrones reutilizados.
3. Persistencia creada.
4. Tablas.
5. Constraints e índices.
6. RLS y policies.
7. Permisos.
8. RPC cliente.
9. Helpers internos.
10. Estados y transiciones.
11. Integración M03.
12. Integración M04.
13. Integración M05/M06/M07.
14. Repositorios Supabase.
15. DataProvider.
16. UI y navegación.
17. Errores tipados.
18. Tests ejecutados.
19. Cantidad de pruebas aprobadas.
20. Compilación.
21. Documentación.
22. SHA.
23. Push.
24. `git status -sb`.
25. Orden manual para aplicar 046.
26. Consultas de validación remota.
27. Smoke posterior.
28. Límites del bloque.
29. Plan exacto del Bloque 3.

No aplicar 046 remotamente.
No comenzar el Bloque 3.
No generar APK.
No implementar turnos, historia clínica ni pagos.
