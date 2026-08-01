# Cursor — M15 inicio, auditoría canónica y Bloque 1 local

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `24ff668eb6702ea20621c5a3a5a2b94c22eb0777`.
- `origin/main` alineada.
- M14 Bloques 1–4: cerrados localmente.
- M14 cierre técnico local: COMPLETADO.
- Compilación Kotlin M14: PASS.
- Pruebas automáticas M14 Bloque 4: NO EJECUTADAS por decisión del usuario.
- Validación funcional M14: MANUAL PENDIENTE.
- Migración 052: PENDIENTE DE APLICACIÓN REMOTA.
- Validación estructural 052: PENDIENTE.
- M14 cierre oficial: PENDIENTE.
- GitHub Android CI: PENDIENTE.
- M13 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- M12 smoke funcional y cierre oficial: PENDIENTES EXTERNOS.
- Migraciones existentes: 001–052.
- Migración 053: inexistente.
- M15: NO INICIADO.

## Objetivo

Identificar el nombre y alcance canónicos exactos de M15 desde la documentación del repositorio y, únicamente si la definición es inequívoca, implementar **M15 Bloque 1 local**.

No inventar el módulo.

## Modo ahorro obligatorio

- Trabajar en un chat nuevo.
- No releer todo el repositorio.
- No usar subagentes.
- No usar tareas paralelas.
- No usar Max Mode.
- Usar búsquedas focalizadas.
- No ejecutar Gradle repetidamente.
- No ejecutar pruebas automáticas.
- Ejecutar una única compilación Kotlin final.
- No generar APK.
- No aplicar SQL.
- No corregir GitHub CI.
- No resolver pendientes remotos de M12, M13 o M14.
- Un único commit y push.

## Reglas generales

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- No modificar migraciones 001–052.
- No crear migración 053 en Bloque 1.
- No iniciar M16.
- No reescribir módulos existentes.
- Preservar comportamiento funcional ya implementado.
- No incluir secretos.
- No afirmar pruebas automáticas PASS.
- La validación del Bloque 1 será por compilación y revisión manual.

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

Ante cambios locales ajenos:

- no usar `reset`, `restore`, `clean`, `checkout` ni `stash`;
- informar;
- detenerse.

## Paso 2 — Identificación canónica de M15

Leer únicamente las fuentes de producto y arquitectura necesarias:

```text
docs/01-producto/D01-Modulos-y-Orden.md
docs/leover-roadmap-implementacion.md
docs/01-producto/
docs/03-modulos/
docs/02-arquitectura/
README.md
```

Buscar:

```powershell
rg -n --hidden -S "M15|módulo 15|modulo 15|próximo módulo|proximo modulo|producto M15|técnico M15|tecnico M15" docs README.md
```

Determinar:

1. nombre exacto;
2. propósito;
3. actores;
4. alcance incluido;
5. exclusiones;
6. dependencias con M01–M14;
7. entidades previstas;
8. estados;
9. permisos;
10. pantallas y rutas;
11. bloques previstos;
12. relación entre numeración de producto y track técnico;
13. código legacy relacionado.

### Gate obligatorio

Si M15:

- no aparece;
- aparece como una sola línea insuficiente;
- contradice otros documentos;
- se solapa con un módulo técnico ya implementado;
- no permite definir actores, estados, datos y Bloque 1 sin inventar;

detenerse sin modificar código y entregar:

```text
M15 BLOQUEADO — ALCANCE CANÓNICO AUSENTE O CONTRADICTORIO
```

Informar:

- archivos revisados;
- definición parcial encontrada;
- contradicciones;
- decisiones exactas que debe aprobar el usuario.

## Paso 3 — Auditoría técnica focalizada

Cuando M15 sea inequívoco, auditar únicamente:

