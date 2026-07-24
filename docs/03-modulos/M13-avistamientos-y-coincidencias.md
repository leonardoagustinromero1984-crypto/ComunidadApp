# M13 — Avistamientos y coincidencias

## 1. Decisión canónica

El siguiente módulo del track técnico Android es:

```text
M13 — Avistamientos y coincidencias
```

M13 **enriquece** el flujo Lost/Found existente. No crea un segundo módulo paralelo de mascotas perdidas y encontradas y no reemplaza el modelo legacy sin una migración explícita posterior.

La base existente se considera prerrequisito:

- `LostFoundSighting`
- `LostFoundScreen`
- `PlatformRepository.addSighting`
- `lost_found_sightings`
- migración `012`

M13 agrega encima:

- avistamientos estructurados;
- evidencia segura;
- candidatos de coincidencia;
- scoring explicable;
- revisión humana;
- confirmación o rechazo;
- trazabilidad y privacidad.

## 2. Relación con el roadmap

La numeración de producto y la numeración técnica no coinciden completamente:

- M12 técnico implementado: Veterinarias.
- M12 producto del roadmap: Mascotas perdidas y encontradas.
- La funcionalidad Lost/Found base ya existe parcialmente en legacy.
- M13 técnico continúa con el producto **Avistamientos y coincidencias**, reutilizando ese legacy.

Esta decisión evita renumerar módulos ya cerrados y evita duplicar Lost/Found.

## 3. Objetivo

Permitir que una persona registre un avistamiento seguro de una mascota, que el sistema proponga coincidencias explicables con casos activos de mascota perdida o encontrada, y que una persona autorizada confirme o descarte la coincidencia sin exponer ubicación exacta ni datos privados.

## 4. Actores

### 4.1 Reportante

Usuario autenticado que registra un avistamiento.

Puede:

- crear un avistamiento;
- editarlo mientras esté activo y no confirmado;
- retirar su propio avistamiento;
- adjuntar referencias de media seguras M05;
- ver el estado de sus reportes;
- aportar una nota adicional segura.

No puede:

- confirmar por sí solo que una mascota es la misma;
- acceder a datos privados del responsable;
- ver coordenadas o contacto privado de otros usuarios.

### 4.2 Responsable del caso

Usuario con autoridad M08 sobre la mascota o caso Lost/Found.

Puede:

- ver candidatos para sus casos;
- revisar evidencia;
- confirmar, rechazar o marcar como inconclusa una coincidencia;
- solicitar contacto mediante un canal seguro cuando exista infraestructura;
- cerrar el flujo de coincidencia.

### 4.3 Gestor de organización

Usuario autorizado por M03/M04 sobre una organización responsable del caso.

Puede realizar las mismas acciones que el responsable, dentro del alcance de su organización.

### 4.4 Moderador

Actor autorizado por M04.

Puede:

- ocultar contenido inseguro;
- descartar reportes abusivos;
- suspender una coincidencia;
- auditar decisiones.

### 4.5 Visitante o usuario no autorizado

Solo puede ver información pública redactada cuando el producto lo permita:

- zona aproximada;
- fecha aproximada;
- rasgos generales;
- imagen segura autorizada.

Nunca ve contacto, notas privadas ni coordenadas exactas.

## 5. Estados

### 5.1 Estado del avistamiento

```text
ACTIVE
CONFIRMED
DISMISSED
WITHDRAWN
EXPIRED
```

Reglas:

1. Todo avistamiento nuevo comienza en `ACTIVE`.
2. `ACTIVE` puede pasar a `CONFIRMED`, `DISMISSED`, `WITHDRAWN` o `EXPIRED`.
3. `CONFIRMED`, `DISMISSED`, `WITHDRAWN` y `EXPIRED` son finales en M13.
4. Solo el reportante puede retirar su avistamiento.
5. Solo un actor autorizado puede confirmarlo o descartarlo.
6. La expiración no debe borrar evidencia ni historial.

### 5.2 Estado de la coincidencia

