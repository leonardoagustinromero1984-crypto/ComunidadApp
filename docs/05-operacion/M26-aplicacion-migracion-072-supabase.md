# Operación — migración 072 (M26 inteligencia asistida)

**Estado:** 072 creada localmente; **no aplicada** a staging.

## Alcance

- Matching visual, duplicados, asistencia stub y recomendaciones evaluadas.
- Sin pagos ni M24.

## Aplicación (solo entorno autorizado)

1. Verificar que la cadena 001–071 esté aplicada.
2. Ejecutar `supabase/migrations/072_m26_ai_matching_duplicates_assistance_recommendations.sql` en el proyecto Supabase autorizado.
3. Validar RPCs listados en `docs/03-modulos/M26-Bloque-2-validacion.md`.
4. Activar `useSupabase` en la app solo tras smoke remoto exitoso.

## Rollback

Forward-only; no hay rollback automático. Revertir requiere migración correctiva explícita.
