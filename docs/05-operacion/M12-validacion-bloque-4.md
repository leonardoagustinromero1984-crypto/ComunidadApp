# M12 Bloque 4 — Validación (cierre local)

**Producto:** LeoVer · Módulo M12 (Veterinarias) · Bloque 4.
**Alcance:** endurecimiento de agenda/turnos, recordatorios preparados (sin push),
seguimiento en UI y métricas agregadas sin PII. Sin pagos ni historia clínica.

## Estado de partida (confirmado)

- Rama `main`. Hotfix de autenticación cerrado y validado manualmente.
- Migración **047** aplicada en Supabase de pruebas.
- Validación **estructural** remota de 047: **13/13 PASS**.
- Smoke funcional completo del Bloque 3: **pendiente externo diferido por decisión del usuario**.
- No se afirma smoke funcional aprobado.

## Qué se validó localmente

| Área | Método | Resultado |
|------|--------|-----------|
| Recordatorios elegibles 24 h / 2 h (solo CONFIRMED) | Test unitario (fakes) | Local PASS |
| No recordar REQUESTED / CANCELLED / finales | Test unitario | Local PASS |
| Idempotencia por turno + tipo | Test unitario | Local PASS |
| Zona horaria en `dueAt` | Test unitario | Local PASS |
| DST / cambio de zona con turnos abiertos | Test unitario | Local PASS |
| Payload/hooks sin PII (sin `requestNote`) | Test unitario | Local PASS |
| M06 disponible vs no disponible | Test unitario | Local PASS |
| `FIRED_PREPARED` sin `pushClaimed` | Test unitario | Local PASS |
| Hook `REMINDER_CANCELLED` al cancelar | Test unitario | Local PASS |
| Expiración de REQUESTED | Test unitario | Local PASS |
| Confirmación simultánea / doble transición | Test unitario | Local PASS |
| Cancelación fuera de ventana | Test unitario | Local PASS |
| Servicio / profesional inactivo al confirmar | Test unitario | Local PASS |
| Timeline construido | Test unitario | Local PASS |
| Privacidad requester / gestor | Test unitario | Local PASS |
| `retrySafeTransition` con conflicto | Test unitario | Local PASS |
| Métricas agregadas sin PII / rango inválido | Test unitario | Local PASS |
| Guardas estáticas B4 (sin 048, sin service_role, hotfix, rutas) | Test estático | Local PASS |

> "Local PASS" significa verificación con fakes en la suite Kotlin. **No** sustituye el smoke
> funcional remoto contra Supabase, que sigue pendiente externo.

## Recordatorios: nota operativa

Los recordatorios quedan en estado **preparado** (`PREPARED` → `FIRED_PREPARED`) con
`pushClaimed = false`. La app **nunca** afirma que envió un push. El disparo real
(24 h / 2 h) y la entrega dependen de infraestructura externa (cron / edge / M06 push),
clasificada como pendiente externo.

## Migración 048

No se crea. Ver decisión en `docs/02-arquitectura/M12-recordatorios-endurecimiento-seguimiento.md`.

## Pendientes externos antes del cierre final de M12

- smoke completo agenda/disponibilidad
- solicitud mascota autorizada
- rechazo mascota ajena
- sobrecupo
- confirmación/rechazo/cancelaciones
- historial
- privacidad requester/gestor
- ausencia pagos e HC
- push M06 real / cron / enqueue M12 (si aplica)
- decisión: sin migración 048 en este bloque

## Estado permitido al finalizar

```text
M12 BLOQUE 4 CERRADO LOCALMENTE
M12 BLOQUE 3 SMOKE FUNCIONAL PENDIENTE EXTERNO
Validación estructural 047: 13/13 PASS
```

No se declara `M12 CERRADO`.
