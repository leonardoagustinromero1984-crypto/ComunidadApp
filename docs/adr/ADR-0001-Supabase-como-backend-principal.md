# ADR-0001 — Supabase como backend principal

- **Estado:** Accepted  
- **Fecha:** 2026-07-14  
- **Módulo:** M00  

## Contexto

La app Android ya opera con Supabase (Auth, Postgres, Storage, migraciones, Edge Function de push) y modo mock. La especificación M00 original sugería NestJS + Postgres local; la auditoría y la Etapa 2 corrigieron esa dirección.

## Problema

¿Cuál es el backend oficial de Leover en la etapa actual, y debe M00 crear un segundo stack (NestJS/Docker/ORM)?

## Opciones consideradas

1. Introducir NestJS + Docker PostGIS en paralelo a Supabase.  
2. Migrar de inmediato toda la lógica a NestJS.  
3. Declarar **Supabase** backend oficial actual y diferir backend dedicado a un ADR futuro.

## Decisión

**Opción 3.** Supabase es el backend principal. FCM permanece solo para push. No se crea NestJS, REST paralela, Docker Compose con otra DB, Prisma ni TypeORM en M00.

## Consecuencias positivas

- Sin duplicar Auth/Storage/datos.  
- Reutiliza migraciones y trabajo ya desplegado.  
- Reduce costo y complejidad de M00.  
- Alinea docs con la realidad del repo.

## Riesgos / consecuencias negativas

- Acoplamiento a Supabase/PostgREST y Edge Functions.  
- Límites de producto pueden exigir más adelante un servicio dedicado.  
- Spec M00 “NestJS” queda históricamente contradicha (esta ADR manda).

## Condiciones para revisar

- Necesidades que Supabase/Edge Functions no cubran de forma segura, mantenible o rentable.  
- Nuevo ADR explícito antes de introducir API propia.

## Referencias

- [M00-Etapa-2-Documentacion-y-Gobierno.md](../03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md)  
- [arquitectura-inicial.md](../02-arquitectura/arquitectura-inicial.md)
