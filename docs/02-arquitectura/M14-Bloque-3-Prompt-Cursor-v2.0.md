# Cursor — M14 Bloque 3: emisión, verificación humana y acceso público seguro

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `0a1bd45`.
- `origin/main` alineada.
- M14 Bloque 1: CERRADO LOCALMENTE.
- M14 Bloque 2: CERRADO LOCALMENTE.
- Migración 050: aplicada remotamente.
- Migración 051: aplicada remotamente.
- Validación estructural final M14: **18/18 PASS**.
- Migraciones existentes: 001–051.
- Próxima migración permitida: 052.
- M13 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- M12 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.

## Objetivo

Implementar **M14 Bloque 3 — Emisión, verificación humana y acceso público seguro**.

El bloque debe completar:

- revisión humana de solicitudes;
- aprobación y rechazo;
- emisión directa por actor autorizado;
- revocación de credenciales verificadas;
- historial y decisiones;
- concurrencia e idempotencia;
- rotación segura del `public_code`;
- payload de QR/deep link sin PII;
- repositorios Supabase;
- ViewModels y UI remota;
- migración 052 creada y validada localmente;
- un único commit y push;
- sin aplicar SQL remotamente.

## Lectura obligatoria

Leer completos:

```text
@docs/03-modulos/M14-pasaporte-identidad-verificable.md
@docs/02-arquitectura/ADR-014-M14-remapeo-pasaporte.md
@docs/03-modulos/M14-auditoria-inicial.md
@docs/03-modulos/M14-plan-funcional-y-tecnico.md
@docs/03-modulos/M14-persistencia-y-seguridad.md
@docs/02-arquitectura/M14-Bloque-1-validacion.md
@docs/02-arquitectura/M14-Bloque-2-validacion.md
@docs/02-arquitectura/M14-Migracion-051-validacion.md
@docs/05-operacion/M14-aplicacion-y-validacion-migracion-050.md
@docs/05-operacion/M14-aplicacion-y-validacion-migracion-051.md
@supabase/migrations/050_m14_pet_passports_and_credentials.sql
@supabase/migrations/051_m14_revoke_residual_table_privileges.sql
```

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–051.
- Crear únicamente la migración 052.
- No crear migración 053.
- No aplicar SQL remotamente.
- No generar APK.
- No usar emulador.
- Ejecutar pruebas focalizadas.
- Ejecutar una única compilación Kotlin final.
- No ejecutar lint/JaCoCo/toda la suite repetidamente.
- No debilitar Android CI.
- No declarar M12 ni M13 cerrados.
- No iniciar M15.
- No implementar historia clínica.
- No implementar pagos, seguros, GPS, chat o biometría.
- No autoverificar.
- No confiar en IDs de actor, rol u organización enviados por cliente.
- No exponer PII, documentos completos ni microchip completo.
- No incluir PII dentro del QR.
- No usar `service_role` en Android.
- No permitir DML directo.

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

Ante cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría exacta de 050/051

Confirmar:

1. tablas y constraints reales;
2. estados actuales de solicitud y credencial;
3. índice único de decisión por solicitud;
4. helpers `_m14_can_*`;
5. autoridad M08;
6. autoridad organizacional M03/M04;
7. profesionales M12;
8. campos del emisor;
9. historial disponible;
10. grants y RLS;
11. contrato Android del Bloque 2;
12. cómo se genera `public_code`;
13. que 051 dejó cero DML directo;
14. que no existe resolución remota.

Si el diseño real contradice la especificación, detenerse con:

```text
M14 BLOQUE 3 BLOQUEADO — CONTRATO 050/051 INCOMPATIBLE
```

No modificar 050 ni 051.

## Paso 3 — Migración 052

Crear exactamente:

```text
supabase/migrations/052_m14_credential_verification_and_public_access.sql
```

La migración debe reutilizar:

```text
pet_passports
pet_passport_credentials
pet_passport_verification_requests
pet_passport_verification_decisions
pet_passport_status_history
```

No crear tablas paralelas.

### Ajuste de estados

Agregar `UNDER_REVIEW` a la constraint de:

```text
pet_passport_verification_requests.status
```

Estados finales:

```text
APPROVED
REJECTED
CANCELLED
EXPIRED
```

No reabrir estados finales.

### Índices o constraints

