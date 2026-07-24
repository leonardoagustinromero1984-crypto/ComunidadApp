# Cursor — M12 cierre técnico condicionado con smoke diferido

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado confirmado

- Rama: `main`.
- HEAD mínimo: `d3bd12ed25af2892c0dfafe0ce1f44b2ac76ca18`.
- M12 Bloques 1–4 cerrados localmente.
- Migración 046 aplicada y validada.
- Migración 047 aplicada y validada estructuralmente: 13/13 PASS.
- Hotfix de autenticación cerrado y validado manualmente.
- Smoke funcional completo de agenda y turnos: **PENDIENTE EXTERNO DIFERIDO**.

## Objetivo

Ejecutar el cierre técnico documental y de regresión de M12 sin inventar el smoke funcional pendiente.

Este cierre debe dejar M12 técnicamente consolidado, pero no puede declarar `M12 CERRADO` hasta completar el smoke externo.

## Reglas obligatorias

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y push.
- No modificar migraciones 040–047.
- No crear migración 048.
- No aplicar SQL remoto.
- No generar APK.
- No iniciar M13.
- No implementar funcionalidad nueva.
- No implementar pagos ni historia clínica.
- No afirmar push/cron real de M06.
- No usar Supabase real en tests.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- Ejecutar pruebas focalizadas y una sola compilación Kotlin final.

## Instrucción principal

Leer completo:

```text
@docs/02-arquitectura/M12-Cierre-Final-Prompt-Cursor-v1.0.md
```

Ejecutar todo su alcance técnico, con estas modificaciones obligatorias:

1. Reemplazar el gate de smoke PASS por:

```text
M12 BLOQUE 3 VALIDACIÓN ESTRUCTURAL REMOTA 13/13 PASS
M12 BLOQUE 3 SMOKE FUNCIONAL PENDIENTE EXTERNO
Riesgo aceptado: validación funcional remota diferida
```

2. No bloquear la auditoría, regresión, compilación y documentación final por el smoke diferido.
3. No marcar el smoke como PASS.
4. No declarar `M12 CERRADO`.
5. Registrar explícitamente el riesgo y el checklist pendiente.

## Estado final permitido

Si toda la auditoría local, pruebas y compilación pasan:

```text
M12 CIERRE TÉCNICO LOCAL COMPLETADO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 CIERRE OFICIAL PENDIENTE
```

Si aparece un bloqueo real:

```text
M12 CIERRE TÉCNICO BLOQUEADO
```

## Documentación

Actualizar o crear según el prompt principal:

```text
docs/03-modulos/M12-cierre-final.md
docs/05-operacion/M12-validacion-final.md
docs/02-arquitectura/M12-matriz-funcional-final.md
```

En todos debe figurar:

```text
Smoke funcional de agenda y turnos: PENDIENTE EXTERNO
No se declara M12 CERRADO
```

## Guardas y pruebas

Crear y ejecutar las guardas de cierre final previstas, pero la guarda del smoke debe validar que esté documentado como pendiente externo, no como PASS.

## Git

Commit esperado:

```text
chore(m12): finalize technical closure with pending smoke
```

Push:

```powershell
git push origin main
```

## Entrega final

Informar todos los puntos del prompt principal y agregar:

```text
Smoke funcional M12 Bloque 3: PENDIENTE EXTERNO
Cierre técnico local: COMPLETADO/PENDIENTE
Cierre oficial M12: PENDIENTE
M13: NO INICIADO
```
