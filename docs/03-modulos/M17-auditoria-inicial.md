# M17 — Auditoría inicial (Bloque 1)

## 1. HEAD inicial

`79e2a36f59e2176cebf3fa26b3e8762f98faf21e` — `fix(m16): complete remote validation and module closure`

## 2. Estado Git inicial

- Rama: `main`, alineada con `origin/main`
- Working tree limpio al inicio
- M16 cerrado oficialmente; migración 053 aplicada en staging
- M17 no iniciado; M18 y posteriores no iniciados

## 3. Nombre oficial vs alcance del bloque

| Fuente | Nombre |
|--------|--------|
| **D01 (autoritativo)** | **M17 Donaciones y voluntariado** |
| Prompt Bloque 1 | Donaciones y campañas solidarias |

**Decisión:** conservar número **M17** y nombre D01. Bloque 1 implementa la subcapa **campañas solidarias** (dinero mock); voluntariado y bienes quedan para bloques posteriores.

## 4. Documentos revisados

- `docs/01-producto/D01-Modulos-y-Orden.md`
- `docs/03-modulos/M16-auditoria-inicial.md`, `M16-cierre-oficial.md`
- Código focal: M03 `Organization*`, M04 moderación, M05 media, M06 notificaciones, M08 mascotas, M10 ubicación, M16 refugios, `DataProvider.kt`, `NavRoutes.kt`, Sumate

## 5. Auditoría M01 / M02

| Aspecto | Autoridad | Uso M17 |
|---------|-----------|---------|
| Sesión actual | M01 `AuthRepository` / sesión mock | Actor `mock_user_admin` en seeds |
| Identidad usuario | M01 userId | `createdBy` interno; no expuesto en público |
| Permisos plataforma | M02 roles | Sin roles M17 paralelos |
| Acciones autenticadas | M02 + M03 membership | Gestión campaña vía pertenencia org |

## 6. Auditoría M03

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| Organización autoritativa | `Organization`, `organizationId` | **Toda campaña pertenece a org M03** |
| Tipos elegibles | SHELTER, RESCUE_GROUP, NGO | `M17_ELIGIBLE_ORGANIZATION_TYPES` |
| Membresías / managers | `organizationManagers` mock; producción membership M03 | `canManageOrganization` |
| Permisos contenido | `OrganizationPermissionCode` | Propuesta `donation.view`, `donation.manage` |
| OrganizationAuthorizationService | Patrón M16 reutilizado | Validación en repositorio, no solo UI |

**Decisión campaña-organización:** M17 **no crea** organizaciones. `organizationId` es FK lógico obligatorio.

## 7. Auditoría M04

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| Moderación contenido | Colas admin existentes | Campo `moderationStatus` interno; sin cola paralela B1 |
| Estados aprobación | Patrón PENDING/APPROVED org | Hook futuro; mock no decide |
| Reportes | Infra M04 compartida | Campañas publicadas moderables en B2+ |
| Auditoría | Eventos admin | Sin duplicar sistema M04 |

## 8. Auditoría M05

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| Imágenes | `OrganizationMediaStorageService`, refs | `coverImageRef`, `galleryImageRefs` |
| Privacidad media | Permisos por recurso | Solo refs públicas en modelo público |
| Borrado | Patrón M05 | B2 integración real |

## 9. Auditoría M06

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| Allowlist vigente | M01–M05 (+ extensiones documentadas) | **No ampliada silenciosamente** |
| Hooks M17 | `M17M06Hooks` definidos | Emisión solo si infra disponible; fallback honesto |
| Comportamiento offline | Patrón M15/M16 | `M17_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE` |

## 10. Auditoría M08

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| Referencia mascota | `petId` + nombre público | Opcional; no exige mascota |
| Campos públicos | Nombre, no PII | `petPublicName` en referencia |
| Privacidad | Sanitizer M08 pattern | Sin userId del responsable |
| Estados restrictivos | Adopciones/tránsito | Validación referencia en B2 |

## 11. Auditoría M10

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| Ubicación pública | Texto aproximado | `publicLocationText` en referencia |
| Domicilio exacto | No expuesto | Sanitizer redacta tel/email |
| Filtros geo | M10 search | Filtro textual B1; geo B2+ |

## 12. Auditoría M16

| Aspecto | Hallazgo | M17 |
|---------|----------|-----|
| organizationId | Perfil refugio → org M03 | Campaña puede vincular `shelterProfileId` |
| Navegación | Rutas `m16/shelters/*` | Enlace desde Sumate; sin modificar M16 |
| Necesidades públicas | Modelo M16 | `needDescription` opcional en referencia |
| Permisos | `shelter.view`, `shelter.manage` | Independientes de `donation.*` |

## 13. Separación M17 / M24 (pagos)

| Responsabilidad | Módulo |
|-----------------|--------|
| Campaña, objetivo, estado, transparencia | **M17** |
| Cobro, tokenización, conciliación, chargeback | **M24** |
| Contribución mock / contratos | M17 B1 |
| `providerReference` sanitizada | Contrato; no UI pública |

**M17 Bloque 1 no almacena:** tarjetas, CVV, cuentas bancarias, tokens secretos, email/teléfono donante en modelos públicos.

## 14. Riesgos

| Riesgo | Mitigación B1 |
|--------|---------------|
| Duplicar org/usuarios/mascotas | FK + mock aislado `M17MemoryStore` |
| Exponer PII financiera | `M17PrivacySanitizer`, modelos públicos |
| Pagos reales prematuros | `registerMockContribution` + aviso UI |
| Cola moderación paralela | Campo interno; integración M04 en B2 |
| Pérdida precisión monetaria | `amountMinor: Long`, no `Double` |

## 15. Funcionalidades diferidas

- Migración **054** y tablas Supabase (evaluar Bloque 2)
- Pagos reales, checkout, webhooks (M24)
- Voluntariado y donación en especie (M17 producto completo)
- Moderación admin campañas (M04 integración)
- Notificaciones M06 en allowlist (B2+)
- Búsqueda geográfica avanzada (M10)

## 16. Propuesta Bloques 2 y 3

**Bloque 2:** migración 054, RLS, `SupabaseM17DonationRepository`, validación remota, permisos M03 reales, M06 allowlist explícita.

**Bloque 3:** integración M24 checkout, conciliación contribuciones, dashboard transparencia, enlaces web compartibles (M11).

## 17. Decisión migración 054

```text
PENDIENTE — no creada en Bloque 1
Evaluar esquema en Bloque 2: m17_campaigns, m17_contributions, m17_campaign_updates
Sin SQL aplicado en Bloque 1
```
