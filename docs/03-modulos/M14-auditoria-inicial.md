# M14 — Auditoría inicial

## Estado actual (post Bloque 2 local)

```text
M14 BLOQUE 1 CERRADO LOCALMENTE
M14 BLOQUE 2 CERRADO LOCALMENTE
MIGRACIÓN 050 PENDIENTE DE APLICACIÓN REMOTA
M13 CIERRE TÉCNICO LOCAL COMPLETADO
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
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
| Owner / responsabilidad | REUTILIZABLE | B2: `m08_actor_has_active_responsibility` |
| Microchip M08 | REUTILIZABLE | Normalización server; máscara en proyección |
| Vacunación en `Pet` | LEGACY_PRESERVADO | No es historia clínica M14 |
| M09 adopciones | FUERA_DE_ALCANCE | No reimplementar |
| M12 veterinarias | PREPARADO | Emisor futuro; sin resolución remota aún |
| Media M05 | REUTILIZABLE | Solo refs `m05://` / `file_asset:` |
| M07 auditoría | REUTILIZABLE | Best-effort `m14.passport.*` |
| M06 push | REQUIERE_ADAPTACIÓN | Hooks; sin push real |

## Persistencia B2

| Artefacto | Estado |
|-----------|--------|
| `050_m14_pet_passports_and_credentials.sql` | Creada, no aplicada |
| 18 RPC cliente | Local en repo |
| Decisiones / historial | Tablas listas; sin RPC de resolución |
| Guard CI | Highest **050** |

## Datos desde M08 vs M14

| Campo | Fuente |
|-------|--------|
| petId, species, name base, sex, color, breed, microchip | M08 / proyección |
| passportNumber, publicCode, status, visibility, credentials, history | M14 (050) |

## Riesgos

- Apply remoto 050 pendiente.
- Smoke M12/M13 externos no bloquean cierre local B2.
- Resolución de verificaciones = Bloque 3.
