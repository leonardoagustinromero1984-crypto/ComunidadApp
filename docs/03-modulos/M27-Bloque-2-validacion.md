# M27 Bloque 2 — Validación

Validaciones locales:

- mappers Supabase → modelos públicos (sin owner en proyección pública);
- `SupabaseM27IntegrationRepository` implementa contrato del repositorio;
- `DataProvider` enruta a Supabase cuando `useSupabase`.

Ejecutar:

```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m27.*" --tests "com.comunidapp.app.data.remote.supabase.m27.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

Remoto (cuando se autorice aplicar 075): ver `docs/05-operacion/M27-aplicacion-migracion-075-supabase.md`.
