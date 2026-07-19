# ADR-0004 — Acceso a datos y clientes de red

- **Estado:** Accepted  
- **Fecha:** 2026-07-14  
- **Módulo:** M00  

## Contexto

Supabase Kotlin (Auth, PostgREST, Storage, Realtime) ya está integrado con Ktor en el classpath. No hay Retrofit ni API REST propia.

## Problema

¿Qué cliente HTTP debe ser el estándar y debe incorporarse Retrofit?

## Opciones consideradas

1. Añadir Retrofit “por si acaso”.  
2. Introducir API NestJS + Retrofit en M00.  
3. Mantener Supabase Kotlin como cliente principal; Ktor para Edge Functions/servicios externos cuando haga falta; no Retrofit sin API REST propia.

## Decisión

**Opción 3.** UI/domain no dependen de Supabase ni Ktor. Frontera: repositorios + modelos.

## Consecuencias positivas

- Una familia de stack (Supabase/Ktor).  
- Sin dependencia muerta Retrofit.  
- Alineado con ADR-0001.

## Riesgos / consecuencias negativas

- Si nace API REST propia, habrá que elegir cliente (posible ADR nuevo; preferir no duplicar Ktor+Retrofit).

## Condiciones para revisar

- Existencia de API REST propia versionada.  
- Servicios externos que justifiquen cliente HTTP separado documentado.

## Referencias

- [ADR-0001](ADR-0001-Supabase-como-backend-principal.md)  
- `gradle/libs.versions.toml` (supabase, ktor)
