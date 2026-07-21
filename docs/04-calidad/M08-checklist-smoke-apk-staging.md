# M08 — Checklist smoke APK staging

**Producto:** LeoVer

```text
M08 ETAPA 4D — BACKEND STAGING Y APK DISTRIBUIBLE LISTOS
SMOKE APK STAGING MANUAL — PENDIENTE
PRODUCCIÓN NO MODIFICADA
```

Histórico (pre-apply):
```text
M08 ETAPA 4D — PREPARACIÓN STAGING LISTA
APPLY 035/036 — PENDIENTE DE CONFIRMACIÓN MANUAL
APK DISTRIBUIBLE — PENDIENTE DEL APPLY
```

## Entrada (automática — completada)

| # | Criterio | Estado | Observación |
|---|---|---|---|
| E1 | Apply 035/036 confirmado | PASS | Humano + historial 001–036 |
| E2 | Matrices 035/036 `runner_summary` PASS | PASS | FAIL=0 en ambas |
| E3 | Staging HTTPS / sin hosts locales | PASS | `local.properties` gitignored |
| E4 | Solo publishable/anon; sin service_role | PASS | Validación Gradle + quality |
| E5 | `testStagingDebugUnitTest` / lint / assemble | PASS | 627 tests, 0 failures |
| E6 | APK `apk/LeoVer-M08-Staging-debug.apk` | PASS | Ignorado por Git |

Pruebas mutantes/JWT de comportamiento: **NO EJECUTADO** en matriz (diferidas a este smoke).

## Smoke en cualquier celular (solo internet)

Instalar `apk/LeoVer-M08-Staging-debug.apk` (**LeoVer Staging**, `com.comunidapp.app.staging`).
Sin emulador, sin PC, sin USB, sin `adb reverse`, sin Supabase local.

| # | Paso | Resultado | Observación |
|---|---|---|---|
| 1 | Enviar APK por Drive, WhatsApp, correo o cable | NO EJECUTADO | |
| 2 | Instalar en teléfono sin entorno de desarrollo | NO EJECUTADO | |
| 3 | Desconectar USB | NO EJECUTADO | |
| 4 | PC apagada o Supabase local detenido | NO EJECUTADO | |
| 5 | Abrir LeoVer Staging | NO EJECUTADO | |
| 6 | Registrar usuario de prueba | NO EJECUTADO | |
| 7 | Iniciar sesión | NO EJECUTADO | |
| 8 | Abrir Mis mascotas | NO EJECUTADO | |
| 9 | Crear mascota | NO EJECUTADO | |
| 10 | Verificar listado | NO EJECUTADO | |
| 11 | Editar perfil | NO EJECUTADO | |
| 12 | Editar salud | NO EJECUTADO | |
| 13 | Guardar microchip | NO EJECUTADO | |
| 14 | Provocar conflicto de microchip | NO EJECUTADO | |
| 15 | Agregar avatar | NO EJECUTADO | |
| 16 | Cerrar y reabrir la aplicación | NO EJECUTADO | |
| 17 | Verificar persistencia remota | NO EJECUTADO | |
| 18 | Logout / login | NO EJECUTADO | |
| 19 | Archivar mascota | NO EJECUTADO | |
| 20 | Comprobar que desaparece de activas | NO EJECUTADO | |
| 21 | Probar en Wi-Fi | NO EJECUTADO | |
| 22 | Probar con datos móviles | NO EJECUTADO | |
| 23 | Probar en un segundo teléfono | NO EJECUTADO | |
| 24 | Confirmar: no PC / USB / adb reverse | NO EJECUTADO | Técnicamente preparado |
| 25 | Confirmar: no muestra datos ajenos | NO EJECUTADO | |
| 26 | Registrar crashes o errores visibles | NO EJECUTADO | |

Resultados permitidos por fila: **PASS** / **FAIL** / **NO EJECUTADO** + observación.

## Salida

- [ ] Registrar dispositivo, versión, fecha y resultado fuera del APK.
- [ ] Eliminar datos sintéticos mediante el procedimiento controlado.
- [ ] Declarar smoke PASS solo si no hay fallos bloqueantes.

Mientras el smoke manual no se complete:

```text
SMOKE APK STAGING MANUAL — PENDIENTE
```

**No** declarar: `M08 ETAPA 4D — CERRADA` ni `SMOKE APK STAGING — PASS`.