Agregar solo cuando falten:

- una decisión final por solicitud;
- orden de cola por estado/fecha;
- búsqueda por emisor;
- idempotencia;
- protección contra decisiones duplicadas.

No alterar datos existentes de forma destructiva.

## Paso 4 — RPC nuevas

Crear exactamente estas diez RPC:

```text
m14_open_verification_review
m14_approve_verification_request
m14_reject_verification_request
m14_expire_verification_request
m14_get_verification_decision
m14_list_verification_decisions
m14_issue_verified_credential
m14_revoke_verified_credential
m14_rotate_public_code
m14_list_passport_status_history
```

Total esperado:

```text
10 RPC cliente nuevas
```

## Paso 5 — Transiciones

### Solicitudes

Permitir únicamente:

```text
PENDING -> UNDER_REVIEW
UNDER_REVIEW -> APPROVED
UNDER_REVIEW -> REJECTED
PENDING -> CANCELLED
PENDING -> EXPIRED
UNDER_REVIEW -> EXPIRED
```

La cancelación propia existente puede seguir operando únicamente desde `PENDING`.

Bloquear:

- aprobación directa desde `PENDING`;
- rechazo directo desde `PENDING`;
- reapertura de estados finales;
- segunda decisión;
- decisión por actor no autorizado;
- auto-verificación.

### Credenciales

Al aprobar:

```text
PENDING_VERIFICATION -> VERIFIED
```

Al rechazar:

```text
PENDING_VERIFICATION -> REJECTED
```

Al expirar una solicitud sin decisión:

- la solicitud pasa a `EXPIRED`;
- la credencial vuelve a `DRAFT`, salvo que su propia fecha de expiración ya haya vencido;
- si venció, pasa a `EXPIRED`.

Al revocar una credencial verificada:

```text
VERIFIED -> REVOKED
```

Estados finales no se editan.

## Paso 6 — Autoridad

Derivar actor exclusivamente desde:

```sql
auth.uid()
```

### Abrir revisión, aprobar o rechazar

Permitir a:

- organización objetivo con `passport.credential.verify`;
- profesional M12 objetivo activo;
- actor con `passport.verify` dentro de autoridad real;
- moderador autorizado.

Prohibir:

- responsable que solicitó la verificación, salvo que sea moderador;
- cualquier actor con responsabilidad activa M08 sobre la mascota;
- actor que no pertenece al emisor/objetivo;
- autoridad enviada por parámetros.

### Emisión directa verificada

`m14_issue_verified_credential` puede ser usada por:

- organización con `passport.credential.issue`;
- profesional M12 activo autorizado;
- moderador.

Reglas anti-autoverificación:

- el emisor no puede ser responsable activo M08 de la mascota;
- debe quedar `issuer_organization_id` o `issuer_professional_id`;
- el actor debe pertenecer realmente al emisor;
- la credencial nace `VERIFIED`;
- registrar trazabilidad;
- no crear una solicitud ficticia.

### Revocación

Puede revocar:

- el emisor original;
- actor con permiso de verificación dentro de la organización emisora;
- moderador.

El responsable puede retirar borradores o pendientes mediante el flujo existente, pero no revocar unilateralmente una credencial verificada emitida por un tercero.

## Paso 7 — Concurrencia e idempotencia

Toda mutación debe:

- usar `SELECT ... FOR UPDATE`;
- verificar estado después del lock;
- impedir decisiones duplicadas;
- devolver el resultado existente ante reintento equivalente;
- devolver `CONFLICT` ante reintento incompatible;
- mantener una única decisión final;
- no duplicar historial;
- usar timestamps del servidor.

Cubrir carreras:

```text
approve vs reject
approve vs expire
reject vs expire
revoke vs segundo revoke
rotate public code simultáneo
```

## Paso 8 — Decisiones e historial

### Decisión

Persistir:

- solicitud;
- `APPROVED` o `REJECTED`;
- actor;
- autoridad;
- razón tipificada;
- nota privada;
- timestamp.

No permitir edición ni borrado.

### Historial

Registrar:

- apertura de revisión;
- aprobación;
- rechazo;
- expiración;
- emisión directa;
- revocación;
- rotación de código público;
- actor;
- razón;
- metadatos no sensibles.

