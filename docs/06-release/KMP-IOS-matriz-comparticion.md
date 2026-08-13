# KMP/iOS — Matriz (post KMP-10)

| Área | Estado |
| ---- | ------ |
| Session / Auth REAL_REMOTE | SHARED |
| Profile / Pets / LF / Adoption READ | SHARED |
| Lost/Found PUBLISH REAL_REMOTE | SHARED |
| Lost/Found MEDIA WRITE (M05) REAL_REMOTE | SHARED |
| Lost/Found MEDIA READ (M05) REAL_REMOTE | SHARED |
| Pets / Adoption MEDIA READ | SHARED (si campo asset/HTTPS) |
| Profile avatar READ | PARTIAL (path-only sin firmador) |
| FileRef + ImagePicker + FileContentReader | SHARED |
| SharedRemoteImage + MediaResolver | SHARED |
| Adoption publish | DEFERRED |
| M09 WIP Android decoding | DEFERRED (WIP preservado) |
