# Cursor — M14 inicio, auditoría canónica y Bloque 1

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama de trabajo: `main`.
- HEAD mínimo: `9da7415ed6c465a8647cf469e53474e1509c0573`.
- `origin/main` alineada.
- M13 Bloques 1–4: cerrados localmente.
- M13 cierre técnico local: COMPLETADO.
- Migraciones 048 y 049: aplicadas y validadas estructuralmente.
- M13 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- M12 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- M14: NO INICIADO.
- Migraciones existentes: 001–049.
- Migración 050: inexistente.

## Archivos locales conocidos

Pueden existir estos dos archivos untracked creados como respaldo de calidad:

```text
docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-048-v1.0.md
docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-049-v1.0.md
```

Son los únicos untracked conocidos y no deben tratarse como cambios ajenos bloqueantes.

Antes de incorporarlos:

- leerlos completos;
- confirmar que no contienen secretos, tokens ni datos personales;
- confirmar que documentan resultados reales ya verificados;
- incorporarlos al único commit del bloque como evidencia de calidad de M13.

Si contienen contradicciones o información sensible:

- no borrarlos;
- no versionarlos;
- informar exactamente el problema;
- detenerse antes del commit.

Cualquier otro cambio local ajeno sí bloquea el inicio.

## Objetivo

Identificar el nombre y alcance canónicos exactos de M14 desde la documentación del repositorio y, únicamente si la definición es clara, implementar **M14 Bloque 1** como una fundación local completa.

No inventar el módulo.

## Reglas generales

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- Mantener cambios incrementales.
- No reescribir módulos existentes que ya funcionan.
- No modificar migraciones 001–049.
- No crear migración 050 en Bloque 1.
- No aplicar SQL remotamente.
- No generar APK.
- No usar emulador.
- No ejecutar lint, JaCoCo ni toda la suite repetidamente.
- Ejecutar pruebas focalizadas.
- Ejecutar una sola compilación Kotlin final.
- No declarar M12 ni M13 cerrados oficialmente.
- No iniciar M15.
- No debilitar Android CI.
- No incluir secretos.
- No agregar pagos, historia clínica, chat, IA, GPS u otras capacidades salvo que la especificación canónica de M14 las incluya expresamente.

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

Se toleran únicamente los dos archivos conocidos de `docs/04-calidad/`.

Ante cualquier otro cambio local:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Identificación canónica de M14

Leer completos, si existen:

```text
docs/01-producto/D01-Modulos-y-Orden.md
docs/leover-roadmap-implementacion.md
docs/01-producto/
docs/03-modulos/
docs/02-arquitectura/
README.md
```

Buscar todas las referencias:

```powershell
rg -n --hidden -S "M14|módulo 14|modulo 14|R4|próximo módulo|proximo modulo" docs README.md .
```

Determinar:

1. nombre exacto;
2. propósito;
3. actores;
4. alcance incluido;
5. exclusiones;
6. dependencias con M01–M13;
7. entidades previstas;
8. permisos previstos;
9. pantallas y rutas;
10. orden de bloques;
11. relación entre numeración de producto y track técnico;
12. código legacy existente relacionado.

### Gate obligatorio

Si M14:

- no aparece;
- aparece solo como una línea insuficiente;
- contradice varios documentos;
- se solapa con un módulo técnico ya implementado;
- no permite definir actores, estados, datos y Bloque 1 sin inventar;

detenerse sin modificar código y entregar:

```text
M14 BLOQUEADO — ALCANCE CANÓNICO AUSENTE O CONTRADICTORIO
```

Informar:

- archivos revisados;
- definición parcial hallada;
- contradicciones;
- decisiones exactas que debe aprobar el usuario.

No inventar una especificación.

## Paso 3 — Auditoría técnica

Cuando M14 sea inequívoco, auditar:

- implementaciones legacy relacionadas;
- dominio reutilizable;
- repositorios;
- tablas/RPC existentes;
- rutas y pantallas;
- permisos;
- M03/M04;
- M05;
- M06;
- M07;
- M08–M13 cuando correspondan;
- privacidad y seguridad;
- riesgo de duplicación;
- dependencias externas.

Clasificar:

