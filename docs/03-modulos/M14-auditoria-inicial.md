# M14 — Auditoría inicial

## Estado previo

```text
M13 CIERRE TÉCNICO LOCAL COMPLETADO
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
M14 NO INICIADO (hasta Bloque 1)
```

## Remapeo

Fuente: `ADR-014-M14-remapeo-pasaporte.md`

```text
Producto M14 Adopciones = cubierto por M09 técnico
M14 técnico = Pasaporte e identidad verificable de mascotas
```

## Clasificación

| Área | Clasificación | Notas |
|------|---------------|-------|
| Pet / M08 identidad | REUTILIZABLE | Fuente autoritativa; no duplicar |
| Owner / responsabilidad | REUTILIZABLE | B1 usa `ownerId`; grafo completo = REQUIERE_ADAPTACIÓN B2 |
| Microchip M08 | REUTILIZABLE | `MicrochipNormalizer`; máscara en proyección |
| Vacunación en `Pet` | LEGACY_PRESERVADO | No es historia clínica M14 |
| M09 adopciones | FUERA_DE_ALCANCE | No reimplementar; puede aportar credencial ADOPTION luego |
| M12 veterinarias | FUERA_DE_ALCANCE B1 | Emisor futuro; sin clínica |
| passport/qr legacy | — | Sin código previo (greenfield) |
| Media M05 | REUTILIZABLE | `m05://` / `file_asset:` |
| M07 auditoría | REUTILIZABLE | Eventos locales preparados |
| M06 push | REQUIERE_ADAPTACIÓN | Hooks preparados; sin push real |

## Datos desde M08 vs M14

| Campo | Fuente |
|-------|--------|
| petId, species, name base, sex, color, breed, microchip | Proyectados/creados desde M08 al crear |
| passportNumber, publicCode, status, visibility, credentials, history | M14 |

## Riesgos

- Autoridad compartida M08 completa pendiente de wire en B2.
- Smoke M12/M13 externos no bloquean B1 local.
