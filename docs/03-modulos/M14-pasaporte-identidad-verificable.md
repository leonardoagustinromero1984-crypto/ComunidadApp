# M14 — Pasaporte e identidad verificable de mascotas

## 1. Decisión canónica

El siguiente módulo del track técnico Android es:

```text
M14 — Pasaporte e identidad verificable de mascotas
```

La definición de producto:

```text
M14 — Adopciones y postulaciones
```

se considera funcionalmente cubierta por el módulo técnico ya implementado:

```text
M09 — Adopciones y postulaciones
```

No se reimplementa M09 ni se renumeran módulos técnicos ya cerrados.

M14 técnico toma el siguiente alcance de producto relevante todavía no consolidado como módulo independiente:

```text
Pasaporte de la mascota
```

La base técnica principal es M08 — Mascotas y responsables.

## 2. Objetivo

Crear una identidad digital estable y verificable para cada mascota, vinculada a su responsable legítimo y a credenciales emitidas o verificadas por actores autorizados.

El pasaporte permitirá:

- consolidar identidad básica;
- asignar un número de pasaporte estable;
- registrar microchip u otros identificadores;
- adjuntar credenciales documentales seguras;
- controlar qué información es privada, compartida o pública redactada;
- verificar credenciales mediante organizaciones autorizadas;
- mantener historial de cambios y verificaciones;
- generar en bloques posteriores un código público o QR seguro.

M14 no es una historia clínica.

## 3. Relación con módulos existentes

### M08 — Mascotas y responsables

Es la fuente autoritativa de:

- mascota;
- responsable actual;
- responsabilidad compartida;
- estado de la mascota;
- autoridad para administrar su pasaporte.

M14 no duplica la mascota ni la relación de responsabilidad.

### M05 — Archivos seguros

Toda credencial documental o imagen usa referencias seguras M05.

### M03/M04 — Organizaciones, permisos y moderación

Aportan:

- autoridad organizacional;
- permisos;
- moderación;
- suspensión o revocación.

### M07 — Auditoría

Registra:

- creación;
- activación;
- emisión de credenciales;
- verificación;
- rechazo;
- revocación;
- cambios de visibilidad.

### M09 — Adopciones

Puede aportar credenciales de adopción o transferencia, pero M14 no reabre ni reimplementa el flujo de adopción.

### M12 — Veterinarias

En bloques posteriores, una veterinaria o profesional autorizado puede emitir o verificar credenciales permitidas, sin convertir M14 en una historia clínica.

## 4. Actores

### Responsable M08

Puede:

- crear el pasaporte de una mascota bajo su responsabilidad;
- completar datos;
- administrar visibilidad;
- adjuntar credenciales;
- solicitar verificación;
- compartir una vista pública redactada;
- archivar el pasaporte cuando corresponda.

No puede:

- auto-verificar credenciales que requieren emisor externo;
- alterar el historial;
- acceder a pasaportes ajenos.

### Responsable compartido

Puede ver o administrar según la autoridad real definida por M08.

### Emisor autorizado

Organización o profesional autorizado que puede:

- emitir determinados tipos de credenciales;
- verificar credenciales compatibles con su autoridad;
- rechazar una solicitud de verificación;
- revocar una credencial propia cuando exista causa válida.

### Gestor de organización

Actor M03/M04 con permisos dentro de su organización.

### Moderador

Puede:

- suspender un pasaporte;
- ocultar una credencial insegura;
- revocar visibilidad pública;
- registrar el motivo;
- auditar abuso.

### Público

Solo puede consultar una proyección redactada mediante un identificador público seguro.

Nunca ve:

- datos del responsable;
- notas privadas;
- documentos completos;
- dirección;
- coordenadas;
- identificadores internos;
- información clínica.

## 5. Estados

### 5.1 Estado del pasaporte

```text
DRAFT
ACTIVE
SUSPENDED
REVOKED
ARCHIVED
```

Reglas:

