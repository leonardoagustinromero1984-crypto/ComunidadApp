# Cursor — Hotfix de autenticación del APK después de M12 Bloque 3

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD esperado: `248ef3cd10d5720632be5ff219292db335733100`.
- M12 Bloque 3: cerrado localmente y pusheado.
- Migración 047: creada, pero no aplicada remotamente.
- Working tree esperado: limpio y alineado con `origin/main`.
- Problema real: el APK instalado muestra permanentemente un mensaje equivalente a:

```text
Ocurrió un problema de autenticación
```

## Objetivo

Realizar un hotfix focalizado para identificar y corregir la causa real del fallo de autenticación del APK `localDebug`.

El hotfix debe dejar:

- configuración Supabase válida en el APK;
- restauración de sesión estable;
- login funcional;
- sesión inválida tratada sin bucle;
- errores 401, 403, red y configuración diferenciados;
- un APK `localDebug` listo para prueba manual.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y un único push.
- No modificar migraciones 040–047.
- No crear migraciones nuevas.
- No aplicar SQL remoto.
- No continuar M12 Bloque 4.
- No agregar funcionalidades de agenda o turnos.
- No hacer refactors masivos.
- No ejecutar Supabase local.
- No ejecutar emulador.
- No ejecutar lint, JaCoCo ni toda la suite.
- No exponer ni versionar secretos.
- Generar solamente un APK `localDebug` al final.
- No agregar el APK a Git.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Resultado esperado:

```text
main
248ef3cd10d5720632be5ff219292db335733100
```

Si hay cambios locales ajenos:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Localizar el mensaje y la ruta de error

Buscar en todo el repositorio:

```text
problema de autenticación
autenticación
authentication problem
AUTHENTICATION
AUTH_ERROR
UNAUTHORIZED
invalid JWT
401
403
refresh token
session expired
```

Identificar exactamente:

- pantalla o componente que muestra el mensaje;
- ViewModel que lo emite;
- repositorio y mapper;
- excepción original;
- código HTTP;
- si ocurre al arrancar, restaurar sesión, iniciar sesión o consultar datos;
- si el mensaje genérico está ocultando un error de configuración, red o RLS.

No cambiar el texto antes de encontrar la causa.

## Paso 3 — Auditar variante y configuración de Supabase

Revisar:

```text
settings.gradle / settings.gradle.kts
build.gradle / build.gradle.kts
app/build.gradle / app/build.gradle.kts
gradle.properties
local.properties
BuildConfig
productFlavors
buildTypes
localDebug
SupabaseClient
DataProvider
AuthRepository
SessionManager
```

Verificar para `localDebug`:

- `SUPABASE_URL` presente;
- `SUPABASE_ANON_KEY` presente;
- `useSupabase = true`;
- URL con formato `https://<project-ref>.supabase.co`;
- URL y anon key pertenecen al mismo proyecto;
- no se usa localhost, `10.0.2.2` ni Supabase local;
- no existe un valor vacío o placeholder;
- el APK recibe los valores en BuildConfig o mecanismo equivalente;
- las comillas y escapes de Gradle no alteran la key;
- no se hardcodean secretos en archivos versionados.

No imprimir valores completos.

## Paso 4 — Diagnóstico seguro

Agregar un diagnóstico seguro reutilizable que registre solamente:

```text
buildType
flavor
useSupabase
urlPresent
supabaseHost
anonKeyPresent
anonKeyLength
sessionPresent
accessTokenPresent
accessTokenExpiresAt
exceptionClass
httpStatus
```

Nunca registrar:

```text
anon key completa
access token
refresh token
contraseña
cabeceras Authorization
datos privados
```

Si el proyecto dispone de una pantalla interna de diagnóstico segura, reutilizarla. No mostrar esta información a usuarios finales en producción.

## Paso 5 — Un único SupabaseClient

Confirmar que:

- existe un único cliente compartido;
- Auth, Postgrest y Storage reutilizan el mismo cliente;
- no se crean clientes con URL/key diferentes;
- DataProvider no mezcla mocks y Supabase;
- `useSupabase=true` no termina usando repositorios mock de autenticación;
- el cliente se crea una sola vez y no se recrea por recomposición.

Corregir únicamente inconsistencias reales.

## Paso 6 — Estado de sesión

Modelar o corregir estados explícitos equivalentes a:

```text
LOADING_SESSION
AUTHENTICATED
UNAUTHENTICATED
CONFIGURATION_ERROR
NETWORK_ERROR
```

Reglas:

- no mostrar error permanente mientras se restaura la sesión;
- ausencia de sesión válida conduce a `UNAUTHENTICATED`, no a error;
- refresh token inválido limpia solo la sesión y vuelve a login;
- access token vencido intenta refresh una vez de manera controlada;
- no entrar en bucle infinito de refresh;
- una excepción de red conserva un estado recuperable;
- la navegación responde al estado real.

## Paso 7 — Login

Revisar el flujo real:

- email normalizado con trim y lowercase cuando corresponda;
- contraseña sin alteraciones;
- cliente configurado;
- respuesta de Supabase Auth;
- persistencia de sesión;
- actualización del AuthState;
- navegación posterior;
- botón protegido contra doble envío;
- mocks deshabilitados cuando `useSupabase=true`.

