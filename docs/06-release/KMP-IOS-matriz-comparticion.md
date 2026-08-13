# KMP/iOS — Matriz de compartición (post KMP-4)

## A. Shared

| Área | Estado |
| ---- | ------ |
| Pets domain + presentation KMP-3 | SHARED |
| Session / profile | SHARED |
| LF/Adoption **status rules** | SHARED (KMP-1) |
| LF/Adoption **SAFE presentation** + fakes | SHARED (KMP-4) |
| ApproximateLocation | SHARED |
| Draft validators (sin publish remoto) | SHARED |
| LeoVerSharedApp vertical | SHARED |
| ErrorSanitizer / VerticalLoadState | SHARED |

## B. Adapter / parcial

| Área | Estado |
| ---- | ------ |
| AndroidSessionMapper | ADAPTER |
| PlatformPreferences | ADAPTER |
| Supabase LostFound/Adoption :app | ANDROID_ONLY — ADAPTER_REQUIRED futuro |
| ImagePicker POC | PARCIAL |

## C. Específico / diferido

| Área | Clasificación |
| ---- | ------------- |
| `LostFoundPost` / `AdoptionPost` / M09 decoding | ANDROID_ONLY + DEFERRED |
| Alert map / GPS / APNs | DEFERRED |
| Adoption applications / completion | ANDROID_ONLY |
| Auth iOS / Keychain / REAL_REMOTE | DEFERRED |
| M24 pagos / M28 / web | Fuera de scope |

## D. Siguiente (no implementar)

KMP-5 propuesto: capa media/redacción real o auth iOS mínimo — decidir tras gate.
