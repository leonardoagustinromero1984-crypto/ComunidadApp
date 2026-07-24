# Cursor — M13 Bloque 3: revisión, decisiones e historial remoto

## Gate obligatorio

No ejecutar hasta confirmar al menos:

```text
MIGRACIÓN 048 APLICADA EN SUPABASE DE PRUEBAS
VALIDACIÓN ESTRUCTURAL 048: 13/13 PASS
```

El smoke funcional de Bloque 2 puede quedar documentado como pendiente externo solo por decisión expresa del usuario. No inventar PASS.

## Proyecto

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Estado esperado

- Rama `main`.
- HEAD mínimo `34a551a52aa1f2b14bc54201826544d9385637f5`.
- M13 Bloque 1 y 2 cerrados localmente.
- Migración 048 aplicada y no modificable.
- Migraciones 001–048 intactas.
- M12 smoke y cierre oficial siguen pendientes.

## Objetivo

Implementar M13 Bloque 3:

- apertura de revisión;
- confirmación humana;
- rechazo;
- decisión inconclusa;
- retiro y expiración cuando corresponda;
- escritura autoritativa en decisiones e historial;
- concurrencia e idempotencia;
- autoridad M08/M03/M04;
- integración Android Supabase;
- UI de revisión remota;
- auditoría M07;
- privacidad.

## Reglas

- Trabajar directamente sobre `main`.
- Sin ramas ni commits intermedios.
- Un único commit y push.
- No modificar 001–048.
- No crear 049 por defecto.
- Si surge una necesidad SQL real, detener el cierre, documentar y proponer 049.
- No aplicar SQL remotamente.
- No generar APK.
- No usar IA ni autoconfirmación.
- No exponer ubicación exacta o contacto.
- No implementar pagos, chat o historia clínica.
- No declarar M12 cerrado.

## Alcance mínimo

### RPC esperadas

Auditar primero las tablas y RPC de 048. Agregar mediante 049 únicamente si es imprescindible y aprobado:

```text
m13_open_match_review
m13_confirm_match_candidate
m13_reject_match_candidate
m13_mark_match_inconclusive
m13_withdraw_match_candidate
m13_expire_match_candidate
m13_list_match_decisions
m13_list_match_status_history
```

### Reglas de transición

```text
PROPOSED -> UNDER_REVIEW
UNDER_REVIEW -> CONFIRMED
UNDER_REVIEW -> REJECTED
UNDER_REVIEW -> INCONCLUSIVE
PROPOSED/UNDER_REVIEW -> WITHDRAWN
PROPOSED/UNDER_REVIEW -> EXPIRED
```

Estados finales no se reabren. Una nueva revisión requiere un candidato nuevo.

### Autoridad

- responsable M08 del caso;
- gestor autorizado M03/M04;
- moderador M04;
- reportante no confirma por sí solo salvo que también tenga autoridad real sobre el caso;
- actor siempre derivado de `auth.uid()`.

### Concurrencia e idempotencia

- bloquear el candidato durante transición;
- una decisión final por candidato;
- reintentos equivalentes no duplican decisiones;
- conflictos tipificados;
- historial append-only.

### Efectos

Al confirmar:

- candidato `CONFIRMED`;
- avistamiento `CONFIRMED`;
- registrar decisión e historial;
- emitir auditoría M07;
- no cerrar automáticamente el caso Lost/Found salvo regla canónica expresa y comprobada.

### Android

- completar repositorios Supabase;
- ViewModels y pantallas de revisión;
- timeline;
- razones y score;
- acciones según autoridad;
- mensajes de conflicto;
- estados de carga/error;
- conservar mocks.

### Pruebas

Cubrir:

- todas las transiciones;
- autoridad positiva y negativa;
- idempotencia;
- concurrencia;
- una sola decisión final;
- historial;
- privacidad;
- no autoconfirmación;
- no cierre automático del caso;
- regresión B1/B2;
- migraciones intactas.

## SQL

No crear 049 automáticamente.

Si la 048 ya contiene estructura suficiente pero no RPC, detenerse y entregar una propuesta exacta de 049 antes de modificar SQL.

## Validación final

- pruebas focalizadas;
- una compilación `compileLocalDebugKotlin`;
- sin APK;
- `git diff --check`;
- un único commit:

```text
feat(m13): add human match review workflow
```

## Estado final permitido

```text
M13 BLOQUE 3 CERRADO LOCALMENTE
SMOKE REMOTO PENDIENTE
```

o:

```text
M13 BLOQUE 3 BLOQUEADO — MIGRACIÓN 049 REQUERIDA
```
