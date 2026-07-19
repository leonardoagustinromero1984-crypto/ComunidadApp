# LEOVER — M04 Etapa 4: UI y Flujos Operativos

**Módulo:** M04 — Administración, Moderación y Soporte  
**Etapa:** 4 — Pantallas y flujos operativos  
**Estado de entrada:** Etapa 3 aprobada y consolidada  
**Commit base:** `91deddf69df45a3a6fe40eb045f2ea6210b170d1`  
**Rama base:** `m04/etapa-3-persistencia-rls-rpc-colas`  
**Backend oficial:** Supabase  
**Calidad de entrada:** 233 tests, 0 failures, 0 errors; build y lint aprobados  
**Staging heredado:** migraciones 014–022 pendientes de validación remota  
**Alcance:** Android, ViewModels, navegación, adaptación de repositorios y pruebas.  
**Prohibido:** producción, M05, Storage administrativo físico, IA de moderación, pagos, GPS/mapas y merge a `main`.

---

## 1. Objetivo

Convertir la base segura de M04 en flujos operativos utilizables para:

- moderar reportes y casos;
- aplicar medidas mediante RPC;
- revisar apelaciones;
- revisar verificación de organizaciones;
- crear y gestionar tickets de soporte;
- visualizar auditoría autorizada;
- aplicar permisos y datos sensibles de forma consistente;
- mantener compatibilidad con la pantalla legacy de moderación.

La UI nunca sustituye la autorización server-side implementada en la migración `022`.

---