1. Un pasaporte nuevo comienza en `DRAFT`.
2. `DRAFT` puede pasar a `ACTIVE` cuando cumple los mínimos.
3. `ACTIVE` puede pasar a `SUSPENDED`, `REVOKED` o `ARCHIVED`.
4. `SUSPENDED` puede volver a `ACTIVE` solo por actor autorizado.
5. `REVOKED` y `ARCHIVED` son finales en M14.
6. Solo puede existir un pasaporte no final por mascota.
7. El pasaporte no cambia la autoridad M08 sobre la mascota.

### 5.2 Estado de credencial

```text
DRAFT
PENDING_VERIFICATION
VERIFIED
REJECTED
EXPIRED
REVOKED
```

Reglas:

1. Una credencial creada por el responsable comienza en `DRAFT`.
2. Puede pasar a `PENDING_VERIFICATION`.
3. Un emisor o verificador autorizado decide `VERIFIED` o `REJECTED`.
4. `VERIFIED` puede pasar a `EXPIRED` o `REVOKED`.
5. Estados finales no se editan; una nueva versión requiere una credencial nueva.
6. No existe autoverificación.

### 5.3 Visibilidad

```text
PRIVATE
RESPONSIBLES
AUTHORIZED_ORGANIZATIONS
PUBLIC_REDACTED
```

La visibilidad se aplica por credencial y por atributo cuando corresponda.

## 6. Entidades

### M14PetPassport

Campos mínimos:

- `id`
- `petId`
- `passportNumber`
- `publicCode` opcional
- `status`
- `displayName`
- `species`
- `breedText` opcional
- `sex` opcional
- `birthDate` opcional
- `primaryColor` opcional
- `distinctiveMarks` opcional
- `microchipNumberMasked` opcional
- `visibility`
- `createdBy`
- `createdAt`
- `updatedAt`

Los datos básicos se proyectan desde M08 cuando exista una fuente autoritativa.

### M14Credential

- `id`
- `passportId`
- `type`
- `title`
- `issuerOrganizationId` opcional
- `issuerProfessionalId` opcional
- `issuedAt` opcional
- `expiresAt` opcional
- `status`
- `visibility`
- `mediaRefs`
- `externalReferenceMasked` opcional
- `createdBy`
- `createdAt`
- `updatedAt`

### M14CredentialType

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

Las atestaciones sanitarias son credenciales documentales. No contienen diagnóstico, evolución ni historia clínica.

### M14VerificationRequest

- `id`
- `credentialId`
- `requestedBy`
- `targetOrganizationId` opcional
- `status`
- `requestedAt`
- `resolvedAt` opcional
- `resolutionReason` opcional

Estados:

```text
PENDING
APPROVED
REJECTED
CANCELLED
EXPIRED
```

### M14VerificationDecision

- `id`
- `requestId`
- `decision`
- `actorUserId`
- `actorAuthority`
- `reasonCode`
- `notePrivate` opcional
- `createdAt`

### M14PassportHistory

Registro append-only de:

- estado anterior;
- estado nuevo;
- actor;
- razón;
- timestamp;
- metadatos no sensibles.

## 7. Número de pasaporte

En Bloque 1 el número se genera localmente para mocks con un generador determinista y sin colisiones.

Formato conceptual:

```text
LV-AR-YYYY-XXXXXXXX
```

Reglas:

- no contiene DNI, teléfono, correo ni ID de usuario;
- es estable;
- no se reutiliza;
- no es el mismo valor que `publicCode`;
- en persistencia remota será generado o confirmado por servidor;
- no se usa como secreto.

## 8. Permisos de dominio

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

En Bloque 1 son constantes y guardas locales. La autoridad real se persiste en Bloque 2.

## 9. Pantallas y rutas

```text
m14/passports
m14/pets/{petId}/passport
m14/pets/{petId}/passport/edit
m14/passports/{passportId}/credentials
m14/passports/{passportId}/credentials/new
m14/credentials/{credentialId}
m14/passports/{passportId}/verification
m14/public/{publicCode}
```

Bloque 1 incluye:

- lista de pasaportes propios;
- creación local desde una mascota M08;
- detalle;
- edición de campos permitidos;
- lista y alta local de credenciales;
- proyección pública redactada;
- estados vacíos, carga y error;
- navegación desde el perfil de la mascota.

