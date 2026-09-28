# LeoVer physical QA plan

Next device cycle for the STAGING app. Do not mark any case PASS in this document.
JVM tests and in-app Maestro reads are not a device pass. The OS notification shade is not the in-app notification list.

Backend max: `20260927230000_1104_vitacora_reunification_retirement` (1104).
Staging: `tobqbddfcyitwgbkthhy`.
Backend modified in this block: NO.
STAGING mutated: NO.
SUPABASE_ACCESS_TOKEN: not present and not used.
service_role: NO.
PROD: NO.
main: not modified.
Merge: NO.
Emulator: NOT RUN.
Maestro execution: NOT RUN.

## Build under test

The 16A binary `apk/LeoVer-M08-Staging-debug.apk` (`4dd3c43335dc87fd4a9cc6f2ce0f59084f22daf6752b3f78bf83739a75dc3073`) is not the install candidate. Its manifest used the placeholder `MAPS_API_KEY_MISSING`.

The 16B binary `apk/LeoVer-M08-Staging-physical-qa.apk` (`7c5331a97f9342133c7eead50219f3872070ba6120e8b5180d330c54653741f1`) is not the install candidate. It was signed by the Cloud debug certificate (`SHA-1 0D:8E:4A:85:0B:6F:81:F5:AD:A1:21:E4:E9:5A:6C:4D:A5:BD:E6:3E`).

The 16D and 16E bytes `c2ea8dd44f742a083e46c91baa23da5ab427331400dca3edb04585d1aa2cd3db` are not the install candidate. That file was an ephemeral artifact and no longer exists. A rebuild is not treated as byte-identical unless its own SHA-256 matches.

Install the GitHub Actions artifact `LeoVer-M08-Staging-physical-qa-final` from workflow `Staging physical QA APK` (`.github/workflows/staging-physical-qa-apk.yml`). The file inside the artifact is `LeoVer-M08-Staging-physical-qa-final.apk`. A localDebug build is not a substitute. The APK is not committed. `apk/` stays gitignored.

| Field | Value |
| --- | --- |
| Branch | `cursor/physical-qa-artifact-16f` |
| Gradle task | `:app:assembleStagingDebug --no-configuration-cache` |
| Artifact name | `LeoVer-M08-Staging-physical-qa-final` |
| Filename | `LeoVer-M08-Staging-physical-qa-final.apk` |
| Package | `com.comunidapp.app.staging` |
| App label | LeoVer Staging |
| versionName | `1.1-staging` |
| versionCode | `2` |
| Signing SHA-1 | `92:D3:AF:F2:AC:CF:50:B8:68:15:95:26:4F:ED:C8:C2:DC:2B:E5:EF` |
| Signing SHA-256 | `07:FF:08:A5:B9:46:B3:59:A8:6F:B4:47:B4:9F:51:A6:08:C2:67:58:FF:ED:5B:48:96:8E:E9:3D:63:5A:AD:09` |
| APK SHA-256 | The workflow verifier prints it on the run summary. It is not `c2ea8dd44f742a083e46c91baa23da5ab427331400dca3edb04585d1aa2cd3db` unless that exact digest is printed. |

The stable QA certificate is the physical QA requirement. Signing material is read from GitHub Actions secrets (`LEOVER_STAGING_QA_KEYSTORE_B64`, store password, key password, key alias). The workflow does not print those values. The keystore is decoded outside the repository at build time.

The verifier fails the job unless the package, version, staging host `tobqbddfcyitwgbkthhy`, Maps manifest meta-data, and both signing fingerprints match. Maps and the Supabase client key are injected from secrets into gitignored `local.properties` for the assemble only. The Maps value must be present and must not be blank or `MAPS_API_KEY_MISSING`. Do not paste any key into the evidence. The map case below must still record whether tiles render. The expected Google restriction is package `com.comunidapp.app.staging` plus the signing certificate above. This block did not change Google Cloud, Google OAuth, or Maps configuration.

## Google auth implementation (16E)

Continuar con Google is hosted Supabase Auth, PKCE, opened in the device browser. It is not Firebase Auth, not the legacy Google Sign-In SDK, and not Credential Manager.

Runtime path:

1. `LoginViewModel.signInWithGoogle` / signup `signInWithGoogle` call `createGoogleOAuthUrl()`.
2. `SupabaseAuthRepository.createGoogleOAuthUrl()` calls `supabase.auth.getOAuthUrl(Google, redirectUrl = com.comunidapp.app://login-callback)` with `prompt=select_account`.
3. `LeoVerGoogleSignIn.openAuthorizeUrl` opens that HTTPS URL in a Custom Tab or an explicit browser. Mail packages are excluded.
4. `MainActivity.handleAuthDeepLink` accepts `com.comunidapp.app://login-callback` and calls `supabase.handleDeeplinks`.
5. The Auth plugin is installed with `FlowType.PKCE`, scheme `com.comunidapp.app`, host `login-callback`.

