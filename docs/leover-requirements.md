# Leover — Documento de Requisitos por Módulo

**Versión:** 1.0  
**Fecha:** Julio 2026  
**Estado:** Borrador para validación de producto  
**Base técnica:** ComunidadApp (Android, Kotlin, Compose, MVVM, Firebase opcional)

---

## 1. Visión y alcance

### 1.1 Visión
Leover es la plataforma que conecta a toda la comunidad animal: personas, mascotas, refugios, veterinarios, voluntarios y organizaciones en un único ecosistema.

### 1.2 Misión
Facilitar el cuidado, rescate, adopción y bienestar de las mascotas mediante tecnología que centralice información hoy dispersa en redes sociales, WhatsApp y sistemas aislados.

### 1.3 Principios de producto
- **Un perfil por usuario**, con identidad y reputación en la comunidad.
- **Una identidad por mascota**, con historial de salud y trazabilidad.
- **Herramientas por organización**, según su tipo (refugio, veterinaria, tienda, etc.).
- **Colaboración abierta**: cualquier miembro puede aportar, publicar o ayudar.
- **Proximidad**: ubicación como eje para encontrar personas y servicios cerca.

### 1.4 Actores del sistema

| Actor | Descripción |
|-------|-------------|
| **Persona** | Dueño de mascota, adoptante, voluntario, hogar de tránsito |
| **Refugio / ONG** | Organización de rescate y adopción |
| **Veterinaria** | Clínica o profesional veterinario |
| **Educador / adiestrador** | Servicios de educación y entrenamiento canino/felino |
| **Paseador** | Servicio de paseo de mascotas |
| **Tienda / emprendimiento** | Comercio o emprendimiento del rubro mascotas |
| **Administrador** | Moderación y verificación (futuro) |

### 1.5 Fuera de alcance (v1)
- Pagos in-app y donaciones monetarias integradas (Fase 3)
- Chat en tiempo real (Fase 3)
- App iOS / Web (solo Android en v1)
- Verificación biométrica o KYC de organizaciones

---

## 2. Requisitos transversales (Fundación)

Estos requisitos aplican a todos los módulos y deben completarse antes o en paralelo al núcleo comunitario.

### US-F00 — Marca Leover
**Como** usuario, **quiero** ver la app con identidad Leover, **para** reconocer la plataforma unificada.

**Criterios de aceptación:**
- [ ] Nombre visible "Leover" en login, splash y stores
- [ ] Icono y colores de marca consistentes
- [ ] Textos de onboarding alineados a la visión del ecosistema

---

### US-F01 — Registro e inicio de sesión
**Como** visitante, **quiero** crear cuenta e iniciar sesión, **para** participar en la comunidad.

**Criterios de aceptación:**
- [ ] Registro con email, contraseña y nombre
- [ ] Verificación de email obligatoria antes del primer acceso completo
- [ ] Recuperación de contraseña por email
- [ ] Mensajes de error claros en español
- [ ] Sesión persistente: al reabrir la app, el usuario autenticado entra directo al inicio

---

### US-F02 — Perfil de persona
**Como** usuario registrado, **quiero** completar y editar mi perfil, **para** que otros me conozcan en la comunidad.

**Criterios de aceptación:**
- [ ] Campos: foto, nombre, bio, ubicación (ciudad/barrio), teléfono opcional
- [ ] Ver mi perfil público como lo ven otros
- [ ] Listado de mis publicaciones y mis mascotas en el perfil
- [ ] Botón cerrar sesión

---

### US-F03 — Identidad de mascota
**Como** dueño, **quiero** registrar mis mascotas con ficha completa, **para** tener su historial centralizado.

**Criterios de aceptación:**
- [ ] CRUD de mascotas: nombre, foto, especie, sexo, edad, tamaño, descripción
- [ ] Sección salud: vacunas, desparasitación, antipulgas, recordatorios
- [ ] Detalle de mascota accesible desde perfil y listado "Mis mascotas"
- [ ] Cada mascota vinculada al `ownerId` del usuario autenticado

---

### US-F04 — Publicar con fotos
**Como** usuario, **quiero** subir fotos al publicar contenido, **para** que la red social sea visual y útil.

