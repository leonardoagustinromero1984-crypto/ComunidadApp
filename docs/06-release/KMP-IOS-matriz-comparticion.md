# KMP/iOS — Matriz (post KMP-8)

| Área | Estado |
| ---- | ------ |
| Session / Auth REAL_REMOTE | SHARED |
| Profile READ REAL_REMOTE | SHARED |
| Pets list/detail READ REAL_REMOTE | SHARED |
| Lost/Found READ REAL_REMOTE | SHARED |
| Lost/Found PUBLISH REAL_REMOTE | SHARED |
| Lost/Found MEDIA WRITE | PARTIAL (M05 deferred) |
| Adoptions READ REAL_REMOTE | SHARED |
| SharedRemoteRuntime | SHARED internal |
| FileRef + ImagePicker | SHARED (reused M08) |
| Adoption publish / applications | DEFERRED |
| Signed media URLs | DEFERRED |
| M09 WIP Android decoding | DEFERRED (WIP preservado) |
