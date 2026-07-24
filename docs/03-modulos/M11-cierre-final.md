# M11 — Cierre final: Refugios

**Producto:** LeoVer  
**Estado:** `M11 CERRADO` (cierre técnico en repositorio)  
**HEAD de cierre:** ver commit `chore(m11): finalize shelter module`  
**Base Bloque 3:** `353627fb713a5cf7f9b1ca534319b246053b0b62`

## Alcance final

M11 cubre operación de refugios institucional (organización M03):

1. **Bloque 1** — perfiles, capacidad, mascotas alojadas, voluntarios, dashboard; integraciones M08/M09/M10.
2. **Bloque 2** — campañas no monetarias, pedidos de insumos, aportes/recepción; hardening permisos 044.
3. **Bloque 3** — urgencias, eventos gratuitos (cupo/waitlist), métricas y CSV sin PII.

Sin pagos, checkout, chat, push real, reputación ni IA.

## Arquitectura

- Autoridad M03 (`has_org_permission` / membresía); no `AccountType` / `active_modules`.
- Mutaciones vía RPC `SECURITY DEFINER` + `search_path = public`.
- Cliente Android: SELECT + RLS; sin `service_role`.
- Evidencia M05: solo `m05://` / `file_asset:`.
- Hooks M06 preparados (sin push). Auditoría M07 en dominio/SQL.

## Persistencia 042–045

| SQL | Contenido |
|-----|-----------|
| 042 | `shelter_profiles`, pets, volunteers |
| 043 | campaigns, updates, supply requests/contributions |
| 044 | harden grants B2 (sin lógica nueva) |
| 045 | emergencies, events, registrations + report RPCs |

**046:** no creada en este cierre.

## Flujos

Ver `docs/02-arquitectura/M11-matriz-funcional-final.md`.

## Integraciones

| Módulo | Uso |
|--------|-----|
| M03 | Org, roles, permisos `shelter.*` |
| M05 | Refs seguras |
| M06 | Hooks sin push |
| M07 | Eventos auditables |
| M08 | Mascotas / CO_RESPONSIBLE |
| M09 | Hook `onAdoptionFinalized` (mock efectivo; remota stub hasta posible 046) |
| M10 | `FOSTER_RETURN` |

## Correcciones locales del cierre

- Rutas públicas urgencias/eventos + botones en listado ops.
- Cableado best-effort M09 → `shelterPetRepository.onAdoptionFinalized`.

## Hallazgos

| Tipo | Detalle |
|------|---------|
| PASS | Bloques 1–3, nav dashboard, fakes, errores tipados, CSV sin PII, migraciones 042–045 |
| CORREGIBLE_LOCAL | Aplicados (públicos B3 + hook M09 cliente) |
| BLOQUEANTE_SQL | Ninguno que detenga el cierre; RPC remota M09→placement queda como **criterio de reapertura** |
| PENDIENTE_EXTERNO | Smoke UI completo; push M06; APK; promoción ambientes |
| FUERA_DE_ALCANCE | Chat, reputación, IA, pagos |

## Pruebas / compilación

Suites focalizadas M11 + hardening + M08/M09/M10/M05 + `M11FinalClosureGuardsTest`.  
`compileLocalDebugKotlin` OK.

## Documentación

- `docs/03-modulos/M11-refugios.md`
- `docs/02-arquitectura/M11-operacion-refugios.md`
- `docs/02-arquitectura/M11-campanas-insumos-red-ayuda.md`
- `docs/02-arquitectura/M11-urgencias-eventos-reportes.md`
- `docs/02-arquitectura/M11-matriz-funcional-final.md`
- `docs/05-operacion/M11-validacion-final.md`
- Prompts/smoke: Bloque 2/3, cierre final, apply remoto 043/045

## Limitaciones

- Smoke UI manual no ejecutado en este cierre → **PENDIENTE_EXTERNO**.
- Supabase `onAdoptionFinalized` sigue no-op hasta RPC futura (no bloquea superficie M11 mock/tests).

## Pendientes externos (no faltantes de M11)

```text
M06 push real
chat
reputación
APK / smoke UI general
promoción de migraciones a otros ambientes
```
