# M15 — Hogares de tránsito

## 1. Decisión canónica

```text
M15 técnico = Hogares de tránsito
Producto M15 = mismo alcance (sin remapeo cruzado como M14/M09)
Legacy M10 = implementación previa preservada
```

Fuente: `ADR-015-M15-hogares-de-transito.md`, `D01-Modulos-y-Orden.md`.

## 2. Objetivo

Coordinar disponibilidad de hogares temporales, solicitudes de alojamiento, ingreso de mascotas y seguimiento hasta el egreso, sobre identidad M08 y permisos M02/M03.

## 3. Actores

| Actor | Capacidades Bloque 1 |
|-------|---------------------|
| Cuidador (dueño de hogar) | Crear/activar perfil, revisar solicitudes, reservar cupo |
| Solicitante (responsable M08 u org M03) | Enviar solicitud por mascota |
| Público autenticado | Ver listado público sin dirección privada |

## 4. Estados

### Hogar

```text
DRAFT → ACTIVE → PAUSED | SUSPENDED | CLOSED
```

### Disponibilidad (derivada)

```text
UNAVAILABLE | AVAILABLE | LIMITED | FULL
```

Regla: perfil no ACTIVE → UNAVAILABLE; ocupación + reservas determinan AVAILABLE/LIMITED/FULL.

### Solicitud

```text
SUBMITTED → UNDER_REVIEW → ACCEPTED | REJECTED | CANCELLED | EXPIRED
```

### Alojamiento (Bloque 1–3)

```text
RESERVED → ACTIVE → COMPLETED | CANCELLED
```

Evolución append-only, egreso tipificado, gastos y ayuda = Bloque 3 (cerrado localmente).

## 5. Exclusiones Bloque 1

- SQL / migración 053.
- Supabase real.
- Gastos, evolución clínica, pedidos de ayuda, finalización completa.
- Pagos, chat, GPS, reputación, IA.
- Reescritura de rutas `foster_*` legacy.

## 6. Bloques previstos

| Bloque | Alcance |
|--------|---------|
| 1 | Dominio, fakes, UI `m15/*`, hub y flujo base solicitud → reserva |
| 2 | Reconciliación M10 (Caso A): adaptadores Supabase M15 → RPC 040/041; **sin 053** |
| 3 | Evolución, egreso, custodia temporal M08, gastos, ayuda — **cerrado localmente** |
| 4 | Métricas, M06 push preparado, cierre técnico |

## 7. Definition of Done

### Bloque 1

```text
M15 BLOQUE 1 CERRADO LOCALMENTE
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

### Bloque 2

```text
M15 BLOQUE 2 CERRADO LOCALMENTE
M10 ES LA PERSISTENCIA AUTORITATIVA DE M15
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

### Bloque 3

```text
M15 BLOQUE 3 CERRADO LOCALMENTE
M10/M08 SON LA BASE AUTORITATIVA
SIN MIGRACIÓN 053
COMPILACIÓN KOTLIN PASS
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```
