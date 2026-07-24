# M11 — Validación final (operación)

LeoVer. Validación estructural remota M11 en Supabase de **pruebas**. Smoke UI manual = **pendiente externo**.

## Migraciones aplicadas (pruebas)

| Migración | Estado remoto |
|-----------|----------------|
| 040–041 (M10) | Aplicadas |
| 042 (M11 B1) | Aplicada |
| 043 (M11 B2) | Aplicada |
| 044 hardening B2 | Aplicada + PASS seguridad |
| 045 (M11 B3) | Aplicada + PASS estructural/seguridad |
| 046 | **No creada** (no requerida por el filtro falso 23/25) |

## PASS 044 (hardening)

- Sin escritura directa authenticated/anon en tablas de campañas/insumos.
- Sin EXECUTE PUBLIC/anon en RPC B2.
- SELECT autenticado conservado; helpers sin EXECUTE a authenticated.

## PASS 045 (estructural + seguridad)

- 3 tablas: `shelter_emergencies`, `shelter_events`, `shelter_event_registrations`.
- RLS + policies.
- 25 RPC Bloque 3 (incl. `m11_list_public_shelter_emergencies` y `m11_list_shelter_emergencies`).
- SECURITY DEFINER + `search_path` seguro.
- Sin escritura directa authenticated/anon.
- Sin EXECUTE PUBLIC/anon; EXECUTE authenticated en RPC cliente.

**Nota:** el conteo inicial 23/25 fue error de filtro (`%emergency%` no matchea `%emergencies%`). Las dos RPC se verificaron individualmente. No falta RPC. No se necesita 046 por ese hallazgo.

## Smoke UI

```text
PENDIENTE_EXTERNO — smoke completo de UI en dispositivo/emulador
```

No inventar como aprobado. Registrar aparte cuando se ejecute.

## Criterio de reapertura M11

Reabrir solo si:

- defecto de seguridad en grants/RLS/RPC;
- inconsistencia de datos entre bloques;
- se autorice RPC remota M09→cierre de `shelter_pet_placements` (posible migración **046** futura);
- cambio de alcance de producto.

## Promoción

No promover a producción sin:

1. PASS estructurales 042–045 (cumplidos en pruebas).
2. Smoke UI externo cuando el producto lo exija.
3. Decisión explícita de ambientes.
