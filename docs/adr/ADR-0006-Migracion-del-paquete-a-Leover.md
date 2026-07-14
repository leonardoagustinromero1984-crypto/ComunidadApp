# ADR-0006 — Migración del paquete a Leover

- **Estado:** Accepted  
- **Fecha:** 2026-07-14  
- **Módulo:** M00  

## Contexto

La marca del producto es **Leover**. El `applicationId` / `namespace` actuales son `com.comunidapp.app`. Hay deep links (`com.comunidapp.app://login-callback`), Firebase (`google-services.json`) y configuración Supabase ligados a ese id.

## Problema

¿Renombrar ahora a `com.leover.app`?

## Opciones consideradas

1. Renombrar en M00 junto con la documentación.  
2. Nunca renombrar.  
3. Documentar la migración y ejecutarla en rama/commit exclusivo **antes** de publicar en tienda, con verificación completa.

## Decisión

**Opción 3.** No renombrar en Etapa 2 ni en el resto de M00 salvo tarea específica aprobada. Objetivo futuro: `com.leover.app`.

## Checklist de migración (cuando se autorice)

- [ ] Rama exclusiva `chore/rename-application-id-leover`.  
- [ ] Cambiar `applicationId`, `namespace`, paquetes Kotlin solo si es necesario.  
- [ ] Actualizar deep links / intent filters.  
- [ ] Regenerar / actualizar Firebase (`google-services.json`) y FCM.  
- [ ] Revisar redirect URLs de Supabase Auth.  
- [ ] `assembleDebug`, tests, lint, smoke auth.  
- [ ] Commit atómico sin mezclar features.

## Consecuencias positivas

- Evita romper auth/push en M00 documentación.  
- Cambio controlado y reversible.

## Riesgos / consecuencias negativas

- Deuda de marca en IDs técnicos hasta el rename.  
- Usuarios de installs internas tendrán que reinstalar tras el cambio de applicationId.

## Condiciones para revisar

- Preparación de release en Play Store.  
- Conflicto legal/marca con `comunidapp`.

## Referencias

- [M00-Etapa-2 §2.5](../03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md)