`signInWithGoogle()` returns a configuration error (`signInWith(Google) auto-open disconnected`). `signInWithGoogleIdToken()` returns a configuration error (`google id token path disconnected`). No source file reads `default_web_client_id`, `server_client_id`, or a certificate hash.

Dependencies used for this flow are `auth-kt` and `androidx.browser`. Firebase on the app is `firebase-messaging` only. Maps uses `play-services-maps` plus the manifest `com.google.android.geo.API_KEY`. There is no `play-services-auth`, `firebase-auth`, or Google Identity / Credential Manager dependency.

`app/google-services.json` has four Android package clients (`Comunidad.com`, `com.comunidapp.app`, `com.comunidapp.app.local`, `com.comunidapp.app.staging`). Every `oauth_client` entry is `client_type` 3. There is no `client_type` 1 and no `certificate_hash`, so the expected SHA-1 is absent. The Google Services plugin still generates `default_web_client_id` for the web client. Application code does not reference that resource. Firebase Cloud Messaging uses `google_app_id` and the sender id. That file was not edited.

The Android OAuth client created in Google Cloud for `com.comunidapp.app.staging` plus SHA-1 `92:D3:AF:F2:AC:CF:50:B8:68:15:95:26:4F:ED:C8:C2:DC:2B:E5:EF` is not read by this runtime. Native Google Sign-In is the consumer of an Android OAuth client (package name + certificate). This app asks Supabase for an authorize URL. Supabase sends the user to Google with the Web OAuth client configured on the Supabase Auth Google provider. Google's redirect is the Supabase callback (`https://<auth-host>/auth/v1/callback`). The app then receives `com.comunidapp.app://login-callback`. An Android client has no client secret and is not part of that code exchange.

`GOOGLE_SERVICES_REFRESH_REQUIRED` is NO. `USER_ACTION_REQUIRED` is NONE. `GOOGLE_OAUTH_RUNTIME_READY` is YES for this repository path. Physical QA can exercise Continuar con Google on the fingerprinted APK. This block did not change the Supabase Google provider, Google Cloud, or `google-services.json`.

Supabase: project `tobqbddfcyitwgbkthhy`, `SUPABASE_ENABLED` true on the staging APK, credential source `STAGING`. The configured URL is `https://tobqbddfcyitwgbkthhy.supabase.co`. `service_role` was not packaged as a credential. The word `service_role` remains in dex as a forbidden-token check.

## Actor map

| Actor | Username | Use on device |
| --- | --- | --- |
| QA01 | qa01owner | Mora owner. Survivor VitaCora and QR. |
| QA02 | qa02finder | FOUND form smoke. Not a new publish. |
| QA03 | qa03rescuer | IN_CARE custodian. Not OWNER. |
| QA04 | qa04rescuer2 | Eligible responder. Do not claim the IN_CARE pin. Device B in the future race. |
| QA05 | qa05unavailable | Responder exclusion. |
| QA06 | qa06foster | Historical Bruno foster. Excluded from FOUND claim. |
| QA07 | qa07shelter | Verified shelter. Bruno responsible. |
| QA08 | qa08pending | Verification PENDING. |
| QA09 | qa09noreq | Verification PENDING. The username is historical. Do not treat it as not requested. |
| QA10 | qa10vet | Consultorio, agenda read, email-required form. |
| QA14 | qa14adopter | Luna PERSON OWNER. Application COMPLETED. |
| QA15 | qa15adopter2 | Luna application CLOSED. |

Password stays in the gitignored actor file. Do not write it here.

## Completed-fixture warnings

- Do not recreate Mora, the responder FOUND, Luna, or Bruno to make a test easier.
- Do not publish a LOST or FOUND, claim a case, submit verification, apply to Luna, or start a transit during this cycle unless a later block has seeded a fresh fixture.
- After reunification, QA01 should see one functional pet named `QA - Mora`. The provisional pet is not a second profile. The provisional VitaCora is not a second timeline.
- QA03 on the responder FOUND is custodian only.
- QA06 on Bruno is historical AUTHORIZED / ENDED, not an active foster and not a FOUND responder.

## Physical matrix

Evidence means a screenshot or a short video saved with the case id. Do not mark PASS or FAIL in this file while preparing the cycle. The person on the device records the result next to the evidence.

