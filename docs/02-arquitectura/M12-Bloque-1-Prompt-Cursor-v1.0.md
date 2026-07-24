# Cursor — M12 Bloque 1: auditoría, dominio y directorio de veterinarias

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD esperado: `ec03064577d6bf936f29411c48af2c93835da0f1`.
- M11 — Refugios: `CERRADO`.
- Working tree esperado: limpio y alineado con `origin/main`.
- Migraciones 040–045 ya aplicadas en Supabase de pruebas.
- No iniciar trabajo sobre otro módulo durante este bloque.

## Objetivo

Implementar **M12 Bloque 1 — auditoría, dominio, contratos y directorio público local de veterinarias**.

Este bloque debe:

1. Auditar el estado real del repositorio respecto de veterinarias, profesionales, servicios, horarios, sedes y agenda.
2. Definir el dominio inicial de M12 sin crear persistencia remota.
3. Crear contratos de repositorio, errores tipados y fakes persistentes.
4. Implementar un directorio público local de veterinarias con filtros y detalle.
5. Preparar la gestión futura de perfiles vinculados a organizaciones M03.
6. Crear documentación técnica y pruebas focalizadas.
7. Hacer un único commit y push sobre `main`.

## Flujo obligatorio

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y un único push.
- No modificar migraciones 040–045.
- No crear migración 046 en este bloque.
- No aplicar SQL remoto.
- No iniciar Supabase local.
- No ejecutar emulador.
- No generar APK.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- Ejecutar pruebas focalizadas y una sola compilación Kotlin final.
- No reescribir código estable sin necesidad.
- No usar Supabase real en tests.
- No iniciar agenda/turnos reales, historia clínica ni pagos.

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
ec03064577d6bf936f29411c48af2c93835da0f1
```

Si aparecen archivos untracked de documentación M11/M12 intencional:

- identificarlos;
- preservarlos;
- incluirlos únicamente si pertenecen a este bloque.

Si hay cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Documentos de entrada

Leer, en este orden, los documentos reales que existan:

```text
docs/01-producto/D01-Modulos-y-Orden.md
docs/03-modulos/M03-Organizaciones-y-Equipos.md
docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md
docs/03-modulos/M05-Archivos-y-Multimedia.md
docs/03-modulos/M08-perfiles-mascotas.md
docs/03-modulos/M11-refugios.md
docs/03-modulos/M11-cierre-final.md
docs/02-arquitectura/M11-matriz-funcional-final.md
```

Leer también los ADR vigentes y la documentación de permisos, navegación, errores, DataProvider y Supabase.

No inventar rutas ausentes: registrar cuáles existen y cuáles no.

## Paso 3 — Auditoría del repositorio

Buscar en código, SQL, tests y docs:

```text
Veterinary
Veterinarian
Vet
Clinic
Professional
ProfessionalProfile
ServiceProfile
service_profiles
Specialty
Speciality
OpeningHours
BusinessHours
Emergency
Guard
Appointment
Schedule
Booking
Reservation
HealthRecord
MedicalRecord
Vaccination
Laboratory
Imaging
Surgery
Hospitalization
```

Revisar especialmente:

- tablas o modelos legacy `service_profiles`;
- organizaciones y sedes M03;
- verificación de organizaciones/profesionales M04;
- referencias seguras M05;
- permisos existentes;
- navegación y dashboards;
- filtros por zona;
- datos de contacto;
- horarios;
- agenda/reservas legacy;
- perfiles empresa/profesional;
- fakes y mocks existentes;
- cualquier código de pagos, GPS o mapas que esté separado o incompleto.

Clasificar cada hallazgo como:

```text
REUTILIZABLE
PARCIAL
LEGACY_COMPATIBLE
LEGACY_INCOMPATIBLE
AUSENTE
FUERA_DE_ALCANCE
```

Crear la matriz de auditoría antes de modificar código.

## Paso 4 — Decisiones de arquitectura

Aplicar estas reglas:

- Una veterinaria institucional se vincula a una organización M03 ACTIVE.
- Una sede veterinaria debe reutilizar una sede M03 cuando el modelo real lo permita.
- No crear un sistema paralelo de usuarios, organizaciones o permisos.
- `AccountType` y `active_modules` no conceden autoridad.
- Un profesional puede asociarse a una veterinaria, pero su perfil profesional no le concede permisos administrativos automáticos.
- La matrícula se registra como dato declarado/verificable; no afirmar verificación oficial sin flujo M04.
- El legacy `service_profiles` no se elimina ni se migra destructivamente en este bloque.
- Los contactos públicos deben ser opt-in y separados de datos internos.
- No persistir direcciones exactas privadas en proyecciones públicas.
- No registrar historia clínica ni datos médicos en este bloque.
- No realizar diagnósticos ni recomendaciones clínicas automáticas.

## Paso 5 — Dominio inicial

Crear o adaptar modelos equivalentes a:

```kotlin
data class VeterinaryClinicProfile(
    val id: String,
    val organizationId: String,
    val branchId: String?,
    val displayName: String,
    val description: String?,
    val status: VeterinaryClinicStatus,
    val verificationStatus: VeterinaryVerificationStatus,
    val publicZoneText: String,
    val publicAddressText: String?,
    val publicPhone: String?,
    val publicEmail: String?,
    val websiteUrl: String?,
    val socialLinks: Map<String, String>,
    val logoAssetRef: String?,
    val coverAssetRef: String?,
    val offersEmergencyCare: Boolean,
    val isOpen24Hours: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

Estados de clínica:

```text
DRAFT
ACTIVE
PAUSED
SUSPENDED
ARCHIVED
```

Verificación:

```text
UNVERIFIED
PENDING
VERIFIED
REJECTED
SUSPENDED
```

Crear:

```kotlin
data class VeterinaryProfessional(
    val id: String,
    val userId: String?,
    val clinicId: String?,
    val displayName: String,
    val licenseNumber: String?,
    val licenseJurisdiction: String?,
    val verificationStatus: VeterinaryVerificationStatus,
    val biography: String?,
    val specialties: Set<VeterinarySpecialty>,
    val publicContactEnabled: Boolean,
    val avatarAssetRef: String?,
    val status: VeterinaryProfessionalStatus
)
```

Estados profesionales:

```text
ACTIVE
INACTIVE
SUSPENDED
ARCHIVED
```

Especialidades mínimas:

```text
GENERAL_MEDICINE
EMERGENCY_AND_CRITICAL_CARE
SURGERY
INTERNAL_MEDICINE
DERMATOLOGY
CARDIOLOGY
NEUROLOGY
ONCOLOGY
OPHTHALMOLOGY
TRAUMATOLOGY
REPRODUCTION
EXOTIC_ANIMALS
DENTISTRY
DIAGNOSTIC_IMAGING
LABORATORY
OTHER
```

Crear:

```kotlin
data class VeterinaryService(
    val id: String,
    val clinicId: String,
    val name: String,
    val category: VeterinaryServiceCategory,
    val description: String?,
    val species: Set<AnimalSpecies>,
    val active: Boolean,
    val requiresAppointment: Boolean,
    val emergencyAvailable: Boolean
)
```

Categorías mínimas:

```text
CONSULTATION
VACCINATION
SURGERY
HOSPITALIZATION
LABORATORY
DIAGNOSTIC_IMAGING
EMERGENCY_GUARD
PREVENTIVE_CARE
DENTISTRY
PHARMACY
OTHER
```

No agregar precios en este bloque.

Crear:

```kotlin
data class VeterinaryOpeningHours(
    val clinicId: String,
    val dayOfWeek: DayOfWeek,
    val closed: Boolean,
    val opensAt: LocalTime?,
    val closesAt: LocalTime?,
    val emergencyOnly: Boolean
)
```

Crear filtros:

```kotlin
data class VeterinaryDirectoryFilter(
    val query: String?,
    val zoneText: String?,
    val specialty: VeterinarySpecialty?,
    val serviceCategory: VeterinaryServiceCategory?,
    val emergencyCareOnly: Boolean,
    val open24HoursOnly: Boolean,
    val verifiedOnly: Boolean
)
```

## Paso 6 — Validaciones

Implementar validadores puros para:

- nombre obligatorio;
- organización y sede válidas;
- zona pública obligatoria;
- horario consistente;
- `closesAt > opensAt` cuando no está cerrado;
- `isOpen24Hours` coherente con horarios;
- `offersEmergencyCare` coherente con servicios;
- email y URL sintácticamente válidos;
- referencias M05 seguras;
- matrícula vacía normalizada a null;
- no exponer contacto cuando `publicContactEnabled = false`;
- enums desconocidos tolerados por mappers.

No validar oficialmente matrículas contra organismos externos en este bloque.

## Paso 7 — Repositorios

Crear contratos equivalentes a:

```text
VeterinaryClinicRepository
VeterinaryProfessionalRepository
VeterinaryDirectoryRepository
```

Métodos mínimos:

```text
observePublicClinics(filter)
getPublicClinic(clinicId)
observeManagedClinics()
getManagedClinic(clinicId)
createLocalDraft(...)
updateLocalDraft(...)
observeClinicProfessionals(clinicId)
observeClinicServices(clinicId)
observeClinicOpeningHours(clinicId)
```

Reglas:

- ViewModels no acceden directamente a Supabase.
- Errores técnicos se mapean a errores de dominio.
- Resolución por ID explícita.
- Flujos actualizables.
- Sin Supabase real en tests.
- Los métodos `createLocalDraft` y `updateLocalDraft` son locales/fake en este bloque; no fingir persistencia remota.

## Paso 8 — Errores tipados

Agregar errores equivalentes:

```text
VETERINARY_CLINIC_NOT_FOUND
VETERINARY_CLINIC_FORBIDDEN
VETERINARY_CLINIC_INVALID
VETERINARY_CLINIC_INACTIVE
VETERINARY_CLINIC_UNVERIFIED

VETERINARY_PROFESSIONAL_NOT_FOUND
VETERINARY_PROFESSIONAL_INVALID
VETERINARY_PROFESSIONAL_UNVERIFIED

VETERINARY_SERVICE_NOT_FOUND
VETERINARY_OPENING_HOURS_INVALID
VETERINARY_PUBLIC_CONTACT_DISABLED
VETERINARY_MEDIA_REF_INVALID
VETERINARY_REPOSITORY_FAILURE
```

No mostrar errores técnicos de Supabase al usuario.

## Paso 9 — Fakes

Crear fakes persistentes en memoria que:

- almacenen clínicas;
- almacenen profesionales;
- almacenen servicios;
- almacenen horarios;
- permitan mutaciones locales;
- filtren por texto, zona, especialidad, servicio, guardia, 24 h y verificación;
- oculten contactos deshabilitados;
- resuelvan por ID;
- actualicen `Flow`;
- preserven estado entre operaciones del mismo test;
- permitan errores forzados;
- no conecten con Supabase.

Incluir datos de ejemplo sobrios y claramente ficticios, sin nombres de veterinarias reales.

## Paso 10 — UI y navegación

Crear rutas equivalentes:

```text
veterinary_directory
veterinary_clinic_detail/{clinicId}
my_veterinary_clinics
veterinary_clinic_draft/{clinicId?}
```

### Directorio público

Mostrar:

- nombre;
- zona pública;
- verificación;
- especialidades principales;
- servicios principales;
- guardia/emergencias;
- 24 horas;
- estado abierto/cerrado solo cuando pueda calcularse con datos confiables;
- logo seguro M05 o placeholder.

Filtros:

- búsqueda textual;
- zona;
- especialidad;
- servicio;
- guardia;
- 24 horas;
- verificadas.

Estados:

```text
LOADING
CONTENT
EMPTY
ERROR
```

### Detalle público

Mostrar:

- descripción;
- horarios;
- profesionales;
- especialidades;
- servicios;
- contacto público opt-in;
- web/redes;
- guardia;
- referencias M05.

No mostrar:

- matrícula completa en listados públicos salvo decisión documentada;
- contacto privado;
- notas internas;
- dirección privada;
- datos médicos;
- precios;
- disponibilidad de turnos ficticia.

### Gestión local

Crear una pantalla mínima de borrador local que permita editar:

- nombre;
- descripción;
- zona pública;
- guardia;
- 24 horas;
- contactos públicos;
- estado del borrador.

Debe indicar claramente que la persistencia remota se implementará en el Bloque 2.

Agregar acceso al directorio desde la navegación principal sin romper M11.

## Paso 11 — ViewModels

Crear ViewModels para:

```text
VeterinaryDirectoryViewModel
VeterinaryClinicDetailViewModel
ManagedVeterinaryClinicsViewModel
VeterinaryClinicDraftViewModel
```

Usar:

- `StateFlow`;
- loading explícito;
- contenido;
- vacío;
- error tipado;
- prevención de doble envío;
- resolución por ID;
- `SharingStarted.Eagerly` donde sea consistente con el proyecto.

No usar `null` como loading permanente.

## Paso 12 — Permisos y autoridad

Definir contratos iniciales equivalentes a:

```text
veterinary.profile.read
veterinary.profile.manage
veterinary.professional.read
veterinary.professional.manage
veterinary.service.read
veterinary.service.manage
```

En este bloque:

- registrar los identificadores en el dominio/Android si el proyecto lo requiere;
- no crear seeds SQL ni migración;
- no conceder autoridad por tipo de cuenta;
- no implementar administración remota.

## Paso 13 — Integraciones

### M03

- organización y sede como autoridad canónica;
- miembros activos y permisos para gestión futura;
- sin duplicar organizaciones.

### M04

- contratos preparados para verificación;
- no marcar una clínica o profesional como VERIFIED automáticamente;
- no crear casos reales de verificación en este bloque.

### M05

Aceptar referencias equivalentes a:

```text
m05://...
file_asset:...
```

Rechazar persistencia de:

```text
http://
https://
bucket público leover
rutas arbitrarias
```

### M06/M07

Preparar hooks/eventos equivalentes:

```text
VETERINARY_CLINIC_DRAFT_CREATED
VETERINARY_CLINIC_PROFILE_UPDATED
VETERINARY_PROFESSIONAL_LINKED
VETERINARY_SERVICE_UPDATED
```

Sin push real y reutilizando auditoría existente.

## Paso 14 — Tests focalizados

Crear:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryFoundationTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/M12VeterinaryStaticGuardsTest.kt
```

Cubrir al menos:

1. directorio loading inicial;
2. listado público;
3. filtro por texto;
4. filtro por zona;
5. filtro por especialidad;
6. filtro por servicio;
7. filtro por guardia;
8. filtro 24 horas;
9. filtro verificadas;
10. combinación de filtros;
11. resultado vacío;
12. error de repositorio;
13. detalle por ID;
14. ID vacío;
15. ID inexistente;
16. contacto público habilitado;
17. contacto público deshabilitado;
18. horarios válidos;
19. horarios inválidos;
20. clínica 24 horas;
21. guardia coherente;
22. profesional verificado;
23. profesional no verificado;
24. matrícula opcional;
25. referencia M05 válida;
26. URL pública como asset rechazada;
27. fake persiste borrador;
28. fake actualiza flujo;
29. doble envío;
30. enum desconocido;
31. usuario sin autoridad de gestión;
32. AccountType no concede autoridad;
33. voluntario M11 no obtiene autoridad veterinaria;
34. no Supabase real;
35. no migración 046;
36. migraciones 040–045 intactas;
37. sin pagos;
38. sin historia clínica;
39. sin service_role Android;
40. rutas registradas.

Ejecutar únicamente suites focalizadas:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M12VeterinaryFoundationTest" `
  --tests "*M12VeterinaryStaticGuardsTest" `
  --tests "*M11FinalClosureGuardsTest" `
  --tests "*M05MediaMappingTest"
```

Si `M05MediaMappingTest` no es el nombre real, identificar y ejecutar únicamente la suite focalizada real de mapping/referencias M05.

No ejecutar toda la suite.

## Paso 15 — Compilación

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar APK, lint, JaCoCo ni assembleDebug.

## Paso 16 — Documentación

Crear:

```text
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-auditoria-y-contratos-iniciales.md
```

Actualizar:

```text
docs/01-producto/D01-Modulos-y-Orden.md
```

Solo si el archivo real usa la numeración técnica M00–Mxx; preservar el contenido funcional original.

Registrar:

- estado inicial;
- auditoría;
- legacy encontrado;
- decisiones;
- modelos;
- repositorios;
- fakes;
- UI;
- rutas;
- permisos;
- integraciones;
- pruebas;
- compilación;
- límites;
- plan del Bloque 2;
- aclaración de que no existe migración 046 todavía.

No afirmar persistencia remota.

## Paso 17 — Git

Revisar:

```powershell
git status
git diff --stat
git diff --check
```

Verificar:

- sin APK;
- sin secretos;
- sin logs;
- sin temporales;
- sin SQL nuevo;
- sin cambios en migraciones 040–045;
- sin historia clínica;
- sin pagos;
- sin código Supabase real nuevo para M12.

Agregar:

```powershell
git add app docs
git diff --cached --stat
git diff --cached --check
```

Crear un único commit:

```powershell
git commit -m "feat(m12): add veterinary directory foundation"
git push origin main
```

Si el push falla:

- no crear rama;
- no rebasear;
- no crear otro commit;
- entregar SHA y dejar pendiente únicamente el push.

## Entrega final

Informar:

1. Estado inicial.
2. Documentos leídos.
3. Auditoría.
4. Legacy encontrado.
5. Decisiones arquitectónicas.
6. Dominio creado.
7. Repositorios.
8. Fakes.
9. Errores tipados.
10. Permisos iniciales.
11. Integraciones M03/M04/M05/M06/M07.
12. Directorio público.
13. Detalle de clínica.
14. Gestión local.
15. ViewModels.
16. Tests ejecutados.
17. Cantidad de pruebas aprobadas.
18. Compilación.
19. Documentación.
20. Archivos principales.
21. SHA.
22. Push.
23. `git status -sb`.
24. Límites del bloque.
25. Plan exacto del Bloque 2.

No crear migración 046.
No aplicar SQL.
No comenzar agenda/turnos reales.
No implementar historia clínica.
No generar APK.
No iniciar otro módulo.
