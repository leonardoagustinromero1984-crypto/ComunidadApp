# PRODUCTION MANUAL CHECKLIST

Todo lo que SQL/código no puede verificar.  
Proyecto STAGING inspeccionado: `tobqbddfcyitwgbkthhy`.  
PRODUCCIÓN aún no existe como proyecto autorizado en este bloque.

Ningún ítem se marca checked sin evidencia.  
Los valores ACTUAL de Dashboard quedan vacíos hasta que Leonardo los complete.

| CHECK | EXPECTED | ACTUAL | PASS/FAIL | EVIDENCE/NOTE |
|---|---|---|---|---|
| [ ] Proyecto Production creado (distinto de STAGING) | Nuevo project ref; no `tobqbddfcyitwgbkthhy`; no `wystsapjfpdtoprlmizz` | no creado en este bloque | FAIL / N/A hasta creación | Dashboard → New project |
| [ ] Región Production | Elegida y documentada (latencia AR) | no leído | — | Settings → General |
| [ ] Plan Production | **Pro** (decisión Leonardo) | no creado | FAIL hasta creación | Settings → General / Subscription |
| [ ] Automatic backups | Daily backup ON | STAGING Free = not available | REQUIRED BEFORE LAUNCH | Settings → Infrastructure → Backups |
| [ ] PITR | **DEFERRED BY OWNER AT INITIAL LAUNCH** | Leonardo 2026-09-04; ~USD 100/month | DEFERRED | reevaluar por crecimiento/riesgo/costo |
| [ ] Retención | anotar días reales en PROD | no inventar | PENDING PROD | misma pantalla |
| [ ] Restore to new project | Pro + physical backups; nunca overwrite STAGING | STAGING Free = not available | MUST VERIFY ON PROD | Backups → Restore |
| [ ] Physical backup / Auth capability | restore de prueba reenlaza Auth/`public` | no ensayable en Free | PENDING PROD | SEC-04D |
| [ ] Restore drill destino aislado | schema + Storage sample + Edge proc | SEC-04C+D | PRODUCTION-CONDITIONAL | `SEC-04-restore-drill-result.md` |
| [ ] Storage backup job/config activo | script diario / destino privado | tooling ensayado; job PROD no | PENDING PROD | `backup-storage.ps1` |
| [ ] Storage restore sample | count/path/size/hash | 2 objetos `sec04d-probe/` PASS | PASS (muestra) | destino `wystsapjfpdtoprlmizz` |
| [ ] Edge redeploy procedure | repo → deploy → secrets nuevos → smoke auth | código en Git; no redeploy PROD | PASS (procedimiento) | `admin-staff`, `vitacora-import-analyze` |
| [ ] Secrets inventory completo | nombres + owner rotación | `SECRETS-INVENTORY.md` | PASS (nombres) | sin valores |
| [ ] Production recovery contact/owner | persona on-call + Leonardo | no asignado en este bloque | PENDING | — |
| [ ] Spend alert | Alerta de gasto configurada | no leído | — | Organization → Billing → Usage |
| [ ] Billing / budget cap | Cap o alerta hard si el plan lo permite | no leído | — | Organization → Billing |
| [ ] Custom domain (si aplica) | HTTPS propio; no asumir | no aplicable aún | N/A | DNS / Auth URL config |
| [ ] Auth Site URL Production | URL/app scheme de producción | no configurado (no hay PROD) | — | Authentication → URL Configuration |
| [ ] OAuth Google Production redirects | Redirects de release, no STAGING | no verificado | — | Google Cloud → OAuth client; Auth → Providers → Google |
| [ ] Google OAuth client secret | Solo Dashboard; rotado para PROD; nunca Git | no inspeccionado valor (prohibido) | — | nombre: Google OAuth client secret |
| [ ] Firebase Production | App Android release + SHA-1/256; API key restricted | `app/google-services.json` es client config tracked | — | Firebase Console → Project settings. No imprimir keys |
| [ ] Maps API restrictions | Restringir por paquete + SHA; APIs mínimas | no verificado | — | Google Cloud → Credentials |
| [ ] Edge secrets Production | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` (solo `admin-staff`) | no hay proyecto PROD | — | Edge Functions → Secrets. Nunca copiar de STAGING al APK |
| [ ] Production env vars Android | `SUPABASE_URL` + `SUPABASE_ANON_KEY` (publishable). Build falla si hay service_role | no hay flavor prod aplicado aquí | — | `local.properties` gitignored; Gradle guard |
| [ ] service_role no en APK | APK solo anon/publishable | inspección Android: no `SUPABASE_SERVICE_ROLE_KEY` en `app/src/main` | PASS (código) | contract tests existentes |
| [ ] Feature flags iniciales | video / reels / imports enabled según lanzamiento | STAGING: enabled=true | — | `security_feature_flags` |
| [ ] Rate limits Production | Aplicar `docs/security/PRODUCTION-INITIAL-LIMITS.md` | STAGING defaults distintos | — | no subir sin necesidad |
| [ ] Observability mínima | Ver sección Observability del gate | no hay dashboards PROD | — | audit events RATE/QUOTA/admin |
| [ ] Root SUPERADMIN MFA enrolled | Root existe + TOTP + AAL2 | STAGING sí (SEC-02); PROD no creado | — | no backdoor |
| [ ] Proyecto restore-test | `wystsapjfpdtoprlmizz` = SEC-04 RESTORE TEST; no Android | designado 2026-09-04 | PASS (proceso) | no es STAGING |

## BEFORE PROD (release)

- [ ] Supabase PROD creado  
- [ ] PROD en Pro  
- [ ] Daily backup confirmado ON  
- [ ] Retention verificada  
- [ ] Restore to new project disponible  
- [ ] Physical backup / Auth capability verificada  
- [ ] Storage backup job/config activo  
- [ ] Storage restore sample probado  
- [ ] Edge redeploy procedure probado  
- [ ] Secrets inventory completo  
- [ ] Root SUPERADMIN + MFA configurado  
- [ ] Production recovery contact/owner definido  
- [ ] PITR: **DEFERRED BY OWNER AT INITIAL LAUNCH** — reevaluar por crecimiento / riesgo / costo  

## Condición PASS de backups en PROD

1. Abrir https://supabase.com/dashboard/project/<PROD_REF>/settings/infrastructure  
2. Anotar: Automatic backups = ON (requisito).  
3. Anotar: PITR = OFF esperado al launch (deferred by owner) **o** ON si más adelante se contrata.  
4. Anotar: Retention = N days (valor real, no inventado).  
5. PASS de launch: Pro + daily ON + restore-to-new-project verificado + Auth linkage en restore de prueba. PITR no es obligatorio al inicio.