## 2. Documentación obligatoria

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-cierre-final.md`
4. `/docs/02-arquitectura/M02-cierre-final.md`
5. `/docs/02-arquitectura/M03-cierre-final.md`
6. `/docs/02-arquitectura/M04-auditoria-inicial.md`
7. `/docs/02-arquitectura/M04-etapa-2-cierre.md`
8. `/docs/02-arquitectura/M04-etapa-3-cierre.md`
9. `/docs/04-calidad/M04-pruebas-persistencia-rls-rpc-colas.md`
10. `/docs/03-modulos/M04-Administracion-Moderacion-y-Soporte.md`
11. `/docs/03-modulos/M04-Etapa-2-Contratos-Moderacion-Verificacion-y-Soporte.md`
12. `/docs/03-modulos/M04-Etapa-3-Persistencia-RLS-RPC-y-Colas-Administrativas.md`
13. ADR-0001 a ADR-0005
14. Este documento.

---

## 3. Protección Git

Antes de trabajar:

1. Confirmar commit base:

```text
91deddf69df45a3a6fe40eb045f2ea6210b170d1
```

2. Confirmar working tree limpio.
3. Crear rama:

```text
m04/etapa-4-ui-flujos-operativos
```

4. No incorporar `wip/gps-mapas-pagos`.
5. No hacer merge a `main`.
6. No iniciar M05.
7. No aplicar migraciones en producción.
8. No afirmar validación remota sin evidencia.

---

## 4. Reglas transversales

- Deny-by-default ante loading, error, permiso desconocido o sesión ausente.
- El servidor sigue siendo la fuente de verdad.
- No habilitar acciones por `AccountType`, `active_modules` o roles internos M03.
- Los permisos efectivos se obtienen mediante M02.
- Datos sensibles solo con permiso específico.
- No mostrar `reporterId`, evidencia, notas internas ni datos sensibles por defecto.
- No guardar tokens, PII o evidencia en logs.
- Confirmar acciones de impacto.
- Evitar doble envío y operaciones concurrentes duplicadas.
- Mostrar errores seguros y accionables.
- No usar service role en Android.
- No usar el bucket público `leover`.
- No crear bucket administrativo en esta etapa.
- No incluir archivos o base64 como evidencia.

---

## 5. Arquitectura Android

Crear paquetes equivalentes, respetando la estructura real:

```text
ui/screens/moderation/
ui/screens/verification/
ui/screens/support/
ui/screens/admin/
viewmodel/moderation/
viewmodel/verification/
viewmodel/support/
```

Reutilizar:

- `PermissionRepository`;
- `AuthorizationService`;
- repositorios Supabase M04;
- `AppResult`;
- `AppError`;
- `AppLogger`;
- componentes Loading/Empty/Error/Retry de M00;
- patrón de confirmación de `PlatformAdminScreen`;
- navegación existente;
- `DataProvider`.

No crear un segundo sistema de sesión, permisos o administración.

---

## 6. Moderación

### 6.1 Bandeja de reportes

Crear o evolucionar la pantalla existente para mostrar:

- estado;
- prioridad;
- target;
- motivo;
- fecha;
- caso asociado;
- asignación cuando corresponda.

Filtros mínimos:

```text
estado
prioridad
tipo de target
asignado
sin caso / con caso
```

Reglas:

- acceso con `moderation.view`;
- `reporterId` visible solo con `moderation.view_sensitive`;
- sin permiso sensible, no mostrar placeholder que permita inferir identidad;
- paginación o carga acotada;
- retry seguro;
- no hacer SELECT directo a `content_reports`;
- usar repositorio/RPC.

### 6.2 Detalle de reporte

Mostrar:

- target seguro;
- motivo;
- descripción sanitizada;
- estado;
- prioridad;
- timestamps;
- caso asociado;
- información sensible solo autorizada.

Acciones según permisos:

```text
triage
cambiar prioridad
marcar duplicado
crear caso
adjuntar a caso
desestimar
```

Toda acción debe:

- bloquear botón mientras ejecuta;
- manejar errores;
- refrescar datos desde servidor;
- no asumir éxito por estado local.

### 6.3 Casos de moderación

Pantallas:

```text
ModerationCaseQueueScreen
ModerationCaseDetailScreen
CreateModerationCaseScreen o diálogo equivalente
```

Capacidades:

- listar casos;
- crear;
- asignar/reasignar;
- adjuntar reportes;
- cambiar estado;
- ver reportes relacionados;
- registrar notas internas;
- aplicar medidas;
- revisar auditoría relacionada.

Permisos:

- ver: `moderation.view`;
- gestionar: `moderation.manage_cases`;
- datos sensibles: `moderation.view_sensitive`;
- medidas: `moderation.apply_actions`.

Notas internas:

- nunca visibles a usuarios normales;
- nunca incluidas en proyecciones públicas;
- no loguear texto completo.

### 6.4 Medidas

Flujo guiado:

1. seleccionar tipo;
2. seleccionar target;
3. motivo codificado;
4. detalle opcional limitado;
5. expiración si temporal;
6. resumen;
7. confirmación;
8. ejecución RPC;
9. refresco de caso y target.

Reglas:

- no permitir medida temporal sin vencimiento;
- no permitir vencimiento en medida permanente;
- medidas de cuenta usan estado M02;
- medidas de organización usan estado M03;
- mostrar advertencia reforzada para BAN/SUSPEND/REVOKE;
- `NO_ACTION` no muta recursos;
- no simular rollback en cliente;
- no permitir acción sin permiso server-side.

### 6.5 Apelaciones

Pantallas:

```text
MyModerationAppealsScreen
ModerationAppealQueueScreen
ModerationAppealDetailScreen
```

Usuario afectado:

- presentar apelación;
- ver estado;
- ver decisión pública segura;
- no ver notas internas ni identidad del revisor salvo política.

Staff:

- listar;
- asignar;
- revisar;
- decidir;
- registrar motivo;
- no revisar acción propia;
- error de conflicto debe mostrarse como denegación segura.

Permiso staff:

```text
moderation.review_appeals
```

---

## 7. Verificación de organizaciones

Pantallas staff:

```text
OrganizationVerificationQueueScreen
OrganizationVerificationReviewScreen
```

Mostrar:

- organización;
- tipo;
- estado;
- fecha de solicitud;
- asignación;
- referencias documentales lógicas;
- historial mínimo;
- notas internas autorizadas.

Acciones:

```text
APPROVE
REJECT
REQUEST_MORE_INFORMATION
REVOKE
MARK_EXPIRED
```

Permisos:

- revisar: `organizations.review_verification`;
- revocar: `organizations.revoke_verification`;
- datos internos mediante gate correspondiente.

Reglas:

- miembro activo de la organización no puede revisarla;
- `REQUEST_MORE_INFORMATION` conserva PENDING;
- no renderizar URL permanente como documento;
- sin subida de archivos físicos en esta etapa;
- la UI debe explicar que los documentos físicos pertenecen a M05;
- refrescar el perfil de organización después de una decisión.

---

## 8. Soporte

### 8.1 Usuario

Pantallas:

```text
MySupportTicketsScreen
CreateSupportTicketScreen
SupportTicketDetailScreen
```

Capacidades:

- crear ticket;
- elegir categoría;
- asunto;
- descripción;
- ver estado;
- ver mensajes visibles;
- responder;
- reintentar;
- cerrar cuando el contrato lo permita.

Reglas:

- el usuario solo ve sus tickets;
- nunca mostrar mensajes `INTERNAL`;
- categorías PRIVACY y SAFETY deben mostrar advertencia de no incluir contraseñas o secretos;
- no reutilizar el chat social.

### 8.2 Staff

Pantallas:

```text
SupportQueueScreen
SupportTicketAdminDetailScreen
```

Capacidades:

- listar cola;
- filtrar;
- asignar/reasignar;
- cambiar prioridad;
- cambiar estado;
- agregar mensaje visible;
- agregar nota interna;
- consultar auditoría autorizada.

Permisos:

- lectura: `support.view`;
- gestión: `support.manage`;
- contenido sensible: `support.view_sensitive`.

Reglas:

- sin permiso sensible, ocultar información clasificada;
- mensaje interno requiere indicador visual inequívoco;
- confirmar antes de cerrar;
- `CLOSED` debe respetar reglas de resolución del dominio.

---

## 9. Auditoría administrativa

Agregar una vista acotada o sección reutilizable para actores con:

```text
audit.view
```

Filtros mínimos:

- fecha;
- actor;
- entidad;
- acción;
- resultado.

No mostrar:

- tokens;
- contenido completo sensible;
- secretos;
- PII innecesaria.

La auditoría es de solo lectura desde Android.

---

## 10. Navegación y entradas

Agregar entradas visibles únicamente según permisos:

- Moderación;
- Casos;
- Apelaciones;
- Verificación;
- Soporte;
- Auditoría.

Reglas:

- las rutas pueden existir, pero cada ViewModel vuelve a validar;
- deep link o navegación directa sin permiso debe mostrar acceso denegado;
- no confiar en que el botón esté oculto;
- conservar la pantalla legacy o migrarla gradualmente sin romper rutas existentes;
- evitar entradas duplicadas entre `ProfileScreen`, `PlatformAdminScreen` y menú administrativo.

---

## 11. Estados de UI

Cada pantalla debe representar explícitamente:

```text
Loading
Content
Empty
Error
AccessDenied
Submitting
SuccessEvent
```

Requisitos:

- no usar una lista vacía como error;
- no mostrar acciones mientras permisos cargan;
- evitar eventos duplicados tras recomposición;
- usar mensajes de éxito de una sola vez;
- mantener filtros cuando se refresca;
- limpiar datos sensibles al salir de la sesión.

---

## 12. Correcciones SQL permitidas

La Etapa 4 no debe ampliar el modelo SQL.

Solo se permite una migración correctiva consecutiva (`023` o posterior) si se demuestra un defecto real de `022` que impide un flujo autorizado o expone datos.

En ese caso:

- documentar el defecto;
- no editar `022`;
- prueba de regresión obligatoria;
- limitar la migración al defecto;
- no agregar funcionalidad nueva encubierta.

---

## 13. Pruebas obligatorias

Agregar pruebas equivalentes a:

```text
ModerationQueueViewModelTest
ModerationReportDetailViewModelTest
ModerationCaseViewModelTest
ModerationActionViewModelTest
ModerationAppealViewModelTest
OrganizationVerificationViewModelTest
SupportTicketViewModelTest
SupportAdminViewModelTest
AdministrativeNavigationAuthorizationTest
SensitiveDataPresentationTest
LogoutAdministrativeStateTest
```

Casos mínimos:

- permiso permitido;
- permiso denegado;
- loading de permisos;
- error de permisos;
- error de red;
- doble toque;
- retry;
- reporter sensible oculto;
- mensaje interno oculto;
- conflicto de apelación;
- conflicto de verificación;
- medida temporal;
- cierre inválido;
- logout limpia datos;
- navegación directa denegada.

Conservar las 233 pruebas existentes.

---

## 14. Calidad local

Ejecutar:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Requisitos:

- build SUCCESS;
- 0 fallos y 0 errores;
- lint sin errores;
- no eliminar pruebas;
- no crear baseline nuevo;
- no usar suppress global;
- documentar cantidad total de tests.

---

## 15. Documentos de salida

Crear exactamente:

```text
/docs/02-arquitectura/M04-etapa-4-cierre.md
/docs/04-calidad/M04-pruebas-ui-flujos-operativos.md
```

El cierre debe incluir:

- rama y commit base;
- inventario de pantallas, rutas y ViewModels;
- flujos implementados;
- permisos;
- protección de datos sensibles;
- cambios legacy;
- archivos;
- pruebas;
- build/lint;
- migraciones nuevas, si existieron;
- staging pendiente;
- riesgos;
- checklist;
- parada.

---

## 16. Criterios de aceptación

- [ ] Working tree inicial limpio.
- [ ] Rama correcta.
- [ ] Sin M05.
- [ ] Sin Storage administrativo físico.
- [ ] Sin producción.
- [ ] Sin merge a main.
- [ ] Moderación usa repositorios/RPC.
- [ ] Reportes y casos operativos.
- [ ] Medidas con confirmación.
- [ ] Apelaciones usuario/staff.
- [ ] Verificación staff.
- [ ] Soporte usuario/staff.
- [ ] Auditoría de solo lectura.
- [ ] Permisos server-side respetados.
- [ ] Datos sensibles ocultos.
- [ ] Mensajes internos ocultos al solicitante.
- [ ] Deep links denegados sin permiso.
- [ ] Loading/error niega acciones.
- [ ] Logout limpia estado sensible.
- [ ] Compatibilidad legacy preservada.
- [ ] Pruebas verdes.
- [ ] Build y lint verdes.
- [ ] Staging declarado honestamente.
- [ ] Documentos de salida creados.
- [ ] Sin iniciar Etapa 5.

---

## 17. Parada

No iniciar M04 Etapa 5.

No iniciar M05.

No hacer merge a `main`.

Detenerse cuando existan:

```text
/docs/02-arquitectura/M04-etapa-4-cierre.md
/docs/04-calidad/M04-pruebas-ui-flujos-operativos.md
```
