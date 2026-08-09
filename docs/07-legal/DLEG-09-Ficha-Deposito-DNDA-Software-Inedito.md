# DLEG-09 — Ficha depósito DNDA — software inédito

> **BORRADOR NO FIRMADO — REQUIERE REVISIÓN LEGAL**

> **Estado del paquete ZIP RC1.1 asociado:** **BORRADOR ANTERIOR — NO PRESENTAR**  
> La identidad visual oficial v1.0 (2026-08-05) cambia la línea de base gráfica futura.  
> **No** regenerar ni presentar el depósito DNDA hasta aprobación visual en la app.

**No presentar ante DNDA sin completar formulario TAD vigente y asesoramiento profesional.**

---

## 1. Identificación de la obra

| Campo | Valor |
|-------|-------|
| **Título propuesto** | LEOVER — Plataforma digital para la comunidad y los servicios relacionados con mascotas — versión RC1.1 |
| **Condición** | Software inédito |
| **Tipo** | Programa de computación / aplicación móvil + componentes servidor y base de datos |
| **Nombre comercial** | LeoVer |
| **Nombre técnico anterior** | ComunidadApp |

---

## 2. Titularidad patrimonial prevista

| Titular | % |
|---------|---|
| Leonardo Agustín Romero | 50 |
| Verónica Luján Obregón | 50 |

**Sociedad constituida:** No.  
**Transferencia futura:** 100 % a sociedad futura (capital 50/50).

---

## 3. Autores / coordinadores / titulares (TAD)

> **No completar de forma concluyente** hasta revisar formulario TAD actual.

| Rol TAD | Propuesta preliminar | Notas |
|---------|---------------------|-------|
| Autores | Leonardo + Verónica (dirección humana; asistencia IA) | Confirmar redacción con DNDA |
| Coordinadores | PENDIENTE según TAD | — |
| Titulares | Leonardo 50 % + Verónica 50 % | Coincide con DLEG-08 |

---

## 4. Descripción breve

LeoVer es una plataforma digital orientada a la creación de perfiles de mascotas, red social, adopciones, animales perdidos, organizaciones, profesionales, comercios y servicios relacionados. El sistema comprende una aplicación Android, arquitectura funcional, persistencia local y remota, base de datos PostgreSQL (Supabase), funciones de servidor y documentación asociada.

---

## 5. Asistencia tecnológica (IA)

El desarrollo fue realizado mediante asistencia intensiva de **Cursor** y modelos de inteligencia artificial, bajo especificaciones, dirección, selección, revisión, integración, validación y control humano de **Leonardo Agustín Romero** y **Verónica Luján Obregón**.

Ver también: `05-MANIFIESTOS/DECLARACION-DE-ASISTENCIA-IA.txt` en paquete DNDA.

---

## 6. Referencias técnicas

| Campo | Valor |
|-------|-------|
| Ruta local | `C:\Users\Supervielle\StudioProjects\ComunidadApp` |
| Rama | `main` |
| Commit HEAD | `5986a01eb8662a44d3283d5ae1161816f65e156e` |
| Tag legal | **No creado** — ver DLEG-04 §6 |
| versionName | `1.1-local` |
| versionCode | `2` |
| applicationId (local) | `com.comunidapp.app.local` |
| Fecha cierre línea base | 2026-08-05 |

---

## 7. Hashes

| Artefacto | SHA-256 |
|-----------|---------|
| APK `LeoVer-RC1.1-local-debug.apk` | `CDAB94DB2C733D40B4D2FD9EAD6D2EA68DC2756A83C1DD9316CB8C2B83AE6266` |
| ZIP paquete DNDA | Ver `05-MANIFIESTOS/HASHES-SHA256.txt` — `E9AEE8E5308EC3D9CF67199BC9F4DAA3173223538FB59EDA56FFF34DE3C7165F` |

---

## 8. Contenido del paquete DNDA

| Carpeta | Contenido |
|---------|-----------|
| 01-CODIGO-FUENTE | Kotlin, Gradle, recursos (logo **excluido**) |
| 02-BASE-DE-DATOS-Y-FUNCIONES | Migraciones, Edge Functions |
| 03-DOCUMENTACION | Docs técnicos + DLEG |
| 04-EJECUTABLE | APK RC1.1 (copia) |
| 05-MANIFIESTOS | Inventarios, hashes, declaraciones |

---

## 9. Exclusiones

- `.git/`, `build/`, `.gradle/`, dependencias binarias  
- Secretos: `local.properties`, `.env`, claves Supabase, `google-services.json`  
- Logo `logo_leover.jpg`, Brand Board y derivados (ver DLEG-10; línea de base visual en revisión)  
- Datos personales de usuarios  
- Keystores  

---

## 10. Marca (INPI)

LEOVER cl. 9, 42, 45 — **100 % Verónica** (provisional, **denominativa**). **Trámite separado** del depósito de software.  
El logo podrá registrarse posteriormente como marca mixta o figurativa (D08-01 / DLEG-10).

---

## 11. Datos pendientes

- CUIL, domicilios, teléfonos (ver DATOS-DE-LOS-TITULARES.txt)  
- Clasificación exacta TAD autores/coordinadores  
- Cierre legal del logo tras DLEG-10 y aprobación visual in-app  
- Número expediente DNDA (post-trámite)  
- **Nuevo paquete DNDA** cuando se apruebe la implementación visual v1.0

---

## 12. Checklist pre-presentación

- [ ] Revisión abogado  
- [ ] Firma DLEG-07 y DLEG-08  
- [ ] ZIP verificado (hash registrado)  
- [ ] Formulario TAD completado  
- [ ] Pago aranceles (fuera de alcance automatizado)
