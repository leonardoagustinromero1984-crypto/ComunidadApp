# M05 — Pruebas UI y flujos operativos de archivos (Etapa 4)

**Módulo:** M05 — Archivos, Media y Documentos  
**Producto:** LeoVer  
**Alcance:** Coordinator, resolver, pickers, ViewModels, logout, legacy  
**Estado remoto:** Migraciones `014`–`024` **PENDIENTE DE VALIDACIÓN REMOTA**  
**Migración 025:** **no creada** (sin defecto bloqueante detectado en `024`)

---

## 1. Suite unitaria Android (nuevas Etapa 4)

| Prueba | Cobertura |
|--------|-----------|
| `FileUploadCoordinatorTest` | upload mock a READY sin bucket legacy; doble submit rechazado con upload activo; cancelación idempotente + retry exitoso; **reemplazo seguro** (viejo intacto hasta nuevo READY, luego unlink/delete); unlink y delete como operaciones explícitas separadas; evidencia/soporte sensibles nunca `PUBLIC`; `clearAllSensitiveState` limpia URI/locks/retry/sesiones |
| `FileDisplayResolverTest` | signed URL expira en memoria y **no** se persiste en el asset; deep link sensible sin permiso → denegado; organización incorrecta → denegado; fallback legacy solo lectura y rechazo de `content://`/`data:` como permanentes |
| `FileUiErrorMapperTest` | backend sin migración `024` (`PGRST202`, "schema cache", función inexistente) → mensaje **recuperable** y seguro; detalles técnicos (bucket/path/token/SQL) jamás reflejados al usuario |
| `FileSessionCleanupTest` | logout administrativo limpia preview URI, estado de upload y locks (logout durante upload incluido) |
| `M05Etapa4AuthSurfaceUntouchedTest` | confirma que la superficie pública de `AuthRepository` y `UsernameValidators` sigue disponible y **sin modificar** en esta etapa |

Además se conservan íntegras las suites previas: validación de archivos
(`FileValidationRulesTest`, `FileNameSanitizerTest` — nombre, extensión, MIME,
tamaño, cantidad, propósito), sesiones (`FilePathSessionRetentionLegacyTest`),
mocks (`FileRepositoryMocksTest`), autorización (`FileAuthorizationTest`),
mapeo Supabase (`M05SupabaseMappingTest`) y adaptadores legacy.

**Resultado local Etapa 4:** **353** tests, **0** failures, **0** errors
(**338** conservadas de Etapa 3 + **15** nuevas).

---

## 2. Matriz de escenarios requeridos

| Escenario | Prueba |
|-----------|--------|
| Validación de archivos (MIME/tamaño/cantidad/purpose) | `FileValidationRulesTest` + coordinator |
| Upload, cancelación y retry | `FileUploadCoordinatorTest` |
| Doble submit | `FileUploadCoordinatorTest` |
| Signed URL y expiración | `FileDisplayResolverTest` |
| Reemplazo seguro | `FileUploadCoordinatorTest` |
| Unlink / delete | `FileUploadCoordinatorTest` |
| Logout durante upload | `FileSessionCleanupTest` |
| Limpieza de datos sensibles | `FileSessionCleanupTest` + coordinator |
| Deep link sin permiso | `FileDisplayResolverTest` |
| Organización incorrecta | `FileDisplayResolverTest` |
| Evidencia y soporte sensibles (nunca públicos) | `FileUploadCoordinatorTest` |
| Fallback legacy | `FileDisplayResolverTest` + `LegacyFileReferenceAdapterTest` |
| Backend remoto sin migración `024` | `FileUiErrorMapperTest` |
| Username / auth no modificados | `M05Etapa4AuthSurfaceUntouchedTest` + diff vacío en `AuthRepository` / validadores / migraciones |

---

## 3. Flujos manuales / smoke (mock o staging)

1. Editar perfil → Photo Picker → validación → progreso → avatar actualizado sin `content://` final.
2. Onboarding → avatar con mismo flujo M05.
3. Editar organización → logo/portada; adjuntar documento (OpenDocument PDF/imagen) con permisos org.
4. Alta/edición de mascota → imagen principal vía M05.
5. Publicar (feed/adopción/perdido-encontrado) → imagen vía `public-media`; **nunca** `leover`.
6. Caso de moderación (staff) → adjuntar evidencia; sin URL permanente visible.
7. Ticket de soporte (usuario) → adjunto propio; mensajes INTERNAL nunca visibles ni sus adjuntos.
8. Ticket de soporte (staff) → adjunto INTERNAL solo con `support.view_sensitive`.
9. Cancelar upload en curso → estado Cancelled; reintentar → completa.
10. Logout con upload en curso → previews/URIs/signed URLs/locks limpiados.
11. Backend sin `024` → mensaje "El servicio de archivos no está disponible todavía…" y opción de reintentar; sin fallback inseguro.
12. Deep link a evidencia sin permiso → denegado.

---

## 4. Seguridad UI

| Regla | Verificación |
|-------|--------------|
| `leover` prohibido en nuevos uploads | Uploader + repos + coordinator (código `LEGACY_BUCKET_DENIED`) |
| Signed URL nunca persistida | Solo memoria con expiración; limpieza en logout |
| Sin `content://` / base64 final | `FileDisplayResolver` + uploader |
| Bucket/path elegido por servidor | `prepareUploadSession`; cliente divergente rechazado |
| Sin detalles técnicos al usuario | `FileUiErrorMapper` |
| Sensibles nunca PUBLIC | Dominio + constraint SQL `024` |
| Deny-by-default en flujos staff | Gates M04 reutilizados |
| AccountType / modules / roles M03 fuera de su org | Sin autoridad (M05 Etapas 2–3) |

---

## 5. Remoto

**PENDIENTE DE VALIDACIÓN REMOTA** para `014`–`024`.  
No se afirma despliegue, checklist ni evidencia en staging/producción.  
No se aplicaron migraciones en esta etapa.
