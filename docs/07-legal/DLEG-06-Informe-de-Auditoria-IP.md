# DLEG-06 — Informe de auditoría de propiedad intelectual

> **BORRADOR NO FIRMADO — REQUIERE REVISIÓN LEGAL**

**Actualización:** 2026-08-05 — titularidad 50/50 y preparación DNDA

---

## 1. Resumen ejecutivo

LeoVer es software inédito en desarrollo (RC1.1 Android). **Titularidad patrimonial acordada:** Leonardo Agustín Romero **50 %** y Verónica Luján Obregón **50 %**. No existe sociedad constituida; se prevé transferencia **100 %** a sociedad futura con capital 50/50.

**Marca LEOVER** (cl. 9, 42, 45): **100 % registral Verónica** — **provisional**; no equivale a titularidad unilateral del software.

Desarrollo con **asistencia intensiva de IA (Cursor)** bajo dirección humana de ambos cofundadores.

**Paquete DNDA RC1.1** preparado en `artifacts/legal/dnda/` con exclusiones de secretos, dependencias binarias y logo pendiente de origen.

---

## 2. Evidencias

- 195 commits (Verónica, mailmap) + 5 (Leonardo Git directo).  
- HEAD `5986a01`, APK SHA-256 verificado.  
- 77 migraciones SQL, Edge Functions, ~630 archivos Kotlin main.  
- Documentación DLEG-00 a DLEG-09.

---

## 3. Riesgos

| Riesgo | Severidad | Mitigación |
|--------|-----------|------------|
| Logo sin origen acreditado | ALTA | Excluido DNDA; confirmar cesión |
| Marca 100 % V vs software 50/50 | MEDIA | DLEG-07, licencia a sociedad |
| Sin LICENSE en repo | MEDIA | Definir régimen |
| google-services.json versionado | MEDIA | Excluido DNDA; evaluar repo |
| Sociedad no constituida | MEDIA | DLEG-07 compromiso constitución |

---

## 4. Documentos preparados

| Doc | Estado |
|-----|--------|
| DLEG-00 – DLEG-06 | Actualizados |
| DLEG-07 Acuerdo cofundadores | Borrador |
| DLEG-08 Reconocimiento cotitularidad | Borrador |
| DLEG-09 Ficha DNDA | Borrador |
| Paquete + ZIP DNDA | Generado localmente |

---

## 5. Próximo paso

1. Revisión **abogado PI / societario** (Argentina).  
2. Completar datos personales DNDA.  
3. Firmar DLEG-07 y DLEG-08.  
4. Presentar TAD DNDA con ZIP e hashes.  
5. Commit documentación legal + tag (post-acuerdo).

**No presentar ante DNDA sin revisión humana.**