**Criterios de aceptación:**
- [ ] Selector de imagen desde galería (cámara opcional v1.1)
- [ ] Subida a almacenamiento en la nube con URL persistente
- [ ] Indicador de carga y manejo de error si falla la subida
- [ ] Imagen visible en feed y detalle del post

---

### US-F05 — Ubicación
**Como** usuario, **quiero** indicar mi ubicación y filtrar contenido cerca, **para** encontrar ayuda y personas en mi zona.

**Criterios de aceptación:**
- [ ] Ubicación en perfil (texto: ciudad/barrio) obligatoria recomendada
- [ ] Ubicación en publicaciones donde aplique (perdidos, eventos, servicios)
- [ ] Filtro "Cerca de mí" por ciudad o radio (radio en v1.1 con GPS)
- [ ] Permiso de ubicación solicitado solo cuando se use mapa o radio

---

### US-F06 — Roles de cuenta
**Como** organización o profesional, **quiero** un tipo de cuenta distinto al de persona, **para** acceder a herramientas específicas.

**Criterios de aceptación:**
- [ ] Tipos: `PERSON`, `SHELTER`, `VET`, `TRAINER`, `WALKER`, `SHOP`, `FOSTER_HOME`
- [ ] En registro o configuración, el usuario elige tipo de cuenta
- [ ] Perfil y campos visibles según tipo (ej. refugio muestra necesidades y adopciones)
- [ ] Una cuenta puede tener solo un tipo principal en v1

---

## 3. Módulo: Red social

**Objetivo:** Reemplazar el uso disperso de Facebook/Instagram para preguntas, fotos y comunidad local.

### US-RS01 — Feed principal
**Como** usuario, **quiero** ver un feed de publicaciones de la comunidad, **para** enterarme de novedades y conectar.

**Criterios de aceptación:**
- [ ] Listado cronológico de posts con autor, fecha, texto, foto opcional
- [ ] Tipos de post: General, Pregunta, Urgente
- [ ] Badge visual por tipo de publicación
- [ ] Pull-to-refresh para actualizar
- [ ] Paginación o carga incremental (mín. 20 posts por página)

---

### US-RS02 — Publicar en la red
**Como** usuario, **quiero** crear publicaciones con texto y fotos, **para** compartir con la comunidad.

**Criterios de aceptación:**
- [ ] Formulario: título o resumen, contenido, foto opcional, ubicación opcional
- [ ] Tipo seleccionable: General, Pregunta, Urgente
- [ ] Publicación aparece en feed propio y global tras confirmación
- [ ] Validación: contenido no vacío

---

### US-RS03 — Interacción: likes
**Como** usuario, **quiero** dar like a publicaciones, **para** mostrar apoyo o interés.

**Criterios de aceptación:**
- [ ] Tap en like alterna estado (liked / no liked)
- [ ] Contador actualizado en tiempo real o al refrescar
- [ ] Un like por usuario por publicación
- [ ] Estado visual diferenciado cuando ya di like

---

### US-RS04 — Interacción: comentarios
**Como** usuario, **quiero** comentar publicaciones, **para** responder preguntas y conversar.

**Criterios de aceptación:**
- [ ] Pantalla o bottom sheet de comentarios por post
- [ ] Listado de comentarios con autor, texto y fecha
- [ ] Enviar comentario autenticado
- [ ] Contador de comentarios actualizado en la tarjeta del feed

---

### US-RS05 — Personas cerca
**Como** usuario, **quiero** descubrir personas de la comunidad en mi zona, **para** conectar con otros dueños o voluntarios.

**Criterios de aceptación:**
- [ ] Sección "Cerca de ti" con perfiles que comparten ciudad/barrio
- [ ] Tarjeta: foto, nombre, bio breve, cantidad de mascotas
- [ ] Tap abre perfil público (sin datos sensibles: teléfono oculto por defecto)
- [ ] Filtro por ciudad configurable en perfil

---

## 4. Módulo: Adopciones

**Objetivo:** Centralizar mascotas en adopción con trazabilidad desde refugios y publicadores.

### US-AD01 — Explorar adopciones
**Como** adoptante, **quiero** ver mascotas disponibles para adopción, **para** encontrar un compañero.