### PHY-CAMERA — camera permission

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Fresh install of this STAGING APK. Camera permission not granted. |
| STEPS | Open Perdí a mi mascota or a pet photo action. Choose the camera source. Respond to the Android camera permission dialog. |
| EXPECTED RESULT | The system dialog appears. Allow opens the camera. Deny stays in LeoVer without a crash and without a published LOST. |
| EVIDENCE TO CAPTURE | Dialog, then the result of Allow and of Deny on a second install or after revoking the permission. |

### PHY-CAPTURE — camera capture

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Camera permission granted. Do not publish Mora as a new LOST. |
| STEPS | Open the lost form, take a photo, stop before Publicar, and leave the form with Volver. |
| EXPECTED RESULT | The captured photo is visible in the form preview. No new LOST exists afterward. |
| EVIDENCE TO CAPTURE | Preview on the form, then the lost/found list after backing out. |

### PHY-GALLERY — gallery photo

| | |
| --- | --- |
| ACTOR | QA02 |
| PRECONDITION | At least one photo in the device gallery. Gallery permission not pre-granted if the picker needs it. |
| STEPS | Open Encontré un animal. Choose Agregar foto and pick a gallery image. Do not tap Publicar. |
| EXPECTED RESULT | The picker returns the image into the form. The provisional-copy sentence stays visible. Nothing is published. |
| EVIDENCE TO CAPTURE | Picker and the form preview. |

### PHY-UPLOAD — media upload

| | |
| --- | --- |
| ACTOR | A later fresh fixture owner, not Mora |
| PRECONDITION | A dedicated pet that is not Mora, Luna, or Bruno. Network on STAGING. |
| STEPS | This case waits for that fixture. Do not upload a new photo onto Mora, Luna, or Bruno in this cycle. |
| EXPECTED RESULT | When a fresh pet exists, the photo upload completes and the pet detail shows the new image after leaving and reopening the screen. |
| EVIDENCE TO CAPTURE | Before, progress, and the reopened pet detail. Record BLOCKED until the fresh pet exists. |

### PHY-GPS-PERM — location permission

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Location permission revoked. |
| STEPS | Open Comunidad → Cerca mío, or Mapa de alertas. Respond to the Android location dialog. |
| EXPECTED RESULT | The system dialog is the Android one. Deny keeps a usable screen (Cerca mío or the map empty/permission state). Allow does not crash. |
| EVIDENCE TO CAPTURE | The dialog and both outcomes. |

### PHY-GPS — real device location

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Location permission granted. Device GPS on. Emulator mock coordinates are not this case. |
| STEPS | Open Cerca mío and Mapa de alertas with the real device position. |
| EXPECTED RESULT | Nearby results or the map anchor follow the physical position, not the synthetic Obelisco pin used by emulator QA. |
| EVIDENCE TO CAPTURE | Screen plus a note of the real city or neighborhood, without a precise home address if it is private. |

### PHY-MAP — map tiles and pin

| | |
| --- | --- |
| ACTOR | QA03 |
| PRECONDITION | This APK. The Maps key is packaged. Google Play services present. Tiles can still fail if the key is restricted to another package or another certificate. |
| STEPS | Sumate → Perdidos / Encontrados → Ver mapa de alertas. Stay on Mapa, then open Lista. Do not claim. |
| EXPECTED RESULT | The screen title is Mapa de alertas. Record whether tiles render. A blank map is an observation, not a product pass. Lista opens. Tomar caso is not used. The IN_CARE case is not an open pin that can be claimed. |
| EVIDENCE TO CAPTURE | Map surface and the Lista. Note whether tiles rendered. |

### PHY-QR-SCAN — physical scan

| | |
| --- | --- |
| ACTOR | QA01 creates the code. A second person or the same phone camera scans it. |
| PRECONDITION | QA01 can open Compartir VitaCora for QA - Mora. A second camera, or another phone, is available. |
| STEPS | Open QA - Mora → Ver VitaCora → Compartir QR. Scan the on-screen code with a physical camera. |
| EXPECTED RESULT | The code opens the LeoVer route `leover://passport/` for the survivor public view. It does not open a retired provisional VitaCora. |
| EVIDENCE TO CAPTURE | The QR screen and the destination screen after the scan. |

### PHY-QR-ROUTE — route after scan

| | |
| --- | --- |
| ACTOR | Logged-out scanner, then QA01 |
| PRECONDITION | The code from PHY-QR-SCAN. |
| STEPS | Scan while logged out. Scan again while logged in as QA01. |
| EXPECTED RESULT | Both land on the survivor public VitaCora route, not a second pet profile. |
| EVIDENCE TO CAPTURE | Both destinations. |

