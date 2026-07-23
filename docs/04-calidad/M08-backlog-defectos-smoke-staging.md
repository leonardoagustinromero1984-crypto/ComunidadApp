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
| Estado | **CORREGIDO A NIVEL DE CÓDIGO — PENDIENTE REVALIDACIÓN MANUAL** |
| Severidad | **ALTA** |
| Detección | Smoke manual Etapa 4D |
| Entorno | LeoVer Staging (APK distribuible, teléfono físico, solo internet) |
| Resolución prevista | Revalidación manual APK staging tras fix en código |

**Síntoma observado:** al navegar desde *Mis mascotas* al detalle de una mascota en LeoVer Staging, la aplicación se cierra (crash visible para el usuario). El resto del flujo smoke previo (registro, login, creación y listado de mascota) se completó.

**Causa raíz (análisis de código, pendiente confirmación en dispositivo):** `PetDetail` trataba `pet == null` como *Loading* eterno; el polling de `observePet` no aislaba excepciones (a diferencia de otras fuentes de datos); la sección de salud / mapeo DTO no toleraba campos incompletos de vacunación/recordatorios; `clinical pollingFlow` carecía de `try/catch`. El fix de `SerialName` del historial **no** es la causa directa del crash al abrir: `PetDetail` no carga el historial de estados al abrir (solo vía pantalla separada).

**Corrección aplicada:** estados explícitos de carga/error en `PetDetailViewModel`; UI `Loading`/`Error`/`Empty` en `PetDetailScreen`; endurecimiento de `PetHealthSection` + mapeo vacunación/recordatorios; `try/catch` en `LegacyPetRepositoryAdapter.observePet`; `try/catch` en `pollingFlow` clínico de Platform; `getPetAccessContext` con `firstOrNull`.

**Prueba:** `PetDetailSmokeRegressionTest`.

**Tests focalizados:** `PetDetailSmokeRegressionTest` + `MarkPetDeceasedViewModelTest` + `PetStatusHistoryViewModelTest` = **19 PASS**.

**Compilación:** `compileLocalDebugKotlin` **PASS**.

**Revalidación manual APK staging:** **PENDIENTE** — no declarar cierre total ni smoke PASS hasta ejecutar smoke en dispositivo.

**Impacto:** bloquea la validación manual completa del detalle de mascota en staging hasta revalidación APK; no afecta backend ni datos (solo lectura).

---

## OTROS DEFECTOS PENDIENTES DE CLASIFICACIÓN

Espacio reservado para observaciones del smoke que aún no tienen ID, severidad ni estado asignados. Al clasificarlas se promueven a la tabla anterior con ID `M08-SMOKE-nnn`.

| # | Observación | Estado |
|---|---|---|
| — | (sin observaciones adicionales registradas al 2026-07-21) | — |

---

## Gap diferido Etapa 6 — Galería pet (no smoke)

| Campo | Valor |
|---|---|
| ID | M08-GAP-GALLERY-001 |
| Estado | **BACKLOG** |
| Severidad | Media (UX) |
| Detección | Auditoría Etapa 6 |
| Resolución prevista | Post-Etapa 6 / estabilización (sin inventar SQL en Etapa 6) |

**Síntoma / gap:** existen RPCs genéricos M05 (`list_file_assets_for_resource` / `link_file_asset` / `unlink_file_asset`) y propósito `PET_GALLERY`, pero no hay fachada pet alineada a `pet.manage_media` ni pantalla de galería. Etapa 6 entrega avatar + fallecimiento + historial + restore + microchip + duplicados privados; **no** declara smoke PASS ni cierra este gap.

**Nota:** `M08-SMOKE-001` tiene corrección en código; smoke manual APK y cierre integral siguen **PENDIENTES** (sin PASS inventado).

### Etapa 7 — integración técnica (2026-07-23)

```text
M08 ETAPA 7 — INTEGRACIÓN Y CIERRE TÉCNICO LISTOS
SMOKE INTEGRAL APK — PENDIENTE
```

- Integración ViewModels verificada con `M08IntegrationRegressionTest`.
- `M08-SMOKE-001`: **CORREGIDO A NIVEL DE CÓDIGO — PENDIENTE REVALIDACIÓN MANUAL**.
- Galería M05 completa: **BACKLOG** (`M08-GAP-GALLERY-001`).
- M08 **no** se declara validado en staging hasta smoke manual APK.

---

## Criterios de salida del backlog

- [x] Reproducción instrumentada / análisis de código de M08-SMOKE-001 con causa raíz documentada (fix aplicado).
- [x] Corrección aplicada a nivel de código. [ ] Verificación manual en staging (APK) **PENDIENTE**.
- [ ] Re-ejecución del smoke manual staging completo con resultado registrado fila por fila.
- [ ] Solo entonces puede declararse `SMOKE APK STAGING MANUAL` como completado sin defectos bloqueantes.
