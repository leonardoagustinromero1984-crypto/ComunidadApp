# Cursor — M15 Bloque 4: métricas, notificaciones, privacidad y cierre técnico local

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `9a507d336f7aeab53cd8df3db80f2099e066f79a`.
- `origin/main` alineada.
- M15 Bloques 1, 2 y 3: CERRADOS LOCALMENTE.
- M10 040/041 es la persistencia remota autoritativa.
- M08 conserva la responsabilidad principal.
- M15 usa adaptadores sobre M10/M08.
- Migraciones existentes: 001–052.
- Migración 053: inexistente.
- Compilación Kotlin del Bloque 3: PASS.
- Pruebas automáticas: NO EJECUTADAS por decisión del usuario.
- Validación funcional M15: MANUAL PENDIENTE.
- M14 migración 052 y cierre oficial: pendientes.
- M13 y M12 cierres oficiales: pendientes.
- GitHub Android CI: pendiente.
- M16: NO INICIADO.

## Objetivo

Completar **M15 Bloque 4** y dejar:

```text
M15 BLOQUE 4 CERRADO LOCALMENTE
M15 CIERRE TÉCNICO LOCAL COMPLETADO
```

El bloque debe cubrir:

1. métricas operativas agregadas sin PII;
2. integración final con M06 usando solamente infraestructura real existente;
3. privacidad final de proyecciones públicas y datos sensibles;
4. endurecimiento de errores, estados terminales, conflictos e idempotencia;
5. dashboard operativo y fallbacks;
6. preparación del smoke remoto/manual integrado M15/M10;
7. documentación final y cierre técnico local.

## Principio arquitectónico

```text
M10 = persistencia autoritativa
M08 = responsabilidad principal
M15 = experiencia funcional y operación
NO DUPLICAR TABLAS
NO DUPLICAR DATOS
NO INVENTAR PUSH O BACKEND
```

## Modo ahorro obligatorio

- Trabajar en un chat nuevo.
- No releer todo el repositorio.
- No usar subagentes.
- No usar tareas paralelas.
- No usar Max Mode.
- Revisar únicamente M15, M10, M08, M05, M06, M07 y patrones de cierre directamente necesarios.
- No ejecutar pruebas automáticas.
- Ejecutar una única compilación Kotlin final.
- No generar APK.
- No corregir GitHub CI.
- No aplicar SQL remotamente.
- No crear migración 053.
- Un único commit y push.
- No iniciar M16.

## Reglas generales

- Trabajar directamente sobre `main`.
- Sin ramas, backups ni checkpoints.
- Sin commits intermedios.
- No modificar migraciones 001–052.
- No crear 053 ni 054.
- No reemplazar M10.
- No transferir responsabilidad principal M08.
- No implementar pagos, reembolsos ni chat.
- No incluir secretos.
- No afirmar pruebas automáticas PASS.
- No afirmar smoke remoto PASS.
- No declarar M15 cerrado oficialmente.
- La validación funcional queda manual y pendiente.

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
docs/03-modulos/M15-plan-funcional-y-tecnico.md
docs/03-modulos/M15-persistencia-remota.md
docs/03-modulos/M15-evolucion-egreso-gastos-y-ayuda.md
docs/03-modulos/M15-matriz-Bloque-3-M10-M08.md
docs/02-arquitectura/M15-Bloque-3-validacion.md
docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Auditar solo:

```text
app/src/main/java/**/*M15*
app/src/main/java/**/*Foster*
app/src/main/java/**/*M10*
app/src/main/java/**/*M08*
app/src/main/java/**/*M05*
app/src/main/java/**/*M06*
app/src/main/java/**/*M07*
app/src/main/java/**/DataProvider.kt
```

Buscar patrones de cierre únicamente si hace falta:

```text
docs/03-modulos/M14*
docs/02-arquitectura/M14*
```

No leer módulos completos ajenos.

## Paso 3 — Métricas operativas agregadas

Crear o completar un contrato equivalente a:

```text
M15OperationalMetrics
M15OperationalMetricsQuery
M15OperationalMetricsRepository
```

La implementación puede componer datos de los repositorios M15/M10 existentes. No requiere SQL nuevo.

### Rango

- `fromInclusive`;
- `toExclusive`;
- zona horaria determinista;
- rechazo de rango vacío, invertido o excesivo;
- código `M15_METRICS_INVALID_RANGE`.

### Métricas mínimas

#### Hogares

- total por estado;
- total por disponibilidad;
- capacidad total;
- plazas ocupadas;
- plazas reservadas;
- plazas disponibles.

#### Solicitudes

