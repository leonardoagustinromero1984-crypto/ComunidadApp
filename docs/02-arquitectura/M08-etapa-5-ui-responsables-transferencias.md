# M08 — Etapa 5: UI de responsables, autorizaciones y transferencias

**Producto:** LeoVer
**Módulo:** M08 — Mascotas y responsables
**Fecha:** 2026-07-21
**Rama:** `m08/etapa-5-ui-responsables-transferencias`
**Estado formal:**

```text
M08 ETAPA 5 — UI RESPONSABLES Y TRANSFERENCIAS LISTA
SMOKE INTEGRAL M08 — PENDIENTE
DEFECTOS ETAPA 4D — BACKLOG
PRODUCCIÓN NO MODIFICADA
```

Restricciones respetadas: sin cambios en migraciones 001–036, sin migración 037, sin SQL remoto, sin commit/push automático.

---

## 1. Alcance

Etapa 5 agrega la capa de presentación Android sobre los contratos y repositorios ya existentes (Etapas 2–4):

- Gestión de responsables (principal PERSON/ORGANIZATION, co-responsables, custodios temporales).
- Gestión de autorizaciones (otorgar/revocar capacidades acotadas a personas).
- Gestión de transferencias de responsabilidad principal (iniciar, aceptar, rechazar, cancelar, historial).

No se agregan RPCs, tablas ni políticas nuevas: la UI consume exclusivamente `PetResponsibilityRepository`, `PetAuthorizationRepository`, `PetTransferRepository`, `PetRepository` (contexto de acceso) y `UserRepository.searchPublicProfiles` (búsqueda controlada de personas).

---

## 2. Navegación

`NavRoutes` incorpora, con codificación URL de parámetros:

| Ruta | Patrón | Destino |
|---|---|---|
| `PET_RESPONSIBILITIES` | `pet_responsibilities/{petId}` | `PetResponsibilitiesScreen` |
| `PET_AUTHORIZATIONS` | `pet_authorizations/{petId}` | `PetAuthorizationsScreen` |
| `PET_TRANSFERS` | `pet_transfers/{petId}` | `PetTransfersScreen` |
| `PET_TRANSFER_DETAIL` | `pet_transfer_detail/{petId}/{transferId}` | `PetTransferDetailScreen` |

Entrada desde `PetDetailScreen`: sección **"Responsables y permisos"**, visible solo cuando `PetDetailViewModel.canViewGovernance` (derivado de `PetAccessContext.canRead` + sesión activa) es verdadero. No se usa igualdad con `ownerId` para autorizar.

Los deep links y rutas existentes no se modifican; los nuevos `composable` se agregan en `ComunidappNavGraph` con factories de ViewModel y `URLDecoder` para argumentos.

---

## 3. ViewModels y estados

Patrón común (consistente con el resto de la app):

- `data class *UiState` inmutable expuesto por `StateFlow`, con `isLoading`, `errorMessage` (reintentable), colecciones de contenido (vacío ⇒ estado Empty en UI), `isSubmitting` (anti doble envío), `actionMessage` (snackbar) y estado de búsqueda de personas.
- Inyección por `ViewModelProvider.Factory` (`Companion.factory(petId)`) leyendo repos de `DataProvider`; si algún repositorio es nulo, el estado cae en error seguro "función no disponible" sin crash.
- Carga inicial: contexto de acceso (`PetRepository.getAccessContext`) + datos de dominio en paralelo; toda mutación re-verifica la capacidad correspondiente del backend antes de llamar al repo.
- Errores mapeados con `M08PetErrorMapper` / mensajes seguros; nunca se muestra texto crudo de PostgreSQL.
- `viewModelScope` únicamente (sin `GlobalScope`); ninguna llamada RPC desde composables.

| ViewModel | Capacidades que gobiernan mutaciones | Mutaciones |
|---|---|---|
| `PetResponsibilitiesViewModel` | `canManageResponsibilities` + pet `ACTIVE` | agregar co-responsable (persona/organización), agregar custodio temporal con vigencia, revocar (nunca PRINCIPAL) |
| `PetAuthorizationsViewModel` | `canManageAuthorizations` + pet `ACTIVE` | otorgar (solo allowlist `GRANTABLE_CAPABILITIES`), revocar |
| `PetTransfersViewModel` | `canInitiateTransfer` / `canAcceptTransfer` / `canCancelTransfer` + pet `ACTIVE` | iniciar (persona XOR organización), aceptar (refresca contexto de acceso), rechazar, cancelar con motivo opcional |