Si `pet_passport_status_history` está orientado al pasaporte y no a la credencial, usar metadatos tipificados sin inventar PII. No cambiar el estado del pasaporte cuando solo cambia una credencial.

## Paso 9 — Rotación de public code

`m14_rotate_public_code`:

- solo responsable M08, gestor autorizado o moderador;
- genera token nuevo mediante helper seguro;
- invalida inmediatamente el código anterior;
- no cambia `passport_number`;
- no cambia responsabilidad;
- es idempotente solo con una clave de operación segura si el proyecto ya usa idempotency keys;
- registra auditoría e historial;
- nunca devuelve PII adicional.

## Paso 10 — QR y deep link seguro

Implementar en Android:

```text
M14PublicQrPayloadService
```

El payload debe contener únicamente:

- esquema/host aprobado;
- `public_code`.

Ejemplo conceptual:

```text
leover://passport/PUB-...
```

o una URL HTTPS de staging ya configurada.

Prohibido incluir:

- nombre;
- microchip;
- petId;
- userId;
- passportNumber;
- contacto;
- token de sesión.

Reglas:

- no agregar una dependencia pesada de QR si no existe;
- si ya existe librería compatible, generar QR visual;
- si no existe, generar payload/copiar/compartir y dejar render visual para Bloque 4;
- validar longitud y esquema;
- no usar URLs arbitrarias.

## Paso 11 — Seguridad SQL

Todas las RPC:

- `SECURITY DEFINER`;
- `search_path = public`;
- actor desde `auth.uid()`;
- autoridad interna;
- sin SQL dinámico inseguro;
- errores consistentes;
- sin fuga de existencia de recursos ajenos.

### Grants

- `authenticated`: EXECUTE en las diez RPC;
- `anon`: sin EXECUTE;
- `PUBLIC`: revocado;
- helpers `_m14_*`: sin EXECUTE para clientes;
- tablas: sin DML directo;
- `SELECT` autenticado continúa protegido por RLS.

### RLS

No eliminar ni debilitar policies existentes.

## Paso 12 — M05, M06, M07 y M12

### M05

- media segura;
- documentos completos privados;
- no devolver media privada en público.

### M06

Preparar:

```text
M14_VERIFICATION_REVIEW_OPENED
M14_VERIFICATION_APPROVED
M14_VERIFICATION_REJECTED
M14_VERIFICATION_EXPIRED
M14_CREDENTIAL_ISSUED
M14_CREDENTIAL_REVOKED
M14_PUBLIC_CODE_ROTATED
```

No afirmar push real.

### M07

Auditoría best-effort, sin romper techo canónico.

### M12

Autoridad profesional derivada de tablas reales de profesionales y clínicas.

No convertir el pasaporte en historia clínica.

## Paso 13 — Android Supabase

Completar:

- DTOs de decisión e historial;
- DTOs de emisión/revocación;
- wrappers de las diez RPC;
- mappers;
- `SupabaseM14VerificationRepository`;
- `SupabaseM14CredentialRepository`;
- `SupabaseM14PassportRepository`;
- errores;
- DataProvider.

Eliminar `INFRASTRUCTURE_UNAVAILABLE` solo para operaciones ahora disponibles.

Conservar mocks con paridad contractual.

## Paso 14 — ViewModels y UI

Implementar:

- cola de verificaciones gestionadas;
- detalle de solicitud;
- abrir revisión;
- aprobar;
- rechazar;
- expirar;
- decisión visible según autoridad;
- emisión directa por emisor;
- revocación;
- historial;
- rotación del código público;
- payload QR/deep link;
- estados carga/vacío/error;
- conflictos;
- mensajes de privacidad.

Rutas sugeridas:

```text
m14/verifications/managed
m14/verifications/{requestId}
m14/credentials/issue
m14/credentials/{credentialId}/revoke
m14/passports/{passportId}/share
m14/passports/{passportId}/history
```

## Paso 15 — Errores

Mapear como mínimo:

```text
VERIFICATION_REVIEW_NOT_ALLOWED
VERIFICATION_ALREADY_UNDER_REVIEW
VERIFICATION_ALREADY_FINAL
DECISION_ALREADY_EXISTS
ISSUER_NOT_AUTHORIZED
SELF_VERIFICATION_NOT_ALLOWED
CREDENTIAL_ALREADY_FINAL
CREDENTIAL_REVOCATION_NOT_ALLOWED
PUBLIC_CODE_ROTATION_NOT_ALLOWED
INVALID_QR_PAYLOAD
CONFLICT
```

