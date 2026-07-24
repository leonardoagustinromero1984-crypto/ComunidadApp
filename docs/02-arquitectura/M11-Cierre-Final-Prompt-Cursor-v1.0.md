# Cursor — M11 Cierre Final: Refugios

## Gate de inicio obligatorio

Ejecutar únicamente después de confirmar:

```text
M11 BLOQUE 3 REMOTO PASS
045 aplicada en Supabase de pruebas
3 tablas con RLS activo
25 RPC validadas
Sin escritura directa del cliente
Smoke de urgencias aprobado
Smoke de eventos/waitlist aprobado
Métricas y CSV sin PII aprobados
```

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama: `main`.
- HEAD mínimo: `353627fb713a5cf7f9b1ca534319b246053b0b62`.
- Migraciones 040–045 aplicadas y validadas en Supabase de pruebas.
- Working tree limpio y alineado con `origin/main`.

## Objetivo

Realizar el **cierre final técnico y funcional del módulo M11 — Refugios**, sin agregar nuevas funcionalidades.

El cierre debe:

1. Auditar integralmente M11 Bloques 1, 2 y 3.
2. Corregir únicamente defectos locales reales que no requieran cambios de base de datos.
3. Validar arquitectura, permisos, estados, navegación, repositorios, fakes, UI y documentación.
4. Ejecutar una regresión focalizada completa del módulo.
5. Dejar documentación final y trazabilidad del cierre.
6. Crear un único commit y push.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y un único push.
- No modificar migraciones 040–045.
- No crear migración 046 en este cierre.
- Si se detecta un defecto que requiere SQL:
  - no modificar migraciones aplicadas;
  - no improvisar una corrección;
  - detener el cierre;
  - documentar el hallazgo y proponer un bloque correctivo 046 separado.
- No aplicar SQL remoto.
- No iniciar Supabase local.
- No ejecutar emulador.
- No generar APK.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- No implementar push real, chat, reputación, pagos, IA ni nuevas funciones.
- No reescribir código estable sin necesidad.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Si existen cambios locales:

- identificar si son documentos intencionales del cierre;
- no usar `reset`, `restore` ni `clean`;
- detenerse ante cambios ajenos.

## Paso 2 — Auditoría integral

Revisar todos los archivos M11:

```text
M11
Shelter
shelter_
m11_
refugio
campaña
insumo
aporte
urgencia
evento
inscripción
reporte
```

Cubrir:

- dominio;
- repositorios;
- implementaciones Supabase;
- fakes;
- DataProvider/DI;
- ViewModels;
- pantallas;
- navegación;
- permisos;
- errores;
- hooks M06;
- auditoría M07;
- referencias M05;
- integraciones M03/M08/M09/M10;
- migraciones 042–045;
- documentación;
- tests.

Clasificar hallazgos:

```text
PASS
CORREGIBLE_LOCAL
BLOQUEANTE_SQL
PENDIENTE_EXTERNO
FUERA_DE_ALCANCE
```

No comenzar correcciones hasta terminar el inventario.

## Paso 3 — Matriz funcional

Verificar que M11 cubra:

### Bloque 1

- perfil de refugio;
- organización M03;
- capacidad;
- estado AVAILABLE/LIMITED/FULL/UNAVAILABLE;
- alojamientos de mascotas M08;
- ingreso, reserva, liberación y cierre;
- voluntarios;
- dashboard;
- adopción M09;
- retorno de tránsito M10.

### Bloque 2

- campañas PUBLIC/INTERNAL;
- draft, active, paused, completed, cancelled;
- actualizaciones;
- pedidos de insumos;
- cantidades solicitadas, comprometidas y recibidas;
- aportes;
- recepción parcial/total;
- estado FULFILLED;
- evidencia segura M05;
- hardening 044.

### Bloque 3

- urgencias;
- severidad y vigencia;
- resolución;
- eventos gratuitos;
- cupos y waitlist;
- inscripciones y asistencia;
- métricas agregadas;
- exportación CSV sin PII.

Verificar que cada operación tenga:

- modelo;
- repositorio;
- implementación;
- fake;
- ViewModel;
- UI o integración;
- permiso;
- error tipado;
- test.

## Paso 4 — Seguridad

Validar estáticamente:

- autoridad por M03, no por `AccountType` ni `active_modules`;
- voluntarios sin privilegios administrativos automáticos;
- actor sensible derivado de sesión/auth;
- sin service role en Android;
- RLS en tablas M11;
- DML directo denegado donde corresponde;
- RPC cliente con grants mínimos;
- helpers internos no ejecutables;
- `SECURITY DEFINER` con `search_path = public`;
- proyecciones públicas sin PII;
- evidencia solo `m05://` o `file_asset:`;
- sin CBU, alias, tarjetas, pagos ni checkout;
- CSV sin información privada.

No modificar SQL durante este cierre.

## Paso 5 — Arquitectura y código

