# LEOVER — M00 Etapa 2: Documentación y Gobierno

**Módulo:** M00 — Fundación Técnica  
**Etapa:** 2 — Decisiones arquitectónicas, gobierno y ordenamiento documental  
**Estado:** Aprobada para ejecución  
**Fecha:** 2026-07-14

## 1. Documentos obligatorios de entrada

Antes de realizar cualquier cambio, leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/03-modulos/M00-Fundacion-Tecnica.md`
3. `/docs/02-arquitectura/M00-auditoria-inicial.md`
4. Este documento: `/docs/03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md`

Este documento corrige y reemplaza cualquier instrucción previa de M00 que obligue a crear NestJS, Docker Compose o una segunda base de datos en esta etapa.

---

## 2. Decisiones aprobadas

### 2.1 Backend oficial actual

Supabase es el backend principal de Leover en la etapa actual.

Se conserva:

- Supabase Auth.
- PostgreSQL administrado por Supabase.
- Supabase Storage.
- Realtime cuando corresponda.
- Edge Functions para lógica segura o integraciones.
- Migraciones SQL existentes.
- Firebase Cloud Messaging únicamente para notificaciones push.

No crear en M00:

- Backend NestJS.
- API REST paralela sin una necesidad real.
- Docker Compose con otra base PostgreSQL.
- ORM Prisma o TypeORM.
- Migración o duplicación de Auth, Storage o datos ya implementados.

Un backend dedicado podrá evaluarse en el futuro solamente mediante un ADR nuevo y cuando existan necesidades que Supabase o sus Edge Functions no puedan resolver de forma segura, mantenible o rentable.

### 2.2 Arquitectura Android

- Mantener el módulo Gradle actual `:app`.
- Mantener MVVM, Compose, Navigation Compose, repositorios y Flow.
- No realizar modularización Gradle masiva en M00.
- Organizar nuevos desarrollos por paquetes y contratos claros.
- No mover archivos existentes únicamente para que coincidan con una estructura teórica.

### 2.3 Inyección de dependencias

- Mantener temporalmente `DataProvider` y `AuthProvider`.
- No migrar toda la aplicación a Hilt en M00.
- Evitar nuevas dependencias directas a singletons desde la UI.
- Los nuevos repositorios deben exponerse mediante interfaces.
- Registrar la posible migración gradual a Hilt como decisión futura, no como trabajo obligatorio inmediato.

### 2.4 Acceso a datos y red

- Supabase Kotlin continúa siendo el cliente principal para Auth, PostgREST, Storage y Realtime.
- Ktor podrá utilizarse para servicios externos o Edge Functions cuando resulte necesario.
- No incorporar Retrofit mientras no exista una API REST propia que lo justifique.
- La UI no debe depender directamente de Supabase ni de Ktor.
- Mantener repositorios y modelos de dominio como frontera entre UI y datos.

### 2.5 Marca y paquete Android

El paquete actual `com.comunidapp.app` no coincide con la marca Leover.

En esta etapa:

- No renombrarlo todavía.
- Documentar la migración controlada a `com.leover.app`.
- Programar el cambio antes de publicar la aplicación en una tienda.
- El cambio deberá realizarse en una rama o commit exclusivo, con compilación, pruebas, deep links, Firebase y Supabase verificados.

### 2.6 Calidad y lint

- No crear un baseline que oculte automáticamente los 53 errores actuales.
- Investigar primero la causa raíz de `InvalidFragmentVersionForActivityResult`.
- Corregir los errores de App Links y cualquier error de seguridad o funcionamiento.
- Solo después podrá crearse un baseline para deuda no crítica y documentada.
- Las advertencias de actualización de dependencias no autorizan actualizaciones masivas.

### 2.7 Firebase legado

- Mantener Firebase Cloud Messaging porque está en uso.
- Verificar si Firestore, Firebase Storage y sus reglas realmente siguen utilizándose.
- No eliminar archivos sin confirmar referencias en código, scripts y documentación.
- Los elementos confirmados como legado deben moverse a `/docs/99-legacy/` o eliminarse en un commit separado y documentado.

---

## 3. Precondición antes de modificar el repositorio

Antes de empezar la Etapa 2:

1. Mostrar `git status`.
2. Identificar cambios locales de GPS, mapas, pagos u otros módulos.
3. Crear un checkpoint seguro mediante commit o rama, sin mezclar cambios si no corresponde.
4. No descartar archivos ni cambios locales.
5. Registrar en el cierre de etapa el commit base utilizado.

Si existe riesgo de pérdida de trabajo, detenerse y explicar el problema antes de continuar.

---

## 4. Trabajo autorizado en la Etapa 2

### Tarea E2-01 — Índice documental

Crear:

`/docs/README.md`

Debe incluir:

- Estructura oficial de documentación.
- Descripción de cada carpeta.
- Documentos vigentes.
- Documentos históricos o reemplazados.
- Regla para evitar duplicados.
- Enlaces relativos válidos.

Crear o completar estas carpetas:

```text
/docs/
├── 00-maestro/
├── 01-producto/
├── 02-arquitectura/
├── 03-modulos/
├── 04-calidad/
├── adr/
└── 99-legacy/
```

No mover documentos todavía si eso rompe enlaces. Primero proponer y documentar el movimiento.

### Tarea E2-02 — Arquitectura inicial real

Crear:

`/docs/02-arquitectura/arquitectura-inicial.md`

Debe describir únicamente lo que existe o fue aprobado:

- Aplicación Android.
- MVVM y flujo de datos.
- Repositorios mock y Supabase.
- Supabase como backend principal.
- PostgreSQL, Auth, Storage y Edge Functions.
- Firebase Cloud Messaging.
- Navegación.
- Dependencias entre capas.
- Límites de cada capa.
- Riesgos actuales.
- Diagrama Mermaid simple.

No describir NestJS, Docker, Prisma ni servicios inexistentes como arquitectura actual.

### Tarea E2-03 — ADR obligatorios

Crear los siguientes archivos con estado `Accepted`:

1. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
2. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
3. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
4. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
5. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
6. `/docs/adr/ADR-0006-Migracion-del-paquete-a-Leover.md`

Cada ADR debe incluir:

- Contexto.
- Problema.
- Opciones consideradas.
- Decisión.
- Consecuencias positivas.
- Riesgos y consecuencias negativas.
- Condiciones para revisar la decisión.
- Estado y fecha.

### Tarea E2-04 — README principal

Actualizar `/README.md` sin borrar información útil.

Debe permitir que una persona nueva pueda:

- Entender qué es Leover.
- Conocer el stack actual.
- Ejecutar la app en modo mock.
- Configurar Supabase localmente mediante `local.properties`.
- Compilar debug.
- Ejecutar pruebas unitarias.
- Ejecutar lint.
- Entender dónde está la documentación.
- Identificar configuraciones que nunca deben commitearse.

No documentar un backend NestJS inexistente.

### Tarea E2-05 — Contribución y gobierno

Crear:

- `/CONTRIBUTING.md`
- `/.github/PULL_REQUEST_TEMPLATE.md`

Incluir:

- Convención de ramas.
- Convención de commits.
- Alcance pequeño por cambio.
- Prohibición de mezclar módulos.
- Compilación y pruebas requeridas.
- Revisión de secretos.
- Actualización documental.
- Checklist de Pull Request.

### Tarea E2-06 — Configuración y ejemplos

Revisar y actualizar:

- `/local.properties.example`
- `/.gitignore`

Crear solamente si las Edge Functions requieren variables de entorno:

- `/supabase/.env.example`

Reglas:

- No colocar secretos reales.
- Usar valores claramente ficticios.
- Explicar cada variable.
- No crear un `.env.example` raíz sin consumidor real.
- La ausencia de credenciales debe producir modo mock o un mensaje controlado, nunca un crash.

### Tarea E2-07 — Inventario de legado

Crear:

`/docs/99-legacy/INVENTARIO.md`

Debe listar:

- Documentos antiguos.
- Archivos Firebase posiblemente obsoletos.
- Roadmaps reemplazados.
- Elementos todavía utilizados.
- Recomendación: conservar, mover o eliminar.

En esta etapa no eliminar archivos funcionales ni realizar limpieza irreversible.

### Tarea E2-08 — Plan de calidad de M00

Crear:

`/docs/04-calidad/M00-plan-de-calidad.md`

Debe incluir:

- Estado actual de compile, tests y lint.
- Plan para resolver `InvalidFragmentVersionForActivityResult`.
- Plan para corregir App Links.
- Criterios para aceptar un lint baseline.
- Pruebas instrumentadas pendientes.
- Reglas para dependencias y actualizaciones.
- Validaciones que deberán ejecutarse en cada etapa.

---

## 5. Trabajo expresamente prohibido

Durante esta etapa no:

- Crear NestJS, Prisma, TypeORM o Docker Compose.
- Crear tablas, migraciones o funciones de negocio nuevas.
- Modificar pantallas o flujos funcionales.
- Implementar pagos, mapas, adopciones, perfiles o módulos futuros.
- Migrar masivamente a Hilt.
- Modularizar Gradle.
- Renombrar el paquete Android.
- Actualizar todas las dependencias.
- Crear un lint baseline sin análisis previo.
- Eliminar Firebase o documentos legacy sin verificación.
- Reescribir el proyecto.

---

## 6. Validación obligatoria

Al terminar:

```bash
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Ejecutar también lint para registrar su estado, aunque todavía pueda fallar:

