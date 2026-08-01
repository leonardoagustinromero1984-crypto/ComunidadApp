# M15 Bloque 2 — Validación

## Estado

```text
M15 BLOQUE 2 CERRADO LOCALMENTE
M10 ES LA PERSISTENCIA AUTORITATIVA DE M15
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS (ver SHA post-commit)
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Caso aplicado

**Caso A** — M10 cubre hogar, disponibilidad, solicitudes y placement base.

## Revisión manual

| # | Ítem | OK |
|---|------|-----|
| 1 | Mapping M10/M15 en `M15FosterMappers.kt` | ✓ |
| 2 | Disponibilidad: ocupación + reservas vía M10 | ✓ |
| 3 | Autoridad server-side en RPC M10 | ✓ |
| 4 | Privacidad: proyección pública sin dirección privada | ✓ |
| 5 | Transiciones solicitud vía delegate M10 | ✓ |
| 6 | Reserva/ingreso vía `accept` + `startPlacement` M10 | ✓ |
| 7 | Errores M10 → M15 en `M15ErrorMapper` | ✓ |
| 8 | DataProvider: Supabase → M10, local → mock | ✓ |
| 9 | Rutas `foster_*` y `m15/*` sin cambios destructivos | ✓ |
| 10 | Sin duplicación de tablas/datos | ✓ |
| 11 | Migraciones 001–052 intactas | ✓ |
| 12 | 053 no creada | ✓ |
| 13 | Sin secretos | ✓ |

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Limitaciones B2

- Mock M15 y remoto M10 no comparten datos en runtime.
- Gastos, evolución, egreso completo: Bloque 3.
- Hub M15 no enlazado desde Sumate.