**Criterios de aceptación:**
- [ ] Listado con foto, nombre, especie, edad, ubicación, estado
- [ ] Filtros: ubicación, especie, sexo, tamaño, estado (disponible, en proceso, adoptado)
- [ ] Solo mascotas con estado `AVAILABLE` visibles por defecto
- [ ] Tap abre detalle completo

---

### US-AD02 — Detalle de adopción
**Como** adoptante, **quiero** ver información completa de una mascota en adopción, **para** decidir si contactar.

**Criterios de aceptación:**
- [ ] Foto, nombre, especie, sexo, edad, tamaño, descripción, ubicación
- [ ] Refugio u organización publicadora con enlace a su perfil
- [ ] Estado visible (disponible / en proceso / adoptado)
- [ ] Botón "Quiero adoptar" o "Contactar" (ver US-AD03)

---

### US-AD03 — Solicitud de adopción
**Como** adoptante, **quiero** expresar interés en adoptar, **para** que el refugio me contacte.

**Criterios de aceptación:**
- [ ] Formulario corto: mensaje, teléfono o email de contacto
- [ ] Notificación al publicador (in-app o email en v1.1)
- [ ] Publicador puede marcar adopción como "en proceso" o "adoptada"
- [ ] Usuario ve confirmación de envío de solicitud

---

### US-AD04 — Publicar adopción
**Como** refugio o persona, **quiero** publicar una mascota en adopción, **para** encontrarle familia.

**Criterios de aceptación:**
- [ ] Formulario: nombre, especie, sexo, edad, tamaño, ubicación, descripción, foto
- [ ] Vinculación automática al perfil del publicador
- [ ] Aparece en módulo Adopciones y opcionalmente en feed (tipo Adopción)
- [ ] Editar y cambiar estado desde "Mis publicaciones"

---

## 5. Módulo: Hogares de tránsito

**Objetivo:** Conectar refugios con hogares temporales, hoy resueltos de boca en boca.

### US-HT01 — Ofrecer hogar de tránsito
**Como** persona, **quiero** registrar que puedo recibir mascotas temporalmente, **para** ayudar a refugios.

**Criterios de aceptación:**
- [ ] Perfil de hogar de tránsito: capacidad (cantidad), especies aceptadas, zona, notas
- [ ] Disponibilidad: disponible / no disponible
- [ ] Visible en directorio de hogares de tránsito
- [ ] Solo usuarios con rol `FOSTER_HOME` o `PERSON` con flag activo

---

### US-HT02 — Buscar hogar de tránsito
**Como** refugio, **quiero** ver hogares de tránsito disponibles cerca, **para** ubicar mascotas rescatadas.

**Criterios de aceptación:**
- [ ] Listado filtrable por zona y especie
- [ ] Tarjeta: nombre, zona, capacidad, especies, estado disponibilidad
- [ ] Botón contactar (formulario o datos de contacto según privacidad)
- [ ] Filtro "solo disponibles"

---

### US-HT03 — Solicitud de tránsito
**Como** refugio, **quiero** solicitar tránsito a un hogar, **para** coordinar rescate.

**Criterios de aceptación:**
- [ ] Mensaje con datos de la mascota (nombre, especie, urgencia, duración estimada)
- [ ] Hogar recibe solicitud y puede aceptar o rechazar (v1.1)
- [ ] En v1: envío de solicitud con confirmación al refugio

---

## 6. Módulo: Refugios

**Objetivo:** Dar identidad y herramientas a organizaciones de rescate.

### US-RF01 — Directorio de refugios
**Como** usuario, **quiero** explorar refugios, **para** conocer organizaciones y cómo ayudar.

**Criterios de aceptación:**
- [ ] Listado: nombre, foto, ubicación, breve descripción
- [ ] Búsqueda por nombre o ciudad
- [ ] Tap abre perfil del refugio

---

### US-RF02 — Perfil de refugio
**Como** usuario, **quiero** ver el perfil completo de un refugio, **para** ver adopciones, necesidades y contacto.

**Criterios de aceptación:**
- [ ] Información: descripción, ubicación, teléfono, email
- [ ] Listado de mascotas en adopción del refugio
- [ ] Listado de necesidades actuales (insumos, voluntarios)
- [ ] Botones: contactar, ver eventos, donar/voluntariado (enlaces a módulos)

