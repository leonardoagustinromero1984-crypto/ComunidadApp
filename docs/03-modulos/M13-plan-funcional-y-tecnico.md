# M13 — Plan funcional y técnico

## Objetivo

Registrar avistamientos seguros, proponer coincidencias explicables con casos Lost/Found activos y permitir decisión humana, sin exponer ubicación exacta ni datos privados.

## Alcance Bloque 1

- Dominio M13 (estados, entidades, permisos constantes).
- Validadores y errores tipificados.
- Fake store + repositorios mock.
- Adaptador `LostFoundSighting` ↔ `M13Sighting`.
- Scoring local (reglas 1–14 de la spec).
- UI: lista, alta, detalle, candidatos, revisión local.
- Navegación canónica `m13/...` integrada desde `LostFoundScreen`.
- Pruebas focalizadas y documentación.

## Exclusiones

Pagos, historia clínica, chat, IA, biometría, GPS background, push real, cierre automático de caso, reemplazo destructivo del legacy, creación automática de 049, apply remoto de SQL en este bloque.

## Dependencias

M01/M02 auth, M03/M04 autoridad, M05 media refs, M07 eventos (nombres), M08 mascota (futuro), Lost/Found legacy, M12 técnico independiente (Veterinarias) — smoke M12 sigue pendiente externo.

## Arquitectura Bloque 1

```text
UI (m13/*) → ViewModels → MockM13*Repository → M13MemoryStore
                              ↘ M13MatchingEngine
                              ↘ M13LegacySightingAdapter → InMemoryDataStore.addSighting (opcional)
```

## Bloque 2 (cerrado localmente; remoto estructural PASS)

- Migración `048` aplicada en Supabase de pruebas; validación estructural **13/13 PASS**.
- Smoke funcional B2: **PENDIENTE EXTERNO** (no declarar PASS).
- Tabla lateral + candidatos + decisiones + historial (estructura).
- 13 RPC de sighting/generate/list; **sin** RPC de confirm/reject.

## Bloque 3 (cerrado localmente) + 049

- Flujo humano mock: open → confirm/reject/inconclusive; withdraw/expire.
- Historial append-only + timeline UI; autoridad dueño/org; concurrencia/idempotencia.
- Migración `049` aplicada en Supabase de pruebas; validación estructural **14/14 PASS**.
- Android Supabase cableado a las 8 RPC de revisión.
- Smoke funcional remoto: **PENDIENTE EXTERNO**.

## Bloque 4 (cerrado localmente)

- Privacidad/redacción endurecida; expiraciones locales + TZ; métricas sin PII; hooks M06 preparados.
- Cron/métricas remotas = `REQUIERE_INFRA_EXTERNA` / stubs.
- Sin migración 050; cierre técnico local; **no** cierre oficial mientras smoke externo pendiente.
- Ver `M13-cierre-tecnico.md` y `M13-Bloque-4-validacion.md`.
