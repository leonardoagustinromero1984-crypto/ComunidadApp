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

Pagos, historia clínica, chat, IA, biometría, GPS background, push real, SQL/`048`, cierre automático de caso, reemplazo destructivo del legacy.

## Dependencias

M01/M02 auth, M03/M04 autoridad (preparada), M05 media refs, M07 eventos (nombres), M08 mascota (futuro), Lost/Found legacy, M12 técnico independiente (Veterinarias) — smoke M12 sigue pendiente externo.

## Arquitectura Bloque 1

```text
UI (m13/*) → ViewModels → MockM13*Repository → M13MemoryStore
                              ↘ M13MatchingEngine
                              ↘ M13LegacySightingAdapter → InMemoryDataStore.addSighting (opcional)
```

## Bloque 2 (propuesta)

Migración `048` (aprobación aparte), tablas/RPC/RLS, repos Supabase, autoridad remota, validación estructural, sin pérdida del legacy.
