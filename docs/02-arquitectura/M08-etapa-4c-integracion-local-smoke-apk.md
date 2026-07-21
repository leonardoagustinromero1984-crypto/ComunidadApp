# M08 Etapa 4C — Integración local y preparación smoke APK

**Producto:** LeoVer  
**Rama:** `m08/etapa-4c-integracion-local-smoke-apk`  
**Base:** `1446c87` (Etapa 4B)

```text
M08 ETAPA 4C — INTEGRACIÓN LOCAL AUTOMÁTICA PASS
SMOKE APK MANUAL — PENDIENTE
STAGING NO AUTORIZADO
```

## Entorno local

- Supabase local desde cero (`db reset`, sin `--linked`).
- Migraciones **001–036** (max **036**, sin 037).
- Matrices: `m08_validate_local_035.sql`, `m08_validate_local_036.sql` → FAIL=0.
- Fixtures: `scripts/sql/m08_prepare_local_smoke_fixtures.sql` (LOCAL TEST DATA ONLY).

## Configuración debug

- Credenciales solo en `local.properties` (gitignored).
- Emulador: `http://10.0.2.2:<API_PORT>` (puerto desde `supabase status -o env`).
- Dispositivo físico: `adb reverse` del puerto API.
- Cleartext **solo** en `app/src/debug/res/xml/network_security_config.xml` (10.0.2.2 / 127.0.0.1 / localhost).
- Release: cleartext OFF.
- **Prohibido:** `service_role` en Android.

## Repositorios

`DataProvider.petRepository` → `LegacyPetRepositoryAdapter` (RPC M08 035/036 + SELECT RLS).  
Sin CRUD PostgREST productivo sobre `pets`.

## APK smoke

- Origen: `app/build/outputs/apk/debug/app-debug.apk`
- Copia local (no trackeada): `apk/LeoVer-M08-Stage4C-debug.apk`
- Checklist: `docs/04-calidad/M08-checklist-smoke-apk-local.md`

## Smoke

La integración automática (reset/lint/matrices/tests/quality/APK) puede PASS.  
**SMOKE APK MANUAL queda PENDIENTE** hasta confirmación humana. No declarar SMOKE PASS aquí.

## Staging

**NO autorizado.** No apply remoto 035/036.

## Siguiente paso

Completar checklist smoke manual → luego Etapa 5 solo con autorización explícita.
