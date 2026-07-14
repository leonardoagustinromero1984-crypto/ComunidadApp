# ADR-0002 — Arquitectura Android monolito modular

- **Estado:** Accepted  
- **Fecha:** 2026-07-14  
- **Módulo:** M00  

## Contexto

El repo tiene un único módulo Gradle `:app` con paquetes `ui`, `viewmodel`, `domain`, `data`. La estructura teórica de M00 mencionaba `core/` y `feature/`.

## Problema

¿Debemos modularizar Gradle o reorganizar carpetas masivamente en M00?

## Opciones consideradas

1. Modularización Gradle inmediata (`:core`, `:feature:*`).  
2. Mover archivos solo para alinearse a la estructura teórica.  
3. Mantener `:app` y organizar por paquetes/contratos; modularizar después con necesidad real.

## Decisión

**Opción 3.** Monolito Android modular por paquetes. Sin multi-módulo Gradle en M00. No mover código existente solo por estética de carpetas.

## Consecuencias positivas

- Sin riesgo de romper imports/navegación en M00.  
- Desarrollo continuo sobre base que ya compila.  
- Fronteras claras vía repositorios e interfaces.

## Riesgos / consecuencias negativas

- Crecimiento del módulo `:app`.  
- Builds más lentos a futuro sin modularización.  
- Disciplina de paquetes depende del equipo.

## Condiciones para revisar

- Tiempos de compilación inaceptables.  
- Equipos paralelos que necesiten ownership por módulo Gradle.  
- ADR nuevo con plan de extracción incremental.

## Referencias

- [arquitectura-inicial.md](../02-arquitectura/arquitectura-inicial.md)
