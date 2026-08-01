# Cursor — M14 Bloque 4 local con validación remota diferida

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `025c1d61021e95be6aa85e567567a28a45a0e547`.
- `origin/main` alineada.
- M14 Bloques 1–3: cerrados localmente.
- Migraciones 050–051: aplicadas y validadas 18/18 PASS.
- Migración 052: creada en el repositorio, pero su aplicación y validación remotas quedan DIFERIDAS.
- GitHub Android CI: fallido y pendiente de diagnóstico por decisión del usuario.
- M13 smoke funcional y cierre oficial: pendientes externos.
- M12 smoke funcional y cierre oficial: pendientes externos.

## Decisión operativa

Continuar M14 Bloque 4 de forma local aunque la migración 052 todavía no esté aplicada remotamente.

Reglas de verdad:

- no declarar 052 aplicada;
- no declarar validación estructural 052 PASS;
- no declarar smoke remoto PASS;
- no declarar M14 cerrado oficialmente;
- cualquier funcionalidad dependiente de Supabase debe quedar documentada como `PENDIENTE_EXTERNO`;
- no corregir GitHub CI en este bloque;
- no aplicar SQL remotamente.

## Archivos temporales conocidos

Se toleran únicamente:

```text
.gradle-m14-compile/
compile_b3.txt
compile_m14_out.txt
compile_m14_pid.txt
compile_m14_run.bat
```

No editarlos, stagearlos ni incluirlos en el commit.
No usar `git clean`, `reset`, `restore`, `checkout` ni `stash`.

Cualquier otro cambio ajeno sí bloquea.

## Modo ahorro de tokens obligatorio

- Abrir chat nuevo.
- No releer todo el repositorio.
- No ejecutar auditorías globales.
- No usar subagentes.
- No usar tareas paralelas.
- No usar Max Mode.
- Leer únicamente archivos M14 y dependencias directas modificadas.
- Usar `git status`, `git diff` y búsquedas focalizadas.
- No explicar cada paso extensamente.
- Editar directamente.
- Ejecutar pruebas focalizadas.
- Una sola compilación Kotlin final solo si hubo cambios Kotlin.
- No generar APK.

## Objetivo

Cerrar localmente M14 Bloque 4 con:

- endurecimiento final;
- expiraciones;
- privacidad;
- métricas sin PII;
- preparación M06/M07;
- UI final;
- regresión;
- documentación de cierre técnico local.

## Reglas generales

- Trabajar directamente sobre `main`.
- Sin ramas ni backups.
- Sin commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–052.
- No crear migración 053 por defecto.
- No aplicar SQL.
- No iniciar M15.
- No declarar M12/M13 cerrados.
- No declarar M14 cerrado oficialmente.
- No corregir GitHub CI.
- No implementar historia clínica.
- No introducir pagos, GPS, chat, biometría ni seguros.
- No exponer PII.
- No debilitar RLS, guards o seguridad.

## Paso 1 — Verificación inicial

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

Se toleran solo los temporales conocidos.

## Paso 2 — Auditoría focalizada

Revisar únicamente:

```text
app/src/main/java/com/comunidapp/app/data/model/M14PassportModels.kt
app/src/main/java/com/comunidapp/app/data/repository/M14Repositories.kt
app/src/main/java/com/comunidapp/app/data/repository/M14Validators.kt
app/src/main/java/com/comunidapp/app/data/repository/M14PublicQrPayloadService.kt
app/src/main/java/com/comunidapp/app/data/repository/SupabaseM14Repositories.kt
app/src/main/java/com/comunidapp/app/data/remote/supabase/m14/
app/src/main/java/com/comunidapp/app/viewmodel/M14*
app/src/main/java/com/comunidapp/app/ui/screens/m14/
app/src/main/java/com/comunidapp/app/navigation/M14NavGraph.kt
app/src/test/java/com/comunidapp/app/viewmodel/M14*
docs/03-modulos/M14*
docs/02-arquitectura/M14*
docs/05-operacion/M14*
supabase/migrations/052_m14_credential_verification_and_public_access.sql
scripts/ci/m07_quality_checks.sh
```

Clasificar cada punto:

```text
PASS_LOCAL
PENDIENTE_EXTERNO
FUERA_DE_ALCANCE
BLOQUEANTE_SQL
```

Si aparece una necesidad SQL real:

```text
M14 BLOQUE 4 BLOQUEADO — MIGRACIÓN 053 REQUERIDA
```

No crear 053 automáticamente.

## Paso 3 — Expiraciones

Implementar o completar política local determinista para:

- solicitudes `PENDING` vencidas;
- solicitudes `UNDER_REVIEW` vencidas;
- credenciales vencidas por `expiresAt`;
- preservación de estados finales;
- reintentos idempotentes;
- zona horaria explícita `America/Argentina/Buenos_Aires`;
- preparación para scheduler externo.

Reglas:

- no afirmar cron real;
- no hacer llamadas remotas en tests;
- no modificar estados terminales;
- conflictos tipificados;
- sin duplicar historial.

## Paso 4 — Privacidad