---

### US-RF03 — Gestión de necesidades (refugio)
**Como** administrador de refugio, **quiero** publicar qué necesito, **para** recibir ayuda de la comunidad.

**Criterios de aceptación:**
- [ ] CRUD de necesidades: ítem, cantidad/descripción, prioridad opcional
- [ ] Visibles en perfil del refugio
- [ ] Fecha de actualización visible

---

## 7. Módulo: Veterinarias

**Objetivo:** Directorio de clínicas y profesionales para el cuidado de mascotas.

### US-VT01 — Directorio de veterinarias
**Como** dueño, **quiero** encontrar veterinarias cerca, **para** atender a mi mascota.

**Criterios de aceptación:**
- [ ] Listado: nombre, foto, dirección/zona, servicios destacados
- [ ] Filtro por ciudad/zona
- [ ] Tap abre perfil de la veterinaria

---

### US-VT02 — Perfil de veterinaria
**Como** dueño, **quiero** ver datos de una veterinaria, **para** contactarla o visitarla.

**Criterios de aceptación:**
- [ ] Campos: nombre, descripción, dirección, horarios, teléfono, email, web opcional
- [ ] Lista de servicios (consulta, vacunas, cirugía, emergencias, etc.)
- [ ] Botón llamar / email / ver en mapa (v1.1)
- [ ] Cuenta tipo `VET` puede editar su propio perfil

---

## 8. Módulo: Educadores y adiestradores

### US-ED01 — Directorio de educadores
**Como** dueño, **quiero** encontrar educadores y adiestradores, **para** entrenar a mi mascota.

**Criterios de aceptación:**
- [ ] Listado: nombre, foto, especialidades, zona
- [ ] Filtro por especialidad (cachorros, ansiedad, agresividad, obediencia, etc.)
- [ ] Perfil con bio, experiencia, contacto, modalidad (presencial/online)

---

### US-ED02 — Perfil profesional editable
**Como** educador, **quiero** mantener mi perfil profesional, **para** que clientes me encuentren.

**Criterios de aceptación:**
- [ ] Registro o conversión a tipo `TRAINER`
- [ ] Campos: especialidades, zona, tarifa orientativa opcional, contacto
- [ ] Visible en directorio tras completar perfil mínimo

---

## 9. Módulo: Paseadores

### US-PS01 — Directorio de paseadores
**Como** dueño, **quiero** encontrar paseadores en mi zona, **para** contratar paseos.

**Criterios de aceptación:**
- [ ] Listado: nombre, foto, zona, disponibilidad resumida
- [ ] Perfil: experiencia, tipos de mascota, radio de trabajo, contacto
- [ ] Filtro por zona

---

### US-PS02 — Perfil de paseador
**Como** paseador, **quiero** publicar mi servicio, **para** conseguir clientes.

**Criterios de aceptación:**
- [ ] Tipo de cuenta `WALKER`
- [ ] Campos: zona, disponibilidad (días/horarios texto), tarifa orientativa opcional
- [ ] Contacto vía formulario o teléfono según preferencia

---

## 10. Módulo: Tiendas y emprendimientos

### US-TI01 — Directorio de tiendas
**Como** usuario, **quiero** descubrir tiendas y emprendimientos del rubro mascotas, **para** comprar o apoyar negocios locales.

**Criterios de aceptación:**
- [ ] Listado: nombre, foto, categoría (alimentos, accesorios, peluquería, etc.), zona
- [ ] Filtro por categoría y ciudad
- [ ] Perfil: descripción, dirección, horarios, contacto, redes opcionales

---

### US-TI02 — Perfil de tienda
**Como** emprendedor, **quiero** registrar mi negocio, **para** darme a conocer en la comunidad.

**Criterios de aceptación:**
- [ ] Tipo de cuenta `SHOP`
- [ ] Campos: categoría, productos/servicios destacados, ubicación, contacto
- [ ] Promociones o novedades publicables en feed (tipo General) — v1.1

---

## 11. Módulo: Perdidos y encontrados

**Objetivo:** Centralizar avisos hoy dispersos en Instagram y grupos locales.

### US-PE01 — Listado de avisos
**Como** usuario, **quiero** ver mascotas perdidas y encontradas, **para** ayudar o buscar la mía.

