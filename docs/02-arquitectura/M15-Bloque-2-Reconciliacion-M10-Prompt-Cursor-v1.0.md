# Cursor — M15 Bloque 2: reconciliación M10/M15 y persistencia remota

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `3ba11148b2ba1d1441dcab1cbe09152cd04d5c35`.
- `origin/main` alineada.
- M15 Bloque 1: CERRADO LOCALMENTE.
- Compilación Kotlin: PASS.
- Pruebas automáticas: NO EJECUTADAS por decisión del usuario.
- Validación funcional: MANUAL PENDIENTE.
- Producto M15: Hogares de tránsito.
- M10 técnico legacy: Hogares de tránsito, persistencia remota ya implementada.
- Migraciones M10 040/041: aplicadas y validadas previamente.
- Migraciones existentes en repo: 001–052.
- Migración 053: inexistente.
- M14 migración 052 y cierre oficial: pendientes.
- M13 y M12 cierres oficiales: pendientes.
- GitHub Android CI: pendiente.

## Objetivo

Implementar **M15 Bloque 2 — Reconciliación canónica con M10 y persistencia remota**, evitando crear un segundo sistema de hogares de tránsito.

La prioridad es:

```text
REUTILIZAR M10
ADAPTAR M15
NO DUPLICAR TABLAS
NO DUPLICAR DATOS
NO ROMPER RUTAS LEGACY
```

M15 debe convertirse en la capa funcional y de producto sobre la persistencia M10 existente.

## Modo ahorro obligatorio

- Trabajar en un chat nuevo.
- No releer todo el repositorio.
- No usar subagentes.
- No usar tareas paralelas.
- No usar Max Mode.
- Revisar solo M10, M15 y dependencias directas.
- No ejecutar pruebas automáticas.
- Ejecutar una única compilación Kotlin final.
- No generar APK.
- No corregir GitHub CI.
- No aplicar SQL remotamente.
- Un único commit y push.

## Reglas generales

- Trabajar directamente sobre `main`.
- Sin ramas, backups ni checkpoints.
- Sin commits intermedios.
- No modificar migraciones 001–052.
- No crear migración 053 por defecto.
- Crear 053 únicamente si la auditoría demuestra una brecha real y no destructiva.
- No crear tablas paralelas que representen hogares, solicitudes o placements ya existentes.
- No iniciar M16.
- No declarar M12, M13, M14 o M15 cerrados oficialmente.
- No implementar gastos, evolución, ayuda, egreso completo, pagos o chat.
- No incluir secretos.
- No afirmar pruebas automáticas PASS.
- Validación funcional manual.

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
working tree limpio
```

Ante cambios ajenos:

- no usar `reset`, `restore`, `clean`, `checkout` ni `stash`;
- informar;
- detenerse.

## Paso 2 — Lectura focalizada

Leer completos:

```text
docs/03-modulos/M15-hogares-de-transito.md
docs/03-modulos/M15-auditoria-inicial.md
docs/03-modulos/M15-plan-funcional-y-tecnico.md
docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md
docs/02-arquitectura/M15-Bloque-1-validacion.md
docs/03-modulos/M10-hogares-de-transito.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Auditar solamente:

```text
supabase/migrations/040*
supabase/migrations/041*
app/src/main/java/**/m10/
app/src/main/java/**/*Foster*
app/src/main/java/**/*foster*
app/src/main/java/**/*M15*
app/src/test/java/**/*M10*
app/src/test/java/**/*M15*
scripts/ci/m07_quality_checks.sh
```

Buscar:

```powershell
rg -n --hidden -S "m10_|foster_home|foster_request|placement|occupancy|reserved|availability|M15" app supabase docs
```

## Paso 3 — Matriz obligatoria de reconciliación

Crear:

```text
docs/03-modulos/M15-matriz-reconciliacion-M10.md
```

Mapear cada concepto:

| M15 | M10 persistente | Decisión |
|---|---|---|
| hogar | tabla/RPC real | REUTILIZAR / ADAPTAR |
| disponibilidad | cálculo real | REUTILIZAR / ADAPTAR |
| solicitud | tabla/RPC real | REUTILIZAR / EXTENDER |
| reserva | tabla/RPC real | REUTILIZAR / EXTENDER |
| ingreso | tabla/RPC real | REUTILIZAR / EXTENDER |
| placement | tabla/RPC real | REUTILIZAR / EXTENDER |
| autoridad | M08/M03/M04 | REUTILIZAR |
| auditoría | M07 | REUTILIZAR |
| eventos | M06 | PREPARADO |