Allowlist de autorizaciones: excluye explícitamente `INITIATE_TRANSFER`, `MARK_DECEASED`, `ARCHIVE` y `MANAGE_RESPONSIBILITIES` (ninguna autorización otorga transferencia, fallecimiento, archivo ni gestión de responsables de forma implícita).

`PetTransferDetailScreen` comparte `PetTransfersViewModel` (keyed por `petId`) y filtra el `transferId` de la ruta; los estados terminales (`ACCEPTED`/`REJECTED`/`CANCELLED`/`EXPIRED`) se muestran sin acciones.

---

## 4. Pantallas Compose

Material3 alineado al resto de la app: `Scaffold` + `TopAppBar` con navegación atrás, contenido scrolleable, `contentDescription` en iconografía accionable, estados Loading/Error(reintentar)/Empty, `SnackbarHost` para resultados y `AlertDialog` de confirmación para toda mutación destructiva o sensible.

- **PetResponsibilitiesScreen:** principal (persona u organización) con estado/vigencia; listas de co-responsables, custodios temporales y vínculos inactivos; alta por búsqueda controlada de personas o ID de organización; fecha fin opcional para custodios; revocación con confirmación (el PRINCIPAL no ofrece revocar). Mutaciones deshabilitadas si la mascota está `ARCHIVED`/`DECEASED`.
- **PetAuthorizationsScreen:** lista con persona autorizada, chips de capacidades permitidas y estado derivado (activa/expirada/revocada) con vigencia; alta con búsqueda de personas + selección de capacidades del allowlist + vigencia opcional; revocación con confirmación.
- **PetTransfersScreen:** transferencia pendiente destacada con acciones según capacidad (aceptar/rechazar el destinatario, cancelar el iniciador) + historial de estados terminales solo lectura; inicio de transferencia con advertencia y confirmación, destino persona (búsqueda) XOR organización (ID); bloqueo si ya existe una PENDING.
- **PetTransferDetailScreen:** detalle de una transferencia (origen, destino, estado, fechas, motivo) con las mismas acciones confirmadas solo cuando está PENDING y la capacidad lo permite.

---

## 5. Correcciones de datos Android-only (sin SQL)

1. **`PetStatusHistoryM08Row`:** `@SerialName` corregidos a las columnas reales de `public.pet_status_history` (035): `reason`, `changed_by`, `changed_at`; el orden de `listStatusHistory` pasa de `created_at` a `changed_at`.
2. **Motivo de cancelación de transferencia:** `PetTransferRepository.cancel` acepta `reason: String? = null` (extensión compatible por default) y lo propaga al RPC de cancelación existente.
3. **`M08PetErrorMapper`:** códigos nuevos de responsabilidades/autorizaciones/transferencias con mensajes en español, ordenados de específico a genérico para evitar coincidencias por subcadena.

---

## 6. Calidad

- Tests unitarios nuevos: `PetResponsibilitiesViewModelTest`, `PetAuthorizationsViewModelTest`, `PetTransfersViewModelTest` (fakes en memoria + `kotlinx-coroutines-test`) y `M08Stage5StaticGuardsTest` (guardas estáticas sobre rutas, gating, DTO y script).
- Script de calidad: `scripts/ci/m08_stage5_quality_checks.sh` (archivos/símbolos presentes, sin autorización por `ownerId`, sin RPC desde composables, sin credenciales service_role, migraciones 001–036 intactas y sin 037, backlog presente sin PASS inventado, producción no tocada).
- Reporte de validación: `docs/04-calidad/M08-reporte-validacion-etapa-5.md`.

---

## 7. Fuera de alcance de Etapa 5

- Smoke integral M08 en staging (pendiente; incluye re-verificar M08-SMOKE-001 del backlog 4D).
- Notificaciones M06 de eventos de transferencia/co-responsabilidad (etapas posteriores).
- Selector avanzado de organizaciones por membresía M03 (el destino organización se ingresa por ID; no se crea sistema paralelo de invitaciones).
- Cualquier cambio de esquema, RLS o RPC.

---

## 8. Documentos relacionados

- `docs/03-modulos/M08-mascotas-y-responsables.md`
- `docs/04-calidad/M08-backlog-defectos-smoke-staging.md`
- `docs/04-calidad/M08-reporte-validacion-etapa-5.md`
- `docs/04-calidad/M08-matriz-impacto-y-no-regresion.md`
- `docs/02-arquitectura/M08-etapa-4d-staging-apk-distribuible.md`
