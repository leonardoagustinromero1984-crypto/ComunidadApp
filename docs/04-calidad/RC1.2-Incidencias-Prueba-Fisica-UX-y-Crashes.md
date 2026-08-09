# RC1.2 — Incidencias prueba física (UX y crashes)

**Fecha:** 2026-08-06  
**Marca:** LeoVer  
**Estado:** Corregido en local (pendiente re-prueba física de Leonardo)  
**HEAD base:** `5986a01eb8662a44d3283d5ae1161816f65e156e`  
**Nota:** Sin dispositivo ADB conectado durante la sesión; crashes/errores se corrigieron por análisis de código + pruebas focalizadas. RC1.2 **no cerrado**.

---

## 1. Crash — detalle de mascota (P0)

| Campo | Detalle |
|-------|---------|
| **Pasos** | Perfil → tocar una mascota creada |
| **Causa raíz** | (1) `petDetail` / args sin encode-decode consistente + factory sin `petId` normalizado; (2) JSON de salud con `vaccinations`/`reminders` null sin `coerceInputValues` → fallo de deserialización al mapear el detalle. |
| **Solución** | Encode/decode de rutas; ViewModel factory con `petId` seguro; DTOs nullable + `coerceInputValues`; estados Loading / datos / vacío / inexistente / error recuperable (Volver/Reintentar). Nunca cerrar la app. |
| **Archivos** | `NavRoutes.kt`, `ComunidappNavGraph.kt`, `PetDetailScreen.kt`, `PetDetailViewModel.kt`, `PetM08Dtos.kt`, `PetM08Mappers.kt`, `SupabasePetM08RemoteDataSource.kt` |
| **Validación** | `PetDetailSmokeRegressionTest` |
| **Estado** | Corregido (pendiente físico) |

---

## 2. Crash — Publicar adopción (P0)

| Campo | Detalle |
|-------|---------|
| **Pasos** | Sumate → Adopciones → Publicar adopción |
| **Stack trace (resumen)** | `java.lang.RuntimeException: Cannot create an instance of class AdoptionFormViewModel` → `InstantiationException` en `ViewModelProvider` al abrir `ADOPTION_FORM` / `PUBLISH_ADOPTION` con `viewModel()` sin factory. |
| **Causa raíz** | `AdoptionFormViewModel(SavedStateHandle, repos…)` con parámetros default de Kotlin: la factory por defecto de Compose **no** puede instanciar ese constructor → crash al componer la pantalla. (Antes también había riesgo con `ExposedDropdownMenu`/`menuAnchor`, ya retirado.) |
| **Solución** | `AdoptionFormViewModel.factory()` con `CreationExtras.createSavedStateHandle()`; pantalla usa `viewModel(factory=…)`. Formulario abre sin mascota preseleccionada; vacío → «Primero creá el perfil…»; con mascotas → «Elegí la mascota…»; Cancelar/Atrás → Adopciones. |
| **Archivos** | `AdoptionFormViewModel.kt`, `AdoptionFormScreen.kt`, `ComunidappNavGraph.kt` |
| **Validación** | `AdoptionFormOpenSmokeTest` |
| **Estado** | Corregido en local (pendiente re-prueba física) |

---

## 2b. Error — crear pasaporte (P0)

| Campo | Detalle |
|-------|---------|
| **Pasos** | Perfil → mascota → Pasaporte → Crear pasaporte |
| **Stack trace (resumen)** | Fallo en capa remota al decodificar respuesta RPC: PostgREST `m14_create_pet_passport` retorna **jsonb** (objeto); `decodeSingle()` espera array de filas → excepción de serialización → `M14_UNKNOWN` → mensaje genérico de error. |
| **Causa raíz** | (1) `SupabaseM14RemoteDataSource.rpc` usaba `decodeSingle()` incompatible con `returns jsonb`. (2) Tras un create exitoso, `observePassportForPet` es cold one-shot y no re-emitía → UI seguía vacía. (3) Errores desconocidos no tenían copy recuperable con Reintentar/Volver. |
| **Solución** | Decode vía `decodeAs<JsonElement>()` + `Json.decodeFromJsonElement` (objeto o array). `createFromPet` aplica el passport del `Result` al StateFlow; si `PASSPORT_ALREADY_EXISTS`, recupera el existente. Error recuperable: título «No pudimos crear el pasaporte», Reintentar/Volver. UNAUTHORIZED sin códigos M08. |
| **Archivos** | `SupabaseM14RemoteDataSource.kt`, `M14PassportViewModels.kt`, `M14PassportScreens.kt`, `M14ErrorMapper.kt` |
| **Validación** | `M14PassportCreateFromPetTest`, `M14ErrorMapperUxTest` |
| **Estado** | Corregido en local (pendiente re-prueba física; sin ADB en sesión) |

---

## 3. Navegación Perfil → Crear atrapada + scrim (P1)

| Campo | Detalle |
|-------|---------|
| **Pasos** | Perfil → `+ Crear` → quedar en Publicar sin volver; sombra sobre FAB |
| **Causa raíz** | Navegación a tab `publish` (selecciona FAB + indicador); hub sin Atrás; posible sheet de menú no cerrado. |
| **Solución** | Ruta `publish_from_profile` (no selecciona FAB); Atrás → Perfil; bottom bar cierra overlay con `popUpTo(HOME)`; menú cierra sheet (`hide`) antes de navegar; indicador transparente en FAB. |
| **Archivos** | `NavRoutes.kt`, `ComunidappNavGraph.kt`, `PublishScreen.kt`, `ComunidappBottomBar.kt`, `ProfileMenuSheet.kt`, `ProfileScreen.kt` |
| **Estado** | Corregido (pendiente físico) |

