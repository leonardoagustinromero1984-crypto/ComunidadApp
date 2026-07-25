# Cursor — M14 Bloque 1: fundación local de pasaporte e identidad verificable

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado de entrada

- Rama: `main`.
- HEAD mínimo: `9da7415ed6c465a8647cf469e53474e1509c0573`.
- `origin/main` alineada.
- M13 cierre técnico local: COMPLETADO.
- M13 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- M12 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- Migraciones existentes: 001–049.
- Migración 050: inexistente.
- M14 alcance canónico: APROBADO.

## Decisión canónica

```text
M14 técnico = Pasaporte e identidad verificable de mascotas
Producto M14 Adopciones = cubierto por M09 técnico
```

No reimplementar adopciones.

## Archivos de lectura obligatoria

```text
@docs/03-modulos/M14-pasaporte-identidad-verificable.md
@docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md
@docs/03-modulos/M08-mascotas-y-responsables.md
@docs/03-modulos/M09-adopciones.md
@docs/03-modulos/M12-veterinarias.md
@docs/01-producto/D01-Modulos-y-Orden.md
```

## Archivos untracked conocidos

Pueden existir:

```text
docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-048-v1.0.md
docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-049-v1.0.md
docs/02-arquitectura/M14-Inicio-Auditoria-y-Bloque-1-Prompt-Cursor-v1.0.md
```

Reglas:

1. Leer los documentos 048/049 completos.
2. Confirmar que no contienen secretos, tokens, claves, PII ni resultados inventados.
3. Confirmar que reflejan las validaciones reales 13/13 y 14/14.
4. Si son seguros, incorporarlos al único commit como evidencia de calidad M13.
5. El prompt inicial bloqueado puede versionarse como registro histórico del gate que detectó la contradicción.
6. No borrar ningún untracked automáticamente.
7. Cualquier otro archivo ajeno bloquea el trabajo.

## Objetivo

Implementar **M14 Bloque 1 — Fundación local de pasaporte e identidad verificable**, sin SQL ni Supabase real.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–049.
- No crear migración 050.
- No aplicar SQL.
- No generar APK.
- No usar emulador.
- No ejecutar lint, JaCoCo ni toda la suite repetidamente.
- Ejecutar pruebas focalizadas.
- Ejecutar una sola compilación Kotlin final.
- No debilitar Android CI.
- No declarar M12 ni M13 cerrados oficialmente.
- No iniciar M15.
- No implementar historia clínica.
- No implementar verificación remota.
- No implementar QR remoto ni lookup público real.
- No usar Supabase en tests.
- No incluir secretos.
- No duplicar mascota o responsables de M08.
- No reimplementar adopciones M09.

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
```

Solo se toleran los tres untracked conocidos.

## Paso 2 — Auditoría focalizada

Auditar:

- modelos de mascota M08;
- autoridad y responsabilidad M08;
- perfil y detalle de mascota;
- adopción/transferencia M09;
- veterinarias/profesionales M12;
- media segura M05;
- auditoría M07;
- permisos M03/M04;
- patrones de repositorio, ViewModel y navegación;
- cualquier `passport`, `microchip`, `credential`, `vaccination`, `document` o `qr` legacy.

Buscar:

```powershell
rg -n --hidden -S "passport|pasaporte|microchip|credential|credencial|vaccin|vacun|steriliz|qr|publicCode" app docs supabase
```

Clasificar:

```text
REUTILIZABLE
REQUIERE_ADAPTACIÓN
LEGACY_PRESERVADO
FUERA_DE_ALCANCE
```

Documentar exactamente qué datos se proyectan desde M08 y cuáles pertenecen a M14.

## Paso 3 — Dominio

Implementar:

### Estados

```text
M14PassportStatus:
DRAFT
ACTIVE
SUSPENDED
REVOKED
ARCHIVED

M14CredentialStatus:
DRAFT
PENDING_VERIFICATION
VERIFIED
REJECTED
EXPIRED
REVOKED

M14VerificationRequestStatus:
PENDING
APPROVED
REJECTED
CANCELLED
EXPIRED

