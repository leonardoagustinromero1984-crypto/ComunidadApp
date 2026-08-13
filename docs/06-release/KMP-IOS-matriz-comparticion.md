# KMP/iOS — Matriz (post KMP-11/12/13)

| Área | Estado |
| ---- | ------ |
| Session / Auth REAL_REMOTE | SHARED |
| Profile / Pets / LF / Adoption READ | SHARED |
| Lost/Found PUBLISH REAL_REMOTE | SHARED |
| Lost/Found MEDIA WRITE (M05) REAL_REMOTE | SHARED |
| Lost/Found MEDIA READ (M05) REAL_REMOTE | SHARED |
| Pets / Adoption MEDIA READ | SHARED |
| Profile avatar READ | SHARED (path `users/.../avatar/...`) |
| Profile EDIT + avatar WRITE | SHARED |
| Adoption PUBLISH | SHARED (media write PARTIAL) |
| Adoption APPLICATION (candidate) | SHARED |
| Shelter application review | DEFERRED |
| FileRef + ImagePicker + FileContentReader | SHARED |
| SharedRemoteImage + MediaResolver | SHARED |
| M09 WIP Android decoding | WIP local (no commit) |
