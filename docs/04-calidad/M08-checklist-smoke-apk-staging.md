# M08 — Checklist smoke APK staging

**Producto:** LeoVer

```text
M08 ETAPA 4D — PREPARACIÓN STAGING LISTA
APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL
APK DISTRIBUIBLE — PENDIENTE DEL APPLY
```

## Bloqueo previo

Este checklist no autoriza cambios remotos. **DO NOT RUN YET** los pasos
manuales de backup, historial, dry-run o apply:

```powershell
# NO ejecutar todavía — ver plan M08-plan-despliegue-staging-035-036.md
# backup fuera del repo → $HOME\LeoVerBackups\<fecha>_pre_m08_035_036\
# NO ejecutar: supabase migration list --linked
# NO ejecutar: supabase db push --linked --dry-run
# NO ejecutar: supabase db push --linked
```

## Entrada al smoke

- [ ] Apply 035/036 confirmado y completado por una persona autorizada.
- [ ] Validaciones 035 y 036 con `runner_summary PASS`.
- [ ] Pruebas mutantes/JWT autorizadas para datos controlados.
- [ ] `SUPABASE_STAGING_URL` usa HTTPS y no es host local.
- [ ] Solo publishable/anon key en Android; ningún secreto real en logs.
- [ ] `./gradlew testStagingDebugUnitTest lintStagingDebug assembleStagingDebug`
      finaliza correctamente.
- [ ] APK esperado: `apk/LeoVer-M08-Staging-debug.apk` (ignorado por Git).

## Smoke funcional

- [ ] Instalar LeoVer Staging en un dispositivo de prueba.
- [ ] Confirmar nombre **LeoVer Staging** e identificación separada.
- [ ] Iniciar sesión con una cuenta sintética autorizada para staging.
- [ ] Listar mascotas accesibles sin exponer mascotas ajenas.
- [ ] Crear mascota y verificar principal.
- [ ] Editar perfil y salud con permisos válidos.
- [ ] Confirmar rechazo sin capacidad suficiente.
- [ ] Archivar/restaurar y revisar estado/listado.
- [ ] Cerrar sesión y confirmar limpieza de sesión.
- [ ] Revisar que UI y logs no muestren claves, tokens ni datos sensibles.

## Salida

- [ ] Registrar dispositivo, versión, fecha y resultado fuera del APK.
- [ ] Eliminar datos sintéticos mediante el procedimiento controlado.
- [ ] Marcar APK distribuible solo si no hay fallos bloqueantes.

Mientras algún ítem de entrada esté pendiente, permanece vigente:
`APK DISTRIBUIBLE — PENDIENTE DEL APPLY`.