- código legacy relacionado;
- dominio reutilizable;
- repositorios;
- rutas;
- ViewModels;
- pantallas;
- tablas/RPC existentes;
- permisos;
- M03/M04;
- M05;
- M06;
- M07;
- módulos funcionalmente dependientes;
- privacidad y seguridad;
- riesgo de duplicación.

Clasificar:

```text
REUTILIZABLE
COMPATIBLE
REQUIERE_ADAPTACIÓN
BLOQUEANTE
FUERA_DE_ALCANCE
```

## Paso 4 — Definición del Bloque 1

El Bloque 1 debe ser una fundación local completa y coherente.

Incluir, según corresponda:

- especificación y ADR;
- modelos de dominio;
- enums y estados;
- validadores;
- errores tipificados;
- contratos de repositorio;
- fakes in-memory;
- servicios puros;
- ViewModels;
- UI y navegación iniciales;
- permisos como constantes;
- preparación M05/M06/M07;
- documentación.

### Restricciones

- Sin SQL.
- Sin Supabase real.
- Sin migración 053.
- Sin llamadas de red.
- Sin secretos.
- Sin funcionalidades de bloques posteriores.
- Preservar legacy compatible.
- No duplicar módulos existentes.
- No afirmar infraestructura remota inexistente.

## Paso 5 — Validación manual y compilación

No ejecutar pruebas automáticas en este bloque.

Realizar revisión manual de:

- transiciones;
- permisos;
- privacidad;
- navegación;
- errores;
- fakes;
- ausencia de SQL nuevo;
- ausencia de secretos;
- compatibilidad con módulos existentes.

Ejecutar una única vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Corregir únicamente errores de compilación y repetir solo si falla.

No ejecutar:

```text
test
lint
JaCoCo
assemble
APK
```

## Paso 6 — Documentación

Crear:

```text
docs/03-modulos/M15-auditoria-inicial.md
docs/03-modulos/M15-plan-funcional-y-tecnico.md
docs/02-arquitectura/M15-Bloque-1-validacion.md
```

Crear la especificación y ADR con nombres derivados del alcance real.

Actualizar la fuente canónica solo para registrar el estado verdadero:

```text
M15 BLOQUE 1 CERRADO LOCALMENTE
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

No redefinir el roadmap sin evidencia.

## Paso 7 — Verificación final

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

Confirmar:

- migraciones 001–052 intactas;
- sin 053;
- sin secretos;
- sin binarios;
- CI no modificado;
- pendientes M12/M13/M14 preservados;
- M16 no iniciado.

## Paso 8 — Git

Un único commit:

```text
feat(m15): establish module foundation
```

Push:

```powershell
git push origin main
```

## Entrega final obligatoria

Informar:

1. Estado inicial.
2. Fuente canónica.
3. Nombre exacto.
4. Propósito.
5. Actores.
6. Alcance.
7. Exclusiones.
8. Estados.
9. Dependencias.
10. Auditoría.
11. Legacy reutilizado.
12. Dominio.
13. Validadores.
14. Errores.
15. Repositorios y fakes.
16. Servicios.
17. DataProvider.
18. ViewModels.
19. UI y rutas.
20. Permisos y seguridad.
21. M05/M06/M07.
22. Revisión manual realizada.
23. Compilación.
24. Pruebas automáticas no ejecutadas.
25. Documentación.
26. Migraciones.
27. Limitaciones.
28. Pendientes.
29. Propuesta exacta del Bloque 2.
30. SHA.
31. Push.
32. `git status -sb`.

## Estado final permitido

Si el alcance es claro:

```text
M15 BLOQUE 1 CERRADO LOCALMENTE
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
M14 MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
M14 CIERRE OFICIAL PENDIENTE
M13 CIERRE OFICIAL PENDIENTE
M12 CIERRE OFICIAL PENDIENTE
```

Si el alcance no es suficiente:

```text
M15 BLOQUEADO — ALCANCE CANÓNICO AUSENTE O CONTRADICTORIO
```