La matriz debe identificar:

- tablas;
- columnas;
- estados;
- RPC;
- firmas;
- repositorios;
- DTO;
- rutas;
- diferencias semánticas;
- datos que no deben duplicarse.

## Paso 4 — Gate de arquitectura

### Caso A — M10 cubre M15 Bloque 1

Si M10 cubre hogar, disponibilidad, solicitudes y placement base:

- no crear migración 053;
- crear adaptadores M15 sobre repositorios/RPC M10;
- mantener las tablas y RPC M10 como fuente autoritativa;
- dejar highest migration en 052;
- no modificar el guard CI de migraciones.

### Caso B — Existe una brecha menor y segura

Crear:

```text
supabase/migrations/053_m15_foster_care_compatibility.sql
```

Únicamente para:

- agregar una columna opcional necesaria;
- ampliar un check de estado compatible;
- agregar índices;
- crear RPC wrapper `m15_*` sobre tablas M10;
- crear vistas/proyecciones compatibles;
- agregar permisos M15;
- mejorar RLS sin debilitarla.

La 053 no puede:

- crear una segunda tabla de hogares;
- crear una segunda tabla de solicitudes;
- copiar datos M10;
- renombrar o borrar objetos;
- alterar datos destructivamente;
- reemplazar las migraciones 040/041;
- romper clientes legacy.

Si se crea 053:

- actualizar highest migration de 052 a 053;
- no aplicarla remotamente;
- documentar aplicación y validación manual.

### Caso C — Reconciliación insegura

Detenerse sin implementar persistencia:

```text
M15 BLOQUE 2 BLOQUEADO — RECONCILIACIÓN M10/M15 NO RESUELTA
```

Informar la incompatibilidad exacta y no crear 053.

## Paso 5 — Modelo canónico

M10 permanece como fuente persistente.

M15 debe mapear:

- `M15FosterHome` ↔ hogar M10;
- disponibilidad derivada de capacidad, ocupación y reservas;
- `M15FosterRequest` ↔ solicitud M10;
- `M15FosterPlacement` ↔ reserva/ingreso M10;
- estados M15 ↔ estados reales M10;
- errores M10 ↔ errores M15;
- permisos M15 ↔ autoridad real.

No mantener dos stores activos cuando Supabase está habilitado.

## Paso 6 — Autoridad y privacidad

Derivar autoridad de:

- `auth.uid()`;
- responsabilidad M08;
- organización M03;
- permisos M02/M04;
- reglas M10 existentes.

No confiar en:

- `ownerId`;
- `organizationId`;
- rol;
- estado;
- capacidad;
- actor enviados desde UI.

Proyección pública:

- nombre visible;
- zona aproximada;
- especies aceptadas;
- capacidad/disponibilidad agregada;
- requisitos públicos.

Nunca exponer:

- dirección exacta;
- teléfono;
- correo;
- coordenadas;
- notas privadas;
- userId;
- IDs internos;
- historial privado.

## Paso 7 — Repositorios y DataProvider

Implementar:

- `SupabaseM15FosterHomeRepository`;
- `SupabaseM15FosterRequestRepository`;
- `SupabaseM15FosterPlacementRepository`;
- DTO y mappers necesarios;
- adaptador a RPC M10 o wrappers M15;
- mapeo de errores;
- switching en DataProvider.

Reglas:

- Supabase habilitado → persistencia remota canónica;
- Supabase deshabilitado → mocks M15;
- sin red real durante compilación;
- no eliminar mocks;
- no romper repositorios M10;
- no duplicar caché persistente.

## Paso 8 — Compatibilidad UI

Mantener:

```text
foster_*
m15/*
```

Las rutas legacy siguen funcionando.

M15 debe usar la misma fuente persistente.

No enlazar todavía M15 desde Sumate si eso requiere rediseño fuera de alcance. Documentar el pendiente.

Agregar fallback claro:

```text
INFRASTRUCTURE_UNAVAILABLE
REMOTE_VALIDATION_PENDING
```

## Paso 9 — Errores

Mapear o reutilizar equivalentes:

