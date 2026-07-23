# Cursor — M11 Bloque 2: campañas, insumos y red de ayuda

Trabajá directamente sobre:

```text
C:\Users\Supervielle\StudioProjects\ComunidadApp
```

## Contexto confirmado

- Rama de trabajo: `main`.
- Commit base de M11 Bloque 1: `2b977ad776742a0c17b001eaec57fefa82563aba`.
- Migraciones `040`, `041` y `042` ya fueron aplicadas correctamente en Supabase de pruebas.
- La versión corregida de `040_m10_foster_homes_core.sql` ya fue reemplazada en el proyecto.
- M11 Bloque 1: 98 pruebas focalizadas aprobadas.
- `compileLocalDebugKotlin`: `BUILD SUCCESSFUL`.
- No aplicar SQL remoto desde Cursor.

## Objetivo

Implementar **M11 Bloque 2 — campañas institucionales, pedidos de insumos y red de ayuda no monetaria**.

El bloque debe permitir que un refugio M11:

1. Cree y gestione campañas públicas o internas.
2. Publique necesidades de alimentos, medicamentos, higiene, transporte, atención veterinaria y otros insumos.
3. Reciba compromisos de aportes no monetarios.
4. Registre confirmación y recepción de esos aportes.
5. Calcule progreso y cierre pedidos de forma auditable.
6. Publique actualizaciones de campaña usando referencias M05 seguras.
7. Mantenga permisos M03/M11, RLS y RPC deny-by-default.

## Flujo obligatorio

- Trabajar directamente sobre `main`.
- No crear ramas, backups ni checkpoints.
- No hacer commits intermedios.
- Un único commit y un único push al finalizar.
- No ejecutar emulador.
- No iniciar Supabase local.
- No aplicar migraciones remotamente.
- No ejecutar lint, JaCoCo, assembleDebug ni toda la suite.
- No generar APK.
- Usar pruebas focalizadas y una compilación Kotlin final.
- No modificar migraciones `040`, `041` ni `042`.
- Toda nueva persistencia debe comenzar en la migración `043`.

## Paso 1 — Estado inicial

Ejecutar:

```powershell
git branch --show-current
git status -sb
git log -1 --oneline
git rev-parse HEAD
```

Estado esperado:

```text
main
```

El working tree puede contener únicamente la corrección ya realizada de `040` si todavía no fue commiteada. En ese caso:

- verificar que sea exactamente el orden válido de parámetros aplicado remotamente;
- conservarla;
- incluirla en el único commit final del bloque;
- documentar que alinea Git con Supabase.

Si aparecen otros cambios locales no relacionados:

- no usar `reset`, `restore` ni `clean`;
- informar;
- detenerse.

## Paso 2 — Auditoría

Buscar en código, SQL, tests y documentación:

```text
ShelterCampaign
Campaign
SupplyRequest
AidRequest
Donation
Contribution
FoodRequest
MedicationRequest
Urgency
campaigns
donations
shelter_campaigns
shelter_supply_requests
```

Revisar especialmente:

- legacy `campaigns`, `donations` o equivalentes;
- M03 organizaciones, permisos y membresías;
- M05 referencias seguras;
- M06 notificaciones;
- M07 auditoría/observabilidad;
- M10 pedidos de ayuda;
- M11 Bloque 1;
- rutas, navegación, DataProvider, fakes y repositorios.

Clasificar cada hallazgo como:

- reutilizable;
- parcial;
- legacy;
- incompatible;
- ausente.

No crear un sistema monetario ni duplicar campañas legacy sin estrategia de compatibilidad.

## Paso 3 — Dominio

Crear o completar modelos equivalentes a:

