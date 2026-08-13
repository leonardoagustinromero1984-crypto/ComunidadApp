# KMP/iOS — Matriz (post KMP-7)

| Área | Estado |
| ---- | ------ |
| Session / Auth REAL_REMOTE | SHARED |
| Profile READ REAL_REMOTE | SHARED |
| Pets list/detail READ REAL_REMOTE | SHARED |
| Lost/Found list/detail READ REAL_REMOTE | SHARED |
| Adoptions list/detail READ REAL_REMOTE | SHARED |
| SharedRemoteRuntime (Auth+Postgrest) | SHARED internal |
| LF / Adoption fakes | SHARED (tests/previews only) |
| Signed media URLs / M14 passport UI | ANDROID_ONLY / DEFERRED |
| Publish LF / Adoption / applications | DEFERRED (KMP-8+) |
| Profile edit / pet write | DEFERRED |
| M09 WIP Android decoding | DEFERRED (WIP preservado) |
