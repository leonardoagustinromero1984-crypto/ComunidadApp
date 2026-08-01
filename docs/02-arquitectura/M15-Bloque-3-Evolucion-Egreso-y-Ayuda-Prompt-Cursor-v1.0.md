# Cursor — M15 Bloque 3: evolución, egreso, gastos y ayuda

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `a481b7b4512410fac64517c8f30343a3f39897d8`.
- `origin/main` alineada.
- M15 Bloque 1: CERRADO LOCALMENTE.
- M15 Bloque 2: CERRADO LOCALMENTE.
- M10 es la persistencia remota autoritativa de M15.
- M15 usa adaptadores sobre tablas/RPC M10.
- Migraciones M10 040/041: preservadas.
- Migraciones existentes: 001–052.
- Migración 053: inexistente.
- Compilación Kotlin del Bloque 2: PASS.
- Pruebas automáticas: NO EJECUTADAS por decisión del usuario.
- Validación funcional M15: MANUAL PENDIENTE.
- M14 migración 052 y cierre oficial: pendientes.
- M13 y M12 cierres oficiales: pendientes.
- GitHub Android CI: pendiente.

## Objetivo

Implementar **M15 Bloque 3 — Evolución, egreso, gastos y ayuda**, manteniendo M10 como única persistencia autoritativa.

El bloque debe completar, según la infraestructura real disponible:

1. seguimiento de evolución del alojamiento;
2. egreso completo;
3. actualización segura de custodia/responsabilidad temporal con M08;
4. gastos asociados al placement;
5. solicitudes de ayuda vinculadas al placement;
6. UI, repositorios y documentación;
7. validación manual y compilación Kotlin.

## Principio arquitectónico

```text
M10 = persistencia autoritativa
M15 = capa funcional y de producto
NO DUPLICAR TABLAS
NO DUPLICAR PLACEMENTS
NO COPIAR DATOS
```

## Modo ahorro obligatorio

- Trabajar en un chat nuevo.
- No releer todo el repositorio.
- No usar subagentes.
- No usar tareas paralelas.
- No usar Max Mode.
- Revisar únicamente M10, M15, M08 y dependencias directas.
- No ejecutar pruebas automáticas.
- Ejecutar una única compilación Kotlin final.
- No generar APK.
- No corregir GitHub CI.
- No aplicar SQL remotamente.
- Un único commit y push.
- No iniciar M16.

## Reglas generales