- total por estado;
- enviadas;
- aceptadas;
- rechazadas;
- canceladas;
- expiradas;
- tiempo agregado de resolución cuando los datos lo permitan.

#### Placements

- total por estado;
- reservados;
- activos;
- completados;
- interrumpidos;
- cancelados;
- egresos por motivo y outcome.

#### Evolución

- cantidad por tipo;
- alertas de salud agregadas;
- incidentes agregados;
- sin textos, notas ni IDs.

#### Gastos

- cantidad por estado y categoría;
- suma por moneda;
- no convertir monedas;
- no exponer descripción, comprobante ni actor.

#### Ayuda

- cantidad por tipo, estado y prioridad;
- abiertas;
- en curso;
- resueltas;
- canceladas;
- expiradas.

#### Calidad operativa

- conflictos;
- reintentos idempotentes;
- fallbacks remotos;
- errores por código agregado;
- sin payloads ni identificadores.

## Paso 4 — Privacidad de métricas

Las métricas nunca pueden contener:

```text
userId
petId
homeId
requestId
placementId
organizationId
dirección
teléfono
correo
coordenadas
microchip
publicCode
nota privada
summary
description
comprobante
URL privada
nombre de persona
nombre de mascota
```

Crear helpers o sanitización defensiva equivalente.

Los logs deben contener:

- código;
- categoría;
- estado;
- timestamp;
- contador;

y nunca payload completo.

## Paso 5 — M06: integración real o fallback honesto

Auditar la infraestructura real M06.

### Si existe publicador/notificador real

Integrar eventos M15 usando el contrato existente:

```text
M15_REQUEST_SUBMITTED
M15_REQUEST_ACCEPTED
M15_PLACEMENT_RESERVED
M15_PLACEMENT_STARTED
M15_EVOLUTION_ADDED
M15_PLACEMENT_COMPLETED
M15_PLACEMENT_INTERRUPTED
M15_EXPENSE_RECORDED
M15_HELP_REQUEST_OPENED
M15_HELP_REQUEST_RESOLVED
```

Reglas:

- publicación best-effort;
- idempotencia;
- sin PII;
- sin bloquear la operación principal;
- destinatarios derivados server-side o por contrato seguro;
- no enviar dirección, contacto, notas, gastos detallados ni comprobantes.

### Si M06 no tiene envío real disponible

No inventar push.

Mantener hooks y devolver/documentar:

```text
M15_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE
M15_REMOTE_VALIDATION_PENDING
```

El cierre técnico local sigue permitido si el fallback es explícito.

## Paso 6 — Privacidad final de proyecciones

Revisar todas las proyecciones públicas M15/M10.

### Permitido

- alias o nombre público del hogar;
- zona aproximada;
- especies aceptadas;
- capacidad agregada;
- disponibilidad;
- requisitos públicos;
- estado público permitido.

### Prohibido

- dirección exacta;
- coordenadas;
- teléfono;
- correo;
- userId;
- organizationId interno;
- IDs internos;
- notas privadas;
- evolución privada;
- alertas detalladas;
- gastos;
- comprobantes;
- solicitudes de ayuda privadas;
- identidad completa del cuidador.

Agregar enmascarado o exclusión donde falte.

## Paso 7 — Estados terminales, conflictos e idempotencia

Endurecer:

- solicitudes terminales no reabren;
- placements terminales no reabren;
- egreso repetido es idempotente;
- evolución append-only;
- gastos terminales no cambian sin transición válida;
- ayuda terminal no reabre;
- capacidad no queda negativa;
- reserva/ocupación no supera capacidad;
- acciones concurrentes devuelven conflicto seguro.

Códigos equivalentes:

```text
M15_METRICS_INVALID_RANGE
M15_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE
M15_PUBLIC_PROJECTION_UNAVAILABLE
M15_PRIVACY_VIOLATION
M15_STATE_ALREADY_FINAL
M15_IDEMPOTENT_REPLAY
M15_CAPACITY_CONFLICT
M15_CONFLICT
M15_REMOTE_VALIDATION_PENDING
```

No filtrar existencia de recursos ajenos.

## Paso 8 — Dashboard operativo

Agregar o completar rutas equivalentes:

```text
m15/operations
m15/operations/metrics
m15/operations/privacy
m15/operations/smoke
```

No es obligatorio crear cuatro pantallas separadas si una sola pantalla/tab resuelve el alcance.

La UI debe mostrar:

- resumen de hogares;
- disponibilidad;
- solicitudes;
- placements;
- evolución agregada;
- gastos agregados;
- ayuda agregada;
- rango de fechas;
- carga;
- vacío;
- error;
- fallback remoto;
- aviso “sin datos personales”;
- estado de infraestructura M06;
- estado de smoke manual;
- próxima acción.

