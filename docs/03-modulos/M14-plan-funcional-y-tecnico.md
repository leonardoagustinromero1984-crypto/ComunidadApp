# M14 — Plan funcional y técnico

## Objetivo

Pasaporte e identidad verificable de mascotas sobre M08, sin historia clínica y sin rehacer M09.

## Alcance Bloque 1 (cerrado)

- Dominio, estados, validadores, errores.
- Generador local `LV-AR-YYYY-XXXXXXXX` + `publicCode` distinto.
- Contratos + fakes in-memory.
- Proyección pública redactada.
- ViewModels, UI, rutas `m14/*`, acceso desde detalle de mascota.
- Permisos constantes; hooks M06/M07 preparados.
- Sin SQL en B1.

## Alcance Bloque 2 (cerrado localmente)

- Migración **050** creada (no aplicada remotamente).
- Tablas: pasaportes, credenciales, solicitudes, decisiones (prep B3), historial.
- 18 RPC cliente; `anon` solo en proyección pública.
- Autoridad M08 vía `m08_actor_has_active_responsibility` + permisos `passport.*`.
- `passport_number` / `public_code` server-side con `extensions.gen_random_bytes`.
- Repositorios Supabase + DataProvider; mocks conservados.
- Guard CI highest = **050**.

## Exclusiones

Historia clínica, resolución remota de verificaciones, QR remoto, lookup con PII, pagos, chat, GPS, autoverificación, adopción M09, M15.

## Arquitectura B2

```text
UI m14/* → ViewModels → SupabaseM14*Repository → SupabaseM14RemoteDataSource → RPC 050
                       ↘ MockM14*Repository (local/fake)
```

## Dependencias

M01/M02 auth, M08 mascota/responsables, M05 media refs, M07 eventos, M03/M04 (moderación), M12 (emisor futuro).

## Propuesta Bloque 3 (exacta)

1. RPC de resolución remota de verificaciones (approve/reject) sobre `pet_passport_verification_decisions`.
2. Emisión autorizada / rechazo / revocación con concurrencia e idempotencia.
3. Historial completo de decisiones; sin autoverificación.
4. publicCode + QR seguro sin exponer PII.
5. UI remota de cola de verificación (M12/org).
6. Migración **051** solo si hace falta SQL adicional (no editar 050 tras apply).
7. Smoke funcional remoto tras apply de 050 (+051 si aplica).

## Limitaciones B2

- 050 no aplicada; smoke remoto pendiente.
- Sin resolución de verificaciones.
- Sin historia clínica ni QR real.

## Pendientes

Aplicación remota 050; smoke M12/M13 externos; cierre oficial M12/M13; Bloques 3–4; M15 no iniciado.
