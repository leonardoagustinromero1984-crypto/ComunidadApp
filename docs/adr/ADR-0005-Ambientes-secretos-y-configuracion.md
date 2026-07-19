# ADR-0005 — Ambientes, secretos y configuración

- **Estado:** Accepted  
- **Fecha:** 2026-07-14  
- **Módulo:** M00  

## Contexto

La config Android entra por `local.properties` → `BuildConfig` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_ENABLED`). Sin flavors `staging`/`production` todavía. Edge Function push usa secrets en el dashboard de Supabase.

Desde M00 Etapa 4, el código de aplicación consulta **`AppConfigProvider` / `AppConfig`** (y `FeatureFlags`) en lugar de leer `BuildConfig` en UI o proveedores. `BuildConfig` permanece como fuente de inyección Gradle; la anon key no se expone en `AppConfig` ni en logs.

## Problema

¿Cómo configurar ambientes y secretos sin filtrarlos al repo y sin crash si faltan?

## Opciones consideradas

1. Committear `.env` / claves en Gradle.  
2. Flavors complejos desde M00.  
3. `local.properties` (+ ejemplo) para Android; secrets de Edge Functions solo en Supabase Dashboard / `supabase/.env.example` ficticio; sin credenciales → modo mock (no crash).

## Decisión

**Opción 3 (simplificada).**

- Nunca commitear `local.properties` ni `.env` reales.  
- Usar `local.properties.example` y `supabase/.env.example` con valores ficticios.  
- Sin credenciales Supabase: mock controlado.  
- No crear `.env.example` en la raíz sin consumidor.  
- Flavors `dev`/`staging`/`prod` pueden añadirse en etapas posteriores de M00 sin sobrecomplicar ahora.  
- `google-services.json` permanece versionado mientras el repo sea privado; si se hace público, sacarlo del Git.

## Consecuencias positivas

- Onboarding claro.  
- App usable offline/mock.  
- Separación Android vs secrets de función.

## Riesgos / consecuencias negativas

- Un solo `BuildConfig` no modela staging cloud distinto.  
- `google-services.json` en Git es riesgo si cambia la visibilidad del repo.

## Condiciones para revisar

- Primer deploy a tienda / entornos cloud múltiples.  
- Repo público o fuga de claves.  
- Necesidad de feature flags remotos.

## Referencias

- `app/build.gradle.kts`  
- `app/src/main/java/com/comunidapp/app/core/config/`  
- `local.properties.example`  
- `supabase/.env.example`  
- [M00-etapa-4-cierre.md](../02-arquitectura/M00-etapa-4-cierre.md)
