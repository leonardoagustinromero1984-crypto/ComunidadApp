# DLEG-03 — Inventario de terceros y licencias

> **BORRADOR NO FIRMADO — REQUIERE REVISIÓN LEGAL**

**Titularidad software LeoVer:** Leonardo 50 % · Verónica 50 % (excluye componentes listados).

Licencias inferidas de proyectos públicos — **no verificadas** en JARs (no se ejecutó Gradle en esta preparación). Si no hay certeza: **DESCONOCIDA**.

---

## 1. Dependencias Gradle (runtime) — resumen

| Componente | Versión | Licencia detectada | Riesgo | Verificación |
|------------|---------|-------------------|--------|--------------|
| Android Gradle Plugin | 9.2.1 | Apache-2.0 | BAJO | DESCONOCIDA en artefacto |
| Kotlin / Compose plugins | 2.2.10 | Apache-2.0 | BAJO | DESCONOCIDA |
| AndroidX (core, lifecycle, navigation, fragment) | ver `libs.versions.toml` | Apache-2.0 | BAJO | DESCONOCIDA |
| Compose BOM / Material3 / UI | 2026.02.01 | Apache-2.0 | BAJO | DESCONOCIDA |
| material-icons-extended | BOM | Apache-2.0 | BAJO | DESCONOCIDA |
| coil-compose | 2.7.0 | Apache-2.0 | BAJO | DESCONOCIDA |
| Supabase Kotlin BOM | 3.0.3 | MIT (habitual) | BAJO | DESCONOCIDA |
| ktor-client-android | 3.0.3 | Apache-2.0 | BAJO | DESCONOCIDA |
| kotlinx-serialization-json | 1.8.0 | Apache-2.0 | BAJO | DESCONOCIDA |
| Firebase BOM / messaging | 33.12.0 | Apache-2.0 | BAJO | DESCONOCIDA |
| androidx.datastore-preferences | 1.1.1 | Apache-2.0 | BAJO | DESCONOCIDA |

**Tests:** JUnit (EPL-2.0), Espresso, AndroidX Test (Apache-2.0) — **excluidos del paquete DNDA** como dependencias binarias; documentados aquí.

**GPL / AGPL / SSPL:** no declarados en Gradle inspeccionado.

---

## 2. Recursos gráficos propios / pendientes

| Recurso | Estado titularidad | Paquete DNDA RC1.1 |
|---------|-------------------|-------------------|
| Brand Board oficial v1.0 | Propuesta 2026-08-05 vía ChatGPT (cuenta Leonardo); intención aporte 50/50 — **DLEG-10** | **EXCLUIDO** del ZIP anterior |
| `logo_leover.jpg` (productivo / identidad anterior en runtime) | Antecedente + base productiva; ver DLEG-10 | **EXCLUIDO** |
| `logo_leover_isotype.png` | Derivado | **EXCLUIDO** |
| `logo_leover_isotype_monochrome.png` | Derivado | **EXCLUIDO** |
| `ic_launcher_*.xml`, mipmaps | Referencian isotipo | Incluidos con nota en manifiesto |
| Históricos `docs/08-marca/assets/historicos/` | **IDENTIDAD ANTERIOR — REEMPLAZADA** | Conservar evidencia; no borrar |

ChatGPT / generador de imágenes: **herramienta** (no titular). Términos de la plataforma: **REVISAR** con abogado.

Detalle: [`DLEG-10-Origen-y-Aporte-del-Logo-LeoVer-BORRADOR.md`](DLEG-10-Origen-y-Aporte-del-Logo-LeoVer-BORRADOR.md).  
Inventario visual: `docs/08-marca/` (`D08-01`, `D08-04`, `D08-05`).

---

## 3. Servicios externos

| Servicio | Titular cuenta | Riesgo |
|----------|----------------|--------|
| Supabase | PENDIENTE DE CONFIRMACIÓN | REVISAR |
| Firebase / FCM | PENDIENTE DE CONFIRMACIÓN | REVISAR |
| GitHub | Cuenta leonardoagustinromero1984-crypto | REVISAR titular legal |

---

## 4. Obra propia sin LICENSE en repo

Código, SQL, Edge Functions y documentación técnica: titularidad **50/50** cofundadores; licencia pública del repo **DESCONOCIDA** (sin archivo LICENSE).

---

## 5. Componentes excluidos del depósito DNDA (binarios / secretos)

- JARs/AARs de dependencias  
- `google-services.json`  
- `local.properties`, `.env`, claves Supabase  
- Keystores  
- Logo pendiente de origen  

Ver `COMPONENTES-DE-TERCEROS-EXCLUIDOS.txt` en paquete DNDA.

---

## 6. Acciones pendientes

1. Agregar `LICENSE` y `NOTICE` tras decisión legal.  
2. Confirmar origen logo o rediseñar con cesión.  
3. Verificar licencias en release comercial con abogado.