Corregir solo defectos locales comprobados, por ejemplo:

- rutas rotas;
- ViewModel con loading infinito;
- doble envío;
- error no tipado;
- fake que no persiste;
- enum desconocido que rompe;
- navegación sin argumento;
- mapeo incorrecto;
- texto o documentación desactualizada;
- dead code claramente M11;
- placeholder que impida un flujo cerrado.

No hacer refactors cosméticos masivos.

## Paso 6 — Guardas finales

Crear:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M11FinalClosureGuardsTest.kt
```

Cubrir al menos:

1. migraciones 042–045 presentes;
2. migraciones aplicadas tratadas como inmutables;
3. tablas de los tres bloques;
4. RLS declarado;
5. RPC con search_path;
6. hardening 044;
7. sin service_role Android;
8. sin URLs públicas persistidas;
9. sin datos bancarios;
10. sin pagos;
11. rutas M11 registradas;
12. dashboard con accesos de los tres bloques;
13. repositorios registrados;
14. fakes registrados;
15. permisos M11 presentes;
16. errores tipados presentes;
17. exportación sin campos PII;
18. no placeholders bloqueantes;
19. documentación final presente;
20. sin migración 046.

## Paso 7 — Regresión focalizada

Identificar los nombres reales y ejecutar las suites focalizadas de:

- M11 Bloque 1;
- M11 Bloque 2;
- hardening 044;
- M11 Bloque 3;
- guardas 042–045;
- integración M08;
- cierre adopción M09;
- tránsito M10;
- referencias seguras M05;
- cierre final M11.

Preferir este comando ajustando únicamente nombres reales:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M11ShelterOperationsCoreTest" `
  --tests "*M11ShelterCampaignsAndAidTest" `
  --tests "*M11CampaignSecurityGuardsTest" `
  --tests "*M11ShelterEmergenciesEventsReportsTest" `
  --tests "*M11FinalClosureGuardsTest" `
  --tests "*M08IntegrationRegressionTest" `
  --tests "*M09AdoptionCompletionTest" `
  --tests "*M10FosterCareManagementTest"
```

Agregar únicamente la suite focalizada real de M05 si no está ya cubierta.

No ejecutar toda la suite.

## Paso 8 — Compilación

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

## Paso 9 — Documentación

Actualizar:

```text
docs/03-modulos/M11-refugios.md
docs/02-arquitectura/M11-campanas-insumos-red-ayuda.md
docs/02-arquitectura/M11-urgencias-eventos-reportes.md
```

Crear:

```text
docs/03-modulos/M11-cierre-final.md
docs/05-operacion/M11-validacion-final.md
docs/02-arquitectura/M11-matriz-funcional-final.md
```

Registrar:

- alcance final;
- arquitectura;
- tablas 042–045;
- RLS/RPC/permisos;
- flujos de los tres bloques;
- integraciones;
- pruebas;
- compilación;
- smoke remoto 042–045;
- limitaciones;
- pendientes externos;
- criterio de reapertura.

Pendientes externos que no bloquean M11:

```text
M06 push real
chat
reputación
APK/manual smoke general
promoción de migraciones a otros ambientes
```

No presentarlos como funcionalidad faltante de M11 cuando pertenecen a otros módulos.

## Paso 10 — Criterio de cierre

M11 puede marcarse `CERRADO` solo si:

- no existen hallazgos `BLOQUEANTE_SQL`;
- todas las pruebas focalizadas pasan;
- compileLocalDebugKotlin pasa;
- no existen placeholders bloqueantes;
- navegación principal está conectada;
- documentación final está completa;
- working tree final queda limpio.

Si algún criterio falla, marcar:

```text
M11 CIERRE FINAL BLOQUEADO
```

y no hacer commit de cierre falso.

## Paso 11 — Git

Revisar:

```powershell
git status
git diff --stat
git diff --check
```

Agregar únicamente archivos relacionados con M11:

```powershell
git add app docs
git diff --cached --stat
git diff --cached --check
```

No agregar APK, secretos, logs, temporales ni SQL nuevo.

Commit único:

```powershell
git commit -m "chore(m11): finalize shelter module"
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Inventario auditado.
3. Matriz funcional.
4. Hallazgos PASS.
5. Correcciones locales.
6. Bloqueantes SQL encontrados o ausencia.
7. Seguridad.
8. Integraciones.
9. Navegación/UI.
10. Repositorios/fakes.
11. Guardas finales.
12. Suites ejecutadas.
13. Cantidad de pruebas aprobadas.
14. Compilación.
15. Documentación.
16. Limitaciones.
17. Pendientes externos.
18. SHA.
19. Push.
20. `git status -sb`.
21. Estado final exacto:

```text
M11 CERRADO
```

o:

```text
M11 CIERRE FINAL BLOQUEADO
```

No aplicar SQL.
No crear 046.
No generar APK.
No iniciar otro módulo.