Endurecer:

- proyección pública;
- errores;
- logs;
- métricas;
- historial;
- QR/deep link;
- notas privadas;
- microchip;
- documentos;
- IDs internos;
- emisores;
- publicCode.

Prohibido exponer:

- nombres de responsables;
- contacto;
- dirección;
- petId;
- userId;
- organizationId interno;
- passportNumber;
- microchip completo;
- notas privadas;
- media privada;
- token de sesión.

## Paso 5 — Métricas sin PII

Preparar métricas locales agregadas:

- pasaportes por estado;
- credenciales por estado y tipo;
- solicitudes por estado;
- tiempo agregado de resolución;
- aprobaciones;
- rechazos;
- expiraciones;
- revocaciones;
- rotaciones de publicCode;
- conflictos;
- reintentos idempotentes.

Agregar validación de rango inválido.

No incluir:

- nombres;
- IDs de usuario;
- microchips;
- contactos;
- documentos;
- publicCode completos;
- notas privadas.

Si una métrica remota requiere SQL:

```text
PENDIENTE_EXTERNO
```

No crear 053.

## Paso 6 — M06 y M07

M06:

- hooks y eventos preparados;
- sin push real;
- sin afirmar infraestructura activa.

M07:

- auditoría local/best-effort;
- sin PII;
- sin romper techo canónico;
- no modificar catálogo remoto en este bloque.

## Paso 7 — UI final

Completar:

- próxima acción;
- estados terminales;
- mensajes de conflicto;
- expiración;
- revocación;
- rotación de código;
- pantalla de compartir;
- historial;
- privacidad pública;
- métricas para gestores cuando la fuente local exista;
- fallback claro cuando Supabase/052 no esté disponible.

No simular resultados remotos.

## Paso 8 — Errores

Completar o reutilizar equivalentes:

```text
M14_EXPIRATION_NOT_ALLOWED
M14_EXPIRATION_ALREADY_APPLIED
M14_METRICS_INVALID_RANGE
M14_PUBLIC_CODE_UNAVAILABLE
M14_PUBLIC_CODE_RATE_LIMITED
M14_HISTORY_UNAVAILABLE
M14_REMOTE_VALIDATION_PENDING
M14_CONFLICT
```

No duplicar códigos equivalentes.

## Paso 9 — Pruebas focalizadas

Crear o completar suites para:

1. expiración PENDING;
2. expiración UNDER_REVIEW;
3. credencial vencida;
4. estados finales preservados;
5. idempotencia;
6. conflicto incompatible;
7. privacidad pública;
8. QR sin PII;
9. microchip enmascarado;
10. documentos privados;
11. métricas sin PII;
12. rango inválido;
13. M06 preparado;
14. M07 compatible;
15. fallback remoto pendiente;
16. migraciones 001–052 intactas;
17. sin 053;
18. sin secretos;
19. regresión M14 Bloques 1–3.

Ejecutar únicamente suites M14 y regresiones directas imprescindibles.

## Paso 10 — Compilación

Solo si se modificó Kotlin después del último BUILD SUCCESSFUL:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

Una sola vez.

No generar APK.

## Paso 11 — Documentación

Crear:

```text
docs/03-modulos/M14-matriz-funcional-final.md
docs/02-arquitectura/M14-Bloque-4-validacion.md
docs/04-calidad/M14-smoke-funcional-pendiente.md
docs/03-modulos/M14-cierre-tecnico.md
```

Actualizar:

```text
docs/03-modulos/M14-pasaporte-identidad-verificable.md
docs/03-modulos/M14-plan-funcional-y-tecnico.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar exactamente:

```text
M14 BLOQUE 4 CERRADO LOCALMENTE
M14 CIERRE TÉCNICO LOCAL COMPLETADO
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
VALIDACIÓN ESTRUCTURAL 052 PENDIENTE
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
GITHUB ANDROID CI PENDIENTE
```

No inventar PASS remotos.

## Paso 12 — Validaciones finales

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–052 intactas;
- sin 053;
- sin secretos;
- temporales fuera del commit;
- CI no debilitado;
- M12/M13 pendientes preservados;
- M15 no iniciado.

## Paso 13 — Git

Commit único:

```text
feat(m14): harden pet passport workflows
```

Push:

```powershell
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría.
3. Privacidad.
4. Seguridad.
5. Expiraciones.
6. Scheduler externo.
7. Métricas.
8. M06.
9. M07.
10. UI.
11. Errores.
12. Tests.
13. Total PASS.
14. Compilación.
15. Documentación.
16. Migraciones.
17. SQL.
18. Limitaciones.
19. Smokes pendientes.
20. Estado de 052.
21. Estado de GitHub CI.
22. Cierre oficial.
23. SHA.
24. Push.
25. `git status -sb`.

## Estado final permitido

```text
M14 BLOQUE 4 CERRADO LOCALMENTE
M14 CIERRE TÉCNICO LOCAL COMPLETADO
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
VALIDACIÓN ESTRUCTURAL 052 PENDIENTE
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
GITHUB ANDROID CI PENDIENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```