Reutilizar códigos existentes equivalentes.

## Paso 16 — Tests focalizados

### Migración 052

Cubrir:

1. solo 052 nueva;
2. 001–051 intactas;
3. `UNDER_REVIEW` agregado;
4. diez RPC presentes;
5. `SECURITY DEFINER`;
6. `search_path=public`;
7. authenticated EXECUTE;
8. anon/PUBLIC sin EXECUTE;
9. helpers protegidos;
10. cero DML directo;
11. `auth.uid()`;
12. `FOR UPDATE`;
13. una decisión;
14. estados finales no reabren;
15. sin aprobación directa desde PENDING;
16. anti-autoverificación;
17. emisor real;
18. no historia clínica;
19. public code seguro;
20. sin PII en QR;
21. sin service_role;
22. sin secretos.

### Android

Cubrir:

- open review;
- approve;
- reject;
- expire;
- emisión directa;
- revocación;
- decisión e historial;
- idempotencia;
- conflictos;
- autoridad positiva y negativa;
- auto-verificación rechazada;
- rotación del código;
- payload QR seguro;
- error mapping;
- switching mock/Supabase;
- regresión B1/B2.

## Paso 17 — Guard CI

Actualizar deliberadamente:

```text
scripts/ci/m07_quality_checks.sh
```

de highest migration:

```text
051
```

a:

```text
052
```

Actualizar únicamente las guardas relacionadas.

Debe fallar ante una futura 053 no incorporada.

## Paso 18 — Validaciones

Ejecutar:

```powershell
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Ejecutar pruebas focalizadas.

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

No aplicar 052 remotamente.

## Paso 19 — Documentación

Crear:

```text
docs/03-modulos/M14-emision-y-verificacion.md
docs/02-arquitectura/M14-Bloque-3-validacion.md
docs/05-operacion/M14-aplicacion-y-validacion-migracion-052.md
```

Actualizar:

```text
docs/03-modulos/M14-pasaporte-identidad-verificable.md
docs/03-modulos/M14-plan-funcional-y-tecnico.md
docs/03-modulos/M14-persistencia-y-seguridad.md
docs/01-producto/D01-Modulos-y-Orden.md
docs/05-operacion/Android-CI-actualizacion-guard-migraciones-047.md
```

Registrar:

- 050/051 aplicadas;
- 18/18 PASS;
- migración 052 creada y no aplicada;
- transiciones;
- autoridad;
- anti-autoverificación;
- concurrencia;
- idempotencia;
- QR/deep link;
- pruebas;
- limitaciones;
- smoke pendiente;
- propuesta exacta del Bloque 4.

## Paso 20 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–051 intactas;
- solo 052 nueva;
- sin 053;
- sin secretos;
- sin binarios;
- CI no debilitado;
- M12/M13 pendientes preservados;
- M15 no iniciado.

## Paso 21 — Git

Commit único:

```text
feat(m14): add credential verification workflow
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Auditoría 050/051.
3. Archivo 052.
4. Ajuste de estados.
5. Diez RPC y firmas.
6. Transiciones.
7. Autoridad.
8. Anti-autoverificación.
9. Emisión directa.
10. Revocación.
11. Concurrencia.
12. Idempotencia.
13. Decisiones.
14. Historial.
15. Rotación public code.
16. QR/deep link.
17. Seguridad SQL.
18. Grants.
19. RLS.
20. M05/M06/M07/M12.
21. DTOs/mappers.
22. Remote data source.
23. Repositorios.
24. DataProvider.
25. ViewModels/UI.
26. Errores.
27. Tests.
28. Total PASS.
29. `bash -n`.
30. Quality script.
31. Compilación.
32. Documentación.
33. Migraciones intactas.
34. 052 creada y no aplicada.
35. Limitaciones.
36. Smokes pendientes.
37. Propuesta exacta del Bloque 4.
38. SHA.
39. Push.
40. `git status -sb`.

## Estado final permitido

```text
M14 BLOQUE 3 CERRADO LOCALMENTE
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
M14 BLOQUE 2 REMOTO PASS
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```
