# M23 Bloque 2 — Validación

## Verificación local

```bat
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m23.*"
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

La suite incluye `M23BookingRemoteMapperTest`: fixtures JSON sin red para reservas, reglas, excepciones y slots públicos.

## Controles estructurales a ejecutar tras aplicar 068 en staging

- Confirmar las cuatro tablas `m23_*` y sus índices.
- Verificar que `anon` y `authenticated` no pueden leer tablas internas directamente.
- Validar que las RPC públicas no exponen `customer_user_id`, `customer_note` ni `organization_id`.
- Repetir dos requests con el mismo `client_request_id`: debe retornarse la misma reserva.
- Intentar dos reservas solapadas concurrentes para el mismo prestador: una debe fallar con `M23_SLOT_UNAVAILABLE`.
- Probar cliente, propietario de prestador, miembro con `booking.view` y actor sin permiso.

## Estado

```text
M23 BLOQUE 1 + BLOQUE 2 — IMPLEMENTADOS LOCALMENTE
MIGRACIÓN 068 — CREADA, NO APLICADA
BLOQUE 3 — NO INICIADO
M24 — NO INICIADO
```
