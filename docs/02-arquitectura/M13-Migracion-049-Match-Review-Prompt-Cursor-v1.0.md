# Cursor — M13 habilitación remota de revisión humana mediante migración 049

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `a140183`.
- `origin/main` alineada.
- M13 Bloque 1: CERRADO LOCALMENTE.
- M13 Bloque 2: CERRADO LOCALMENTE.
- Migración 048: aplicada en Supabase de pruebas.
- Validación estructural 048: 13/13 PASS.
- M13 Bloque 3 local/mock: CERRADO LOCALMENTE.
- M13 Bloque 2 smoke funcional: PENDIENTE EXTERNO.
- M12 smoke funcional y cierre oficial: PENDIENTES.
- Migraciones existentes: 001–048.
- Próxima migración permitida: 049.

## Objetivo

Crear la migración `049` necesaria para habilitar de forma remota el flujo de revisión humana de coincidencias ya implementado localmente en M13 Bloque 3.

La migración debe exponer RPC seguras para:

- abrir revisión;
- confirmar;
- rechazar;
- marcar como inconclusa;
- retirar;
- expirar;
- listar decisiones;
- listar historial.

No debe reimplementar el Bloque 3 local ni agregar funcionalidades fuera del alcance.

## Lectura obligatoria

Leer completos antes de modificar código:

```text
@docs/03-modulos/M13-avistamientos-y-coincidencias.md
@docs/03-modulos/M13-persistencia-y-seguridad.md
@docs/02-arquitectura/M13-Bloque-2-validacion.md
@docs/02-arquitectura/M13-Bloque-3-validacion.md
@docs/02-arquitectura/M13-propuesta-migracion-049-match-review.md
@docs/05-operacion/M13-aplicacion-y-validacion-migracion-048.md
@supabase/migrations/048_m13_sightings_and_match_candidates.sql
```

La propuesta documentada para 049 es la fuente principal del diseño. No inventar firmas incompatibles ni omitir validaciones allí definidas.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–048.
- Crear únicamente la migración 049.
- No aplicar SQL remotamente.
- No generar APK.
- No usar emulador.
- No ejecutar lint, JaCoCo ni toda la suite repetidamente.
- Ejecutar pruebas focalizadas.
- Ejecutar una sola compilación Kotlin final si cambia código Android.
- No declarar M12 cerrado.
- No declarar M13 cerrado.
- No implementar IA, biometría, chat, pagos, GPS en segundo plano ni coordenadas exactas públicas.
- No cerrar automáticamente un caso Lost/Found al confirmar una coincidencia.
- No confiar en actor, rol, organización o autoridad enviados por el cliente.
- No exponer DML directo.
- No debilitar RLS ni el CI.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
git rev-parse origin/main
```

Si existen cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría exacta de 048 y de la propuesta 049

Confirmar:

1. nombres reales de tablas;
2. PK y FKs;
3. estados permitidos;
4. columnas de decisiones;
5. columnas del historial;
6. funciones helper existentes;
7. permisos ya sembrados;
8. autoridad real sobre el caso;
9. cómo se marca el avistamiento como `CONFIRMED`;
10. cómo se registra auditoría M07;
11. cómo se evita cierre automático de `lost_found_posts`;
12. qué DTOs/repositories Android ya esperan estas RPC.

Si existe contradicción entre la propuesta y 048, detenerse con:

```text
M13 MIGRACIÓN 049 BLOQUEADA — CONTRATO INCOMPATIBLE CON 048
```

No editar 048.

## Paso 3 — Crear migración 049

Crear exactamente:

```text
supabase/migrations/049_m13_match_review_workflow.sql
```

No crear tablas nuevas salvo bloqueo técnico real y documentado. La migración debe reutilizar:

```text
lost_found_match_candidates
lost_found_match_decisions
lost_found_match_status_history
lost_found_sighting_details
```

## Paso 4 — RPC cliente esperadas

Crear exactamente estas ocho RPC, respetando las firmas aprobadas en la propuesta:

```text
m13_open_match_review
m13_confirm_match_candidate
m13_reject_match_candidate
m13_mark_match_inconclusive
m13_withdraw_match_candidate
m13_expire_match_candidate
m13_list_match_decisions
m13_list_match_status_history
```

Total esperado:

```text
8 RPC cliente nuevas
```

### Alcance

#### `m13_open_match_review`

- solo desde `PROPOSED`;
- pasa a `UNDER_REVIEW`;
- registra historial;
- idempotente ante reintento equivalente;
- bloquea fila durante transición.

#### `m13_confirm_match_candidate`

- solo desde `UNDER_REVIEW`;
- exige autoridad real;
- crea una única decisión final;
- pasa candidato a `CONFIRMED`;
- marca el avistamiento M13 como `CONFIRMED`;
- registra historial;
- emite auditoría M07 cuando exista helper canónico;
- no cierra automáticamente `lost_found_posts`.

#### `m13_reject_match_candidate`

- solo desde `UNDER_REVIEW`;
- crea decisión final `REJECTED`;
- registra historial;
- no altera el caso Lost/Found.

#### `m13_mark_match_inconclusive`

- solo desde `UNDER_REVIEW`;
- crea decisión final `INCONCLUSIVE`;
- registra historial.

#### `m13_withdraw_match_candidate`

- permitido solo desde `PROPOSED` o `UNDER_REVIEW`;
- requiere autoridad;
- registra historial;
- no crea una segunda decisión final incompatible.

#### `m13_expire_match_candidate`

- permitido solo desde `PROPOSED` o `UNDER_REVIEW`;
- requiere autoridad o política interna aprobada;
- registra historial;
- idempotente.

#### Listados

- solo actores autorizados;
- orden determinista;
- no filtrar datos privados;
- decisiones e historial append-only;
- paginación o límite seguro si la arquitectura ya lo usa.

## Paso 5 — Transiciones obligatorias

Permitir únicamente:

```text
PROPOSED -> UNDER_REVIEW

