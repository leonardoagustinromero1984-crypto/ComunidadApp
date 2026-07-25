# M14 — Plan funcional y técnico

## Objetivo

Fundación local del pasaporte e identidad verificable de mascotas sobre M08, sin historia clínica y sin rehacer M09.

## Alcance Bloque 1

- Dominio, estados, validadores, errores.
- Generador local `LV-AR-YYYY-XXXXXXXX` + `publicCode` distinto.
- Contratos + fakes in-memory.
- Proyección pública redactada.
- ViewModels, UI, rutas `m14/*`, acceso desde detalle de mascota.
- Permisos constantes; hooks M06/M07 preparados.
- Sin SQL / sin migración 050 / sin Supabase real.

## Exclusiones

Historia clínica, verificación remota, QR remoto, lookup público real, pagos, chat, GPS, autoverificación, adopción M09, M15.

## Arquitectura B1

```text
UI m14/* → ViewModels → MockM14*Repository → M14MemoryStore
                              ↘ M14PublicProjectionService
                              ↘ M14PassportNumberGenerator
                              ↘ M08 Pet (InMemoryDataStore)
```

Supabase path → `INFRASTRUCTURE_UNAVAILABLE` hasta 050.

## Dependencias

M01/M02 auth, M08 mascota/responsables, M05 media refs, M07 eventos, M03/M04 (moderación futura), M12 (emisor futuro).

## Propuesta Bloque 2 (exacta)

1. Migración **050** (solo con aprobación explícita): tablas pasaporte, credenciales, solicitudes, decisiones, historial.
2. RLS/RPC SECURITY DEFINER; actor `auth.uid()`; sin DML cliente.
3. Autoridad M08/M03/M04 cableada en SQL.
4. Repositorios Supabase reales + DataProvider.
5. Validación estructural remota; sin smoke inventado.
6. No modificar 001–049.

## Limitaciones B1

- Autoridad compartida simplificada (`ownerId`).
- Verificación local solo con emisor distinto del creador.
- Sin push real; sin QR.

## Pendientes

Smoke M12/M13 externos; cierre oficial M12/M13; persistencia 050; Bloques 3–4.
