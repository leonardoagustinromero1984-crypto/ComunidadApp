# DLEG-04 — Registro de versiones y evidencias

> **BORRADOR NO FIRMADO — REQUIERE REVISIÓN LEGAL**

**Fecha preparación DNDA:** 2026-08-05  
**Titularidad software:** Leonardo 50 % · Verónica 50 %

---

## 1. Git

| Campo | Valor |
|-------|-------|
| Rama | `main` |
| HEAD | `5986a01eb8662a44d3283d5ae1161816f65e156e` |
| Mensaje HEAD | `fix(branding): correct launcher icon and package rc1.1` |
| Remote | `git@github.com:leonardoagustinromero1984-crypto/ComunidadApp.git` |
| Tag legal propuesto | `legal/dnda-leover-rc1.1-2026-08-05` — **NO CREADO** (ver §6) |
| `.mailmap` | Presente (sin commit) |
| Config local Git | `Verónica Luján Obregón <obregonveronica@gmail.com>` |

---

## 2. Versión aplicación

| Campo | Valor |
|-------|-------|
| applicationId (local) | `com.comunidapp.app.local` |
| versionName | `1.1-local` |
| versionCode | `2` |
| minSdk / targetSdk | 26 / 36 |

---

## 3. APK RC1.1

| Campo | Valor |
|-------|-------|
| Ruta | `artifacts\rc1.1\LeoVer-RC1.1-local-debug.apk` |
| Tamaño | 32 255 503 bytes |
| SHA-256 | `CDAB94DB2C733D40B4D2FD9EAD6D2EA68DC2756A83C1DD9316CB8C2B83AE6266` |
| Recálculo 2026-08-05 | **Coincide** |

---

## 4. Paquete DNDA

| Campo | Valor |
|-------|-------|
| Carpeta | `artifacts\legal\dnda\LeoVer-RC1.1-DNDA-2026-08-05\` |
| ZIP | `artifacts\legal\dnda\LeoVer-RC1.1-DNDA-2026-08-05.zip` |
| Tamaño ZIP | 34 088 893 bytes (~32,5 MiB) |
| Archivos en paquete | 1 114 |
| SHA-256 ZIP | `E9AEE8E5308EC3D9CF67199BC9F4DAA3173223538FB59EDA56FFF34DE3C7165F` |

---

## 5. Línea de base legal — método

1. HEAD de referencia: **`5986a01`**.  
2. Paquete DNDA generado desde working tree **sin** `.git/`.  
3. Documentos legales DLEG en `03-DOCUMENTACION/` del paquete.  
4. Post-firma: commit documentación legal + tag anotado (decisión cofundadores).  
5. Presentación TAD con ZIP y hashes.

---

## 6. Tag legal — no creado

**Motivo:** el working tree contiene documentación legal (`docs/07-legal/`, `.mailmap`, paquete DNDA) **sin commit**. Un tag sobre `5986a01` no incluiría esos archivos y podría inducir error sobre el alcance del depósito.

**Recomendación:** tras revisión y **commit acordado** de documentación legal, crear:

```
git tag -a legal/dnda-leover-rc1.1-2026-08-05 -m "Línea de base para depósito DNDA de software inédito LeoVer RC1.1"
```

---

## 7. Archivos sensibles (ubicación únicamente)

`local.properties`, `supabase/.env`, `app/google-services.json` (excluidos del paquete DNDA).