```bash
./gradlew.bat :app:lintDebug
```

La Etapa 2 no debe empeorar la cantidad de errores de compilación, pruebas o lint.

---

## 7. Documento de cierre

Crear:

`/docs/02-arquitectura/M00-etapa-2-cierre.md`

Debe contener:

- Commit base.
- Archivos creados.
- Archivos modificados.
- Archivos movidos o no movidos.
- Decisiones registradas.
- Resultado de compilación.
- Resultado de pruebas.
- Resultado de lint.
- Riesgos encontrados.
- Pendientes reales.
- Recomendación para Etapa 3.

---

## 8. Criterios de aceptación

La Etapa 2 se considera completa únicamente cuando:

- [ ] Existe `/docs/README.md` con enlaces correctos.
- [ ] Existe `arquitectura-inicial.md` fiel al sistema actual.
- [ ] Están creados y aceptados los seis ADR.
- [ ] README permite levantar la aplicación desde cero.
- [ ] CONTRIBUTING y plantilla de PR existen.
- [ ] Los ejemplos de configuración no contienen secretos.
- [ ] Existe inventario de legado sin eliminaciones riesgosas.
- [ ] Existe plan de calidad para lint y pruebas.
- [ ] La app sigue compilando.
- [ ] Los tests unitarios continúan pasando.
- [ ] No se agregó un segundo backend.
- [ ] No se desarrollaron módulos funcionales.
- [ ] Existe `M00-etapa-2-cierre.md`.

---

## 9. Instrucción final para Cursor

Ejecutá exclusivamente las tareas autorizadas de esta Etapa 2. Trabajá en cambios pequeños, conservá todo el código funcional y detenete al generar el documento de cierre. No avances a la siguiente etapa sin aprobación explícita.
