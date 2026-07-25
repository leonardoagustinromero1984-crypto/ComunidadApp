# Cursor — M13 Bloque 4: endurecimiento, métricas y cierre técnico

## Gate obligatorio

No ejecutar hasta confirmar:

```text
MIGRACIÓN 049 APLICADA EN SUPABASE DE PRUEBAS
VALIDACIÓN ESTRUCTURAL 049: 14/14 PASS
```

El smoke funcional remoto de M13 puede quedar pendiente solo por decisión expresa del usuario. No inventar PASS.

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama `main`.
- HEAD mínimo `14cec199cb7cab9faf2848c6ec6899662128e3ec`.
- M13 Bloques 1–3 cerrados localmente.
- Migraciones 048 y 049 aplicadas y estructuralmente validadas.
- Migraciones 001–049 intactas.
- Android CI no debilitado.
- M12 smoke y cierre oficial pendientes.

## Objetivo

Implementar M13 Bloque 4:

- endurecimiento final;
- privacidad y redacción;
- expiraciones;
- métricas agregadas sin PII;
- preparación M06;
- auditoría M07 compatible;
- regresión focalizada;
- documentación final;
- cierre técnico local de M13.

No declarar el cierre oficial de M13 mientras los smokes externos sigan pendientes.

## Reglas

- Trabajar directamente sobre `main`.
- Sin ramas, backups ni commits intermedios.
- Un único commit y push.
- No modificar migraciones 001–049.
- No crear migración 050 por defecto.
- Si aparece una necesidad SQL real, detener el cierre y proponer 050.
- No aplicar SQL remotamente.
- No generar APK.
- No usar IA, biometría, chat, pagos o GPS en segundo plano.
- No exponer ubicación exacta, contacto o notas privadas.
- No cerrar automáticamente casos Lost/Found.
- No declarar M12 cerrado.
- No iniciar M14.
- Pruebas focalizadas y una sola compilación Kotlin final.

## Paso 1 — Auditoría integral

Auditar M13 completo:

- legacy Lost/Found;
- dominio;
- scoring;
- repositorios mock y Supabase;
- DataProvider;
- navegación;
- pantallas;
- permisos;
- autoridad;
- 048;
- 049;
- decisiones;
- historial;
- concurrencia;
- idempotencia;
- privacidad;
- M05;
- M06;
- M07;
- errores;
- tests;
- documentación.

Clasificar:

```text
PASS
CORREGIBLE_LOCAL
BLOQUEANTE_SQL
PENDIENTE_EXTERNO
FUERA_DE_ALCANCE
```

## Paso 2 — Privacidad y seguridad

Confirmar y endurecer:

- sin coordenadas exactas públicas;
- sin contacto público;
- sin identidad completa del reportante;
- notas privadas solo para autoridad;
- media segura M05;
- sin URLs arbitrarias;
- sin service_role;
- sin secretos;
- actor desde auth.uid();
- sin DML directo;
- helpers protegidos;
- sin autoconfirmación;
- sin cierre automático del caso.

## Paso 3 — Expiraciones

Implementar una política local y contractual de expiración para:

- avistamientos `ACTIVE`;
- candidatos `PROPOSED`;
- candidatos `UNDER_REVIEW` cuando corresponda.

Requisitos:

- fechas y ventanas configurables;
- zona horaria explícita;
- idempotencia;
- no tocar estados finales;
- sin scheduler real si la infraestructura no existe;
- documentar cron/scheduler como `REQUIERE_INFRA_EXTERNA`.

No crear SQL por defecto.

## Paso 4 — Métricas sin PII

Agregar métricas agregadas:

- avistamientos por estado;
- candidatos por nivel;
- candidatos por estado;
- tasa de confirmación;
- tiempo medio hasta revisión;
- tiempo medio hasta decisión;
- expirados;
- distribución de razones de coincidencia;
- rango temporal validado.

No incluir:

- nombres;
- correos;
- teléfonos;
- coordenadas;
- notas;
- identificadores de usuario en salida agregada.

## Paso 5 — M06

Preparar eventos/recordatorios donde la infraestructura real lo permita:

```text
M13_MATCH_PROPOSED
M13_MATCH_REVIEW_OPENED
M13_MATCH_CONFIRMED
M13_MATCH_REJECTED
M13_MATCH_INCONCLUSIVE
M13_SIGHTING_EXPIRED
M13_MATCH_EXPIRED
```

Reglas:

- reutilizar contratos existentes;
- no afirmar push real;
- idempotencia;
- `INFRASTRUCTURE_UNAVAILABLE` cuando corresponda;
- no ampliar silenciosamente el catálogo M07 si existe techo canónico.

Si ampliar el catálogo M07 requiere una decisión separada, documentarlo como pendiente y mantener integración best-effort.

## Paso 6 — UI

Endurecer:

- redacción por rol;
- timeline;
- estados finales;
- próximo paso;
- score y razones;
- acciones según autoridad;
- mensajes de conflicto;
- expiración;
- métricas para gestores;
- estados carga/error/vacío.

## Paso 7 — Pruebas focalizadas

Cubrir:

- privacidad por rol;
- expiraciones;
- idempotencia;
- métricas sin PII;
- rangos inválidos;
- eventos M06 preparados;
- M07 best-effort;
- no autoconfirmación;
- no cierre automático;
- 048/049 intactas;
- sin 050;
- regresión Bloques 1–3;
- auth y CI guards mínimos.

## Paso 8 — Validación

Ejecutar pruebas focalizadas.

Ejecutar una sola vez:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No generar APK.

Ejecutar:

```powershell
git diff --check
git status -sb
git diff --stat
```

## Paso 9 — Documentación

Crear:

```text
docs/03-modulos/M13-cierre-tecnico.md
docs/02-arquitectura/M13-Bloque-4-validacion.md
docs/02-arquitectura/M13-matriz-funcional-final.md
docs/05-operacion/M13-smoke-funcional-pendiente-cierre.md
```

Actualizar:

```text
docs/03-modulos/M13-avistamientos-y-coincidencias.md
docs/03-modulos/M13-plan-funcional-y-tecnico.md
docs/01-producto/D01-Modulos-y-Orden.md
```

Registrar:

- smokes remotos pendientes;
- limitaciones M06/M07;
- criterios de reapertura;
- criterio de cierre oficial;
- M12 todavía pendiente;
- M14 no iniciado.

## Paso 10 — Git

Commit único:

```text
feat(m13): harden sightings and matching
```

Push:

```powershell
git push origin main
```

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría.
3. Privacidad.
4. Seguridad.
5. Expiraciones.
6. Métricas.
7. M06.
8. M07.
9. UI.
10. Errores.
11. Tests.
12. Total PASS.
13. Compilación.
14. Docs.
15. Migraciones.
16. SQL.
17. Limitaciones.
18. Smokes pendientes.
19. Criterio de cierre oficial.
20. SHA.
21. Push.
22. `git status -sb`.

## Estado final permitido

```text
M13 BLOQUE 4 CERRADO LOCALMENTE
M13 CIERRE TÉCNICO LOCAL COMPLETADO
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M13 CIERRE OFICIAL PENDIENTE
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

Si aparece una necesidad SQL:

```text
M13 BLOQUE 4 BLOQUEADO — MIGRACIÓN 050 REQUERIDA
```
