# M22 Bloque 3 — Validación

## Reglas verificadas localmente
1. Un borrador sólo se publica si tiene al menos una sede activa y una oferta activa.
2. El catálogo y el detalle público sólo muestran perfiles `ACTIVE`.
3. Suspender elimina al prestador del catálogo; reactivarlo lo repone.
4. Archivar es idempotente y terminal: un perfil archivado no puede republicarse.
5. Sólo el propietario puede operar el ciclo de vida en el mock.
6. Los filtros de catálogo combinan categoría y ciudad sin exponer información interna.
7. Ofertas inactivas no intervienen en el resumen de precios.
8. El stub M06 informa indisponibilidad sin bloquear la operación.

## Comandos
```powershell
.\gradlew.bat testLocalDebugUnitTest --tests "com.comunidapp.app.domain.m22.*" --no-configuration-cache --max-workers=1 --console=plain
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Pendiente
Aplicar y endurecer la migración 066 solamente en Bloque 4, en un entorno autorizado.