M14Visibility:
PRIVATE
RESPONSIBLES
AUTHORIZED_ORGANIZATIONS
PUBLIC_REDACTED
```

### Modelos

- `M14PetPassport`
- `M14Credential`
- `M14CredentialType`
- `M14VerificationRequest`
- `M14VerificationDecision`
- `M14PassportHistory`
- `M14PublicPassportProjection`
- filtros y resultados paginados cuando el patrón del proyecto lo use

### Reglas

- un solo pasaporte no final por mascota;
- M08 es autoridad de responsabilidad;
- número de pasaporte estable;
- `publicCode` distinto del número de pasaporte;
- sin autoverificación;
- estados finales no se editan;
- documentos completos no son públicos;
- microchip público enmascarado;
- historial append-only.

## Paso 4 — Validadores

Cubrir como mínimo:

- mascota obligatoria;
- responsabilidad M08;
- nombre y especie;
- fechas coherentes;
- expiración posterior a emisión;
- tipo de credencial;
- transiciones;
- visibilidad;
- microchip normalizado y enmascarado;
- referencias M05 seguras;
- títulos y notas con longitud segura;
- ausencia de PII en `publicCode`;
- proyección pública redactada.

Aceptar solo referencias seguras existentes en el proyecto, por ejemplo según corresponda:

```text
m05://
file_asset:
```

Rechazar URLs arbitrarias y bucket público inseguro.

## Paso 5 — Errores tipificados

Implementar o ampliar el mapper M14 con códigos equivalentes a:

```text
PET_NOT_FOUND
PASSPORT_NOT_FOUND
PASSPORT_ALREADY_EXISTS
INVALID_PASSPORT_STATUS
INVALID_TRANSITION
UNAUTHORIZED
INVALID_CREDENTIAL
INVALID_CREDENTIAL_DATES
INVALID_MEDIA_REFERENCE
CREDENTIAL_NOT_FOUND
VERIFICATION_NOT_ALLOWED
VERIFICATION_ALREADY_FINAL
PUBLIC_PROJECTION_REDACTED
INFRASTRUCTURE_UNAVAILABLE
CONFLICT
```

Seguir el patrón de errores existente. No duplicar códigos equivalentes.

## Paso 6 — Generador local

Crear un generador testeable de número de pasaporte.

Formato conceptual:

```text
LV-AR-YYYY-XXXXXXXX
```

Requisitos:

- sin colisiones;
- determinista cuando recibe clock/sequence;
- thread-safe o protegido;
- no contiene datos personales;
- no reutiliza números;
- separa `passportNumber` de `publicCode`.

Para el fake puede usarse secuencia atómica y reloj inyectable.

## Paso 7 — Contratos y fakes

Crear contratos:

- repositorio de pasaportes;
- repositorio de credenciales;
- repositorio de solicitudes de verificación;
- política de autoridad;
- política de visibilidad/redacción;
- generador de número;
- auditoría M07 preparada.

Crear almacenamiento in-memory y mocks.

Requisitos:

- persistencia durante la sesión de prueba;
- IDs sin colisiones;
- aislamiento por mascota/pasaporte;
- un pasaporte activo por mascota;
- filtros;
- orden estable;
- idempotencia;
- historial append-only;
- sin red;
- sin Supabase.

Integrar en `DataProvider` conservando módulos existentes.

## Paso 8 — Autoridad local

Constantes:

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

Guardas:

- responsable M08 crea y gestiona;
- responsable compartido según autoridad real;
- organización solo dentro de su alcance;
- emisor verifica tipos autorizados;
- moderador suspende u oculta;
- público solo proyección redactada;
- reportante no auto-verifica.

En Bloque 1 no registrar permisos en SQL.

## Paso 9 — Proyección pública

Crear un servicio puro que devuelva únicamente:

- nombre visible;
- especie;
- raza opcional;
- sexo opcional;
- color;
- marcas distintivas permitidas;
- estado del pasaporte;
- credenciales `PUBLIC_REDACTED`;
- microchip enmascarado;
- fecha de última actualización aproximada.

Excluir:

- petId;
- userId;
- organizationId;
- contacto;
- dirección;
- notas;
- documentos completos;
- media privada;
- número de microchip completo;
- IDs internos;
- datos clínicos.

## Paso 10 — ViewModels y UI

Agregar rutas:

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

Implementar:

- lista de pasaportes propios;
- creación desde mascota M08;
- detalle;
- edición permitida;
- lista de credenciales;
- alta local de credencial;
- detalle de credencial;
- pantalla de verificación preparada pero local;
- proyección pública redactada;
- navegación desde detalle de mascota;
- estados carga/vacío/error;
- etiquetas claras:

```text
Pendiente de verificación
Verificado por una organización
Información pública resumida
```

No implementar todavía:

- QR escaneable real;
- lookup remoto;
- verificación remota;
- emisión remota;
- revocación remota;
- transferencia de responsabilidad.

## Paso 11 — M05, M06, M07 y M12

### M05

- referencias seguras;
- sin archivos reales en tests;
- documentos completos privados.

### M06

Preparar eventos:

```text
M14_PASSPORT_CREATED
M14_PASSPORT_ACTIVATED
M14_CREDENTIAL_ADDED
M14_VERIFICATION_REQUESTED
```

No afirmar push real.

### M07

Preparar auditoría local sin ampliar silenciosamente el catálogo si existe techo canónico.

### M12

Preparar interfaz de emisor/verificador para credenciales permitidas. No implementar historia clínica ni usar turnos.

## Paso 12 — Pruebas focalizadas

Crear suites que cubran como mínimo:

1. creación de pasaporte;
2. pasaporte duplicado por mascota;
3. estados y transiciones;
4. generación sin colisiones;
5. separación `passportNumber` / `publicCode`;
6. fechas de credencial;
7. estados de credencial;
8. sin autoverificación;
9. autoridad responsable;
10. mascota ajena rechazada;
11. autoridad organizacional;
12. moderación;
13. visibilidad;
14. redacción pública;
15. microchip enmascarado;
16. documentos no públicos;
17. media M05;
18. historial append-only;
19. IDs sin colisiones;
20. aislamiento;
21. filtros y orden;
22. DataProvider;
23. navegación;
24. estados UI;
25. M06 preparado;
26. M07 preparado;
27. sin historia clínica;
28. sin Supabase real;
29. migraciones 001–049 intactas;
30. sin migración 050;
31. sin secretos;
32. regresión mínima M05/M08/M09/M12/auth.

## Paso 13 — Documentación

Crear o actualizar:

```text
docs/03-modulos/M14-pasaporte-identidad-verificable.md
docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md
docs/03-modulos/M14-auditoria-inicial.md
docs/03-modulos/M14-plan-funcional-y-tecnico.md
docs/02-arquitectura/M14-Bloque-1-validacion.md
```

Actualizar D01 para registrar:

```text
Producto M14 Adopciones = cubierto por M09 técnico
M14 técnico = Pasaporte e identidad verificable
M14 BLOQUE 1 CERRADO LOCALMENTE
```

No reescribir el resto del roadmap.

Versionar, si son seguros:

```text
docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-048-v1.0.md
docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-049-v1.0.md
docs/02-arquitectura/M14-Inicio-Auditoria-y-Bloque-1-Prompt-Cursor-v1.0.md
```

Documentar:

- estado anterior;
- remapeo;
- auditoría;
- dominio;
- privacidad;
- autoridad;
- UI;
- pruebas;
- limitaciones;
- pendientes;
- propuesta completa del Bloque 2;
- M12/M13 pendientes externos.

## Paso 14 — Validaciones

Ejecutar pruebas focalizadas M14 y regresiones mínimas.

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–049 intactas;
- sin 050;
- sin secretos;
- sin binarios;
- CI no debilitado;
- M12/M13 siguen pendientes externos;
- M15 no iniciado.

## Paso 15 — Git

Commit único:

```text
feat(m14): establish pet passport foundation
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Documentos untracked revisados.
3. Decisión de remapeo.
4. Auditoría.
5. Legacy reutilizado.
6. Dominio.
7. Estados.
8. Validadores.
9. Errores.
10. Generador de número.
11. Contratos.
12. Fakes.
13. Autoridad.
14. Privacidad.
15. Proyección pública.
16. DataProvider.
17. ViewModels.
18. UI y rutas.
19. M05/M06/M07/M12.
20. Tests ejecutados.
21. Total PASS.
22. Compilación.
23. Documentación.
24. Migraciones.
25. Limitaciones.
26. Pendientes.
27. Propuesta exacta del Bloque 2.
28. SHA.
29. Push.
30. `git status -sb`.

## Estado final permitido

```text
M14 BLOQUE 1 CERRADO LOCALMENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```
