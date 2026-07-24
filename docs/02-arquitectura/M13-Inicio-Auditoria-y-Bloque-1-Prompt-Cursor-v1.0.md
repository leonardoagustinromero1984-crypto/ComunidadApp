# Cursor — M13 inicio, auditoría canónica y Bloque 1

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama de trabajo: `main`.
- HEAD mínimo: `c3c4fce8b440b938042bb3df3e5338dc0c5c9053`.
- `origin/main` alineada.
- Android CI remoto: PASS.
- M12 cierre técnico local: COMPLETADO.
- M12 smoke funcional: PENDIENTE EXTERNO.
- M12 cierre oficial: PENDIENTE.
- M13: NO INICIADO.

## Decisión operativa

El usuario autoriza comenzar M13 aunque el smoke funcional de M12 continúe diferido.

Esto no permite declarar M12 cerrado ni borrar sus pendientes.

## Objetivo

Identificar el alcance canónico exacto de M13 desde la documentación existente del repositorio y, únicamente después de confirmarlo, implementar su primer bloque funcional local.

No inventar el nombre ni el alcance de M13.

## Reglas de trabajo

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push cuando el bloque esté completo.
- Mantener cambios incrementales.
- No reescribir módulos existentes que ya funcionan.
- No modificar migraciones 001–047.
- No crear migración 048 por defecto.
- No aplicar SQL remotamente.
- No generar APK.
- No usar emulador.
- No ejecutar lint, JaCoCo ni toda la suite repetidamente.
- Ejecutar pruebas focalizadas durante el desarrollo.
- Ejecutar una única compilación Kotlin final.
- No declarar M12 cerrado.
- Preservar el hotfix de autenticación y el CI verde.
- No introducir pagos, historia clínica u otras capacidades fuera del alcance canónico de M13.

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

Si existen cambios locales ajenos:

- no ejecutar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Identificación canónica de M13

Leer primero, si existen:

```text
docs/01-producto/D01-Modulos-y-Orden.md
docs/01-producto/
docs/03-modulos/
docs/02-arquitectura/
README.md
```

Buscar todas las referencias relevantes:

```powershell
rg -n --hidden -S "M13|módulo 13|modulo 13|próximo módulo|proximo modulo" docs README.md .
```

Determinar:

1. nombre exacto de M13;
2. propósito;
3. actores;
4. funcionalidades incluidas;
5. funcionalidades excluidas;
6. dependencias con M01–M12;
7. entidades o tablas previstas;
8. permisos previstos;
9. rutas y pantallas previstas;
10. orden de bloques recomendado.

### Gate obligatorio

Si el nombre o alcance de M13:

- no aparece;
- es ambiguo;
- contradice más de un documento;

detenerse antes de modificar código.

En ese caso entregar:

```text
M13 BLOQUEADO — ALCANCE CANÓNICO AUSENTE O CONTRADICTORIO
```

e indicar los archivos revisados y la decisión que debe tomar el usuario.

No inventar una definición.

## Paso 3 — Auditoría técnica del módulo identificado

Una vez identificado M13, auditar el repositorio para detectar:

- código existente reutilizable;
- modelos legacy;
- rutas o pantallas parciales;
- tablas o RPC relacionadas;
- permisos relacionados;
- contratos M06/M07 reutilizables;
- referencias seguras M05;
- autoridad M03/M04/M08 cuando corresponda;
- duplicación con M09–M12;
- riesgos de privacidad y seguridad;
- dependencias externas.

Clasificar cada hallazgo:

```text
REUTILIZABLE
COMPATIBLE
REQUIERE_ADAPTACIÓN
BLOQUEANTE
FUERA_DE_ALCANCE
```

## Paso 4 — Definir Bloque 1

El Bloque 1 debe ser el bloque funcional local mínimo pero completo del módulo identificado.

Debe incluir, según corresponda al alcance canónico:

- modelos de dominio;
- enums y estados;
- validadores;
- errores tipificados;
- contratos de repositorio;
- fakes persistentes en memoria;
- filtros y consultas locales;
- ViewModels;
- UI y navegación básicas;
- permisos como constantes de dominio;
- auditoría M07 preparada;
- media M05 por referencia segura;
- pruebas focalizadas.

### Restricciones del Bloque 1

- Sin migración SQL.
- Sin Supabase real.
- Sin llamadas de red en tests.
- Sin secretos.
- Sin push real.
- Sin pagos.
- Sin funcionalidades de bloques posteriores.
- Preservar legacy compatible.
- No crear arquitectura duplicada si ya existe una base reutilizable.

## Paso 5 — Documentación

Crear:

```text
docs/03-modulos/M13-auditoria-inicial.md
docs/03-modulos/M13-plan-funcional-y-tecnico.md
docs/02-arquitectura/M13-Bloque-1-validacion.md
```

Actualizar el documento canónico de módulos únicamente si M13 ya está definido allí y solo para registrar estado:

```text
NO INICIADO → BLOQUE 1 CERRADO LOCALMENTE
```

No redefinir el roadmap sin evidencia documental.

La documentación debe incluir:

- nombre exacto del módulo;
- fuente documental que lo define;
- estado anterior;
- objetivo;
- alcance;
- exclusiones;
- dependencias;
- auditoría;
- decisiones;
- arquitectura;
- archivos;
- pruebas;
- riesgos;
- pendientes;
- propuesta del Bloque 2.

## Paso 6 — Pruebas focalizadas

Crear pruebas específicas del Bloque 1.

Cubrir como mínimo:

1. validaciones de dominio;
2. estados permitidos;
3. errores;
4. persistencia fake;
5. filtros;
6. aislamiento entre entidades;
7. permisos constantes;
8. wiring en DataProvider cuando corresponda;
9. rutas de navegación;
10. guardas de exclusiones;
11. ausencia de secretos;
12. ausencia de SQL/migración nueva;
13. regresión mínima de módulos dependientes.

Ejecutar solo las suites del nuevo módulo y regresiones imprescindibles.

## Paso 7 — Compilación final

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

## Paso 8 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- migraciones 001–047 intactas;
- sin migración 048;
- M12 smoke sigue pendiente externo;
- CI workflow no fue debilitado;
- sin secretos;
- sin archivos binarios;
- sin cambios fuera del alcance de M13.

## Paso 9 — Git

Realizar un único commit:

```text
feat(m13): establish module foundation
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Fuente canónica de M13.
3. Nombre exacto de M13.
4. Objetivo.
5. Alcance.
6. Exclusiones.
7. Dependencias.
8. Auditoría del repositorio.
9. Código reutilizado.
10. Código creado.
11. Dominio y errores.
12. Repositorios y fakes.
13. UI y navegación.
14. Permisos y seguridad.
15. Tests ejecutados.
16. Cantidad total de tests PASS.
17. Compilación.
18. Documentación.
19. Migraciones.
20. Limitaciones.
21. Pendientes.
22. Propuesta completa del Bloque 2.
23. SHA.
24. Push.
25. `git status -sb`.

## Estado final permitido

Si M13 se identifica y el bloque pasa:

```text
M13 BLOQUE 1 CERRADO LOCALMENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

Si no existe definición canónica suficiente:

```text
M13 BLOQUEADO — ALCANCE CANÓNICO AUSENTE O CONTRADICTORIO
```