Diferenciar:

```text
credenciales inválidas
correo no confirmado
sesión vencida
refresh token inválido
configuración ausente
sin conexión
timeout
servidor no disponible
401
403/RLS
```

## Paso 8 — 401, 403 y errores de red

Aplicar reglas:

- 401 por sesión/token inválido:
  - intentar refresh controlado cuando corresponde;
  - si falla, limpiar sesión;
  - volver al login;
  - mostrar “Tu sesión venció. Iniciá sesión nuevamente.”;

- 403:
  - no cerrar sesión automáticamente;
  - mapear como permiso/RLS;
  - mostrar “No tenés permisos para realizar esta acción.”;

- error de red/timeout:
  - no limpiar sesión;
  - mostrar mensaje de conexión;
  - permitir reintento;

- configuración inválida:
  - no intentar login;
  - mostrar mensaje de versión/configuración.

No mapear todos los errores como autenticación.

## Paso 9 — Manifest y red

Comprobar:

```text
android.permission.INTERNET
Network Security Config
HTTPS
cleartextTraffic
R8/ProGuard de localDebug
```

Confirmar que el APK físico no depende de:

```text
localhost
127.0.0.1
10.0.2.2
servicios disponibles solamente en la PC
```

## Paso 10 — Mensajes de usuario

Reemplazar el mensaje genérico solo después de corregir el flujo.

Mensajes equivalentes:

```text
La configuración de Supabase no está disponible en esta versión.
El correo o la contraseña son incorrectos.
Tu correo todavía no fue confirmado.
Tu sesión venció. Iniciá sesión nuevamente.
No pudimos conectarnos. Revisá tu conexión.
No tenés permisos para realizar esta acción.
```

No mostrar excepciones técnicas, URLs internas, keys ni tokens.

## Paso 11 — Pruebas focalizadas

Crear o actualizar suites reales para cubrir:

1. URL ausente;
2. anon key ausente;
3. URL/key válidas;
4. `useSupabase=false`;
5. cliente único;
6. sesión inexistente;
7. sesión restaurada;
8. access token vencido;
9. refresh exitoso;
10. refresh inválido;
11. limpieza controlada de sesión;
12. prevención de loop infinito;
13. login correcto;
14. credenciales inválidas;
15. correo no confirmado;
16. error de red;
17. timeout;
18. 401;
19. 403;
20. configuración inválida;
21. navegación a login;
22. navegación autenticada;
23. doble envío;
24. no exposición de secretos;
25. `localDebug` usa Supabase real;
26. migraciones 040–047 intactas.

Ejecutar únicamente las suites focalizadas reales de autenticación/configuración y una guarda de M12 que confirme que 047 no cambió.

No ejecutar toda la suite.

## Paso 12 — Compilación y APK

Ejecutar una sola compilación Kotlin:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

Si pasa, generar solamente:

```powershell
.\gradlew.bat assembleLocalDebug
```

Informar la ruta exacta del APK, normalmente equivalente a:

```text
app\build\outputs\apk\local\debug\app-local-debug.apk
```

No asumir la ruta: informar la real.

## Paso 13 — Revisión de seguridad

Ejecutar:

```powershell
git status
git diff --stat
git diff --check
```

Verificar:

- sin keys reales;
- sin tokens;
- sin contraseñas;
- sin archivos `.env` nuevos;
- sin cambios SQL;
- 040–047 intactas;
- sin APK staged;
- sin logs con secretos;
- sin cambios funcionales M12 ajenos al hotfix.

Agregar únicamente archivos de código, tests y documentación necesarios.

## Paso 14 — Documentación

Crear:

```text
docs/05-operacion/Auth-diagnostico-y-hotfix-APK-localDebug.md
```

Registrar:

- síntoma;
- causa raíz;
- variante afectada;
- configuración encontrada;
- corrección;
- comportamiento de sesión;
- mapeo 401/403/red;
- pruebas;
- compilación;
- APK;
- pasos de validación manual;
- confirmación de que no se versionaron secretos.

No escribir valores sensibles.

## Paso 15 — Git

Crear un único commit:

```powershell
git commit -m "fix(auth): resolve apk authentication failure"
git push origin main
```

Si el push falla:

- no crear rama;
- no rebasear;
- no crear otro commit;
- entregar SHA y dejar pendiente únicamente el push.

## Entrega final

Informar:

1. Estado inicial.
2. Punto exacto donde se mostraba el error.
3. Excepción/código real.
4. Causa raíz.
5. Variante afectada.
6. Fuente de configuración Supabase.
7. Estado de URL/key sin revelar valores.
8. Corrección realizada.
9. Cliente Supabase compartido.
10. Restauración de sesión.
11. Manejo de refresh inválido.
12. Manejo de 401.
13. Manejo de 403.
14. Manejo de red/timeout.
15. Mensajes corregidos.
16. Tests ejecutados.
17. Cantidad aprobada.
18. Compilación.
19. APK generado.
20. Ruta exacta del APK.
21. Archivos principales.
22. Confirmación de ausencia de secretos.
23. Confirmación de 040–047 intactas.
24. SHA.
25. Push.
26. `git status -sb`.
27. Pasos exactos para instalar y validar el APK.

No aplicar 047.
No comenzar M12 Bloque 4.