- Trabajar directamente sobre `main`.
- Sin ramas, backups ni checkpoints.
- Sin commits intermedios.
- No modificar migraciones 001–052.
- No crear migración 053 por defecto.
- Crear 053 únicamente si la auditoría demuestra una brecha real y segura.
- No crear tablas paralelas de hogares, solicitudes o placements.
- No reemplazar M10.
- No declarar M12, M13, M14 o M15 cerrados oficialmente.
- No afirmar pruebas automáticas PASS.
- Validación funcional manual.
- No incluir secretos.
- No implementar pagos reales, billetera, facturación ni cobro.
- No implementar chat.
- No exponer datos privados del hogar o de la mascota.

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
docs/03-modulos/M15-matriz-reconciliacion-M10.md
docs/02-arquitectura/M15-Bloque-2-validacion.md
docs/03-modulos/M10-hogares-de-transito.md
docs/03-modulos/M08-mascotas-y-responsables.md
supabase/migrations/040*
supabase/migrations/041*
```

Auditar solamente:

```text
app/src/main/java/**/*M15*
app/src/main/java/**/*Foster*
app/src/main/java/**/*foster*
app/src/main/java/**/*M08*
app/src/main/java/**/m10/
app/src/main/java/**/m08/
supabase/migrations/040*
supabase/migrations/041*
```

Buscar:

```powershell
rg -n --hidden -S "evolution|evolucion|progress|follow.?up|discharge|egreso|checkout|expense|gasto|help|ayuda|placement|custody|responsibility|foster" app supabase docs
```

## Paso 3 — Auditoría de capacidad real

Determinar exactamente si M10 040/041 ya soporta:

- notas o eventos de evolución;
- cambios de estado del placement;
- egreso;
- motivo de egreso;
- fecha de egreso;
- historial;
- gastos;
- comprobantes;
- solicitudes de ayuda;
- autoridad del hogar;
- autoridad de organización;
- vínculo con mascota M08;
- actualización de responsabilidad/custodia;
- auditoría M07;
- hooks M06.

Crear:

```text
docs/03-modulos/M15-matriz-Bloque-3-M10-M08.md
```

Clasificar cada capacidad:

```text
REUTILIZAR
ADAPTAR
EXTENDER_CON_053
PENDIENTE_EXTERNO
FUERA_DE_ALCANCE
BLOQUEANTE
```

## Paso 4 — Gate de migración

### Caso A — M10/M08 cubren todo

Si las tablas y RPC existentes cubren evolución, egreso, gastos y ayuda:

- no crear migración 053;
- implementar adaptadores y UI;
- mantener highest migration en 052;
- no modificar CI.

### Caso B — Brecha menor y segura

Crear exactamente:

```text
supabase/migrations/053_m15_foster_evolution_discharge_and_support.sql
```

Solo se permite extender la persistencia M10 existente mediante:

- tabla de eventos/evolución vinculada a `foster_placements`, si no existe;
- tabla de gastos vinculada a `foster_placements`, si no existe;
- tabla de solicitudes de ayuda vinculada a `foster_placements`, si no existe;
- columnas opcionales de egreso faltantes;
- índices;
- constraints;
- RPC `m15_*` o wrappers seguros sobre M10/M08;
- permisos;
- RLS;
- auditoría.

La 053 no puede:

- duplicar hogares;
- duplicar solicitudes;
- duplicar placements;
- copiar datos M10;
- borrar o renombrar objetos;
- modificar destructivamente 040/041;
- transferir responsabilidad M08 sin reglas explícitas;
- agregar pagos reales.

Si se crea 053:

- no aplicarla remotamente;
- actualizar highest migration a 053;
- crear guía de aplicación y validación;
- toda corrección posterior comenzará en 054.

### Caso C — Reconciliación insegura

Detenerse:

```text
M15 BLOQUE 3 BLOQUEADO — EVOLUCIÓN/EGRESO/CUSTODIA NO RESUELTOS
```

No inventar una transferencia de responsabilidad.

## Paso 5 — Evolución del placement

Implementar o adaptar:

### Modelo

```text
M15PlacementEvolution
```

Campos mínimos, ajustados al contrato real:

- id;
- placementId;
- eventType;
- summary;
- privateNote opcional;
- healthAlert boolean sin historia clínica;
- mediaRefs seguras opcionales;
- createdBy;
- createdAt.

Tipos iniciales:

```text
GENERAL_UPDATE
ADAPTATION
BEHAVIOR
FEEDING
HEALTH_ALERT
VISIT
INCIDENT
OTHER
```

Reglas:

- solo placement RESERVED o ACTIVE;
- append-only;
- no editar o borrar eventos históricos;
- no incluir diagnóstico, receta o historia clínica;
- media M05 segura;
- notas privadas solo para actores autorizados;
- proyección pública sin notas privadas.

## Paso 6 — Egreso completo

Implementar flujo de egreso:

```text
RESERVED -> CANCELLED
ACTIVE -> COMPLETED
ACTIVE -> INTERRUPTED
```

Motivos tipificados:

```text
RETURNED_TO_RESPONSIBLE
ADOPTED
TRANSFERRED_TO_ANOTHER_FOSTER
TRANSFERRED_TO_SHELTER
VETERINARY_CARE
INCOMPATIBILITY
EMERGENCY
OTHER
```

Reglas:

- `SELECT ... FOR UPDATE` si existe SQL nuevo;
- idempotencia;
- estado final no se reabre;
- fecha y actor server-side cuando sea remoto;
- motivo obligatorio;
- nota privada opcional;
- actualización de capacidad/disponibilidad;
- historial append-only;
- auditoría M07;
- eventos M06 preparados.

## Paso 7 — Custodia y responsabilidad M08

Auditar el modelo real M08 antes de editar.

Separar:

```text
responsabilidad legal/principal
custodia temporal por tránsito
```

Reglas:

- el egreso no transfiere automáticamente responsabilidad legal;
- la custodia temporal debe vincularse al placement;
- durante ACTIVE, el cuidador puede recibir autoridad operativa limitada;
- al finalizar, esa autoridad temporal se revoca;
- la responsabilidad principal M08 permanece salvo flujo explícito ya existente;
- adopción se resuelve por M09, no por M15;
- traslado a otro hogar crea o enlaza un nuevo placement, no reescribe el histórico;
- no confiar en actor/owner enviados por UI.

Si M08 no soporta custodia temporal y es necesaria una extensión, debe justificarse dentro de la migración 053.

## Paso 8 — Gastos

Implementar registro de gastos, sin pagos:

### Modelo

```text
M15PlacementExpense
```

Campos mínimos:

- id;
- placementId;
- category;
- amount;
- currency;
- occurredAt;
- description;
- receiptMediaRef opcional;
- status;
- createdBy;
- createdAt.

Categorías:

```text
FOOD
VETERINARY
MEDICATION
TRANSPORT
HYGIENE
ACCESSORIES
OTHER
```

Estados:

```text
RECORDED
SUBMITTED_FOR_REVIEW
APPROVED
REJECTED
CANCELLED
```

Reglas:

- monto positivo;
- moneda ISO permitida;
- comprobante M05 seguro;
- sin datos de tarjeta o cuenta bancaria;
- no implica reembolso ni pago;
- privacidad por placement y organización;
- estados finales protegidos;
- métricas agregadas sin PII.

## Paso 9 — Solicitudes de ayuda

Implementar:

```text
M15PlacementHelpRequest
```

Tipos iniciales:

```text
FOOD
VETERINARY
TRANSPORT
SUPPLIES
TEMPORARY_REPLACEMENT
EMERGENCY
OTHER
```

Estados:

```text
OPEN
IN_PROGRESS
RESOLVED
CANCELLED
EXPIRED
```

Reglas:

- vinculada a placement;
- solo placement RESERVED o ACTIVE;
- prioridad tipificada;
- descripción segura;
- sin publicación de dirección/contacto;
- actor y autoridad derivados;
- resolución auditada;
- no crear chat;
- no crear pagos;
- M06 preparado para notificación futura.

## Paso 10 — Repositorios y adaptadores

Crear o completar:

- repositorio de evolución;
- repositorio de egreso;
- repositorio de gastos;
- repositorio de ayuda;
- DTO/mappers;
- adaptadores M15 sobre M10/M08;
- Supabase solo si la operación existe;
- mocks con paridad contractual;
- DataProvider.

Cuando `useSupabase = true`:

- usar M10/M08 o RPC 053;
- nunca usar un store persistente paralelo M15.

Cuando `useSupabase = false`:

- usar mocks M15.

## Paso 11 — Autoridad y privacidad

### Autoridad

Permitir según contrato real:

- cuidador del hogar;
- responsable M08;
- organización solicitante;
- gestor de organización;
- moderador.

Cada acción debe verificar:

- placement;
- relación con hogar;
- relación con mascota;
- estado;
- permiso;
- pertenencia organizacional.

### Privacidad

Nunca exponer públicamente:

- dirección exacta;
- teléfono;
- correo;
- coordenadas;
- notas privadas;
- gastos detallados;
- comprobantes;
- IDs internos;
- identidad completa del cuidador;
- datos clínicos.

## Paso 12 — UI y navegación

Agregar o completar rutas M15:

```text
m15/placements/{placementId}
m15/placements/{placementId}/evolution
m15/placements/{placementId}/evolution/new
m15/placements/{placementId}/discharge
m15/placements/{placementId}/expenses
m15/placements/{placementId}/expenses/new
m15/placements/{placementId}/help
m15/placements/{placementId}/help/new
```

Implementar:

- detalle del alojamiento;
- timeline de evolución;
- alta de evolución;
- egreso;
- gastos;
- solicitud de ayuda;
- estados vacíos/carga/error;
- privacidad;
- fallback remoto;
- próxima acción;
- terminales;
- conflictos.

Mantener rutas `foster_*` y `m15/*`.

## Paso 13 — Errores

Agregar o reutilizar equivalentes:

```text
M15_EVOLUTION_NOT_ALLOWED
M15_EVOLUTION_NOT_FOUND
M15_DISCHARGE_NOT_ALLOWED
M15_DISCHARGE_ALREADY_APPLIED
M15_INVALID_DISCHARGE_REASON
M15_TEMPORARY_CUSTODY_NOT_ALLOWED
M15_EXPENSE_INVALID_AMOUNT
M15_EXPENSE_INVALID_CURRENCY
M15_EXPENSE_NOT_FOUND
M15_HELP_REQUEST_NOT_ALLOWED
M15_HELP_REQUEST_NOT_FOUND
M15_HELP_REQUEST_ALREADY_FINAL
M15_MEDIA_REFERENCE_INVALID
M15_CONFLICT
M15_REMOTE_VALIDATION_PENDING
```

No filtrar existencia de recursos ajenos.

## Paso 14 — M05, M06 y M07

M05:

- media segura;
- comprobantes privados;
- sin URLs arbitrarias.

M06:

Preparar:

```text
M15_EVOLUTION_ADDED
M15_PLACEMENT_COMPLETED
M15_PLACEMENT_INTERRUPTED
M15_EXPENSE_RECORDED
M15_HELP_REQUEST_OPENED
M15_HELP_REQUEST_RESOLVED
```

No afirmar push real.

M07:

- auditoría best-effort;
- sin PII;
- sin romper catálogo canónico.

## Paso 15 — Revisión manual

No ejecutar pruebas automáticas.

Revisar manualmente:

1. evolución append-only;
2. estados de placement;
3. egreso idempotente;
4. capacidad liberada;
5. custodia temporal;
6. responsabilidad principal preservada;
7. gastos sin pagos;
8. ayuda sin chat;
9. autoridad;
10. privacidad;
11. M05;
12. M06/M07;
13. DataProvider;
14. rutas legacy;
15. ausencia de duplicación;
16. migraciones 001–052 intactas;
17. 053 solo si se justificó;
18. sin 054;
19. sin secretos.

## Paso 16 — Compilación

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

## Paso 17 — Documentación

Crear:

```text
docs/03-modulos/M15-evolucion-egreso-gastos-y-ayuda.md
docs/03-modulos/M15-matriz-Bloque-3-M10-M08.md
docs/02-arquitectura/M15-Bloque-3-validacion.md
```

Si se crea 053:

```text
docs/05-operacion/M15-aplicacion-y-validacion-migracion-053.md
```

Actualizar:

```text
docs/03-modulos/M15-hogares-de-transito.md
docs/03-modulos/M15-plan-funcional-y-tecnico.md
docs/03-modulos/M15-persistencia-remota.md
docs/03-modulos/M15-matriz-reconciliacion-M10.md
docs/02-arquitectura/ADR-015-M15-hogares-de-transito.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar:

- fuente autoritativa;
- evolución;
- egreso;
- custodia;
- gastos;
- ayuda;
- existencia o ausencia de 053;
- compilación;
- pruebas no ejecutadas;
- validación manual pendiente;
- limitaciones;
- smoke pendiente;
- propuesta exacta del Bloque 4.

## Paso 18 — Verificación final

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

## Paso 19 — Git

Commit único:

```text
feat(m15): add foster placement lifecycle
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Auditoría M10/M08.
3. Matriz de capacidad.
4. Caso A, B o C.
5. Migración 053 creada o no.
6. Evolución.
7. Egreso.
8. Motivos.
9. Custodia temporal.
10. Responsabilidad M08.
11. Gastos.
12. Solicitudes de ayuda.
13. Autoridad.
14. Privacidad.
15. DTO/mappers.
16. Repositorios.
17. DataProvider.
18. UI y rutas.
19. Errores.
20. M05/M06/M07.
21. Revisión manual.
22. Compilación.
23. Pruebas no ejecutadas.
24. Documentación.
25. Migraciones.
26. Limitaciones.
27. Pendientes.
28. Propuesta exacta del Bloque 4.
29. SHA.
30. Push.
31. `git status -sb`.

## Estado final permitido — sin 053

```text
M15 BLOQUE 3 CERRADO LOCALMENTE
M10/M08 SON LA BASE AUTORITATIVA
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Estado final permitido — con 053

```text
M15 BLOQUE 3 CERRADO LOCALMENTE
M10/M08 SON LA BASE AUTORITATIVA
MIGRACIÓN 053 PENDIENTE DE APLICACIÓN REMOTA
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Estado final permitido — bloqueado

```text
M15 BLOQUE 3 BLOQUEADO — EVOLUCIÓN/EGRESO/CUSTODIA NO RESUELTOS
```
