# ANDROID-LOCATION-ALERTS-01

Physical Maps baseline: **WORKING** (Google Cloud, package `com.comunidapp.app.staging`). This block does not change the API key or Cloud restrictions.

Canonical FOUND correction (1087): publishing FOUND creates the case, a provisional pet, and a VitaCora immediately. The previous rule “FOUND does not create pet/VitaCora until a responder claims” is annulled.

## Onboarding hotfix

Root cause: `SessionViewModel.authStateForProfile` and `Onb02EntryPolicy.skipSelectorForExistingComplete` treated a complete PERSON + empty local prefs as an existing reinstall and marked ONB-02 completed. New Google users and clean STAGING installs skipped Tutorial LeoVer.

Canonical state: `tutorial_progress.onb02_flow` v1.

New user: profile → tutorial → selector → Home.

Existing FLOW-completed user: login / reinstall → Home.

## QA checklist

1. New Google user → Completar perfil → Tutorial appears
2. Complete tutorial → selector / Home
3. Logout / login → tutorial does not repeat
4. Reinstall completed user (remote FLOW present) → tutorial does not repeat
5. Precise permission works
6. Approximate permission works
7. Denied: no crash, no loop
8. Permanently denied: Activar ubicación
9. Manual pin
10. Usar mi ubicación
11. App usable without permission
12. Responder base location save / edit
13. Public profile does not show exact coords
14. LOST existing pet
15. LOST new minimal pet + one VitaCora
16. FOUND publish requires photo + basic existing fields (species, sex, size, optional name, optional estimated age). Creates 1 case + 1 provisional pet + 1 VitaCora. Finder is custodian, not OWNER. No color field.
17. Backend nearest order, max 10; after 15 minutes add next 10; prior waves stay enabled until claim/confirm
18. Recipients unique; retry does not duplicate
19. Responder claims → CLAIMED + custody transfer + AUTHORIZED. Does **not** create a second pet or VitaCora. Pending alerts cancel. Future waves stop.
20. Simultaneous claims: nearest responder wins. Responder B: “Este caso ya fue tomado por otro colaborador.”
21. Progressive edit keeps same `pet_id` / VitaCora
22. Exact coords ACL
23. Possible owner can tap “Podría ser mi mascota” before claim; does not merge or close
24. Finder (or later the claiming responder) confirms owner → stop waves, safe relink onto the LOST pet, archive provisional pet, preserve FOUND history
25. Feed / Stories / Reels / Post / pets / VitaCora / Health / Mi manada / campaigns / Maps regression

## APK

path: `apk/LeoVer-M08-Staging-debug.apk`

SHA-256: `001378FCBD3880CF92FB33BAA3D64004FE0E6CFB28BF8057290AE76826BACA6F`