---

## 4. Doble signo `++` (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | Texto `+ Crear` + icono `Icons.Default.Add` |
| **Solución** | Texto `Crear` + icono `+` |
| **Archivo** | `ProfileScreen.kt` |
| **Estado** | Corregido |

---

## 5. Insets / status bar (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | `enableEdgeToEdge` + Main Scaffold solo aplicaba padding inferior |
| **Solución** | `contentWindowInsets = 0` en host; `LeoTopAppBar` con `statusBars`; bottom bar con `navigationBars` |
| **Archivos** | `ComunidappNavGraph.kt`, `LeoComponents.kt`, `ComunidappBottomBar.kt`, `Theme.kt` (iconos claros status) |
| **Estado** | Corregido (pendiente físico) |

---

## 6. Comunidad — categorías y filtros (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | Tiles con colores no deterministas; solo feedback visual; botón Filtros sin acción |
| **Solución** | Estilo blanco/naranja seleccionado; filtro real + título/loading/empty; `ModalBottomSheet` con localidad/activos, Limpiar/Aplicar |
| **Archivos** | `ComunidadScreen.kt`, `ServiceViewModel.kt`, `LeoComponents.kt` |
| **Validación** | `ComunidadFiltersSmokeTest` |
| **Estado** | Corregido (pendiente físico) |

---

## 6b. Perfil mascota — UX integral (P1/P2)

| Tema | Solución |
|------|----------|
| Responsable principal | Nombre completo vía `userRepository`; nunca ID crudo |
| Salud vacía | CTA Agregar información / Editar |
| Red de cuidado | Sección única (sin duplicar autorizaciones en detalle) |
| Microchip | Retirado de UI; campo legado en DTO/BD |
| Historial | Fuera del primer plano del detalle |
| Archivar | «Archivar perfil» en menú; reversible con Restaurar |
| Fallecimiento | «Informar fallecimiento» empático en menú admin |
| Mxx | Strings productivos humanizados en flujos tocados |

**Doc de producto:** `docs/08-marca/D08-14-Perfil-Mascota-Salud-y-Red-de-Cuidado.md`

---

## 7. Reels / Historias “Próximamente” (P2)

| Campo | Detalle |
|-------|---------|
| **Solución** | Flujos reales `PublishReelScreen` / `PublishStoryScreen`; tipos `REEL`/`STORY`; `expires_at` + migración `078`; carrusel e Inicio/Perfil filtran por tipo |
| **Backend** | Migración creada; **no aplicada** a remoto en esta sesión (bloqueo: sin confirmación de entorno) |
| **Estado** | Cliente listo; migración pendiente de aplicar en staging |

---

## 8. Mapa de alertas vacío (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | Placeholder sin GPS, sin filtros útiles, sin detalle in-app; mock sin coordenadas |
| **Solución** | `AlertMapScreen` Mapa\|Lista, filtros, privacidad, empty/permiso/error, detalle; mock con coords demo |
| **Archivos** | `AlertMapScreen.kt`, `AlertMapViewModel.kt`, `AlertLocationPrivacy.kt`, `NavRoutes`, mock |
| **Validación** | `AlertMapFilterTest` |
| **Estado** | Corregido (pendiente físico) |

---

## 9. Sumate pierde estado (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | `remember` local + `navigate(SUMATE)` tras publicar |
| **Solución** | `SumateViewModel`/`SavedStateHandle`; retorno con `popBackStack(SUMATE)` |
| **Archivos** | `SumateViewModel.kt`, `SumateScreen.kt`, `ComunidappNavGraph.kt` |
| **Validación** | `SumateStatePersistenceTest` |
| **Estado** | Corregido (pendiente físico) |

---

## 10. System insets (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | Edge-to-edge + Home sin statusBars; NavigationBar con insets duplicables; nav bar sin color |
| **Solución** | `SocialHomeTopBar` + statusBars; bottom bar `windowInsets=0` + padding único; theme nav bar cream/light; Scaffolds tab con `contentWindowInsets=0` |
| **Archivos** | `SocialHomeComponents.kt`, `ComunidappBottomBar.kt`, `Theme.kt`, Home/Sumate/Comunidad/Profile |
| **Estado** | Corregido (pendiente físico) |

---

## 11. Username no controlado en alta (P1)

| Campo | Detalle |
|-------|---------|
| **Causa** | Registro sin username; se difería a onboarding |
| **Solución** | Campo obligatorio en registro; debounce/disponibilidad; migración 079 no destructiva; cuentas existentes intactas |
| **Archivos** | `RegisterScreen`, `RegisterViewModel`, `UsernameValidators`, Auth repos, `079_*.sql` |
| **Validación** | `UsernameValidatorsTest`, `AuthViewModelsTest`, `MockAuthRepositoryTest` |
| **Estado** | Cliente listo; migración pendiente de aplicar en staging |

---

## Confirmaciones

- Sin commit.
- Sin push.
- Sin cambio de `versionName` / `versionCode`.
- Sin emulador.
- Un solo `assembleLocalDebug` al cierre.
- RC1.2 **no cerrado**.