```kotlin
data class ShelterCampaign(
    val id: String,
    val shelterProfileId: String,
    val title: String,
    val description: String,
    val category: ShelterCampaignCategory,
    val visibility: ShelterCampaignVisibility,
    val status: ShelterCampaignStatus,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val coverAssetRef: String?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

Categorías mínimas:

```text
FOOD
MEDICATION
HYGIENE
VETERINARY
TRANSPORT
INFRASTRUCTURE
EMERGENCY
OTHER
```

Visibilidad:

```text
PUBLIC
INTERNAL
```

Estados:

```text
DRAFT
ACTIVE
PAUSED
COMPLETED
CANCELLED
```

Crear:

```kotlin
data class ShelterCampaignUpdate(
    val id: String,
    val campaignId: String,
    val authorUserId: String,
    val visibility: ShelterCampaignVisibility,
    val message: String,
    val evidenceRef: String?,
    val createdAt: Instant
)
```

Crear:

```kotlin
data class ShelterSupplyRequest(
    val id: String,
    val shelterProfileId: String,
    val campaignId: String?,
    val category: ShelterSupplyCategory,
    val itemName: String,
    val description: String?,
    val quantityRequested: Int,
    val quantityCommitted: Int,
    val quantityReceived: Int,
    val unitText: String,
    val priority: ShelterSupplyPriority,
    val status: ShelterSupplyRequestStatus,
    val expiresAt: Instant?,
    val publicNotes: String?,
    val internalNotes: String?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

Prioridades:

```text
NORMAL
HIGH
URGENT
```

Estados:

```text
DRAFT
OPEN
PARTIALLY_COMMITTED
FULLY_COMMITTED
PARTIALLY_RECEIVED
FULFILLED
EXPIRED
CANCELLED
```

Crear:

```kotlin
data class ShelterSupplyContribution(
    val id: String,
    val requestId: String,
    val contributorUserId: String,
    val quantityCommitted: Int,
    val quantityReceived: Int,
    val status: ShelterSupplyContributionStatus,
    val contributorNotes: String?,
    val internalReceiptNotes: String?,
    val evidenceRef: String?,
    val committedAt: Instant,
    val receivedAt: Instant?,
    val cancelledAt: Instant?
)
```

Estados:

```text
PLEDGED
CONFIRMED
PARTIALLY_RECEIVED
RECEIVED
CANCELLED
REJECTED
```

## Paso 4 — Reglas de negocio

### Campañas

- Solo refugios `ACTIVE` pueden activar campañas.
- `title` y `description` son obligatorios.
- Una campaña pública debe mostrar solamente información segura.
- `INTERNAL` solo puede ser vista por miembros autorizados.
- `COMPLETED` requiere no tener pedidos vinculados abiertos.
- `CANCELLED` debe cancelar de forma transaccional los pedidos abiertos asociados.
- No usar hard-delete.
- No almacenar meta monetaria ni moneda.
- No almacenar CBU, alias, tarjeta, enlaces de pago ni datos bancarios.

### Pedidos de insumos

- `quantityRequested > 0`.
- `unitText` e `itemName` obligatorios.
- El refugio debe estar activo.
- Un pedido público solo expone `publicNotes`.
- `internalNotes` nunca aparece en listados públicos.
- No permitir compromisos superiores a la cantidad pendiente.
- `quantityReceived` nunca puede superar `quantityCommitted`.
- El estado se recalcula automáticamente:
  - recibido >= solicitado → `FULFILLED`;
  - recibido > 0 → `PARTIALLY_RECEIVED`;
  - comprometido >= solicitado → `FULLY_COMMITTED`;
  - comprometido > 0 → `PARTIALLY_COMMITTED`;
  - sin aportes y vigente → `OPEN`;
  - vencido sin completar → `EXPIRED`.
- Los estados derivados no se editan manualmente desde Android.
- Cancelar un pedido cancela compromisos no recibidos, preservando el historial.

### Aportes

- Solo usuarios autenticados.
- Cantidad positiva.
- El aportante puede cancelar únicamente antes de recepción.
- Solo gestores del refugio pueden confirmar o registrar recepción.
- La recepción puede ser parcial.
- No registrar dinero, cobros ni pagos.
- La evidencia debe ser referencia segura M05.
- No aceptar URLs públicas directas de buckets o dominios externos como evidencia.

## Paso 5 — Permisos

Agregar o reutilizar capacidades equivalentes:

```text
shelter.campaign.read
shelter.campaign.manage
shelter.supply.read
shelter.supply.manage
shelter.contribution.read
shelter.contribution.manage
```

Reglas:

- autoridad derivada de organización M03, membresía activa y permiso;
- `AccountType` y `active_modules` no conceden autoridad;
- voluntario M11 no recibe permisos administrativos automáticamente;
- deny-by-default ante error, organización inexistente o permiso desconocido.

## Paso 6 — Migración 043

Crear exactamente:

```text
supabase/migrations/043_m11_shelter_campaigns_and_aid.sql
```

No modificar `040`, `041` ni `042`.

Crear o completar tablas equivalentes:

```text
shelter_campaigns
shelter_campaign_updates
shelter_supply_requests
shelter_supply_contributions
```

Agregar:

- claves foráneas;
- timestamps;
- checks de estados y cantidades;
- índices por refugio;
- índices por campaña;
- índices por pedido;
- índices por contribuyente;
- índices por estado y vencimiento;
- unicidades parciales cuando correspondan;
- RLS;
- RPC;
- grants mínimos;
- comentarios técnicos.

La migración debe ser reejecutable tras un fallo parcial sin borrar datos:

- `CREATE TABLE IF NOT EXISTS`;
- `CREATE INDEX IF NOT EXISTS`;
- `CREATE OR REPLACE FUNCTION`;
- `DROP POLICY IF EXISTS` antes de cada `CREATE POLICY`;
- `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`;
- no usar `DROP TABLE`;
- no hacer resets destructivos.

## Paso 7 — RPC

Crear operaciones equivalentes a:

### Campañas

```text
m11_create_shelter_campaign
m11_update_shelter_campaign
m11_change_shelter_campaign_status
m11_get_shelter_campaign
m11_list_public_shelter_campaigns
m11_list_shelter_campaigns
m11_add_shelter_campaign_update
m11_list_shelter_campaign_updates
```

### Pedidos

```text
m11_create_supply_request
m11_update_supply_request
m11_cancel_supply_request
m11_get_supply_request
m11_list_public_supply_requests
m11_list_shelter_supply_requests
```

### Aportes

```text
m11_pledge_supply_contribution
m11_cancel_supply_contribution
m11_confirm_supply_contribution
m11_record_supply_receipt
m11_get_supply_contribution
m11_list_supply_contributions
```

Seguridad obligatoria:

- actor derivado de `auth.uid()`;
- `SECURITY DEFINER`;
- `SET search_path = public`;
- helpers internos revocados a `PUBLIC`, `anon` y `authenticated`;
- DML directo del cliente denegado;
- proyecciones públicas limitadas;
- no usar `service_role` desde Android.

## Paso 8 — Integración M05, M06 y M07

### M05

Aceptar solamente referencias equivalentes a:

```text
m05://...
file_asset:...
```

Rechazar:

- URLs públicas directas;
- bucket público `leover`;
- rutas arbitrarias;
- `http://` y `https://` como evidencia persistida.

### M06

Preparar hooks de dominio o contratos para notificaciones futuras:

- campaña activada;
- pedido urgente creado;
- aporte comprometido;
- aporte recibido;
- pedido cumplido.

No implementar push ni trabajos en segundo plano en este bloque.

### M07

Registrar eventos auditables equivalentes a:

```text
SHELTER_CAMPAIGN_CREATED
SHELTER_CAMPAIGN_STATUS_CHANGED
SHELTER_SUPPLY_REQUEST_CREATED
SHELTER_SUPPLY_CONTRIBUTION_PLEDGED
SHELTER_SUPPLY_CONTRIBUTION_RECEIVED
SHELTER_SUPPLY_REQUEST_FULFILLED
```

Reutilizar el mecanismo existente; no crear un subsistema paralelo.

## Paso 9 — Repositorios

Crear contratos claros:

```text
ShelterCampaignRepository
ShelterSupplyRepository
```

Métodos equivalentes:

```text
observePublicCampaigns(...)
observeShelterCampaigns(...)
getCampaign(...)
createCampaign(...)
updateCampaign(...)
changeCampaignStatus(...)
addCampaignUpdate(...)
observeCampaignUpdates(...)

observePublicSupplyRequests(...)
observeShelterSupplyRequests(...)
getSupplyRequest(...)
createSupplyRequest(...)
updateSupplyRequest(...)
cancelSupplyRequest(...)
pledgeContribution(...)
cancelContribution(...)
confirmContribution(...)
recordReceipt(...)
observeContributions(...)
```

Todos deben:

- devolver errores tipados;
- mapear errores técnicos;
- tolerar enums desconocidos;
- resolver por ID;
- no acceder a Supabase desde ViewModels;
- no usar Supabase real en tests.

## Paso 10 — Errores de dominio

Agregar errores equivalentes:

```text
SHELTER_CAMPAIGN_NOT_FOUND
SHELTER_CAMPAIGN_FORBIDDEN
SHELTER_CAMPAIGN_INVALID_TRANSITION
SHELTER_CAMPAIGN_HAS_OPEN_REQUESTS
SHELTER_CAMPAIGN_NOT_ACTIVE

SHELTER_SUPPLY_REQUEST_NOT_FOUND
SHELTER_SUPPLY_REQUEST_FORBIDDEN
SHELTER_SUPPLY_REQUEST_INVALID
SHELTER_SUPPLY_REQUEST_CLOSED
SHELTER_SUPPLY_REQUEST_EXPIRED

SHELTER_CONTRIBUTION_NOT_FOUND
SHELTER_CONTRIBUTION_FORBIDDEN
SHELTER_CONTRIBUTION_INVALID
SHELTER_CONTRIBUTION_EXCEEDS_REMAINING
SHELTER_CONTRIBUTION_ALREADY_RECEIVED

SHELTER_EVIDENCE_REF_INVALID
```

No mostrar errores PostgreSQL/Supabase al usuario.

## Paso 11 — UI y navegación

Crear o completar rutas equivalentes:

```text
shelter_campaigns/{shelterId}
shelter_campaign_detail/{campaignId}
shelter_campaign_form/{shelterId}
shelter_campaign_form/{shelterId}/{campaignId}
shelter_campaign_update/{campaignId}

shelter_supply_requests/{shelterId}
shelter_supply_request_detail/{requestId}
shelter_supply_request_form/{shelterId}
shelter_supply_request_form/{shelterId}/{requestId}
shelter_supply_contribute/{requestId}
shelter_supply_contributions/{requestId}
```

Agregar accesos desde el dashboard M11.

### Listado público

Mostrar:

- refugio;
- campaña o pedido;
- categoría;
- prioridad;
- cantidad solicitada;
- comprometida;
- recibida;
- unidad;
- progreso;
- vencimiento;
- estado;
- zona pública segura.

No mostrar:

- dirección exacta;
- notas internas;
- teléfono privado;
- datos bancarios;
- datos de otros aportantes.

### Gestión del refugio

Permitir:

- crear/editar campaña;
- activar, pausar, completar o cancelar;
- crear/editar/cancelar pedido;
- agregar novedades;
- confirmar aporte;
- registrar recepción parcial o total;
- ver historial.

Prevenir doble envío en todas las mutaciones.

## Paso 12 — ViewModels

Crear ViewModels para:

- campañas públicas;
- campañas del refugio;
- detalle;
- formulario;
- actualizaciones;
- pedidos públicos;
- pedidos del refugio;
- detalle de pedido;
- formulario;
- aporte;
- recepción y lista de contribuciones.

Usar:

- `StateFlow`;
- loading explícito;
- contenido;
- vacío;
- error;
- eventos de éxito;
- prevención de doble envío;
- `SharingStarted.Eagerly` cuando corresponda.

No usar `null` como loading eterno.

## Paso 13 — Fakes

Los fakes deben:

- persistir campañas;
- persistir actualizaciones;
- filtrar por visibilidad;
- persistir pedidos;
- persistir aportes;
- recalcular totales y estados;
- impedir sobrecompromiso;
- permitir recepción parcial;
- preservar historial;
- validar permisos;
- validar referencias M05;
- resolver por ID;
- emitir flujos actualizados.

No usar listas estáticas que ignoren mutaciones.

## Paso 14 — Tests focalizados

Crear:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M11ShelterCampaignsAndAidTest.kt
```

Cubrir como mínimo:

1. crear campaña draft;
2. activar campaña válida;
3. impedir campaña en refugio inactivo;
4. listado público solo ACTIVE/PUBLIC;
5. ocultar campaña interna;
6. editar campaña;
7. pausar;
8. impedir completar con pedidos abiertos;
9. cancelar campaña y pedidos abiertos;
10. agregar actualización pública;
11. agregar actualización interna;
12. rechazar evidencia insegura;
13. crear pedido válido;
14. cantidad mayor que cero;
15. unidad obligatoria;
16. notas internas no públicas;
17. compromiso parcial;
18. compromiso total;
19. impedir sobrecompromiso;
20. cancelar aporte antes de recepción;
21. impedir cancelación después de recepción;
22. confirmar aporte;
23. recepción parcial;
24. recepción total;
25. pedido pasa a FULFILLED;
26. pedido vencido;
27. cancelar pedido;
28. preservar historial;
29. usuario sin permiso;
30. voluntario sin autoridad automática;
31. error de repositorio;
32. enum desconocido;
33. ID vacío;
34. ID inexistente;
35. doble envío;
36. loading inicial;
37. integración M05;
38. hook M06 preparado sin push;
39. auditoría M07;
40. no conectar con Supabase real.

Crear también:

```text
app/src/test/java/com/comunidapp/app/viewmodel/M11CampaignMigrationStaticGuardsTest.kt
```

Cubrir:

- migración 043;
- tablas esperadas;
- RLS;
- policies idempotentes;
- RPC `m11_*`;
- `search_path`;
- parámetros `DEFAULT` válidos;
- sin `DROP TABLE`;
- sin datos bancarios;
- sin URLs públicas persistidas;
- sin `service_role` en Android;
- migraciones 040–042 intactas.

Ejecutar únicamente:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "*M11ShelterCampaignsAndAidTest" `
  --tests "*M11CampaignMigrationStaticGuardsTest" `
  --tests "*M11ShelterOperationsCoreTest" `
  --tests "*M05Media*" `
  --tests "*M10FosterCareManagementTest"
```

Si el patrón `*M05Media*` no encuentra tests, identificar y usar únicamente la suite focalizada real de referencias seguras M05. No ejecutar toda la suite.

## Paso 15 — Compilación

Ejecutar:

```powershell
.\gradlew.bat compileLocalDebugKotlin
```

No ejecutar:

```text
assembleDebug
lint
jacoco
connectedAndroidTest
```

## Paso 16 — Documentación del repositorio

Actualizar:

```text
docs/03-modulos/M11-refugios.md
```

Crear:

```text
docs/02-arquitectura/M11-campanas-insumos-red-ayuda.md
docs/05-operacion/M11-aplicacion-migracion-043-supabase.md
```

Registrar:

- auditoría;
- modelos;
- estados;
- reglas;
- migración 043;
- RLS;
- RPC;
- seguridad M05;
- hooks M06;
- auditoría M07;
- pantallas;
- tests;
- compilación;
- limitaciones;
- smoke remoto posterior.

En el documento operativo incluir consultas para verificar:

- cuatro tablas;
- RLS activo;
- policies;
- constraints;
- índices;
- funciones `m11_*`;
- permisos `shelter.*`;
- smoke de campaña, pedido, aporte parcial, recepción y cumplimiento.

## Fuera de alcance

No implementar:

- pagos;
- CBU, alias o cuentas bancarias;
- cobros;
- checkout;
- Mercado Pago;
- donaciones monetarias;
- chat;
- push;
- WorkManager;
- IA;
- reputación;
- eventos;
- reportes avanzados;
- APK;
- aplicación remota de 043.

## Paso 17 — Git

Ejecutar:

```powershell
git status
git diff --stat
git diff --check
```

Verificar:

- sin APK;
- sin secretos;
- sin logs;
- sin temporales;
- sin cambios destructivos;
- migraciones 041 y 042 intactas;
- 040 solo contiene la corrección aplicada remotamente;
- migración nueva exactamente 043.

Agregar:

```powershell
git add app supabase docs
git diff --cached --stat
git diff --cached --check
```

Crear un único commit:

```powershell
git commit -m "feat(m11): add shelter campaigns and aid network"
git push origin main
```

Si el push es bloqueado:

- no crear rama;
- no hacer rebase;
- no crear otro commit;
- entregar SHA;
- dejar pendiente solo `git push origin main`.

## Entrega final

Informar:

1. Estado inicial.
2. Auditoría y legacy encontrado.
3. Qué se implementó.
4. Archivos principales.
5. Migración 043.
6. Tablas, constraints, índices y RLS.
7. RPC y permisos.
8. Campañas.
9. Pedidos de insumos.
10. Aportes y recepción.
11. Integración M03/M05/M06/M07.
12. Pantallas y rutas.
13. Tests aprobados.
14. Resultado de `compileLocalDebugKotlin`.
15. Documentación.
16. SHA.
17. Push.
18. `git status -sb`.
19. Orden manual para aplicar 043.
20. Pendientes del Bloque 3.

No aplicar SQL remoto.
No comenzar el Bloque 3.
No generar APK.