No mostrar PII ni IDs.

## Paso 9 — Smoke remoto integrado, pero no ejecutado

Preparar una guía y, si el patrón del proyecto lo admite, un runner/checklist manual que no se ejecute automáticamente.

Cubrir:

1. abrir hub M15;
2. consultar hogares desde M10;
3. crear solicitud;
4. aceptar/rechazar;
5. reservar;
6. iniciar placement;
7. agregar evolución;
8. registrar gasto;
9. abrir/resolver ayuda;
10. egresar;
11. verificar capacidad liberada;
12. verificar custodia temporal revocada;
13. verificar privacidad pública;
14. verificar eventos M06 o fallback;
15. verificar métricas agregadas;
16. confirmar que no existe duplicación M10/M15.

No ejecutar operaciones remotas desde Cursor.

Registrar:

```text
M15 SMOKE FUNCIONAL REMOTO PENDIENTE EXTERNO
```

## Paso 10 — Revisión manual

No ejecutar pruebas automáticas.

Revisar manualmente:

1. métricas por dominio;
2. rango inválido;
3. ausencia de PII;
4. logs seguros;
5. M06 real o fallback honesto;
6. privacidad pública;
7. estados terminales;
8. idempotencia;
9. capacidad;
10. DataProvider;
11. navegación;
12. smoke preparado;
13. M10/M08 autoritativos;
14. sin duplicación;
15. migraciones 001–052 intactas;
16. sin 053/054;
17. sin secretos;
18. M16 no iniciado.

## Paso 11 — Compilación

Ejecutar una única vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Si falla:

- corregir solamente errores de compilación;
- repetir hasta `BUILD SUCCESSFUL`.

No ejecutar:

```text
test
lint
JaCoCo
assemble
APK
```

## Paso 12 — Documentación

Crear:

```text
docs/03-modulos/M15-metricas-operativas-y-privacidad.md
docs/02-arquitectura/M15-Bloque-4-validacion.md
docs/03-modulos/M15-smoke-funcional-pendiente.md
docs/03-modulos/M15-cierre-tecnico.md
docs/03-modulos/M15-matriz-funcional-final.md
```

Actualizar:

```text
docs/03-modulos/M15-hogares-de-transito.md
docs/03-modulos/M15-plan-funcional-y-tecnico.md
docs/03-modulos/M15-persistencia-remota.md
docs/03-modulos/M15-evolucion-egreso-gastos-y-ayuda.md
docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar exactamente:

```text
M15 BLOQUE 4 CERRADO LOCALMENTE
M15 CIERRE TÉCNICO LOCAL COMPLETADO
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
M15 SMOKE FUNCIONAL REMOTO PENDIENTE EXTERNO
M15 CIERRE OFICIAL PENDIENTE
M14 MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
M14 CIERRE OFICIAL PENDIENTE
M13 CIERRE OFICIAL PENDIENTE
M12 CIERRE OFICIAL PENDIENTE
GITHUB ANDROID CI PENDIENTE
```

No afirmar cierre oficial.

## Paso 13 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- migraciones 001–052 intactas;
- sin 053/054;
- sin secretos;
- sin binarios;
- CI no modificado;
- M16 no iniciado;
- documentación coherente;
- pruebas automáticas no ejecutadas.

## Paso 14 — Git

Commit único:

```text
feat(m15): finalize foster care operations
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Archivos modificados.
3. Métricas.
4. Rango y timezone.
5. Privacidad.
6. Logs.
7. Integración M06.
8. Fallback M06.
9. Estados terminales.
10. Idempotencia.
11. Conflictos.
12. Capacidad.
13. Dashboard.
14. Rutas.
15. Smoke preparado.
16. DataProvider.
17. Errores.
18. Revisión manual.
19. Compilación.
20. Pruebas automáticas no ejecutadas.
21. Documentación.
22. Migraciones.
23. Limitaciones.
24. Pendientes.
25. Estado técnico local.
26. Propuesta del siguiente módulo sin iniciarlo.
27. SHA.
28. Push.
29. `git status -sb`.

## Estado final permitido

```text
M15 BLOQUE 4 CERRADO LOCALMENTE
M15 CIERRE TÉCNICO LOCAL COMPLETADO
M10/M08 SON LA BASE AUTORITATIVA
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
M15 SMOKE FUNCIONAL REMOTO PENDIENTE EXTERNO
M15 CIERRE OFICIAL PENDIENTE
```
