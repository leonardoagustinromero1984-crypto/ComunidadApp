# Cursor — M12 Cierre Final

## Gate obligatorio

Ejecutar este prompt únicamente después de confirmar:

```text
M12 BLOQUE 3 SMOKE FUNCIONAL PASS
M12 LISTO PARA CIERRE FINAL
```

No ejecutar con el smoke pendiente o con algún FALLO.

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama: `main`.
- HEAD mínimo: `d3bd12ed25af2892c0dfafe0ce1f44b2ac76ca18`.
- Working tree limpio y alineado con `origin/main`.
- Migraciones 040–047 aplicadas y validadas en Supabase de pruebas.
- 046 validación remota PASS.
- 047 validación estructural 13/13 PASS.
- Smoke funcional de agenda y turnos PASS.
- Hotfix de autenticación validado manualmente.
- M12 Bloques 1–4 cerrados localmente.

## Objetivo

Realizar el cierre técnico final de **M12 — Veterinarias**.

Este bloque es exclusivamente de:

- auditoría final;
- correcciones locales menores comprobadas;
- regresión focalizada;
- compilación;
- matriz funcional;
- documentación;
- commit y push final.

No agregar funcionalidad nueva.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 040–047.
- No crear migración 048.
- No aplicar SQL remotamente.
- Si se detecta un bloqueo SQL:
  - detener el cierre;
  - documentar;
  - proponer un bloque correctivo separado con 048;
  - no declarar M12 cerrado.
- No iniciar M13.
- No generar APK.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- No implementar pagos ni historia clínica.
- No afirmar push/cron real de M06 si sigue siendo externo.
- No usar Supabase real en tests.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Si hay cambios locales ajenos:

- no usar reset, restore ni clean;
- informar;
- detenerse.

## Paso 2 — Auditoría integral

Auditar M12 completo:

- dominio;
- validadores;
- errores;
- permisos;
- fakes;
- repositorios;
- Supabase data sources;
- DataProvider;
- ViewModels;
- UI;
- navegación;
- media M05;
- autoridad M03/M04/M08;
- hooks M06;
- auditoría M07;
- perfiles;
- profesionales;
- especialidades;
- servicios;
- horarios;
- agenda;
- disponibilidad;
- excepciones;
- slots;
- turnos;
- historial;
- recordatorios;
- métricas;
- privacidad;
- auth hotfix;
- migraciones 046/047;
- documentación;
- pruebas.

Clasificar cada hallazgo:

```text
PASS
CORREGIBLE_LOCAL
BLOQUEANTE_SQL
PENDIENTE_EXTERNO
FUERA_DE_ALCANCE
```

## Paso 3 — Matriz funcional final

Verificar y documentar:

### Bloque 1
- directorio;
- detalle;
- filtros;
- borradores;
- dominio y fakes.

### Bloque 2
- perfiles;
- profesionales;
- servicios;
- horarios;
- verificación;
- RLS/RPC.

### Bloque 3
- settings;
- reglas;
- excepciones;
- slots;
- turnos;
- cupos;
- autoridad M08;
- historial;
- privacidad.

### Bloque 4
- recordatorios preparados;
- idempotencia;
- zonas horarias;
- vencimientos;
- reintentos;
- UI de seguimiento;
- métricas sin PII.

### Integraciones
- M03;
- M04;
- M05;
- M06;
- M07;
- M08;
- autenticación.

## Paso 4 — Seguridad estática

Confirmar:

- sin service_role Android;
- sin claves/tokens versionados;
- RLS/RPC como autoridad;
- cero DML directo esperado;
- actor desde auth.uid();
- helpers internos protegidos;
- media M05 segura;
- contacto opt-in;
- notas privadas;
- sin pagos;
- sin historia clínica;
- sin modificación de 040–047;
- sin migración 048.

## Paso 5 — Correcciones permitidas

Corregir únicamente defectos locales reales y pequeños encontrados durante la auditoría:

- mappers;
- wiring;
- navegación;
- estados UI;
- mensajes;
- guardas;
- documentación;
- tests.

No hacer refactors masivos.

No corregir SQL dentro de 046/047.

## Paso 6 — Guardas de cierre

Crear:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M12FinalClosureGuardsTest.kt
```

Cubrir al menos:

1. bloques 1–4 presentes;
2. rutas principales registradas;
3. repositorios en DataProvider;
4. fakes conservados;
5. implementaciones Supabase existentes;
6. permisos veterinary.*;
7. migración 046 presente;
8. migración 047 presente;
9. 040–047 intactas;
10. sin migración 048;
11. sin service_role;
12. sin pagos;
13. sin historia clínica;
14. sin keys/tokens;
15. contactos opt-in;
16. notas privadas;
17. actor auth.uid();
18. helpers protegidos;
19. auth hotfix preservado;
20. smoke documentado como PASS;
21. push M06 externo documentado;
22. cron/scheduler externo documentado;
23. métricas sin PII;
24. legacy service_profiles/bookings preservado.

## Paso 7 — Pruebas focalizadas

Ejecutar exclusivamente suites focalizadas de:

- M12 Bloque 1;
- M12 persistencia/Bloque 2;
- M12 agenda/Bloque 3;
- M12 recordatorios/endurecimiento/Bloque 4;
- M12 final closure guards;
- auth hotfix guards;
- M03/M04/M05/M08 regresión mínima necesaria.

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
docs/03-modulos/M12-veterinarias.md
docs/02-arquitectura/M12-agenda-disponibilidad-turnos.md
docs/02-arquitectura/M12-recordatorios-endurecimiento-seguimiento.md
```

Crear:

```text
docs/03-modulos/M12-cierre-final.md
docs/05-operacion/M12-validacion-final.md
docs/02-arquitectura/M12-matriz-funcional-final.md
```

Registrar:

- estado anterior;
- commits principales;
- migraciones;
- seguridad;
- matriz funcional;
- smoke;
- pruebas;
- compilación;
- limitaciones externas;
- criterios de reapertura;
- pendientes no bloqueantes;
- estado final.

Pendientes externos no bloqueantes posibles:

- push real M06;
- scheduler/cron;
- APK general de release;
- promoción de ambientes;
- chat;
- reputación;
- pagos;
- historia clínica.

## Paso 10 — Git

Revisar:

```powershell
git status
git diff --stat
git diff --check
```

Agregar únicamente código, tests y docs del cierre.

Commit único:

```powershell
git commit -m "chore(m12): finalize veterinary module"
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría.
3. Matriz funcional.
4. Hallazgos PASS.
5. Correcciones locales.
6. Bloqueantes SQL.
7. Seguridad.
8. Integraciones.
9. UI/nav.
10. Repositorios/fakes.
11. Guardas.
12. Suites.
13. Cantidad de pruebas.
14. Compilación.
15. Docs.
16. Limitaciones.
17. Pendientes externos.
18. SHA.
19. Push.
20. git status -sb.
21. Estado final exacto.

Estado permitido:

```text
M12 CERRADO
```

o, si aparece un bloqueo:

```text
M12 CIERRE FINAL BLOQUEADO
```

No iniciar M13.