La resolución remota de verificaciones queda para bloques posteriores.

## 10. Privacidad y seguridad

1. El responsable se obtiene de M08.
2. No se confía en un `ownerId` enviado por la UI.
3. La vista pública es redactada.
4. El microchip público se muestra enmascarado.
5. Los documentos completos nunca son públicos.
6. Las referencias de media deben ser seguras M05.
7. No se exponen notas privadas.
8. No se exponen identificadores de usuario u organización.
9. Sin `service_role` en Android.
10. Sin DML directo cuando exista Supabase.
11. Sin URLs arbitrarias.
12. Sin datos clínicos detallados.
13. Sin QR que incluya PII directamente.
14. Toda verificación futura requiere decisión humana.

## 11. Exclusiones

Fuera de M14:

- historia clínica;
- diagnósticos;
- recetas;
- turnos veterinarios;
- pagos;
- seguros;
- chat;
- tracking GPS;
- reconocimiento biométrico;
- generación de documentos oficiales estatales;
- validación con registros gubernamentales no integrados;
- firma digital legal avanzada;
- autoverificación;
- transferencia automática de responsabilidad;
- cierre o modificación automática de adopciones;
- exposición pública de documentos completos.

## 12. Orden de bloques

### Bloque 1 — Fundación local

- ADR y especificación;
- auditoría M08/M09/M12;
- dominio;
- estados;
- validadores;
- errores;
- generador local de número;
- contratos;
- fakes;
- proyección pública;
- ViewModels;
- UI y navegación;
- permisos constantes;
- pruebas;
- sin SQL.

### Bloque 2 — Persistencia y seguridad

**Estado:** CERRADO LOCALMENTE + remoto PASS (050/051 aplicadas; 18/18).

### Bloque 3 — Emisión, verificación y código público

**Estado:** CERRADO LOCALMENTE. Migración `052_m14_credential_verification_and_public_access.sql` creada; **no aplicada**.

- revisión humana (open/approve/reject/expire);
- emisión directa y revocación;
- concurrencia e idempotencia;
- rotación de publicCode;
- QR/deep link sin PII;
- UI y repos remotas;
- smoke funcional pendiente tras apply.

### Bloque 4 — Endurecimiento y cierre

- expiraciones;
- privacidad final;
- métricas sin PII;
- preparación M06/M07;
- regresión;
- documentación;
- cierre técnico y oficial.

## 13. Definición de terminado del Bloque 1

1. M14 técnico queda definido sin duplicar M09.
2. M08 sigue siendo la autoridad de mascota y responsables.
3. Existe dominio completo de pasaporte y credenciales.
4. Existe generador local sin colisiones.
5. No existe autoverificación.
6. Existen contratos y fakes.
7. Existe proyección pública redactada.
8. Existen UI y navegación iniciales.
9. Las referencias M05 inseguras son rechazadas.
10. Las pruebas focalizadas pasan.
11. `compileLocalDebugKotlin` pasa.
12. Migraciones 001–049 permanecen intactas.
13. No existe migración 050 **en Bloque 1** (050 llega en Bloque 2).
14. Los documentos de calidad 048/049 son revisados y versionados si son seguros.
15. Un único commit y push.
16. M12 y M13 siguen pendientes de smoke y cierre oficial.

## 14. Definición de terminado del Bloque 2

1. Existe únicamente migración 050 nueva; sin 051.
2. M08 es autoridad de mascota/responsables en SQL.
3. `public_code` criptográficamente no predecible.
4. 18 RPC cliente; anon solo en proyección pública.
5. Sin DML directo; helpers `_m14_*` protegidos.
6. Sin resolución remota de verificaciones; sin historia clínica.
7. Repositorios Supabase cableados; mocks conservados.
8. Guard CI highest = 050.
9. Tests focalizados + `compileLocalDebugKotlin` PASS.
10. 050 no aplicada remotamente.
11. M12/M13 smokes y cierres oficiales siguen pendientes externos.
12. Un único commit y push; M15 no iniciado.