```text
M15_HOME_NOT_FOUND
M15_HOME_ALREADY_EXISTS
M15_HOME_NOT_ACTIVE
M15_CAPACITY_INVALID
M15_AVAILABILITY_CONFLICT
M15_REQUEST_NOT_FOUND
M15_REQUEST_INVALID_TRANSITION
M15_REQUEST_ALREADY_PENDING
M15_PLACEMENT_NOT_FOUND
M15_PLACEMENT_CONFLICT
M15_UNAUTHORIZED
M15_INFRASTRUCTURE_UNAVAILABLE
M15_REMOTE_VALIDATION_PENDING
```

No filtrar existencia de recursos ajenos.

## Paso 10 — M05, M06 y M07

M05:

- media segura únicamente;
- dirección y documentos privados.

M06:

- hooks preparados;
- no afirmar push real.

M07:

- auditoría best-effort;
- sin PII;
- sin romper catálogo canónico.

## Paso 11 — Revisión manual

No ejecutar pruebas automáticas.

Revisar manualmente:

1. mapping M10/M15;
2. disponibilidad:
   - usados = ocupación + reservas;
   - AVAILABLE;
   - LIMITED;
   - FULL;
   - UNAVAILABLE;
3. autoridad;
4. privacidad;
5. transición de solicitud;
6. reserva/ingreso;
7. errores;
8. DataProvider;
9. rutas legacy;
10. ausencia de duplicación;
11. migraciones 001–052 intactas;
12. 053 solo si fue necesaria;
13. sin secretos.

## Paso 12 — Compilación

Ejecutar una única vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Si falla:

- corregir únicamente errores de compilación;
- repetir hasta `BUILD SUCCESSFUL`.

No ejecutar:

```text
test
lint
JaCoCo
assemble
APK
```

## Paso 13 — Documentación

Crear:

```text
docs/03-modulos/M15-matriz-reconciliacion-M10.md
docs/03-modulos/M15-persistencia-remota.md
docs/02-arquitectura/M15-Bloque-2-validacion.md
```

Si existe 053, crear además:

```text
docs/05-operacion/M15-aplicacion-y-validacion-migracion-053.md
```

Actualizar:

```text
docs/03-modulos/M15-hogares-de-transito.md
docs/03-modulos/M15-plan-funcional-y-tecnico.md
docs/03-modulos/M15-auditoria-inicial.md
docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar:

- M10 como persistencia autoritativa;
- decisión de reutilización/adaptación;
- existencia o ausencia de 053;
- pruebas automáticas no ejecutadas;
- compilación;
- validación funcional manual pendiente;
- limitaciones;
- propuesta del Bloque 3;
- pendientes M12/M13/M14.

## Paso 14 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- 001–052 intactas;
- 053 solo si fue justificada;
- sin 054;
- sin secretos;
- sin binarios;
- CI no debilitado;
- M16 no iniciado.

## Paso 15 — Git

Commit único:

```text
feat(m15): reconcile foster care persistence
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Auditoría M10.
3. Matriz de reconciliación.
4. Caso aplicado: A, B o C.
5. Fuente persistente autoritativa.
6. Tablas/RPC reutilizadas.
7. Brechas detectadas.
8. Migración 053 creada o no.
9. Autoridad.
10. Privacidad.
11. DTO/mappers.
12. Remote data source o adaptador.
13. Repositorios Supabase.
14. DataProvider.
15. Errores.
16. Compatibilidad de rutas.
17. M05/M06/M07.
18. Revisión manual.
19. Compilación.
20. Pruebas automáticas no ejecutadas.
21. Documentación.
22. Migraciones.
23. Limitaciones.
24. Pendientes.
25. Propuesta exacta del Bloque 3.
26. SHA.
27. Push.
28. `git status -sb`.

## Estado final permitido — Caso A

```text
M15 BLOQUE 2 CERRADO LOCALMENTE
M10 ES LA PERSISTENCIA AUTORITATIVA DE M15
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Estado final permitido — Caso B

```text
M15 BLOQUE 2 CERRADO LOCALMENTE
M10 ES LA PERSISTENCIA AUTORITATIVA DE M15
MIGRACIÓN 053 PENDIENTE DE APLICACIÓN REMOTA
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Estado final permitido — Caso C

```text
M15 BLOQUE 2 BLOQUEADO — RECONCILIACIÓN M10/M15 NO RESUELTA
```