UNDER_REVIEW -> CONFIRMED
UNDER_REVIEW -> REJECTED
UNDER_REVIEW -> INCONCLUSIVE

PROPOSED -> WITHDRAWN
UNDER_REVIEW -> WITHDRAWN

PROPOSED -> EXPIRED
UNDER_REVIEW -> EXPIRED
```

Bloquear:

- transición desde estados finales;
- reapertura;
- segunda decisión final;
- confirmación directa desde `PROPOSED`;
- rechazo directo desde `PROPOSED`;
- confirmación por reportante sin autoridad sobre el caso.

## Paso 6 — Autoridad

Derivar siempre al actor desde:

```sql
auth.uid()
```

Autorizar mediante las fuentes canónicas reales:

- dueño del caso Lost/Found;
- autoridad M08 cuando corresponda;
- gestor de organización con permiso;
- moderador autorizado;
- permisos `lostfound.match.review` y `lostfound.match.confirm`.

Reglas:

- `review` habilita apertura, retiro e inconclusa según contrato;
- `confirm` habilita confirmación;
- moderación no debe convertirse en acceso global implícito sin la comprobación canónica;
- el reportante no confirma por ser reportante;
- parámetros de actor/rol/organización no son autoridad.

## Paso 7 — Concurrencia e idempotencia

Cada transición debe:

- bloquear el candidato con `FOR UPDATE`;
- verificar estado actual después del lock;
- usar una clave o regla idempotente aprobada;
- evitar decisiones duplicadas;
- devolver el resultado existente ante reintento equivalente;
- devolver conflicto tipificado ante reintento incompatible;
- impedir carreras entre confirm/reject/inconclusive;
- mantener historial append-only.

## Paso 8 — Decisiones e historial

### Decisiones

- una sola decisión final por candidato;
- actor y autoridad auditables;
- razón tipificada;
- nota privada opcional;
- timestamps de servidor;
- sin edición posterior.

### Historial

Registrar como mínimo:

- estado anterior;
- estado nuevo;
- actor;
- motivo;
- timestamp;
- metadatos no sensibles;
- origen RPC o sistema cuando corresponda.

No borrar ni actualizar registros históricos.

## Paso 9 — Seguridad SQL

Todas las RPC cliente deben:

- usar `SECURITY DEFINER`;
- fijar `search_path = public`;
- validar autenticación;
- validar autoridad dentro de la función;
- usar consultas parametrizadas;
- no exponer coordenadas exactas;
- no exponer contacto ni notas privadas a actores no autorizados;
- devolver errores consistentes sin revelar existencia de datos ajenos.

### Grants

- `authenticated`: EXECUTE solo en las 8 RPC cliente.
- `anon`: sin EXECUTE.
- `PUBLIC`: revocado.
- helpers `_m13_*`: sin EXECUTE para `PUBLIC`, `anon`, `authenticated`.
- tablas: sin INSERT/UPDATE/DELETE para `authenticated` ni `anon`.

### RLS

- mantener RLS activo;
- no eliminar policies existentes;
- agregar solo policies estrictamente necesarias;
- acceso normal por RPC.

## Paso 10 — Helpers internos

Crear o reutilizar helpers con prefijo:

```text
_m13_
```

Solo cuando reduzcan duplicación y mantengan seguridad.

Posibles responsabilidades:

- autoridad de revisión;
- validación de transición;
- escritura append-only de historial;
- escritura idempotente de decisión;
- redacción de salida.

Todos deben quedar revocados para clientes.

## Paso 11 — Android

Auditar el código ya creado en Bloque 3.

Conectar las ocho RPC en:

- remote data source;
- repositorio Supabase;
- error mapper;
- ViewModels;
- UI de revisión;
- timeline;
- estados de carga/error.

Eliminar el fallback funcional:

```text
MATCH_REVIEW_RPC_UNAVAILABLE
```

solo para las operaciones ahora implementadas.

Conservarlo para cualquier capacidad todavía ausente.

No alterar mocks salvo para mantener paridad de contrato.

## Paso 12 — Errores

Mapear como mínimo:

```text
MATCH_NOT_FOUND
MATCH_REVIEW_NOT_ALLOWED
MATCH_REVIEW_ALREADY_OPEN
MATCH_ALREADY_FINAL
INVALID_TRANSITION
UNAUTHORIZED
CONFLICT
DECISION_ALREADY_EXISTS
```

Reutilizar códigos existentes cuando sean equivalentes.

No filtrar información sensible mediante mensajes de error.

## Paso 13 — Tests focalizados

### Migración estática 049

Cubrir:

1. existe solo 049 nueva;
2. 001–048 intactas;
3. ocho RPC presentes;
4. `SECURITY DEFINER`;
5. `search_path=public`;
6. EXECUTE para authenticated;
7. sin EXECUTE para anon/PUBLIC;
8. helpers protegidos;
9. sin DML directo;
10. actor desde `auth.uid()`;
11. `FOR UPDATE`;
12. una decisión final;
13. historial append-only;
14. sin autoconfirmación;
15. sin cierre automático del caso;
16. transiciones exactas;
17. estados finales no reabren;
18. permisos usados;
19. sin service_role;
20. sin secretos.

### Android

Cubrir:

- open review;
- confirm;
- reject;
- inconclusive;
- withdraw;
- expire;
- list decisions;
- list history;
- autoridad positiva y negativa;
- idempotencia;
- conflicto;
- error mapping;
- UI gated;
- timeline;
- switching mock/Supabase;
- regresión de Bloques 1–3.

## Paso 14 — Guard CI de migraciones

Actualizar deliberadamente:

```text
scripts/ci/m07_quality_checks.sh
```

de highest migration:

```text
048
```

a:

```text
049
```

Actualizar únicamente las guardas estáticas que validan el techo de migración.

El guard debe seguir siendo estricto y fallar ante una futura `050`.

No hacer reemplazos globales.

## Paso 15 — Validaciones

Ejecutar:

```powershell
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Ejecutar pruebas focalizadas M13 y regresiones mínimas necesarias.