### PHY-PUSH — Android notification shade

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Notification permission granted. App in the background. |
| STEPS | Trigger a real STAGING event that should push, or record that no new event was sent in this block. Open the Android shade. Separately open the in-app bell list. |
| EXPECTED RESULT | Shade delivery is its own result. An in-app Notificaciones row is not a shade pass. Do not use mark-read as a pass criterion. Mark-read remains P1. |
| EVIDENCE TO CAPTURE | Shade and, separately, the in-app list. |

### PHY-GOOGLE-NEW — new Google user

| | |
| --- | --- |
| ACTOR | A Google account that has never completed LeoVer on this staging project |
| PRECONDITION | STAGING APK. Username/password login is a different case. |
| STEPS | On the login screen tap Continuar con Google. Complete the Google account chooser. Finish Completar perfil. Pass the tutorial. Reach the initial role or function selector. |
| EXPECTED RESULT | OAuth returns to LeoVer Staging. Profile completion, tutorial, and the function selector appear in that order for a new account. No service account and no existing QA user is reused. |
| EVIDENCE TO CAPTURE | Account chooser, Completar perfil, tutorial, and the selector. Hide the email if the capture would publish it. |

### PHY-GOOGLE-REINSTALL — existing Google account

| | |
| --- | --- |
| ACTOR | The same Google account after PHY-GOOGLE-NEW, or another Google account already registered on STAGING |
| PRECONDITION | App data cleared or the app uninstalled and reinstalled. Same APK. |
| STEPS | Tap Continuar con Google and choose the existing account. |
| EXPECTED RESULT | LeoVer logs in to the existing profile. It does not force a second new profile or a second tutorial as if the account were new. |
| EVIDENCE TO CAPTURE | Chooser and the recovered home. |

### PHY-LOGOUT-REINSTALL — password session

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Logged in with username and password. |
| STEPS | Perfil → Configuración → Cerrar sesión. Confirm the login screen says Correo o usuario. Uninstall, reinstall this APK, and log in again as QA01. |
| EXPECTED RESULT | Logout returns to the login form. Reinstall login restores QA - Mora as the one survivor pet. |
| EVIDENCE TO CAPTURE | Login screen after logout, and Mis mascotas after reinstall. |

### PHY-EMAIL — external inbox

| | |
| --- | --- |
| ACTOR | QA10 for the form. A mailbox the tester controls for a later send. |
| PRECONDITION | Do not send to a fake address. This block did not send an invite. |
| STEPS | Veterinary context → Consultorio → Nuevo paciente. Leave email empty and tap Crear e invitar. Stop. A real send waits for an agreed mailbox in a later block. |
| EXPECTED RESULT | Empty email shows El email es obligatorio. No message arrives because nothing was sent. Inbox delivery is not implied by the form. |
| EVIDENCE TO CAPTURE | The validation message. If a later send is authorized, the inbox message and the LeoVer result. |

### PHY-RACE — two-device responder

| | |
| --- | --- |
| ACTOR | Device A: QA03 or another eligible responder who is not already the IN_CARE custodian. Device B: QA04. QA06 must not participate. |
| PRECONDITION | A new OPEN FOUND that is not `3e238403-174b-4ce8-82fd-58daa4faa636`. Both devices on this APK, eligible, and online. |
| STEPS | Both open that OPEN found. Device A taps Tomar caso and succeeds. Device B taps Tomar caso afterward. |
| EXPECTED RESULT | A succeeds. B sees El caso fue asignado a un colaborador más cercano. One active custodian. No ownership is created for either responder. |
| EVIDENCE TO CAPTURE | Both devices, including B's denial. Record BLOCKED until the fresh FOUND exists. |

### PHY-PERMS — other Android dialogs

| | |
| --- | --- |
| ACTOR | QA01 |
| PRECONDITION | Fresh install. |
| STEPS | Walk notification, camera, and location prompts as they appear. Include Mientras la app está en uso if Android offers it. |
| EXPECTED RESULT | Each dialog is the system dialog. LeoVer remains usable after deny. |
| EVIDENCE TO CAPTURE | Each dialog. |

## In-app reads that are not physical passes

These are Maestro flows classified UPDATED_READY or READY_TO_RUN. They still need a device or emulator run later. They are not marked PASS here.

- Login smoke QA01, QA02, QA03.
- Lost form and found form opened and closed without Publicar.
- Mora survivor VitaCora and on-screen QR.
- Notification list from the home bell.
- IN_CARE list row for QA - Responder Path 12, CABA.
- QA06 historical transit and QA07 completed transit screen.
- Luna owner pet, Completada, and Cerrada.
- QA07 verified and QA08/QA09 pending verification.
- Community nearby names.
- Veterinary Consultorio and Mis turnos.

## Explicitly not this cycle

- Mark notification read or unread.
- Recreate Mora, Luna, Bruno, or the responder FOUND.
- Apply migration 1105 or any other migration.
- Touch PROD or main.
