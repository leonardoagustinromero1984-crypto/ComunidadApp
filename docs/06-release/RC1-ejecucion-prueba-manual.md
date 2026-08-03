# RC1 — Ejecución de prueba manual

**APK:** `artifacts\rc1\LeoVer-RC1-local-debug.apk`  
**SHA-256:** `8B2D036C7A64E361E0B2331C6A45A9A71EF1986988AB8AC36E89F30FA63091BD`  
**Plan base:** `RC1-plan-prueba-manual.md`  
**Estado global:** prueba física **NO ejecutada** en esta etapa.

> Completar columnas dispositivo/cuenta/fecha/resultado tras instalar la APK en hardware real.

| ID | Recorrido | Precondiciones | Dispositivo | Android | Cuenta | Fecha | Resultado | Evidencia | Problema | Severidad | Reproducción | Corrección |
|----|-----------|----------------|-------------|---------|--------|-------|-----------|-----------|----------|-----------|--------------|------------|
| R01 | Registro / login | APK instalada | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R02 | Crear mascota | Sesión activa | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R03 | Editar mascota | Mascota creada (R02) | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R04 | Comunidad | Sesión activa | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R05 | Adopción | Sumate accesible | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R06 | Mascota perdida | Sumate/Publicar | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R07 | Refugio | Sumate M16 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R08 | Evento | Sumate M18 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R09 | Mensajería | Comunidad M20 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R10 | Prestador | Comunidad M22 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R11 | Reserva | M22/M23 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R12 | Marketplace | Comunidad M25 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R13 | Pedido sin pago | Carrito M25 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R14 | IA con stub | Comunidad M26 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R15 | Integraciones sandbox | Comunidad M27 | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R16 | Administración básica | Usuario staff (si disponible) | — | — | — | — | PENDIENTE | — | — | — | — | — |
| R17 | Cerrar sesión | Sesión activa | — | — | — | — | PENDIENTE | — | — | — | — | — |

## Estados permitidos

`PENDIENTE` · `PASS` · `FAIL` · `BLOQUEADO` · `NO APLICA`

## Notas

- Usar datos de prueba; no PII real.
- Pagos M24 fuera de alcance.
- Registrar incidencias en `RC1-backlog-hallazgos.md` si corresponde.
