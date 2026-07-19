# M05 — Pruebas de persistencia, Storage, RLS y RPC (Etapa 3)

**Módulo:** M05 — Archivos, Media y Documentos  
**Producto:** LeoVer  
**Alcance:** SQL/Storage/RLS documentado (`024`) + suite unitaria Android  
**Estado remoto:** Migraciones `014`–`024` **PENDIENTE DE VALIDACIÓN REMOTA** (no se afirma deploy ni checklist ejecutado en staging/producción en esta etapa).

---

## 1. Suite SQL / Storage / RLS (manual / staging)

Ejecutar solo en proyecto de staging o local. **No** correr destructivos en producción.

### 1.1 Bucket `leover` (legado)

| Caso | Esperado |
|------|----------|
| SELECT público de objeto existente | OK (`leover_public_read`) |
| URL pública legacy existente | Sin cambio de path/URL |
| INSERT autenticado a `leover` | Denegado (política upload eliminada) |
| UPDATE / DELETE autenticado | Denegado |
| Objetos existentes | **No** borrados ni movidos |
| Upload M05 con purpose mapeado a `leover` | Imposible (`m05_logical_bucket` no lo elige; `OTHER` rechazado) |

### 1.2 Buckets nuevos

| Caso | Esperado |
|------|----------|
| `public-media` público | SELECT abierto; MIME imagen; límite ~8 MiB |
| `organization-documents` privado | Sin lectura anónima; PDF/imagen |
| `moderation-evidence` privado | Sin lectura anónima; no usar `leover` |
| `support-attachments` privado | Sin lectura anónima; no usar `leover` |
| `profile-avatars` / `organization-media` | Existen; **no** recreados por `024` |

### 1.3 Tablas y RLS

| Caso | Esperado |
|------|----------|
| INSERT/UPDATE/DELETE directo `file_*` | Denegado (`with check (false)` + revoke) |
| SELECT `file_assets` sin permiso de lectura | 0 filas |
| Ownership dual USER+ORG | Constraint XOR rechaza |
| Purpose sensible + visibility PUBLIC | Constraint rechaza |
| Version con bucket `leover` | Check rechaza |
| Path con `..` o `://` | Check / builder rechaza |
| `resource_id` tipo URL `http%` | RPC `VALIDATION` |

### 1.4 Upload y paths

| Caso | Esperado |
|------|----------|
| `create_file_upload_session` | Crea asset + version + session; path **servidor** |
| `p_storage_path` distinto al generado | `CLIENT_STORAGE_PATH_DENIED` |
| MIME / size fuera de allowlist | `MIME_TYPE_INVALID` / `FILE_SIZE_INVALID` |
| Storage INSERT sin sesión válida | Denegado |
| Storage INSERT con sesión `CREATED`/`READY_TO_UPLOAD`/`UPLOADING` del actor | OK (bucket tipado) |
| `complete_file_upload` | Version READY, asset READY, `current_version_id` |
| `cancel_file_upload` | Idempotente |
| Purpose `OTHER` | `VALIDATION` |

### 1.5 Acceso y signed URLs

| Caso | Esperado |
|------|----------|
| `resolve_public_file_asset` no PUBLIC/READY | `NOT_FOUND` / no resoluble |
| `request_file_signed_url` | Devuelve `bucket`, `path`, `expires_in_seconds` — **sin** token/URL |
| TTL `SENSITIVE_SHORT` | ≤ 600 s |
| Persistencia de signed URL en SQL | **No** existe columna ni escritura |
| Staff sin permiso sensible | `FORBIDDEN` + audit opcional |
| M03 fuera de su org | Sin acceso a docs de otra org |

### 1.6 Retención / legal hold / delete

| Caso | Esperado |
|------|----------|
| Soft delete con legal hold | Bloqueado |
| Unlink | Soft en link; asset permanece |
| `can_physically_delete_file_asset` con links activos | `false` / `ACTIVE_LINKS` |
| Legal hold place/release | Solo staff + policy `legal_hold_allowed` |
| AccountType / active_modules | **No** conceden |

### 1.7 RPC de seguridad

| Caso | Esperado |
|------|----------|
| Funciones privilegiadas | `SECURITY DEFINER` + `search_path = public` |
| Actor | Siempre `auth.uid()` / `m05_require_active_actor` |
| `execute` a `public`/`anon` | Revocado; grant a `authenticated` |
| Antivirus / EXIF / thumbnails / sniffing | **No** implementados — no afirmar |

---

## 2. Suite unitaria Android (repo)

| Área | Cobertura |
|------|-----------|
| Mocks Etapa 2 | `FileRepositoryMocksTest` y dominio files (conservados) |
| Mapping RPC → dominio | `M05SupabaseMappingTest` |
| DataProvider contratos | `M05DataProviderWiringTest` |
| Legacy adapter | `LegacyFileReferenceAdapterTest` |
| Refs M04 | `FileAssetReferenceResolverTest` |
| Autorización / paths / retention | Tests dominio Etapa 2 existentes |

**Resultado local Etapa 3:** **338** tests, **0** failures, **0** errors  
(327 conservadas de Etapa 2 + 11 nuevas de Etapa 3).

No dependen de staging ni de red real.

---

## 3. Calidad local ejecutada

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **SUCCESS** (338 / 0 / 0) |
| `lintDebug` | **SUCCESS** |

---

## 4. Fuera de alcance (no afirmar)

- Validación remota de `024` en staging/producción  
- Pantallas / navegación de archivos (Etapa 4)  
- Migración masiva de columnas `*_url`  
- Antivirus, strip EXIF, generación de thumbnails, MIME sniffing profundo  
- GPS, mapas, pagos, M06  

---

## 5. Evidencia de no-regresión crítica `leover`

Checklist de aceptación documental (staging pendiente):

1. Lectura pública legacy sigue disponible.  
2. Escritura autenticada abierta eliminada.  
3. Sin borrado/movimiento de objetos.  
4. Sin cambio de URLs legacy.  
5. Sin uploads M05 nuevos hacia `leover`.
