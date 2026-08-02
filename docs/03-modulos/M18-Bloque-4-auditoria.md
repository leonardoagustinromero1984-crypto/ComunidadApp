# M18 Bloque 4 — Auditoría integraciones y cierre preparatorio

**Fecha:** 2026-08-02  
**Recurrencia:** fuera de alcance D01 (no implementada)

## Ciclo de vida evento

Validado: `DRAFT`, `PUBLISHED`, `PAUSED`, `CANCELLED`, `COMPLETED`. Terminales no reabren. Evento pasado rechaza inscripciones (`validateRegistration` + `endsAt`).

## Integraciones

| Módulo | Uso M18 |
|--------|---------|
| **M03** | `organizationId`, permisos `event.view` / `event.manage` |
| **M04** | `M18EventModerationAdapter` — reporte evento, imagen, contenido organizador |
| **M05** | `coverImageRef` mock URI; sin binarios en M18 |
| **M06** | hooks ampliados; infra no disponible no bloquea flujo principal |
| **M10** | `publicLocationText`, `venueName` en detalle público |
| **M16** | enlace contextual refugio → directorio M18 |
| **M17** | sin duplicar pantallas; relación vía organización |

## Descubrimiento

Filtros extendidos: texto, tipo, organización, ubicación, cupos, completados, próximos.

## Métricas

`M18EventOperationsSummary`: ocupación, conversión registro→asistencia — calculadas, no duplicadas en DB.

## Resiliencia

`M18EventResilience`: estados parciales M04/M05/M06/M10; errores sin PII.

## Privacidad final

Auditoría: directorio, detalle, panel org, RPC públicas — sin lista de participantes ni userId en modelos públicos.

## Migraciones

- **058:** no aplicada
- **059:** no requerida (brechas ATTENDED/remoto documentadas para post-058)

## Cierre operativo global

**PENDIENTE** — requiere aplicación 058 + validación remota PASS.
