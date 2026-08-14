# IOS-PILOT-2 — Universal Links / AASA (plan, sin publicar)

**Base SHA:** `33cf1c14aec66abf168244b4514a57c11be29419`  
**Dominio esperado:** `leover.com.ar` (+ `www.leover.com.ar` allowlisted en parser)

## App-side (READY)

| Ruta HTTPS | Target shared |
| ---------- | ------------- |
| `/mascota/{code}` | `PetPublic` |
| `/adopciones/{code}` | `AdoptionPublic` |
| `/perdidos/{code}` | `LostCase` |
| `/encontrados/{code}` | `FoundCase` |

Custom scheme (READY en Info.plist): `leover://` — paths `passport`, `mascota`, `adopciones`, `perdidos`, `encontrados` vía `DeepLinkParser`.

Puente: Swift `onOpenURL` → `IosDeepLinkBridge.offerDeepLinkUrl` → parser.

Hosts allowlist: `leover.com.ar`, `www.leover.com.ar` (`DeepLinkHosts`).

## Faltantes (NO activar aún)

| Ítem | Clasificación |
| ---- | ------------- |
| Entitlement Associated Domains `applinks:leover.com.ar` (y www si aplica) | APPLE_DEVELOPER_REQUIRED |
| App ID con Associated Domains | APPLE_DEVELOPER_REQUIRED |
| `TEAM_ID` real | APPLE_DEVELOPER_REQUIRED (placeholder en draft) |
| Publicar AASA en web | EXTERNAL_CONFIG_REQUIRED / WEB_REQUIRED |
| HTTPS sin redirect en `/.well-known/apple-app-site-association` | WEB_REQUIRED |
| Validación en iPhone real | REAL_DEVICE_REQUIRED |

**App ID formato AASA:** `TEAM_ID.BUNDLE_ID`  
Bundle actual: `com.comunidapp.leover.kmppoc`  
Team: vacío en repo → usar placeholder `TEAM_ID` hasta confirmar.

Draft seguro (placeholders): `docs/06-release/IOS-PILOT-2-aasa-draft.json`  
**No es el archivo productivo.** No desplegar desde esta fase.

## Fallback web

Si Universal Link no abre la app: el navegador debe mostrar la página pública HTTPS existente (misma ruta). Custom scheme `leover://` sigue disponible para pruebas internas sin AASA.

## Checklist validación posterior (NOT_RUN)

1. HTTPS válido en `leover.com.ar`
2. `https://leover.com.ar/.well-known/apple-app-site-association` (y/o raíz legacy si se usa)
3. Sin redirect HTTP→HTML que rompa verificación Apple
4. `Content-Type` razonable (`application/json` o `application/pkcs7-mime`)
5. `appIDs` = `TEAM_ID.com.comunidapp.leover.kmppoc` (cuando Team exista)
6. Entitlement `applinks:` coincide con dominio
7. Rutas públicas reales viven en web
8. App instalada firmada en iPhone
9. Tap desde Notes / Messages / Safari abre app (no solo Safari)

## Qué ya soporta DeepLinkParser

Allowlist host + paths públicos + custom scheme; rechaza hosts ajenos y schemes peligrosos.  
**No** reemplaza AASA ni Associated Domains.
