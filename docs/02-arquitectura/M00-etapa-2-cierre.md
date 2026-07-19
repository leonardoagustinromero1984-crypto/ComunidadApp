# M00 — Cierre Etapa 2 (Documentación y Gobierno)

**Fecha:** 2026-07-14  
**Rama:** `m00/etapa-2`  
**Módulo:** M00 — Fundación Técnica  

## 1. Commits base

| Ref | SHA | Descripción |
|-----|-----|-------------|
| `main` / origen del trabajo | `98d287c` | Unify friendships… (último commit en main al iniciar) |
| Checkpoint WIP | `68ceb82` | `checkpoint: preserve WIP GPS/payments and M00 docs before Etapa 2` |
| Rama de trabajo Etapa 2 | `m00/etapa-2` (creada desde `68ceb82`) | Docs/gobierno de esta etapa |

**Estado git al inicio de Etapa 2 (requerido por la spec):** cambios locales de GPS/mapa/pagos + docs M00/D01 presentes. Se protegieron con commit de checkpoint en `checkpoint/pre-m00-etapa-2` / heredado por `m00/etapa-2`. **Nada se descartó.**

## 2. Archivos creados

| Archivo |
|---------|
| `docs/README.md` |
| `docs/00-maestro/README.md` |
| `docs/02-arquitectura/arquitectura-inicial.md` |
| `docs/02-arquitectura/M00-etapa-2-cierre.md` (este) |
| `docs/adr/ADR-0001-Supabase-como-backend-principal.md` |
| `docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md` |
| `docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md` |
| `docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md` |
| `docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md` |
| `docs/adr/ADR-0006-Migracion-del-paquete-a-Leover.md` |
| `docs/99-legacy/INVENTARIO.md` |
| `docs/04-calidad/M00-plan-de-calidad.md` |
| `CONTRIBUTING.md` |
| `.github/PULL_REQUEST_TEMPLATE.md` |
| `supabase/.env.example` |

Carpetas aseguradas: `docs/00-maestro/`, `docs/04-calidad/`, `docs/adr/`, `docs/99-legacy/`, `.github/`.

## 3. Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `README.md` | Setup mock/Supabase, comandos compile/test/lint, docs, sin NestJS |
| `local.properties.example` | Variables documentadas, valores ficticios, sin secretos |
| `.gitignore` | `.env`, `supabase/.env`, excepción `!.env.example` |

## 4. Archivos movidos / no movidos

- **No se movió** ningún documento legacy a `99-legacy/` (solo inventario + propuesta).  
- **No se eliminó** Firebase (`firebase.json`, rules, etc.).  
- **No se renombró** el paquete Android.  
- **No se modificó** código de negocio ni pantallas en esta etapa.

## 5. Decisiones registradas (ADR Accepted)

1. Supabase = backend principal; sin NestJS/Docker DB/ORM en M00.  
2. Android = monolito `:app` por paquetes.  
3. Mantener `DataProvider` / `AuthProvider`; Hilt futuro.  
4. Supabase Kotlin + Ktor; sin Retrofit.  
5. Secretos vía `local.properties` / secrets Edge; mock sin crash.  
6. Rename a `com.leover.app` diferido (pre-tienda, rama exclusiva).

## 6. Validación ejecutada

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
```

| Comando | Resultado |
|---------|-----------|
| assembleDebug | **SUCCESS** |
| testDebugUnitTest | **SUCCESS** (7 tests) |
| lintDebug | **FAILED** — 53 errors, 35 warnings, 1 hint (igual que auditoría; sin empeorar) |

## 7. Riesgos

- Lint sigue en rojo; plan de causa raíz en `docs/04-calidad/M00-plan-de-calidad.md`.  
- WIP de GPS/pagos convive en la misma línea de commits del checkpoint: mergear a `main` debe separar PRs (docs M00 vs features).  
- `google-services.json` sigue versionado (repo privado).  
- Spec M00 original (NestJS) queda históricamente desalineada; rigen Etapa 2 + ADR-0001.

## 8. Pendientes reales (no Etapa 2)

- Fixes lint: Fragment version + App Links (siguiente etapa de calidad M00).  
- CI GitHub Actions.  
- Feature flags / logging abstraction (M00 posterior).  
- Movimiento planificado de docs Firebase a `99-legacy/`.  
- Separar en PRs: documentación M00 vs WIP GPS/pagos.

## 9. Criterios de aceptación Etapa 2

- [x] `docs/README.md` con enlaces  
- [x] `arquitectura-inicial.md` fiel al sistema  
- [x] Seis ADR Accepted  
- [x] README permite levantar desde cero  
- [x] CONTRIBUTING + plantilla PR  
- [x] Ejemplos sin secretos reales  
- [x] Inventario legacy sin eliminaciones  
- [x] Plan de calidad lint/tests  
- [x] App compila  
- [x] Unit tests pasan  
- [x] Sin segundo backend  
- [x] Sin módulos funcionales desarrollados en esta etapa  
- [x] Este cierre  

## 10. Recomendación para Etapa 3

Según la corrección de gobierno: **no** implementar NestJS/Docker.

Próximo trabajo sugerido de M00 (calidad / fundación Android), sujeto a aprobación:

1. PR solo-docs de esta Etapa 2 hacia `main` (sin WIP de mapas/pagos), **o** cherry-pick de archivos docs.  
2. Etapa de calidad: dependencia Fragment ≥ 1.3 / 1.8 + corrección `autoVerify` App Links.  
3. Re-medir lint; solo entonces considerar baseline de deuda no crítica.  
4. Después: AppConfig por ambientes livianos, FeatureFlags locales, AppLogger, CI.

**No avanzar** hasta aprobación explícita.
