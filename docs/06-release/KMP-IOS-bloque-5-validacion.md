# KMP-IOS — Bloque 5 validación

**HEAD base KMP-5:** `b93faa9`
**KMP-5.1 fix SHA:** `005def3b1f545bdb4325c7581edda5d1775344b5`

## Gate cloud

| Run | Resultado |
| --- | --------- |
| Gate #6 | FAIL — framework link ObjC export ClassCastException |
| Gate #7 | **PASS** (macOS real) sobre SHA `005def3…` |

**KMP-5 queda CLOSED GREEN** tras KMP-5.1 + Gate #7.

## Fix KMP-5.1 (resumen)

| Check | Resultado |
| ----- | --------- |
| Tests shared (post-5.1) | 92 PASS |
| Android / iOS simulator compile | PASS |
| Keychain cast warnings | 0 |
| SESSION IOS | REAL_REMOTE |

### Superficie ObjC (`internal`)

SecureStorageSessionManager, SupabaseAuthSessionGateway, createAuthRepository,
createSecureSessionStorage, IosKeychainSecureSessionStorage, IosSupabaseConfigReader,
AndroidSecureSessionStorage.

Entry Swift: `PocIosViewController()`.

### Keychain

CFString / CFData / CFDictionary nativos — sin NSUserDefaults para tokens.
