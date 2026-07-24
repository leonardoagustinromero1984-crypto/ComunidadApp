# LeoVer M12 — Cierre técnico final (smoke diferido)

## Estado declarado

```text
M12 CIERRE TÉCNICO LOCAL COMPLETADO
SMOKE PENDIENTE EXTERNO
CIERRE OFICIAL PENDIENTE
```

**No se declara M12 CERRADO.** El cierre oficial queda condicionado al smoke
funcional del Bloque 3 contra Supabase, que sigue **PENDIENTE EXTERNO** por
decisión del usuario.

## Alcance del cierre técnico

Bloques 1–4 cerrados localmente en `HEAD`:

- **Bloque 1** — Modelos y fundaciones de veterinarias (`VeterinaryModels`,
  permisos `veterinary.profile.*`, directorio y navegación base).
- **Bloque 2** — Persistencia de perfiles y servicios (repos Block 2,
  migración `046_m12_veterinary_profiles_and_services.sql`).
- **Bloque 3** — Agenda, disponibilidad y turnos (repos de agenda/turnos,
  migración `047_m12_veterinary_appointments_and_availability.sql`).
- **Bloque 4** — Endurecimiento, recordatorios preparados (sin push real),
  métricas operativas sin PII y timeline.

## Lo que este cierre NO incluye

- Sin nuevas features fuera de Bloques 1–4.
- Sin migración `048` ni modificación de `040`–`047` (ya aplicadas).
- Sin APK ni aplicación de SQL como parte de este cierre.
- Sin pagos (MercadoPago) ni historia clínica en fuentes M12.

## Pendientes externos antes del cierre oficial

- **Smoke funcional Bloque 3**: PENDIENTE EXTERNO. Ver
  `docs/05-operacion/M12-smoke-funcional-pendiente-cierre.md`.
- **Push M06 real / cron / scheduler externo**: la app nunca afirma haber
  entregado un push (`pushClaimed = false`). El disparo real (24 h / 2 h) y la
  entrega dependen de infraestructura externa (cron / edge / M06 push),
  clasificada como PENDIENTE EXTERNO.

## Privacidad

- `requestNote` (nota privada del solicitante) solo es visible para el
  solicitante; se oculta a la clínica/gestor salvo autorización explícita.
- Las métricas operativas (`VeterinaryAppointmentOperationalMetrics`) no
  contienen PII: solo conteos por estado, ocupación y minutos promedio de
  confirmación.
- El contacto público solo se expone con opt-in (`publicContactEnabled`).

## Resultado permitido

```text
M12 CIERRE TÉCNICO LOCAL COMPLETADO / SMOKE PENDIENTE EXTERNO / CIERRE OFICIAL PENDIENTE
```