**Criterios de aceptación:**
- [ ] Listado con tipo (perdido/encontrado), especie, zona, fecha, foto
- [ ] Filtros: tipo, especie, ubicación
- [ ] Orden por fecha descendente
- [ ] Badge visual urgente si publicación &lt; 48 h (opcional)

---

### US-PE02 — Publicar aviso
**Como** usuario, **quiero** publicar que perdí o encontré una mascota, **para** llegar a la comunidad local.

**Criterios de aceptación:**
- [ ] Formulario: tipo, nombre opcional, especie, foto, ubicación, descripción, contacto
- [ ] Aviso en módulo Perdidos y en feed (tipo Perdido/Encontrado)
- [ ] Validación de campos obligatorios: ubicación, descripción, contacto

---

### US-PE03 — Mapa de avisos (v1.1)
**Como** usuario, **quiero** ver avisos en un mapa, **para** identificar zonas de búsqueda.

**Criterios de aceptación:**
- [ ] Mapa con pins por aviso (coordenadas o geocoding de dirección)
- [ ] Tap en pin muestra resumen y enlace al detalle
- [ ] Permiso de ubicación para centrar mapa en mi posición

---

### US-PE04 — Marcar como resuelto
**Como** autor del aviso, **quiero** marcarlo como resuelto, **para** informar que la mascota fue encontrada/reclamada.

**Criterios de aceptación:**
- [ ] Estado: activo / resuelto
- [ ] Avisos resueltos ocultos del listado principal por defecto
- [ ] Opción "Ver resueltos" en filtros

---

## 12. Módulo: Donaciones y voluntariado

**Objetivo:** Canalizar ayuda material y tiempo hacia refugios y causas.

### US-DO01 — Ver necesidades de refugios
**Como** usuario, **quiero** ver qué necesitan los refugios, **para** donar insumos o tiempo.

**Criterios de aceptación:**
- [ ] Agregado de necesidades de todos los refugios o filtro por refugio
- [ ] Ítem, cantidad, refugio, fecha de actualización
- [ ] Enlace al perfil del refugio para coordinar entrega

---

### US-DO02 — Campañas de donación (v1)
**Como** refugio, **quiero** crear una campaña de donación, **para** comunicar una necesidad urgente.

**Criterios de aceptación:**
- [ ] Campaña: título, descripción, ítems o meta descriptiva, fecha límite opcional
- [ ] Visible en módulo Donaciones y perfil del refugio
- [ ] Sin pago in-app en v1; contacto para coordinar donación

---

### US-DO03 — Oportunidades de voluntariado
**Como** usuario, **quiero** ver formas de ser voluntario, **para** ofrecer traslados, eventos o cuidado.

**Criterios de aceptación:**
- [ ] Listado: título, organización, tipo (traslado, evento, cuidado, otro), zona, fecha
- [ ] Publicar oportunidad: solo refugios y organizaciones en v1
- [ ] Botón "Quiero ayudar" envía interés al organizador

---

## 13. Módulo: Eventos de adopción

**Objetivo:** Promover jornadas presenciales que conecten adopciones con la comunidad.

### US-EV01 — Calendario de eventos
**Como** usuario, **quiero** ver eventos de adopción próximos, **para** asistir y conocer mascotas.

**Criterios de aceptación:**
- [ ] Listado ordenado por fecha
- [ ] Tarjeta: título, fecha, hora, lugar, organizador, imagen opcional
- [ ] Filtro por ciudad
- [ ] Eventos pasados archivados o marcados como finalizados

---

### US-EV02 — Detalle de evento
**Como** usuario, **quiero** ver información completa del evento, **para** decidir si asistir.

**Criterios de aceptación:**
- [ ] Descripción, fecha, hora, dirección, mapa opcional (v1.1)
- [ ] Organizador con enlace a perfil
- [ ] Mascotas destacadas o cantidad estimada en adopción
- [ ] Botón "Me interesa" o "Compartir"

---

### US-EV03 — Publicar evento
**Como** refugio, **quiero** publicar un evento de adopción, **para** convocar a la comunidad.