```text
PROPOSED
UNDER_REVIEW
CONFIRMED
REJECTED
INCONCLUSIVE
WITHDRAWN
EXPIRED
```

Reglas:

1. Una coincidencia nace en `PROPOSED`.
2. Al abrir una revisión pasa a `UNDER_REVIEW`.
3. La decisión humana final puede ser `CONFIRMED`, `REJECTED` o `INCONCLUSIVE`.
4. El sistema nunca confirma automáticamente.
5. Una coincidencia final no puede volver a activa sin un nuevo candidato.
6. Confirmar una coincidencia debe preservar trazabilidad y emitir un evento M07.

## 6. Entidades

### 6.1 `M13Sighting`

Adaptador de dominio sobre el concepto legacy `LostFoundSighting`.

Campos mínimos:

- `id`
- `reporterUserId`
- `lostFoundCaseId` opcional
- `species`
- `breedText` opcional
- `primaryColor`
- `secondaryColor` opcional
- `sex` opcional
- `size` opcional
- `observedAt`
- `zoneText`
- `latitudeApprox` opcional
- `longitudeApprox` opcional
- `accuracyMeters` opcional
- `description`
- `mediaRefs`
- `status`
- `createdAt`
- `updatedAt`

Las coordenadas exactas no forman parte de la salida pública.

### 6.2 `M13MatchCandidate`

- `id`
- `caseId`
- `sightingId`
- `score`
- `level`
- `reasons`
- `status`
- `createdAt`
- `updatedAt`

### 6.3 `M13MatchReason`

Razones explicables y no biométricas:

```text
SPECIES_MATCH
ZONE_PROXIMITY
TIME_PROXIMITY
BREED_MATCH
COLOR_MATCH
SEX_MATCH
SIZE_MATCH
MANUAL_LINK
```

### 6.4 `M13MatchDecision`

- `id`
- `candidateId`
- `decision`
- `actorUserId`
- `actorAuthority`
- `reasonCode`
- `notePrivate` opcional
- `createdAt`

## 7. Matching: reglas numeradas

M13 Bloque 1 utiliza matching local, determinista y explicable. No usa IA de imagen.

1. **Especie obligatoria:** especies distintas no generan candidato.
2. **Caso activo:** solo se comparan casos Lost/Found activos.
3. **Ventana temporal:** por defecto, hasta 30 días entre el caso y el avistamiento.
4. **Proximidad geográfica:** radio por defecto de 10 km cuando existen coordenadas aproximadas.
5. **Zona textual:** se usa como respaldo cuando no hay coordenadas.
6. **Rasgos opcionales:** raza, color, sexo y tamaño suman evidencia; la ausencia de un dato no invalida.
7. **Puntuación explicable:** cada candidato conserva razones legibles.
8. **Niveles:**

```text
LOW: 0–39
MEDIUM: 40–69
HIGH: 70–100
```

9. **Sin autoconfirmación:** incluso un candidato `HIGH` requiere decisión humana.
10. **Sin reconocimiento biométrico:** no se realiza reconocimiento facial, ocular ni identificación automática por imagen.
11. **Sin exposición:** el scoring no revela coordenadas exactas ni contacto privado.
12. **Aislamiento:** una coincidencia solo puede relacionar el caso y el avistamiento indicados.
13. **Idempotencia:** recalcular el mismo par caso-avistamiento no crea duplicados.
14. **Trazabilidad:** toda confirmación, rechazo o descarte debe quedar auditado.

## 8. Permisos de dominio

Constantes previstas:

```text
lostfound.sighting.read
lostfound.sighting.create
lostfound.sighting.manage_own
lostfound.sighting.moderate
lostfound.match.read
lostfound.match.review
lostfound.match.confirm
```

En Bloque 1 son contratos de dominio y guardas locales. La persistencia remota y autoridad RPC se definen en Bloque 2.

## 9. Pantallas y rutas

Rutas canónicas:

```text
m13/sightings
m13/sightings/new
m13/sightings/{sightingId}
m13/cases/{caseId}/matches
m13/matches/{candidateId}
```

