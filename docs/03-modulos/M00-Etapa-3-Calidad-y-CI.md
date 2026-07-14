# LEOVER — M00 Etapa 3: Calidad, saneamiento Git y CI

**Módulo:** M00 — Fundación Técnica  
**Etapa:** 3 — Calidad y automatización  
**Estado:** Aprobado para ejecución  
**Fecha:** 2026-07-14  
**Documento anterior:** `docs/02-arquitectura/M00-etapa-2-cierre.md`

---

## 1. Objetivo

Cerrar la base de calidad de Leover antes de continuar con capacidades funcionales. Esta etapa debe:

1. Separar la documentación aprobada del trabajo WIP de GPS/mapas/pagos sin perder ningún cambio.
2. Resolver las causas reales de los errores de Android Lint.
3. Mantener compilación y pruebas unitarias en verde.
4. Incorporar integración continua para Android.
5. Dejar evidencia escrita y reproducible del estado final.

Esta etapa **no desarrolla módulos de negocio**.

---

## 2. Decisiones obligatorias

1. **Supabase continúa como backend oficial.** No crear NestJS, Docker, Prisma, TypeORM ni una segunda base de datos.
2. **Se conserva el monolito Android `:app`.** No modularizar Gradle ni mover masivamente paquetes.
3. **No renombrar todavía** `applicationId`, `namespace` ni el paquete `com.comunidapp.app`.
4. **No borrar Firebase ni `google-services.json`** en esta etapa. Solo documentar riesgos.
5. **No mezclar** los cambios WIP de GPS/mapas/pagos con el PR de documentación o calidad.
6. **No crear un lint baseline como primer recurso.** Primero se corrigen causas raíz.
7. No actualizar todas las dependencias por warnings de versión. Cada cambio debe estar justificado por un error concreto.
8. No modificar pantallas, reglas de negocio, migraciones funcionales ni flujos del usuario salvo que sea estrictamente necesario para corregir un problema de calidad.

---

## 3. Documentos que Cursor debe leer antes de actuar

