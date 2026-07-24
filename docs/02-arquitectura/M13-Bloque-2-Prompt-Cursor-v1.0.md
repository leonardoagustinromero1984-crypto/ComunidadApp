# Cursor — M13 Bloque 2: persistencia segura de avistamientos y candidatos

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `57ff62a2093faed0f41791cd4b68b03cce66f638`.
- `origin/main` alineada.
- Android CI remoto previo: PASS.
- M13 Bloque 1: CERRADO LOCALMENTE.
- M12 smoke funcional: PENDIENTE EXTERNO.
- M12 cierre oficial: PENDIENTE.
- Migraciones existentes: 001–047.
- Próxima migración permitida: 048.

## Objetivo

Implementar **M13 Bloque 2 — Persistencia, RLS/RPC y repositorios Supabase** para avistamientos y candidatos de coincidencia, preservando por completo el Lost/Found legacy.

El bloque debe cerrar localmente con la migración `048` creada y validada de forma estática, pero **no aplicada remotamente**.

## Lectura obligatoria

Leer completos:

```text
@docs/03-modulos/M13-avistamientos-y-coincidencias.md
@docs/02-arquitectura/ADR-013-M13-track-tecnico-avistamientos-coincidencias.md
@docs/03-modulos/M13-auditoria-inicial.md
@docs/03-modulos/M13-plan-funcional-y-tecnico.md
@docs/02-arquitectura/M13-Bloque-1-validacion.md
@docs/01-producto/D01-Modulos-y-Orden.md
```

Auditar también:

