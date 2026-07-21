# M08 — Checklist smoke APK local (Etapa 4C)

**Producto:** LeoVer  
**APK:** `apk/LeoVer-M08-Stage4C-debug.apk` (local, no Git)  
**Backend:** Supabase local (001–036)

```text
M08 ETAPA 4C — INTEGRACIÓN LOCAL AUTOMÁTICA PASS
SMOKE APK MANUAL — PENDIENTE
STAGING NO AUTORIZADO
```

Marcá cada ítem: **PASS** | **FAIL** | **NO EJECUTADO** | observación.

| # | Paso | Resultado | Observación |
|---|---|---|---|
| 1 | Iniciar Supabase local (`supabase start` / status OK) | | |
| 2 | Conectar emulador o dispositivo (`adb devices`; reverse si físico) | | |
| 3 | Instalar APK debug Stage 4C | | |
| 4 | Registrar / login con usuario local sintético (Usuario A) | | |
| 5 | Abrir MyPets | | |
| 6 | Crear mascota | | |
| 7 | Aparece en listado accesible | | |
| 8 | Editar nombre, especie, raza, edad, descripción | | |
| 9 | Actualizar datos de salud | | |
| 10 | Actualizar microchip | | |
| 11 | Provocar conflicto microchip ACTIVE → mensaje de dominio | | |
| 12 | Cerrar y reabrir la app | | |
| 13 | Verificar persistencia | | |
| 14 | Abrir detalle | | |
| 15 | Comprobar capacidades (canUpdate / canArchive, no ownerId==uid) | | |
| 16 | Probar avatar M05 → `m08_set_pet_avatar_asset` | | |
| 17 | Archivar mascota | | |
| 18 | Desaparece del listado ACTIVE | | |
| 19 | Restaurar si hay flujo UI disponible | | |
| 20 | Validar co-responsable (Usuario B) ve la mascota | | |
| 21 | Autorizado READ (Usuario C) ve sin editar | | |
| 22 | Principal organización / `owner_id` null no crashea | | |
| 23 | Perfil público no muestra mascotas ajenas | | |
| 24 | Logout / login | | |
| 25 | Sin crash ni filtrado de datos ajenos | | |

## Firmas

| Campo | Valor |
|---|---|
| Ejecutor | |
| Fecha | |
| Dispositivo / emulador | |
| Veredicto smoke | **PENDIENTE** (no declarar PASS sin prueba humana) |