Integración:

- `LostFoundScreen` sigue siendo la entrada principal del producto.
- M13 agrega navegación a lista, alta, detalle y candidatos.
- No se crea una segunda pantalla raíz que compita con Lost/Found.

## 10. Privacidad y seguridad

1. No publicar coordenadas exactas.
2. No exponer teléfono, correo ni dirección.
3. Las notas privadas solo son visibles para actores autorizados.
4. Las imágenes usan referencias seguras M05.
5. La identidad del reportante se redacta en vistas públicas.
6. Las acciones sensibles requieren usuario autenticado.
7. La autoridad se deriva de M03/M04/M08, no de datos enviados por el cliente.
8. Sin `service_role` en Android.
9. Sin DML directo cuando exista persistencia Supabase.
10. Toda función remota futura debe usar RLS/RPC, `SECURITY DEFINER` controlado y `search_path=public`.

## 11. Exclusiones

Fuera del alcance de M13:

- pagos;
- historia clínica;
- chat en tiempo real;
- llamadas o contacto directo expuesto;
- reconocimiento facial o biométrico;
- clasificación automática por IA;
- seguimiento GPS en segundo plano;
- publicación de ubicación exacta;
- recompensas económicas;
- actuación policial o municipal;
- cierre automático del caso sin confirmación humana;
- reemplazo destructivo del legacy Lost/Found;
- push real hasta disponer de infraestructura M06.

## 12. Orden de bloques

### Bloque 1 — Fundación local y matching explicable

- auditoría legacy;
- dominio;
- estados;
- validadores;
- errores;
- contratos;
- fakes;
- adaptador de `LostFoundSighting`;
- scoring local;
- lista, alta y detalle;
- candidatos locales;
- navegación;
- permisos constantes;
- pruebas;
- documentación;
- sin SQL.

### Bloque 2 — Persistencia y seguridad

- migración `048_m13_sightings_and_match_candidates.sql` creada localmente;
- tabla lateral `lost_found_sighting_details` + candidatos/decisiones/historial;
- 13 RPC cliente; RLS/grants; sin confirm/reject remoto;
- repositorios Supabase + DataProvider;
- **048 pendiente de aplicación remota**;
- validación estructural remota y smoke: pendientes.

### Bloque 3 — Revisión y confirmación

- flujo `PROPOSED → UNDER_REVIEW → decisión`;
- autoridad M08/M03/M04;
- moderación;
- historial;
- idempotencia y concurrencia;
- eventos M07;
- smoke remoto.

### Bloque 4 — Endurecimiento y cierre

- privacidad final;
- expiraciones;
- métricas sin PII;
- preparación M06;
- regresión;
- documentación;
- cierre técnico y oficial.

## 13. Definición de terminado del Bloque 1

El Bloque 1 queda cerrado localmente cuando:

1. el legacy se conserva;
2. existe dominio M13 completo;
3. el matching es determinista y explicable;
4. no hay autoconfirmación;
5. existen contratos y fakes;
6. existe navegación básica;
7. las vistas públicas están redactadas;
8. las referencias de media son seguras;
9. las pruebas focalizadas pasan;
10. `compileLocalDebugKotlin` pasa;
11. no existía migración `048` al cerrar Bloque 1 (histórico; 048 llega en Bloque 2);
12. no se aplica SQL;
13. hay un único commit y push;
14. M12 sigue documentado como pendiente externo.

## 14. Definición de terminado del Bloque 2

El Bloque 2 queda cerrado localmente cuando:

1. existe solo `048` nueva (sin 049);
2. 001–047 intactas;
3. legacy `lost_found_sightings` preservado;
4. lateral + candidatos + decisiones + historial;
5. 13 RPC cliente sin confirm/reject;
6. RLS/grants/helpers correctos;
7. repos Supabase + DataProvider;
8. guard CI highest = 048;
9. 048 no aplicada remotamente;
10. M12 sigue pendiente externo.
