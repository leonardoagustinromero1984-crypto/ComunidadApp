# M15 — Auditoría inicial

## Estado

```text
M15 BLOQUE 1 CERRADO LOCALMENTE
M14 CIERRE TÉCNICO LOCAL COMPLETADO (052 pendiente remoto)
M13/M12 CIERRES OFICIALES PENDIENTES EXTERNOS
```

## Remapeo

Fuente: `ADR-015-M15-hogares-de-transito.md`

```text
Producto M15 Hogares de tránsito = M15 técnico
Legacy M10 = preservado (040/041 pendientes remoto)
```

## Clasificación

| Área | Clasificación | Notas |
|------|---------------|-------|
| `FosterModels` / M10 repos | LEGACY_PRESERVADO | Completo; rutas `foster_*` intactas |
| `M10FosterMemoryStore` | REUTILIZABLE (referencia) | Lógica espejada en M15 fakes |
| M08 mascota / responsable | REUTILIZABLE | Autoridad para solicitar tránsito |
| M08 `TEMPORARY_CUSTODIAN` | REQUIERE_ADAPTACIÓN | Bloque 3 |
| M03 org solicitante | COMPATIBLE | `requesterOrganizationId` preparado |
| M05 media | FUERA_DE_ALCANCE B1 | Bloque 2+ evolución |
| M06 notificaciones | REQUIERE_ADAPTACIÓN | Hooks `M15M06Hooks` sin push real |
| M07 auditoría | REUTILIZABLE | `M15AuditEvents` best-effort local |
| Listings legacy `foster_homes` | INCOMPATIBLE | No usados por M10/M15 |
| Migraciones 040/041 | BLOQUEANTE remoto | Apply pendiente; no tocadas en B1 |

## Riesgos

- Duplicación temporal M10 vs M15 hasta unificación Bloque 2.
- Smoke M10 remoto pendiente independiente de M15 B1.

## Bloque 1 entregado

- Dominio `M15Foster*`
- Fakes `M15MemoryStore` + repos mock
- UI `m15/*` + `M15NavGraph`
- Sin SQL; sin 053
