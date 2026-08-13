# KMP-IOS — Backlog (post KMP-11/12/13)

## Terminado

- KMP-1…10
- **KMP-11:** Adoption publish REAL_REMOTE
- **KMP-12:** Adoption application / interest REAL_REMOTE (candidate-side)
- **KMP-13:** Profile edit mínimo + avatar read/write REAL_REMOTE

## iOS ahora

```text
SESSION / PROFILE / PETS / LOST_FOUND / ADOPTIONS = REAL_REMOTE
LOST/FOUND PUBLISH = REAL_REMOTE
LOST/FOUND MEDIA WRITE = REAL_REMOTE
LOST/FOUND MEDIA READ = REAL_REMOTE
PETS / ADOPTION MEDIA READ = REAL_REMOTE
ADOPTION PUBLISH = REAL_REMOTE (media write PARTIAL)
ADOPTION APPLICATION = REAL_REMOTE
PROFILE EDIT = REAL_REMOTE
PROFILE MEDIA READ = REAL_REMOTE (legacy path firmado)
PROFILE MEDIA WRITE = REAL_REMOTE (profile-avatars legacy)
```

## Pendiente

- Shelter-side application review (accept/reject/list received)
- Adoption media write M05 dedicado (si backend lo exige distinto del pet snapshot)
- Deep links / Apple Sign In / APNs
- Pet create

## No iniciar

M24, M28, SQL/schema, web, KMP-14 en este paquete