1. `docs/01-producto/D01-Modulos-y-Orden.md`
2. `docs/03-modulos/M00-Fundacion-Tecnica.md`
3. `docs/02-arquitectura/M00-auditoria-inicial.md`
4. `docs/03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md`
5. `docs/02-arquitectura/M00-etapa-2-cierre.md`
6. `docs/adr/ADR-0001-Supabase-como-backend-principal.md`
7. `docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
8. `docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
9. `docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
10. `docs/04-calidad/M00-plan-de-calidad.md`
11. Este documento.

Las decisiones de este archivo reemplazan cualquier requisito histórico incompatible con Supabase.

---

## 4. Alcance de implementación

### 4.1 Bloque A — Saneamiento de ramas y commits

Antes de modificar código:

1. Ejecutar y registrar:

```bash
git status
git branch --all
git log --oneline --decorate --graph -30
```

2. Crear una referencia de seguridad que preserve el estado completo actual:

```text
backup/pre-m00-etapa-3
```

3. **No fusionar directamente** a `main` el checkpoint `68ceb82` si contiene GPS/mapas/pagos junto con documentación.

4. Conseguir una rama limpia para calidad:

```text
m00/etapa-3-calidad-ci
```

Debe partir de `main` y contener únicamente:

- Documentación y gobierno aprobados de M00 Etapa 2.
- Cambios técnicos de calidad de esta Etapa 3.

5. Estrategia preferida:

- Si existen commits posteriores al checkpoint que contienen solo documentación, hacer cherry-pick únicamente de esos commits.
- Si la historia está mezclada, crear la rama desde `main` y copiar exclusivamente las rutas aprobadas mediante `git checkout <rama-etapa-2> -- <rutas>` o mecanismo equivalente.
- Crear un commit exclusivo de documentación antes de comenzar los fixes de calidad.

6. Confirmar que los cambios WIP siguen disponibles en su rama/checkpoint original y que no fueron descartados.

7. Registrar el procedimiento y los SHA resultantes en el cierre de etapa.

### Rutas aprobadas de documentación/gobierno

```text
README.md
CONTRIBUTING.md
.gitignore
local.properties.example
.github/PULL_REQUEST_TEMPLATE.md
supabase/.env.example
docs/README.md
docs/00-maestro/**
docs/01-producto/**
docs/02-arquitectura/**
docs/03-modulos/**
docs/04-calidad/**
docs/adr/**
docs/99-legacy/INVENTARIO.md
```

No copiar cambios de negocio, GPS, mapas o pagos al commit de documentación.

---

### 4.2 Bloque B — Diagnóstico y corrección de Android Lint

#### B1. Línea base de evidencia

Antes de corregir, ejecutar:

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --continue
```

Guardar el resumen de errores por ID en:

```text
docs/04-calidad/M00-lint-antes.md
```

#### B2. `InvalidFragmentVersionForActivityResult`

1. Confirmar qué dependencia introduce la versión de Fragment mediante `dependencyInsight` o reporte equivalente.
2. Determinar si el error proviene de una dependencia transitiva desactualizada.
3. Aplicar el cambio mínimo compatible con el stack actual:
   - Preferir una dependencia explícita en el version catalog si resuelve la incompatibilidad.
   - No actualizar Compose, Kotlin, AGP ni todas las bibliotecas sin necesidad.
4. Recompilar y volver a ejecutar tests y lint.
5. Documentar dependencia anterior, cambio realizado y motivo.

#### B3. `AppLinkUrlError`

1. Revisar los `intent-filter` del `AndroidManifest.xml`.
2. Para esquemas personalizados de OAuth o deep links internos, **no usar `android:autoVerify="true"`**.
3. Solo permitir `autoVerify` cuando exista un dominio HTTPS real y su archivo Digital Asset Links esté publicado y comprobado.
4. No inventar un dominio de producción.
5. Verificar que el flujo OAuth existente siga compilando y que el manifest sea válido.

#### B4. Recursos no utilizados

1. Revisar cada `UnusedResources`.
2. Eliminar únicamente recursos comprobablemente no referenciados y no utilizados por reflection, manifest, previews, flavors o configuración remota.
3. Cuando un recurso deba conservarse, justificar una supresión puntual y mínima.
4. No aplicar supresiones globales.

#### B5. Warnings de versiones

Los avisos `GradleDependency` no justifican una actualización masiva. Crear una sección en el informe final con:

- Dependencia actual.
- Versión sugerida por lint.
- Decisión: mantener o actualizar.
- Justificación y riesgo.

#### B6. Baseline de lint

Solo se admite si, luego de corregir causas raíz, permanecen observaciones históricas no críticas que no puedan resolverse en esta etapa.

Si se crea:

- Debe ser pequeño y revisado.
- No puede incluir errores de seguridad, manifest, App Links ni incompatibilidad de Activity Result.
- Debe documentarse cada ID incluido.
- El CI debe fallar ante cualquier problema nuevo no contenido en el baseline.

La opción preferida es finalizar sin baseline.

---

### 4.3 Bloque C — Pruebas y controles locales

Después de cada bloque de cambios ejecutar:

```bash
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
```

Al cierre, los tres comandos deben finalizar correctamente.

No modificar ni eliminar tests para lograr verde. Si un test falla por un cambio necesario, corregir el código o actualizar el test justificadamente.

Las pruebas instrumentadas quedan fuera de esta etapa, pero debe documentarse el comando futuro para ejecutarlas con emulador.

---

### 4.4 Bloque D — Integración continua Android

Crear exactamente:

```text
.github/workflows/android-ci.yml
```

Requisitos:

1. Ejecutarse en pull requests hacia `main` y en pushes a `main`.
2. Usar acciones oficiales mantenidas para checkout, Java, caché Gradle y artefactos.
3. Utilizar una versión de JDK compatible con la configuración Gradle/AGP existente; no cambiar el proyecto solo para adaptar el workflow.
4. Conceder permisos mínimos al workflow.
5. Ejecutar, como mínimo:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

6. Publicar como artefactos los reportes de tests y lint cuando existan, especialmente ante fallos.
7. No requerir credenciales reales de Supabase, Firebase ni Mercado Pago.
8. Usar el modo mock o valores ficticios seguros si el build los necesita.
9. No imprimir secretos ni el contenido de archivos de configuración sensibles.
10. Configurar concurrencia para cancelar ejecuciones anteriores de la misma rama cuando sea razonable.

Antes del cierre, validar localmente que los mismos comandos utilizados por CI pasan.

---

### 4.5 Bloque E — Documentación de calidad

Crear o actualizar:

```text
docs/04-calidad/M00-lint-antes.md
docs/04-calidad/M00-lint-despues.md
docs/04-calidad/M00-plan-de-calidad.md
docs/02-arquitectura/M00-etapa-3-cierre.md
```

`M00-lint-despues.md` debe comparar:

- Errores antes y después.
- Warnings antes y después.
- Correcciones realizadas.
- Problemas deliberadamente pendientes.
- Existencia o no de baseline.

---

## 5. Archivos que pueden modificarse

Solo cuando sea necesario para los objetivos de esta etapa:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/**
app/lint-baseline.xml                  # únicamente si cumple las reglas anteriores
.github/workflows/android-ci.yml
docs/**
README.md
CONTRIBUTING.md
.gitignore
```

Cualquier otro archivo modificado debe justificarse expresamente en el cierre.

---

## 6. Prohibiciones

- No desarrollar M01 ni módulos funcionales posteriores.
- No crear nuevas pantallas ni cambiar diseño visual.
- No migrar autenticación, datos o storage fuera de Supabase.
- No crear backend paralelo.
- No implementar Mercado Pago.
- No completar funcionalidades WIP de mapas/GPS.
- No renombrar el paquete de Android.
- No aplicar refactorización masiva.
- No subir APK, credenciales, `.env`, claves privadas o archivos locales.
- No desactivar lint ni convertir errores en warnings globalmente.
- No usar `@SuppressLint` generalizado para ocultar deuda.

---

## 7. Criterios de aceptación

La etapa se considera terminada únicamente si:

- [ ] Existe una rama limpia `m00/etapa-3-calidad-ci` basada en `main`.
- [ ] La documentación aprobada de Etapa 2 quedó separada del WIP de GPS/mapas/pagos.
- [ ] Existe una referencia de respaldo y ningún cambio del usuario se perdió.
- [ ] `:app:assembleDebug` finaliza correctamente.
- [ ] `:app:testDebugUnitTest` finaliza correctamente y mantiene al menos los 7 tests existentes.
- [ ] `:app:lintDebug` finaliza correctamente.
- [ ] Los errores `InvalidFragmentVersionForActivityResult` fueron corregidos en causa raíz.
- [ ] Los `AppLinkUrlError` fueron corregidos sin inventar dominios.
- [ ] No se realizó actualización masiva de dependencias.
- [ ] No se ocultaron errores críticos mediante baseline o supresiones globales.
- [ ] Existe `.github/workflows/android-ci.yml`.
- [ ] El workflow ejecuta build, tests y lint sin secretos reales.
- [ ] Existen informes antes/después de lint.
- [ ] No hubo cambios funcionales en módulos de negocio.
- [ ] No se creó un segundo backend.
- [ ] El cierre documenta commits, archivos, pruebas, riesgos y pendientes.

---

## 8. Salida obligatoria

Cursor debe detenerse al crear:

```text
docs/02-arquitectura/M00-etapa-3-cierre.md
```

El cierre debe incluir:

1. Rama, commits y estrategia utilizada para separar WIP.
2. Confirmación de que ningún cambio se perdió.
3. Archivos creados, modificados y eliminados.
4. Causa raíz de cada familia de errores lint.
5. Resultado antes/después.
6. Comandos y resultados de build, tests y lint.
7. Descripción del workflow de CI.
8. Riesgos y deuda pendiente.
9. Checklist de criterios de aceptación.
10. Recomendación para la siguiente etapa.

No avanzar a AppConfig, FeatureFlags, AppLogger ni M01 hasta revisión explícita del cierre.