```text
REUTILIZABLE
COMPATIBLE
REQUIERE_ADAPTACIÓN
BLOQUEANTE
FUERA_DE_ALCANCE
```

## Paso 4 — Definición del Bloque 1

El Bloque 1 debe ser la primera unidad local coherente y completa del M14 canónico.

Incluir, según corresponda:

- modelos de dominio;
- enums y estados;
- validadores;
- errores tipificados;
- contratos de repositorio;
- fakes in-memory;
- servicios puros;
- filtros;
- ViewModels;
- UI y navegación iniciales;
- permisos como constantes;
- preparación M06/M07;
- media segura M05;
- pruebas focalizadas;
- documentación.

### Restricciones del Bloque 1

- Sin migración 050.
- Sin Supabase real.
- Sin llamadas de red en tests.
- Sin secretos.
- Sin push real.
- Sin funcionalidades de bloques posteriores.
- Preservar legacy compatible.
- No duplicar arquitectura existente.
- No afirmar capacidades remotas inexistentes.

## Paso 5 — Compatibilidad con módulos pendientes

Mantener visibles y sin falsear:

```text
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
```

No modificar el estado de M12/M13 salvo para incorporar los dos documentos de calidad conocidos.

## Paso 6 — Documentación

Crear:

```text
docs/03-modulos/M14-auditoria-inicial.md
docs/03-modulos/M14-plan-funcional-y-tecnico.md
docs/02-arquitectura/M14-Bloque-1-validacion.md
```

Actualizar la fuente canónica solo para registrar el estado real:

```text
M14 BLOQUE 1 CERRADO LOCALMENTE
```

No redefinir el roadmap sin evidencia.

La documentación debe registrar:

- fuente canónica;
- estado anterior;
- objetivo;
- alcance;
- exclusiones;
- actores;
- estados;
- dependencias;
- legacy;
- arquitectura;
- archivos;
- seguridad;
- pruebas;
- limitaciones;
- pendientes;
- propuesta completa del Bloque 2.

## Paso 7 — Pruebas focalizadas

Crear pruebas del nuevo módulo que cubran como mínimo:

1. validaciones de dominio;
2. transiciones o estados;
3. errores;
4. persistencia fake;
5. filtros;
6. aislamiento entre entidades;
7. idempotencia cuando corresponda;
8. permisos;
9. DataProvider;
10. navegación;
11. privacidad;
12. media segura;
13. ausencia de secretos;
14. ausencia de SQL nuevo;
15. migraciones 001–049 intactas;
16. ausencia de migración 050;
17. regresión mínima de dependencias.

Ejecutar únicamente suites M14 y regresiones imprescindibles.

## Paso 8 — Compilación final

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

## Paso 9 — Calidad y CI

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- migraciones 001–049 intactas;
- sin 050;
- guard CI no modificado innecesariamente;
- sin secretos;
- sin binarios;
- documentos 048/049 revisados e incorporados de forma segura;
- M12 y M13 siguen pendientes externos;
- M15 no iniciado.

No ejecutar el workflow completo localmente salvo que una guarda focalizada lo requiera.

## Paso 10 — Git

Un único commit:

```text
feat(m14): establish module foundation
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Archivos de calidad M13 incorporados.
3. Fuente canónica de M14.
4. Nombre exacto.
5. Propósito.
6. Actores.
7. Alcance.
8. Exclusiones.
9. Estados.
10. Dependencias.
11. Auditoría técnica.
12. Legacy reutilizado.
13. Dominio creado.
14. Validadores.
15. Errores.
16. Repositorios y fakes.
17. Servicios.
18. DataProvider.
19. UI y rutas.
20. Permisos y seguridad.
21. M05/M06/M07.
22. Tests ejecutados.
23. Total PASS.
24. Compilación.
25. Documentación.
26. Migraciones.
27. Limitaciones.
28. Pendientes.
29. Propuesta exacta del Bloque 2.
30. SHA.
31. Push.
32. `git status -sb`.

## Estado final permitido

Si la definición es clara y el bloque pasa:

```text
M14 BLOQUE 1 CERRADO LOCALMENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

Si el alcance no es suficiente:

```text
M14 BLOQUEADO — ALCANCE CANÓNICO AUSENTE O CONTRADICTORIO
```
