# M15 — Métricas operativas y privacidad

## Contratos (Bloque 4)

```text
M15OperationalMetrics
M15OperationalMetricsQuery
M15OperationsRepository
M15MetricsPolicy
M15PrivacySanitizer
```

Implementación: `M15Block4Operations.kt`, `M15OperationalModels.kt`.

## Rango de consulta

| Campo | Semántica |
|-------|-----------|
| `fromInclusive` | Inicio inclusive (epoch ms UTC) |
| `toExclusive` | Fin exclusive (epoch ms UTC) |
| `zoneIdName` | Zona determinista (default `America/Argentina/Buenos_Aires`) |

Validaciones:

- Rango vacío o invertido → `M15_METRICS_INVALID_RANGE`
- Rango > 366 días → `M15_METRICS_INVALID_RANGE`
- Zona inválida → `M15_METRICS_INVALID_RANGE`

## Métricas mínimas (sin PII)

### Hogares

Total por estado y disponibilidad; capacidad total; plazas ocupadas, reservadas y disponibles.

### Solicitudes

Total por estado; enviadas, aceptadas, rechazadas, canceladas, expiradas; tiempo agregado de resolución cuando hay datos.

### Placements

Total por estado; reservados, activos, completados, interrumpidos, cancelados; egresos por motivo y outcome.

### Evolución

Cantidad por tipo; alertas de salud e incidentes agregados. Sin textos, notas ni IDs.

### Gastos

Cantidad por estado y categoría; suma por moneda (minor units). Sin descripción, comprobante ni actor.

### Ayuda

Cantidad por tipo, estado y prioridad; abiertas, en curso, resueltas, canceladas, expiradas.

### Calidad operativa

Conflictos, reintentos idempotentes, fallbacks remotos, errores por código agregado.

## Privacidad

Las métricas **nunca** contienen: userId, petId, homeId, requestId, placementId, organizationId, dirección, teléfono, correo, coordenadas, microchip, publicCode, notas, descripciones, comprobantes, URLs privadas, nombres de persona o mascota.

`M15PrivacySanitizer` aplica en proyecciones públicas de hogares (`toPublicListing()`).

Logs seguros: código + categoría + estado + timestamp + contador.

## Modo remoto

`SupabaseM15OperationsRepository.getOperationalMetrics` → `M15_REMOTE_VALIDATION_PENDING` (sin SQL nuevo).

## Estado

```text
M15 BLOQUE 4 CERRADO LOCALMENTE
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```
