# M18 — Preparación cierre global

**Estado:** preparación técnica completada (Bloques 1–4) — **cierre operativo PENDIENTE**

## Checklist remoto (no marcar PASS hasta aplicar 058)

### Pre-aplicación

- [ ] Entorno staging autorizado (no producción)
- [ ] Backup lógico tablas `m18_*` si re-aplicación parcial
- [ ] Verificar 058 no registrada en `schema_migrations`

### Aplicación 058

- [ ] Ejecutar `058_m18_community_events_and_registrations.sql` en orden
- [ ] Registrar versión 058 en `schema_migrations`
- [ ] Verificar tablas: `m18_community_events`, `m18_event_registrations`, `m18_event_reminders`
- [ ] Verificar permisos `event.view`, `event.manage`
- [ ] Verificar RLS activo en las 3 tablas
- [ ] Smoke RPC: `m18_list_public_events`, `m18_register_for_event`, `m18_get_capacity_summary`

### Post-aplicación app

- [ ] `DataProvider.useSupabase = true` en build de prueba
- [ ] Smoke repositorio remoto (sin APK obligatorio)
- [ ] Confirmar mock sigue PASS offline

### Brechas conocidas post-058

- Estados `ATTENDED` / `REJECTED`: solo mock hasta migración futura
- Promoción manual waitlist: automática en cancelación (058)
- M06 recordatorios: infra pendiente allowlist

## Veredicto objetivo

```
M18 BLOQUES 1–4 TÉCNICAMENTE COMPLETADOS
MIGRACIÓN 058 NO APLICADA (al cierre local)
VALIDACIÓN REMOTA PENDIENTE
CIERRE OPERATIVO GLOBAL PENDIENTE
M19 NO INICIADO
```