**Criterios de aceptación:**
- [ ] Formulario: título, descripción, fecha, hora, lugar, foto opcional
- [ ] Solo cuentas `SHELTER` u organizaciones autorizadas
- [ ] Evento visible en módulo Eventos y opcionalmente en feed

---

## 14. Navegación e información (UX)

### US-NAV01 — Estructura principal
**Como** usuario, **quiero** acceder a todos los módulos sin perderte, **para** usar Leover como plataforma única.

**Criterios de aceptación:**
- [ ] Bottom navigation sugerida: **Inicio** | **Explorar** | **Publicar** | **Comunidad** | **Perfil**
- [ ] "Explorar" agrupa: Adopciones, Tránsito, Refugios, Vets, Paseadores, Tiendas, Eventos, Donaciones, Perdidos
- [ ] "Publicar" ofrece tipos: Post, Adopción, Perdido/Encontrado, Evento (según rol)
- [ ] Accesos rápidos desde Inicio (chips o secciones destacadas)

---

## 15. Requisitos no funcionales

| ID | Requisito | Criterio |
|----|-----------|----------|
| NFR-01 | Idioma | UI en español (Argentina) |
| NFR-02 | Offline | Lectura de contenido cacheado reciente; aviso si sin conexión al publicar |
| NFR-03 | Rendimiento | Feed inicial &lt; 2 s en red 4G |
| NFR-04 | Seguridad | Reglas Firestore por `ownerId` / rol; contraseñas nunca en cliente |
| NFR-05 | Privacidad | Teléfono y email no públicos por defecto; contacto vía formulario |
| NFR-06 | Accesibilidad | Contraste Material 3, contentDescription en imágenes e iconos |
| NFR-07 | minSdk | 26; targetSdk alineado con políticas Play Store |

---

## 16. Matriz de priorización (MVP Leover)

| Prioridad | Módulo / Historia | Fase |
|-----------|-------------------|------|
| P0 | US-F01 a US-F06 (Fundación) | Fase 0 |
| P0 | US-RS01, US-RS02, US-RS03, US-RS04 | Fase 1 |
| P0 | US-PE01, US-PE02, US-PE04 | Fase 1 |
| P0 | US-AD01 a US-AD04 | Fase 1 |
| P0 | US-RF01, US-RF02 | Fase 1 |
| P1 | US-RS05, US-HT01, US-HT02 | Fase 2 |
| P1 | US-DO01, US-DO03, US-EV01 a US-EV03 | Fase 2 |
| P1 | US-RF03, US-PE03 | Fase 2 |
| P2 | US-VT01, US-VT02, US-ED01, US-PS01, US-TI01 | Fase 2 |
| P2 | US-DO02, US-HT03, US-AD03 (notificaciones) | Fase 2–3 |
| P3 | Chat, pagos, turnos, reseñas | Fase 3 |

---

## 17. Trazabilidad con ComunidadApp actual

| Historia | Estado en código actual |
|----------|-------------------------|
| US-F01 | Parcial — auth mock + Firebase, sin sesión persistente |
| US-F02 | Parcial — perfil mock fijo |
| US-F03 | Parcial — mascotas mock, sin CRUD |
| US-F04 | No implementado |
| US-RS01 | Parcial — feed sin paginación ni fotos reales |
| US-RS03, US-RS04 | No implementado (UI decorativa) |
| US-AD01, US-AD02 | Parcial |
| US-AD04 | Parcial — sin foto |
| US-RF01, US-RF02 | Parcial |
| US-PE01, US-PE02 | Parcial |
| Resto | No implementado |

---

## 18. Glosario

| Término | Definición |
|---------|------------|
| **Hogar de tránsito** | Hogar temporal que recibe mascotas rescatadas hasta adopción definitiva |
| **Feed** | Cronología de publicaciones de la red social |
| **Organización** | Cuenta institucional: refugio, vet, tienda, etc. |
| **Post** | Publicación en la red social |
| **Campaña** | Pedido estructurado de donación con meta o ítems |

---

## 19. Aprobación

| Rol | Nombre | Fecha | Firma |
|-----|--------|-------|-------|
| Producto | | | |
| Tecnología | | | |
| Diseño | | | |

---

*Documento vivo: actualizar al cerrar cada fase o cambiar alcance del MVP.*
