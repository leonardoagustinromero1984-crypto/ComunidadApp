# M16 — Auditoría inicial (Bloque 1)

> **Nota de vigencia (2026-08-09):** documento histórico de inicio de Bloque 1. Para numeración Mxx actual consultar `D01-Modulos-y-Orden-v1.2.md` (p. ej. M10 = tránsito SQL, no geoservicios).

## 1. HEAD inicial

`058ea74d70f5dd5e7a6ab56ff5e13cba2c03c76b` — `docs(m15): complete foster care module closure`

## 2. Estado Git

- Rama: `main`, alineada con `origin/main`
- Working tree limpio al inicio
- M15 oficialmente cerrado; sin modificaciones M15 en este bloque

## 3. Documentos revisados

- `docs/01-producto/D01-Modulos-y-Orden.md`
- `docs/03-modulos/M03-Organizaciones-y-Equipos.md`
- `docs/03-modulos/M11-cierre-final.md` (M11 técnico refugios legacy)
- `docs/02-arquitectura/M15-Bloque-4-validacion.md` (patrón de cierre M15)
- Código focal: `OrganizationRepositories.kt`, `ShelterOperationsRepositories.kt`, `M15*`, `DataProvider.kt`, `NavRoutes.kt`

## 4. Componentes reutilizables

| Componente | Ubicación | Uso M16 |
|------------|-----------|---------|
| `Organization` / `OrganizationRepository` | M03 domain + repos | Autoridad organizaciones |
| `OrganizationPermissionCode` / roles | M03 authorization | Permisos refugio |
| `ShelterProfile` / M11 repos | M11 legacy | Referencia; **no duplicar** |
| `M15MemoryStore` pattern | M15 | Patrón mock Bloque 1 |
| `M15PrivacySanitizer` pattern | M15 | Sanitización pública |
| `OrganizationMediaStorageService` | M05 | Imagen futura (ref only) |

## 5. Autoridad por dominio

| Dominio | Autoridad | M16 |
|---------|-----------|-----|
| Usuarios / sesión | M01 + M02 | Referencia `auth.uid()` |
| Roles plataforma | M02 | Sin roles M16 paralelos |
| Organizaciones / equipos | **M03** | `organizationId` obligatorio |
| Moderación / verificación admin | **M04** | Solicitud local; decisión externa |
| Mascotas | **M08** | Sin perfiles duplicados B1 |
| Adopciones | **M09** (producto M14) | Sin reimplementar B1 |
| Tránsito | **M15** (cerrado) | Sin reimplementar |
| Notificaciones | **M06** | Hooks; allowlist M01–M05 |
| Ubicación / mapas | **M10** búsqueda | Zona pública aproximada B1 |
| Archivos / imágenes | **M05** | `publicImageRef` |
| Donaciones / pagos | **M17/M24** | Fuera de alcance |
| Operación refugio legacy | **M11** | Preservado (`shelter_*`) |

## 6. Deuda / inconsistencias

- Conviven `Shelter` legacy (006), M11 `ShelterProfile` y producto M16 — track M16 nuevo prefijo `m16/*`
- M11 y M16 comparten dominio semántico; M16 es producto D01 R4
- Verificación administrativa pertenece a M04; mock solo solicita PENDING

## 7. Contratos que M16 consume

- `OrganizationRepository.getById` (futuro B2)
- `OrganizationAuthorizationService` / membership (futuro B2)
- `OrganizationPermissionCode.ORGANIZATION_UPDATE`, `ORGANIZATION_PUBLISH`, `ORGANIZATION_REQUEST_VERIFICATION`
- Referencias M08 pet (B3), M09 adopción (B3), M15 tránsito (B3)

## 8. Contratos que M16 expone

- `M16ShelterRepository` — CRUD perfil + búsqueda pública
- `M16PublicShelter` — proyección sanitizada
- Rutas `m16/shelters/*`

## 9. Riesgos de duplicación

- **Mitigado:** perfil M16 referencia `organizationId` M03, no crea org
- **Mitigado:** sin tablas SQL B1; mock aislado `M16MemoryStore`
- **Pendiente B2:** alinear con M11/M03 remoto sin tablas paralelas

## 10. Decisión refugio-organización

```text
1 perfil M16 máximo por organización elegible (SHELTER | RESCUE_GROUP | NGO)
organizationId = FK lógico a M03
```

## 11. Decisión privacidad

- Vista pública via `M16PrivacySanitizer.toPublicShelter()`
- Sin userId, notas internas, dirección privada en público

## 12. Decisión capacidad

- Agregada local/mock; `M16ShelterCapacity` con validación no negativa
- Sin sync mascotas/adopciones/tránsito en B1

## 13. Decisión migración 053

```text
NO CREADA EN BLOQUE 1
Propuesta B2: shelter_profiles M16 vinculadas organization_id (M03)
               sin duplicar organizations / members / pets
Highest migration permanece 052
```

## 14. Bloques propuestos

| Bloque | Alcance |
|--------|---------|
| 1 | Fundación local (este bloque) |
| 2 | Persistencia 053 + Supabase + RLS |
| 3 | Integración M08/M09/M15 |
| 4 | Métricas, privacidad, smoke, cierre |

## 15. Fuera de alcance B1

- SQL / migración 053
- Supabase real
- Donaciones, pagos, chat
- Notificaciones push reales
- Gestión casos, tareas, reportes (resto M16 producto)
- Modificar M15
