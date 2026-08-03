# RC1 — Instalación en Android

APK de entrega: `artifacts\rc1\LeoVer-RC1-local-debug.apk`  
**No distribuir públicamente.** Uso interno de prueba manual RC1.

## Requisitos

- Android **8.0+** (API 26+, según minSdk)
- Espacio libre ~35 MB
- Conexión a Internet recomendada (backend staging HTTPS)

## Pasos

1. **Transferir** el archivo `LeoVer-RC1-local-debug.apk` al teléfono (USB, correo interno, Drive privado, etc.).
2. En el teléfono, abrir **Ajustes → Seguridad** (o equivalente) y **permitir temporalmente** la instalación desde esa fuente (archivos / navegador / gestor usado).
3. Abrir el APK desde el gestor de archivos o la notificación de descarga.
4. Confirmar **Instalar**.
5. Abrir **LeoVer Local** desde el launcher.
6. Conceder permisos **solo cuando la app los solicite** (p. ej. notificaciones).
7. Usar **cuentas y datos de prueba**; no ingresar información personal sensible real.
8. Anotar **modelo de dispositivo** y **versión de Android** en `RC1-ejecucion-prueba-manual.md`.
9. Si existe una instalación previa con **distinta firma** (otro APK debug/release), **desinstalar** la versión anterior antes de instalar.
10. Al terminar las pruebas, **desactivar** de nuevo la instalación desde fuentes desconocidas.

## Conflictos de firma

Esta APK usa la firma **debug** del entorno que la generó. Otra máquina o variante (`stagingDebug`) puede producir firma distinta → desinstalar antes.

## Qué esperar

| Aspecto | Comportamiento |
|---------|----------------|
| Tipo | APK **debug** (`application-debuggable`) |
| Nombre en launcher | **LeoVer Local** |
| Pagos M24 | **No existen** |
| OAuth M27 | **Simulado (stub)** |
| Webhooks M27 | **No se envían a URLs externas reales** (entrega simulada) |
| IA M26 | **Stub**; sin motor externo productivo |
| Marketplace / donaciones | Flujos **sin procesamiento de dinero** |

## Backend

Esta build `localDebug` usa credenciales **staging** remotas (HTTPS) cuando no hay URL local válida. **Producción no está configurada** para esta variante.

## Seguridad

- No compartir el APK fuera del equipo de prueba.
- No publicar en tiendas ni enlaces públicos.
- No usar contraseñas personales reales durante la prueba.

## Desinstalación

Ajustes → Aplicaciones → LeoVer Local → Desinstalar.

## Siguiente paso

Ejecutar los 17 recorridos documentados en `RC1-ejecucion-prueba-manual.md`.
