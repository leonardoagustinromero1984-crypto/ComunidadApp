# REBASE-03F — Physical QA checklist

**Producto:** LeoVer  
**APK:** `apk/LeoVer-REBASE-03F-STAGING.apk`  
**Ambiente:** Canonical Staging (`tobqbddfcyitwgbkthhy`)  
**Resultado físico:** `PENDING_USER`

Marcá cada prueba: **PASS** / **FAIL** / **N/A** + observación breve.

Identificá usuarios/mascotas QA con prefijo claro (ej. `qa03f-…`). No borrar datos de Staging hasta terminar el QA.

| ID | Prueba | Resultado | Observación |
| --- | --- | --- | --- |
| QA01 | Abrir app / inicio | | |
| QA02 | Crear cuenta nueva | | |
| QA03 | Username disponible/ocupado (`Leo` / `leo` / `LEO`) | | |
| QA04 | Crear mascota sin foto | | |
| QA05 | Crear mascota con foto | | |
| QA06 | Cerrar y volver a abrir mascota | | |
| QA07 | Foto sigue visible | | |
| QA08 | Editar nombre sin tocar foto | | |
| QA09 | Foto sigue visible después del edit | | |
| QA10 | Reemplazar foto | | |
| QA11 | Nueva foto sigue visible al recargar | | |
| QA12 | VitaCora abre correctamente | | |
| QA13 | Registrar dato de salud | | |
| QA14 | Salud aparece al recargar | | |
| QA15 | Datos de nacimiento/edad correctos | | |
| QA16 | No aparece “Pasaporte” como producto activo | | |
| QA17 | No aparece selector de tipo de cuenta legacy | | |
| QA18 | Familia/responsables si UI disponible | | |
| QA19 | Navegación atrás | | |
| QA20 | No crash en flujo mascota/VitaCora | | |
| QA21 | Perdidos/Encontrados básico si accesible | | |
| QA22 | Adopción pública web | | |
| QA23 | Página privada/inexistente devuelve 404 segura | | |
| QA24 | Logout/login y mascota persiste | | |
| QA25 | Revisión visual general | | |

## Gate crítico (foto)

QA05–QA11 son gate. Si alguno falla: `PHYSICAL_QA = FAIL`.

Secuencia obligatoria:

1. Crear mascota con foto → reload  
2. Editar texto sin cambiar foto → reload  
3. Reemplazar foto → reload  

Si el upload real falla, anotá el paso del diagnóstico:

`REGISTER_MEDIA` / `STORAGE_UPLOAD` / `SET_PET_AVATAR` / `MEDIA_SELECT` / `SIGNED_URL_RESOLUTION`

No asumir cambio de schema.
