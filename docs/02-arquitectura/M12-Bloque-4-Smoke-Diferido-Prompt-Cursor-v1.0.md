# Cursor — M12 Bloque 4 con smoke funcional diferido

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `304d4e0d81ecddf5a85c56c1cc49b4e65725f6cb`.
- Hotfix de autenticación cerrado y validado manualmente.
- Migración 047 aplicada correctamente en Supabase de pruebas.
- Validación estructural remota de 047: 13/13 PASS.
- Smoke funcional completo del Bloque 3: **PENDIENTE EXTERNO DIFERIDO POR DECISIÓN DEL USUARIO**.
- No afirmar `M12 BLOQUE 3 REMOTO PASS`.
- Estado correcto:

```text
M12 BLOQUE 3 — VALIDACIÓN ESTRUCTURAL REMOTA PASS
SMOKE FUNCIONAL — PENDIENTE EXTERNO
```

## Decisión operativa

Se autoriza continuar con M12 Bloque 4 sin ejecutar ahora el smoke funcional completo de agenda y turnos.

Esta decisión no convierte el smoke pendiente en PASS.

Todo hallazgo que requiera probar el flujo real contra Supabase debe clasificarse como:

```text
PENDIENTE_SMOKE_EXTERNO
```

No inventar resultados ni marcar como aprobado lo que no fue probado.

## Instrucción principal

Leer completo y ejecutar:

```text
@docs/02-arquitectura/M12-Bloque-4-Prompt-Cursor-v1.0.md
```

Aplicar las siguientes modificaciones al gate original:

1. Reemplazar el requisito:

```text
M12 BLOQUE 3 REMOTO PASS
Smoke de disponibilidad y turnos aprobado
```

por:

```text
M12 BLOQUE 3 — VALIDACIÓN ESTRUCTURAL REMOTA PASS
047 aplicada en Supabase de pruebas
13/13 controles estructurales PASS
Smoke funcional diferido como pendiente externo
```

2. No bloquear el Bloque 4 por el smoke diferido.
3. No afirmar que el smoke fue aprobado.
4. Mantener todas las demás restricciones del prompt original.

## Reglas obligatorias adicionales

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 040–047.
- No crear migración 048 salvo que exista un bloqueo SQL real e imprescindible.
- Si aparece una necesidad SQL real:
  - detener el cierre del bloque;
  - documentarla;
  - proponer un bloque separado para 048;
  - no crearla silenciosamente.
- No aplicar SQL remotamente.
- No generar APK.
- No iniciar M13.
- No implementar pagos ni historia clínica.
- No afirmar push real de M06 si la infraestructura no existe.
- No usar Supabase real en tests.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- Ejecutar pruebas focalizadas y una sola compilación Kotlin final.

## Entrega final adicional

Además de los puntos del prompt original, informar:

```text
Smoke funcional M12 Bloque 3: PENDIENTE EXTERNO
Riesgo aceptado: validación funcional remota diferida
Validación estructural 047: 13/13 PASS
```

En la documentación final del Bloque 4 debe quedar una sección visible:

```text
Pendientes externos antes del cierre final de M12
```

con:

- smoke completo de agenda y disponibilidad;
- solicitud con mascota autorizada;
- rechazo de mascota ajena;
- sobrecupo;
- confirmación, rechazo y cancelaciones;
- historial;
- privacidad requester/gestor;
- ausencia de pagos e historia clínica.

## Commit esperado

```text
feat(m12): harden appointments and reminders
```

## Estado permitido al finalizar

Si todo lo local pasa:

```text
M12 BLOQUE 4 CERRADO LOCALMENTE
M12 BLOQUE 3 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

No declarar `M12 CERRADO` todavía.
