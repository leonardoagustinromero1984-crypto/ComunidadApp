# KMP-IOS — Bloque 27 auditoría (Quiet hours / prefs completion)

Fuente: `026` `m06_update_preference` + Android NotificationPreference.

## QUIET_HOURS

```text
QUIET_HOURS = REAL_REMOTE
```

Campos: `quiet_hours_start`, `quiet_hours_end`, `quiet_hours_days` (default all days si null), `timezone`, `marketing_consent`.

## UI SHARED

- Horario silencioso (enable + start/end + timezone)
- Marketing consent
- Push toggles (KMP-25)
- Days picker: no UI — null → SQL default 1..7

## NO

- Cambiar permiso iOS sistema
- Inventar campos
- SQL nuevo
