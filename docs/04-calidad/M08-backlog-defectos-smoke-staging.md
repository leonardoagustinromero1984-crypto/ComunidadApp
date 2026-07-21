# M08 — Backlog de defectos del smoke manual staging (Etapa 4D)

**Producto:** LeoVer
**Módulo:** M08 — Mascotas y responsables
**Fecha de registro:** 2026-07-21
**Entorno:** LeoVer Staging (`com.comunidapp.app.staging`, APK distribuible 4D)
**Estado formal:**

```text
M08 ETAPA 4D — BACKEND STAGING Y APK DISTRIBUIBLE LISTOS
SMOKE MANUAL — PARCIAL CON DEFECTOS REGISTRADOS
DEFECTOS FUNCIONALES — BACKLOG
M08 ETAPA 5 — AUTORIZADA
```

Reglas de este backlog:

- El smoke manual staging quedó **PARCIAL**: no se declara PASS del smoke APK staging ni cierre de Etapa 4D sin observaciones.
- Los defectos aquí registrados quedan en **BACKLOG** y se difieren a **Etapa 7 / estabilización**, salvo que bloqueen una etapa autorizada.
- No se inventan causas raíz: cada defecto registra solo lo observado. La causa se confirma con reproducción instrumentada antes de corregir.
- La autorización de Etapa 5 no implica que estos defectos estén resueltos.

---

## Defectos registrados

### M08-SMOKE-001 — Crash al abrir el detalle de mascota

| Campo | Valor |
|---|---|
| ID | M08-SMOKE-001 |
| Estado | **BACKLOG** |
| Severidad | **ALTA** |
| Detección | Smoke manual Etapa 4D |
| Entorno | LeoVer Staging (APK distribuible, teléfono físico, solo internet) |
| Resolución prevista | Diferido a **Etapa 7 / estabilización** |

**Síntoma observado:** al navegar desde *Mis mascotas* al detalle de una mascota en LeoVer Staging, la aplicación se cierra (crash visible para el usuario). El resto del flujo smoke previo (registro, login, creación y listado de mascota) se completó.

**Causa raíz:** **no confirmada.** No se registra causa inventada; requiere reproducción con logs/instrumentación en staging antes de asignar corrección definitiva.

**Nota de contexto (no es diagnóstico):** la Etapa 5 corrigió en Android un desalineamiento de `SerialName` del DTO de historial de estados (`pet_status_history`: `reason` / `changed_by` / `changed_at`). Su relación con este defecto queda pendiente de verificación en el smoke integral M08.

**Impacto:** bloquea la validación manual completa del detalle de mascota en staging; no afecta backend ni datos (solo lectura).

---

## OTROS DEFECTOS PENDIENTES DE CLASIFICACIÓN

Espacio reservado para observaciones del smoke que aún no tienen ID, severidad ni estado asignados. Al clasificarlas se promueven a la tabla anterior con ID `M08-SMOKE-nnn`.

| # | Observación | Estado |
|---|---|---|
| — | (sin observaciones adicionales registradas al 2026-07-21) | — |

---

## Criterios de salida del backlog

- [ ] Reproducción instrumentada de M08-SMOKE-001 con causa raíz confirmada.
- [ ] Corrección aplicada y verificada en staging.
- [ ] Re-ejecución del smoke manual staging completo con resultado registrado fila por fila.
- [ ] Solo entonces puede declararse `SMOKE APK STAGING MANUAL` como completado sin defectos bloqueantes.