Si cambió código Android, ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar APK.

No aplicar 049 remotamente.

## Paso 16 — Documentación

Crear:

```text
docs/03-modulos/M13-revision-humana-remota.md
docs/02-arquitectura/M13-Migracion-049-validacion.md
docs/05-operacion/M13-aplicacion-y-validacion-migracion-049.md
```

Actualizar:

```text
docs/02-arquitectura/M13-Bloque-3-validacion.md
docs/03-modulos/M13-avistamientos-y-coincidencias.md
docs/03-modulos/M13-plan-funcional-y-tecnico.md
docs/05-operacion/Android-CI-actualizacion-guard-migraciones-047.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar:

- causa por la que 049 era necesaria;
- 048 aplicada e intacta;
- RPC creadas;
- transiciones;
- autoridad;
- concurrencia;
- idempotencia;
- decisiones e historial;
- seguridad;
- pruebas;
- compilación;
- 049 no aplicada;
- smoke remoto pendiente;
- propuesta del Bloque 4.

## Paso 17 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–048 intactas;
- solo 049 nueva;
- sin 050;
- sin secretos;
- sin binarios;
- CI no debilitado;
- M12 sigue pendiente externo;
- M13 no declarado cerrado.

## Paso 18 — Git

Commit único:

```text
feat(m13): enable remote human match review
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Auditoría de 048.
3. Compatibilidad con propuesta 049.
4. Archivo 049.
5. Ocho RPC y firmas.
6. Transiciones.
7. Autoridad.
8. Concurrencia.
9. Idempotencia.
10. Decisiones.
11. Historial.
12. Efecto de confirmación.
13. Confirmación de no cierre automático.
14. RLS.
15. Grants.
16. Helpers.
17. Android remote data source.
18. Repositorios.
19. ViewModels/UI.
20. Errores.
21. Tests ejecutados.
22. Total PASS.
23. `bash -n`.
24. Quality script.
25. Compilación.
26. Documentación.
27. Migraciones intactas.
28. 049 creada y no aplicada.
29. Limitaciones.
30. Smoke remoto pendiente.
31. Propuesta del Bloque 4.
32. SHA.
33. Push.
34. `git status -sb`.

## Estado final permitido

```text
M13 REVISIÓN REMOTA CERRADA LOCALMENTE
MIGRACIÓN 049 PENDIENTE DE APLICACIÓN REMOTA
M13 BLOQUE 2 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

Si hay incompatibilidad real:

```text
M13 MIGRACIÓN 049 BLOQUEADA — CONTRATO INCOMPATIBLE CON 048
```
