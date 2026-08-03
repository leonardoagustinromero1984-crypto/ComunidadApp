# RC1 — Backlog de hallazgos

| ID | Sev | Módulo | Descripción | Impacto | Evidencia | Solución | Corregido | Pendiente | SQL | Decisión | Dispositivo |
|----|-----|--------|-------------|---------|-----------|----------|-----------|-----------|-----|----------|-------------|
| NAV-001 | ALTO | M09 | popUpTo ADOPTIONS sin composable | Back stack roto post-apply | ComunidappNavGraph:961 | popUpTo SUMATE | **Sí** | No | No | No | No |
| NAV-002 | MEDIO | M17 | Tabs in-kind/volunteer sin rutas | Clicks sin acción | M17NavGraph | Rutas detalle o disable tabs | No | **Sí** | No | No | No |
| NAV-003 | MEDIO | Legacy | Dual CHAT/M20, FOSTER/M15, SHELTER/M16 | Datos inconsistentes | Nav + DataProvider | Unificar entry points | No | **Sí** | No | **Sí** | No |
| NAV-004 | BAJO | M04 | ADMINISTRATIVE_OPS_HUB huérfana | Dead constant | NavRoutes | Registrar o eliminar | No | **Sí** | No | No | No |
| NAV-005 | BAJO | M20 | M20_CONVERSATIONS alias | Confusión | NavRoutes | Eliminar alias | No | **Sí** | No | No | No |
| NAV-006 | BAJO | M06 | Deep link a CHAT legacy | Split messaging | NotificationDeepLinkRouter | Migrar a M20 | No | **Sí** | No | No | Sí |
| NAV-007 | BAJO | M23 | IDs mock en nav graph | Demo only | M23NavGraph | IDs dinámicos | No | **Sí** | No | No | No |
| DP-001 | MEDIO | M10/M15 | Split-brain mock stores | Datos divergentes mock | DataProvider | Unificar store mock | No | **Sí** | No | No | No |
| DP-002 | MEDIO | Legacy | Dual repo stacks | Pantallas desconectadas | DataProvider | Deprecar legacy repos | No | **Sí** | No | **Sí** | No |
| PERM-001 | MEDIO | M20 | Legacy chat vs M20 | Permisos UX distintos | ChatViewModel | Unificar | No | **Sí** | No | **Sí** | No |
| PII-001 | BAJO | Legacy | PaymentStatus en mappers | Ruido pre-M24 | SupabaseMappers | Limpiar con M24 | No | **Sí** | No | **Sí** | No |
| RES-001 | BAJO | Legacy | Empty states heterogéneos | UX | Pantallas legacy | Normalizar | No | **Sí** | No | No | No |
| SQL-001 | ALTO | M09–M14 | Gap migraciones 039–052 | Schema drift | staging query | Reconciliación | No | **Sí** | **Sí** | **Sí** | No |
| UI-001 | BAJO | Legacy | Terminología mixta | Confusión | UI strings | Copy pass | No | **Sí** | No | No | No |
| UI-002 | BAJO | M17 | Tabs sin destino | UX muerta | M17 hub | Ver NAV-002 | No | **Sí** | No | No | No |
| UI-003 | MEJORA | Nav | Hub naming | Consistencia | NavRoutes | Normalizar RC2 | No | **Sí** | No | No | No |

## Resumen por severidad

| Severidad | Total | Corregido RC1 | Pendiente |
|-----------|-------|---------------|------------|
| CRÍTICO | 0 | 0 | 0 |
| ALTO | 2 | 1 (NAV-001) | 1 (SQL-001) |
| MEDIO | 5 | 0 | 5 |
| BAJO | 8 | 0 | 8 |
| MEJORA | 1 | 0 | 1 |

## Reglas aplicadas

1. Críticos/altos locales corregidos cuando posible → NAV-001 corregido.
2. SQL-001 documentado; no migración en RC1.
3. Mejoras cosméticas pendientes.