```text
supabase/migrations/012*
supabase/migrations/046*
supabase/migrations/047*
scripts/ci/m07_quality_checks.sh
.github/workflows/android-ci.yml
```

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–047.
- Crear únicamente la migración 048.
- No aplicar SQL en Supabase.
- No generar APK.
- No usar emulador.
- No ejecutar lint, JaCoCo ni toda la suite repetidamente.
- Ejecutar pruebas focalizadas.
- Ejecutar una sola compilación Kotlin final.
- No declarar M12 cerrado.
- No implementar el Bloque 3 completo.
- No confirmar ni rechazar coincidencias remotamente todavía.
- No implementar IA, biometría, chat, pagos, GPS en segundo plano ni ubicación exacta pública.
- No eliminar, renombrar ni recrear destructivamente `lost_found_sightings`.
- No introducir `service_role` en Android.
- No permitir DML directo del cliente.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
git rev-parse origin/main
```

Si hay cambios ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría de persistencia legacy

Inspeccionar la definición real de:

- `lost_found_sightings`;
- tabla o entidad autoritativa de casos Lost/Found activos;
- relaciones con mascotas M08;
- responsables de casos;
- organizaciones M03;
- permisos M04;
- media M05;
- auditoría M07;
- funciones/RPC existentes relacionadas.

Determinar y documentar:

1. PK y tipo de ID de `lost_found_sightings`.
2. Columnas existentes y nulabilidad.
3. Tabla real usada como `caseId`.
4. Cómo se determina que un caso está activo.
5. Cómo se obtiene la autoridad del responsable M08.
6. Cómo se obtiene la autoridad organizacional.
7. Qué campos legacy pueden reutilizarse.
8. Qué campos M13 faltan.
9. Si conviene extensión no destructiva o tabla lateral.

### Regla de compatibilidad

Preferir:

```text
ALTER TABLE ... ADD COLUMN IF NOT EXISTS
```

solo cuando los tipos y significados sean compatibles.

Si extender `lost_found_sightings` fuera inseguro, crear una tabla lateral 1:1, sin duplicar ni reemplazar el registro legacy.

No inventar FKs hacia tablas inexistentes.

Si no existe una fuente autoritativa de casos activos o responsabilidad, detenerse con:

```text
M13 BLOQUE 2 BLOQUEADO — AUTORIDAD LOST/FOUND NO RESUELTA
```

## Paso 3 — Migración 048

Crear exactamente:

```text
supabase/migrations/048_m13_sightings_and_match_candidates.sql
```

### Objetos mínimos esperados

La migración debe dejar persistencia para:

1. detalles M13 del avistamiento, ya sea mediante extensión compatible de `lost_found_sightings` o una tabla lateral 1:1;
2. `lost_found_match_candidates`;
3. `lost_found_match_decisions`;
4. `lost_found_match_status_history`.

Si se adopta tabla lateral, nombre recomendado:

```text
lost_found_sighting_details
```

No crear simultáneamente columnas duplicadas y tabla lateral para los mismos datos.

### Datos mínimos del avistamiento M13

Persistir, usando columnas legacy cuando correspondan:

- reportante;
- caso relacionado opcional;
- especie;
- raza opcional;
- color primario/secundario;
- sexo opcional;
- tamaño opcional;
- fecha observada;
- zona textual;
- ubicación aproximada opcional;
- precisión opcional;
- descripción;
- referencias de media seguras;
- estado M13;
- timestamps.

### Candidatos

`lost_found_match_candidates` debe incluir como mínimo:

- ID;
- case ID real;
- sighting ID;
- score 0–100;
- nivel `LOW|MEDIUM|HIGH`;
- razones explicables en estructura segura;
- estado;
- created/updated;
- actor o fuente de creación;
- versión o identificador del algoritmo cuando sea útil;
- unicidad por `case_id + sighting_id`.

### Decisiones e historial

Las tablas de decisiones e historial se crean ahora para soportar el Bloque 3, pero en este bloque:

- no exponer RPC de confirmación/rechazo final;
- no implementar todas las transiciones remotas;
- permitir solo la escritura interna necesaria para creación/recalculo/retirada;
- preservar trazabilidad.

### Constraints e índices

Agregar:

- PK;
- FKs reales;
- checks de estados;
- score entre 0 y 100;
- coherencia entre score y nivel;
- unicidad caso-avistamiento;
- índices por caso, avistamiento, estado y fecha;
- timestamps;
- borrado no destructivo o cascada únicamente donde esté justificada.

## Paso 4 — Matching server-side explicable

Implementar helpers SQL internos para calcular candidatos usando la misma semántica del Bloque 1:

1. especie obligatoria;
2. caso activo;
3. ventana temporal por defecto 30 días;
4. radio por defecto 10 km cuando haya ubicación;
5. zona textual como respaldo;
6. raza, color, sexo y tamaño suman evidencia;
7. score máximo 100;
8. `LOW 0–39`, `MEDIUM 40–69`, `HIGH 70–100`;
9. razones explicables;
10. orden determinista;
11. idempotencia por caso-avistamiento;
12. sin autoconfirmación;
13. sin IA de imagen;
14. sin exponer coordenadas exactas.

Usar funciones internas con prefijo:

```text
_m13_
```

Los helpers internos no deben quedar ejecutables por:

```text
PUBLIC
anon
authenticated
```

## Paso 5 — RPC cliente

Crear RPC cliente con nombres estables. Ajustar firmas a la estructura real del legacy, pero conservar estos nombres salvo bloqueo técnico documentado:

### Avistamientos

```text
m13_create_sighting
m13_update_my_sighting
m13_withdraw_my_sighting
m13_get_sighting
m13_list_public_sightings
m13_list_my_sightings
m13_list_managed_sightings
```

### Candidatos

```text
m13_generate_match_candidates_for_sighting
m13_generate_match_candidates_for_case
m13_list_case_match_candidates
m13_list_sighting_match_candidates
m13_get_match_candidate
m13_recalculate_match_candidate
```

Total esperado:

```text
13 RPC cliente
```

### Alcance de las RPC

- `create/update/withdraw`: solo reportante autenticado sobre lo propio.
- `get/list public`: respuesta redactada.
- `list my`: usuario autenticado sobre lo propio.
- `list managed`: responsable M08 o gestor autorizado.
- `generate/recalculate`: actor autorizado sobre el caso o moderador.
- `list/get candidate`: responsable/gestor/moderador; el reportante solo cuando la política canónica lo permita y siempre redactado.
- ninguna RPC confirma o rechaza definitivamente en este bloque.

## Paso 6 — Seguridad SQL

Todas las RPC cliente deben:

- usar `SECURITY DEFINER`;
- fijar `search_path = public`;
- derivar actor desde `auth.uid()`;
- validar autoridad dentro de la función;
- no confiar en actor/rol enviados por parámetros;
- devolver información redactada según autoridad;
- evitar SQL dinámico inseguro;
- manejar errores tipificados y consistentes.

### Grants

- `authenticated`: EXECUTE únicamente en RPC cliente autorizadas.
- `anon`: no recibe EXECUTE por defecto en Bloque 2.
- `PUBLIC`: revocado.
- tablas: sin DML directo para `authenticated` y `anon`.
- helpers `_m13_*`: sin EXECUTE para `PUBLIC`, `anon`, `authenticated`.

### RLS

Activar RLS en todas las tablas nuevas.

Mantener políticas de defensa en profundidad, aunque el acceso normal sea por RPC.

No debilitar RLS legacy.

## Paso 7 — Permisos reales

Registrar en el catálogo canónico usado por M03/M04, previsiblemente `organization_permissions`, estos códigos:

```text
lostfound.sighting.read
lostfound.sighting.create
lostfound.sighting.manage_own
lostfound.sighting.moderate
lostfound.match.read
lostfound.match.review
lostfound.match.confirm
```

Usar `ON CONFLICT` seguro según la PK/unique real.

Reglas:

- acciones propias se autorizan principalmente por `auth.uid()`;
- responsabilidad del caso se obtiene de M08;
- gestión organizacional usa M03/M04;
- moderación usa M04;
- la presencia del código en el catálogo no reemplaza las comprobaciones de autoridad.

## Paso 8 — Media y privacidad

Aceptar únicamente referencias seguras ya autorizadas por M05.

Auditar los formatos reales usados por el proyecto. No asumir que `m05:` es suficiente.

Aceptar solo formatos canónicos existentes, por ejemplo cuando correspondan:

```text
m05://
file_asset:
```

Rechazar:

- bucket público `leover`;
- URLs HTTP/HTTPS arbitrarias;
- coordenadas exactas en respuestas públicas;
- contacto personal;
- notas privadas;
- identidad completa del reportante en proyecciones públicas.

## Paso 9 — Android Supabase

Crear o completar:

- DTOs M13;
- mappers dominio/DTO;
- `SupabaseM13SightingRepository`;
- `SupabaseM13MatchRepository`;
- fuentes de datos;
- parsing de errores;
- paginación/filtros;
- integración en `DataProvider`.

Reglas:

- conservar repositorios mock del Bloque 1;
- mock cuando Supabase esté deshabilitado;
- Supabase cuando esté habilitado;
- sin secretos;
- sin red en tests;
- no alterar el hotfix auth;
- no romper rutas/UI del Bloque 1.

## Paso 10 — Errores y contratos

Mapear errores remotos a los códigos M13 existentes.

Agregar únicamente los códigos indispensables, por ejemplo:

```text
CASE_NOT_FOUND
CASE_NOT_ACTIVE
MATCH_GENERATION_NOT_ALLOWED
MATCH_DATA_INSUFFICIENT
CONFLICT
```

No duplicar códigos equivalentes.

Los errores de privacidad y autoridad deben ser distinguibles sin filtrar información sensible.

## Paso 11 — Tests focalizados

Crear suites específicas para:

### Migración estática

1. existe solo 048 nueva;
2. 001–047 intactas;
3. tablas/columnas esperadas;
4. constraints;
5. índices;
6. RLS;
7. políticas;
8. 13 RPC cliente;
9. `SECURITY DEFINER`;
10. `search_path=public`;
11. grants authenticated;
12. sin grants PUBLIC/anon;
13. sin DML directo;
14. helpers protegidos;
15. actor desde `auth.uid()`;
16. permisos presentes;
17. sin service_role;
18. sin secretos;
19. media segura;
20. sin autoconfirmación.

### Repositorios Android

- DTO parsing;
- mappers;
- redacción;
- create/update/withdraw;
- list public/my/managed;
- generate/list/get/recalculate;
- error mapper;
- switching mock/Supabase;
- sin llamadas reales.

### Regresión mínima

- M13 Bloque 1;
- Lost/Found legacy;
- M05;
- M08;
- auth hotfix;
- guardas de CI relacionadas.

## Paso 12 — Actualización del guard de migraciones CI

Como se crea la migración 048, actualizar deliberadamente:

```text
scripts/ci/m07_quality_checks.sh
```

para que el techo esperado pase de:

```text
047
```

a:

```text
048
```

Actualizar también únicamente las guardas estáticas que verifiquen el highest migration y la documentación operativa del CI.

No hacer reemplazos globales.

El control debe seguir siendo estricto y fallar ante una futura 049 no incorporada deliberadamente.

## Paso 13 — Validaciones

Durante el desarrollo, ejecutar solo pruebas focalizadas.

Al final ejecutar:

```powershell
bash -n scripts/ci/m07_quality_checks.sh
bash scripts/ci/m07_quality_checks.sh
```

Luego las suites focalizadas M13 y regresiones mínimas.

Ejecutar una sola compilación:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar APK.

No aplicar la migración 048 remotamente.

## Paso 14 — Documentación

Crear:

```text
docs/03-modulos/M13-persistencia-y-seguridad.md
docs/02-arquitectura/M13-Bloque-2-validacion.md
docs/05-operacion/M13-aplicacion-y-validacion-migracion-048.md
```

Actualizar:

```text
docs/03-modulos/M13-avistamientos-y-coincidencias.md
docs/03-modulos/M13-plan-funcional-y-tecnico.md
docs/01-producto/D01-Modulos-y-Orden.md
docs/05-operacion/Android-CI-actualizacion-guard-migraciones-047.md
```

La guía operativa de 048 debe incluir:

- aplicación manual completa del archivo;
- no reejecutar si da Success;
- validación estructural;
- smoke remoto pendiente;
- regla de no editar 048 después de aplicada;
- si aparece un defecto SQL posterior, usar 049.

No aplicar ni simular resultados remotos.

## Paso 15 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- solo migración 048 nueva;
- 001–047 intactas;
- sin 049;
- sin secretos;
- sin binarios;
- workflow CI no debilitado;
- M12 sigue pendiente externo;
- Bloque 3 no implementado.

## Paso 16 — Git

Un único commit:

```text
feat(m13): persist sightings and match candidates
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Auditoría de `lost_found_sightings`.
3. Fuente real de casos activos.
4. Estrategia elegida: extensión o tabla lateral.
5. Tablas creadas/alteradas.
6. Constraints e índices.
7. Matching SQL.
8. 13 RPC y firmas.
9. Autoridad M08/M03/M04.
10. RLS y políticas.
11. Grants.
12. Permisos.
13. Privacidad.
14. Media M05.
15. Repositorios Supabase.
16. DataProvider.
17. Errores.
18. Tests ejecutados.
19. Total PASS.
20. `bash -n`.
21. Quality script.
22. Compilación.
23. Documentación.
24. Migraciones intactas.
25. Migración 048 creada y no aplicada.
26. Limitaciones.
27. Pendientes remotos.
28. Propuesta exacta del Bloque 3.
29. SHA.
30. Push.
31. `git status -sb`.

## Estado final permitido

```text
M13 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 048 PENDIENTE DE APLICACIÓN REMOTA
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

Si la autoridad Lost/Found no puede resolverse de forma segura:

```text
M13 BLOQUE 2 BLOQUEADO — AUTORIDAD LOST/FOUND NO RESUELTA
```
