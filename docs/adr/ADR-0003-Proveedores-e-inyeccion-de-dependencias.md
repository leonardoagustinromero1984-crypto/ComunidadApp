# ADR-0003 — Proveedores e inyección de dependencias

- **Estado:** Accepted  
- **Fecha:** 2026-07-14  
- **Módulo:** M00  

## Contexto

La app usa `DataProvider` y `AuthProvider` (service locator) con repositorios mock/Supabase. M00 original prefería Hilt, permitiendo alternativa válida.

## Problema

¿Migrar toda la app a Hilt dentro de M00?

## Opciones consideradas

1. Migración completa a Hilt ahora.  
2. Mantener locators y permitir nuevas dependencias UI→singleton.  
3. Mantener `DataProvider`/`AuthProvider`; nuevos repos solo por interfaces; Hilt como mejora futura.

## Decisión

**Opción 3.** No migrar a Hilt en M00. Evitar nuevas dependencias directas a singletons desde la UI. Exponer nuevos repositorios mediante interfaces.

## Consecuencias positivas

- Evita refactor masivo y roturas de ViewModels.  
- Consistente con el código actual.  
- Permite tests con fakes vía interfaces.

## Riesgos / consecuencias negativas

- Locators dificultan tests aislados y scopes.  
- Riesgo de “atajos” a singletons si no se revisan PRs.

## Condiciones para revisar

- Necesidad clara de scopes/cycle de vida o testing difícil.  
- ADR de migración gradual a Hilt módulo por módulo.

## Referencias

- `app/.../data/provider/DataProvider.kt`  
- `app/.../data/repository/AuthProvider.kt`
