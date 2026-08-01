# M16 Bloque 2 — Auditoría focalizada

**Fecha:** 2026-08-01  
**Alcance:** persistencia remota, migración 053, RLS, M03, M04, M06.

## Autoridades reutilizadas

| Autoridad | Uso M16 |
|-----------|---------|
| `public.organizations` (M03, 019) | FK `organization_id` UNIQUE; elegibilidad `SHELTER`, `RESCUE_GROUP`, `NGO` |
| `public.organization_memberships` | Sin copia; RLS vía `has_org_permission` |
| `has_org_permission(org_id, code)` | Mutaciones con `shelter.manage`; lectura interna con `shelter.view` |
| `shelter.view` / `shelter.manage` (M11, 042) | Permisos reutilizados; sin permisos M16 paralelos |
| `public.moderation_cases` (M04, 022) | FK opcional en solicitudes de verificación |
| `organizations.review_verification` / `moderation.manage` | Decisión admin vía `m16_decide_shelter_verification` |
| Patrón RPC M10/M11/M14 | `_m16_*` helpers + `m16_*` RPCs + revoke/grant |

## Tablas M16 (053)

- `m16_shelter_profiles` — perfil 1:1 por organización elegible
- `m16_shelter_opening_periods` — horarios normalizados
- `m16_shelter_public_contacts` — contactos con flag `is_public`
- `m16_shelter_needs` — necesidades públicas
- `m16_shelter_verification_requests` — cola auditada; decisión M04

## Superficie pública sanitizada

- RPC `m16_list_public_shelters` y `m16_get_public_shelter` → `jsonb` sin `organization_id`, notas internas ni auditoría
- Grants `anon` + `authenticated` en RPCs públicas únicamente
- Tablas internas: `revoke all` para `anon`; mutaciones `with check (false)` + RPC

## Decisiones

1. **No duplicar M11 `shelter_profiles`:** M11 = operación; M16 = directorio público.
2. **No crear roles/equipos M16:** autorización vía M03 existente.
3. **Idempotencia:** create retorna perfil existente; transiciones repetidas retornan estado actual.
4. **Terminal:** `PERMANENTLY_CLOSED` bloqueado en SQL y repositorio Kotlin.
5. **Verificación:** solicitud → `PENDING`; `VERIFIED`/`REJECTED` solo vía `m16_decide_shelter_verification` (M04).

## M06

- Hooks Kotlin definidos (`M16M06Hooks`); SQL `origin_module` **no incluye M16** (026/028).
- Estado: `M16_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE` — no defecto crítico Bloque 2.

## M04 integración

- `m16_request_verification` crea fila en `m16_shelter_verification_requests`.
- `m16_decide_shelter_verification` para moderadores; sin pantalla M16 paralela.
- Enlace opcional `moderation_case_id` — ampliación UI M04 diferida.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Confusión M11 vs M16 shelter | Documentación y tablas separadas |
| SQL no aplicado en staging | Migración lista; apply manual autorizado |
| M06 sin allowlist M16 | Hooks preparados; extensión 026 futura |

## Diferido (Bloque 3)

- Sincronización ocupación M08/M09/M15
- Donaciones, pagos, tiendas
- Migración 054

## Alcance definitivo Bloque 2

Implementado: 053, RLS, RPCs, repositorio Supabase, autorización M03, adaptador M04 mínimo, documentación.
