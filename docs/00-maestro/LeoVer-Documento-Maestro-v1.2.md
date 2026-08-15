# LeoVer — Documento Maestro Integral de la Startup

**Versión:** 1.2
**Fecha:** 15 de agosto de 2026
**Estado:** Documento maestro consolidado para gobierno y ejecución

LEOVER

Documento Maestro Integral de la Startup

Visión, producto, negocio, operación, tecnología y ejecución

Versión: 1.2

Fecha: 15 de agosto de 2026

Estado: Documento maestro consolidado para gobierno y ejecución

Ámbito inicial: Argentina · Piloto: San Vicente + Almirante Brown · Expansión prevista a Latinoamérica

Producto: LeoVer

Sociedad operadora prevista: COMUNIDAPP S.A.S. (aún no constituida; documentos legales = DRAFT PRE-LAUNCH)

“Una identidad para cada mascota. Una red para ayudarla durante toda su vida.”

| Decisión de gobierno Este documento reemplaza al Documento Maestro v1.1 como fuente estratégica superior de LeoVer. La v1.1 se conserva intacta como registro histórico. Las especificaciones de producto, arquitectura, módulos y operación deberán alinearse a esta versión o registrar explícitamente una decisión posterior aprobada. |
| --- |

## Control del documento

| Campo | Definición |
| --- | --- |
| Documento | LeoVer — Documento Maestro Integral de la Startup. |
| Finalidad | Definir de manera unificada la estrategia, el producto completo, el modelo económico, la operación, la tecnología, los riesgos y el plan de ejecución. |
| Audiencia | Fundadores, COMUNIDAPP S.A.S., producto, diseño, tecnología, operaciones, legal, comunidad, aliados, profesionales e inversores. |
| Nivel de detalle | Estratégico y funcional de alto nivel. No reemplaza especificaciones técnicas, documentos legales ni documentos ejecutables por módulo. |
| Fuente anterior | Documento Maestro Integral v1.1, 9 de agosto de 2026. |
| Gobierno | Toda decisión posterior que contradiga esta versión deberá quedar registrada, justificada y versionada. |
| Estado | Consolidado para REBASE-03B: VitaCora, responsabilidad, menores, erasure/privacidad, tutoriales, consentimiento versionado, publicidad UNDER_18 y Compromiso Comunidad LeoVer. Políticas de producto cerradas para diseño; revisión jurídica final antes de producción. |

### Cómo usar este documento

- Es la fuente superior para estrategia, alcance, principios y límites de LeoVer.
- D01 traduce esta visión en módulos, dependencias y orden de desarrollo; **D01 v1.3** es el mapa técnico vigente (alineado con Maestro v1.2).
- Cada módulo tendrá alcance, actores, estados, datos, seguridad, pruebas y entregables propios antes de implementarse.
- Cursor y otras herramientas de IA pueden proponer implementación, pero no gobiernan el producto ni crean reglas de negocio por sí solas.
- Las decisiones configurables se administran mediante catálogos y políticas; las reglas fundacionales de seguridad, privacidad, bienestar y gobierno no pueden desactivarse por configuración.
- Esta etapa de v1.2 es **gobierno**. No implica implementación de SQL, Android, Web, iOS ni APK.
### Contenido

| Secciones 0–12 | Secciones 13–25 |
| --- | --- |
| 0. Resumen ejecutivo 1. Identidad de la startup 2. Problema, oportunidad y contexto 3. Tesis estratégica y ventaja buscada (incluye 3.5–3.21: VitaCora, contexto, responsabilidad, grants, propuestas, guardería, media, tiempo, baseline, menores, tutoriales, legal/privacidad, Compromiso Comunidad) 4. Usuarios y grupos de interés 5. Propuesta de valor por segmento 6. Ecosistema de productos y aplicaciones 7. Catálogo integral de módulos 8. Experiencias y recorridos principales 9. Confianza, seguridad y bienestar animal 10. Modelo de negocio y monetización 11. Estrategia comercial y de lanzamiento 12. Competencia y diferenciación | 13. Marca y comunicación 14. Marco legal, privacidad y cumplimiento 15. Modelo operativo y organización 16. Visión tecnológica 17. Estrategia de datos y analítica 18. Inteligencia artificial 19. Roadmap integral del producto 20. Métricas, objetivos y experimentos 21. Modelo económico y financiación 22. Riesgos y mitigaciones 23. Gobierno del proyecto y documentación 24. Plan de transición y acción 25. Decisiones pendientes y estados |

## 0. Resumen ejecutivo

| Definición estratégica LeoVer será una plataforma multilateral con capas social, organizacional, profesional, comercial y de infraestructura, centrada en la identidad persistente de la mascota. |
| --- |

LeoVer es una startup de tecnología y bienestar animal que conecta en una sola red la identidad de la mascota, su historia, sus responsables, los procesos de ayuda, la comunidad y los servicios vinculados a su cuidado. La plataforma busca reducir la fragmentación que hoy obliga a repetir datos y coordinar por canales desconectados.

**VitaCora LeoVer** es la capa de vida, historia y cuidados de la mascota. No reemplaza documentación oficial ni registros profesionales; integra vistas autorizadas sobre dominios autoritativos y distingue información declarada, profesional, de tercero, verificada, inferida y de sistema. “Pasaporte” queda como terminología histórica/legacy de implementaciones previas; el baseline nuevo no crea conceptos `passport_*`.

| Elemento | Definición v1.2 |
| --- | --- |
| Entidad central | La mascota, con identidad persistente independiente de publicaciones, cuentas y casos. |
| Modelo | Plataforma multilateral: social, organizacional, profesional, comercial e infraestructura. |
| Puerta de entrada | Identidad preventiva, pérdidas/hallazgos, adopción, tránsito y red local de ayuda. |
| Confianza | Verificación proporcional, reputación basada en hechos, trazabilidad, privacidad y moderación. |
| Crecimiento | Web pública compartible, comunidad útil, densidad territorial y resultados reales. |
| Monetización | Suscripciones comerciales, Brand Studio y aportes voluntarios a LeoVer; funciones sociales esenciales gratuitas. |
| Donaciones a terceros | Directas al destinatario, 0% de comisión de LeoVer. |
| Métrica norte | Mascotas únicas con al menos un resultado de ayuda exitoso, confirmado, válido y no revertido por mes. |
| Piloto | Partidos de San Vicente y Almirante Brown, Provincia de Buenos Aires. |

### 0.1 Posicionamiento

Para personas, organizaciones y profesionales que necesitan proteger, encontrar, adoptar, cuidar o asistir animales de compañía, LeoVer será la red confiable que conserva contexto, identidad y trazabilidad. A diferencia de soluciones aisladas, conecta procesos sin perder la historia de la mascota.

### 0.2 Estrategia de ejecución

Diseñar el ecosistema completo desde el inicio no significa programarlo todo junto. Cada release debe resolver un conjunto de problemas de punta a punta, con seguridad, datos, administración, métricas y pruebas suficientes, evitando decisiones locales que bloqueen capacidades futuras.

## 1. Identidad de la startup

### 1.1 Nombre y marca

Nombre público: LeoVer. La capitalización oficial de marca es “LeoVer”. La marca denominativa fue presentada/registrada por el proyecto en las clases 9, 42 y 45; antes de realizar afirmaciones públicas sobre estado registral deberá verificarse el estado/certificado vigente. El registro de una marca mixta o del isotipo queda como opción futura y no bloquea el piloto.

### 1.2 Propósito

| Propósito Mejorar la vida de los animales conectando a las personas, organizaciones y profesionales que pueden protegerlos, encontrarlos, adoptarlos, cuidarlos y acompañarlos. |
| --- |

### 1.3 Visión

Convertirse en la comunidad y plataforma de bienestar animal confiable, donde cada mascota pueda contar con una identidad digital y una red de ayuda durante toda su vida.

### 1.4 Misión

Crear tecnología accesible que simplifique rescates, reencuentros, adopciones responsables, cuidados y contratación de servicios, generando confianza e información útil, con privacidad por diseño y sin fragmentar la experiencia.

### 1.5 Valores y principios

| Principio | Aplicación |
| --- | --- |
| Bienestar animal | La seguridad y el cuidado prevalecen sobre crecimiento, publicidad o conveniencia operativa. |
| Confianza | La identidad, actividad y hechos relevantes deben ser verificables cuando corresponda. |
| Comunidad | La plataforma facilita ayuda local y reconoce resultados útiles, no sólo popularidad. **Compromiso Comunidad LeoVer:** funciones comunitarias esenciales gratuitas para personas, rescatistas, refugios y ONG. |
| Inclusión | Funciones críticas simples, accesibles, comprensibles y utilizables en dispositivos y conexiones diversas. |
| Privacidad | Minimización, protección por defecto y permisos por finalidad. |
| Transparencia | Estados, reglas, publicidad, moderación y uso de datos deben ser comprensibles. |
| Ejecución responsable | Crecimiento por entregables sostenibles y medibles. |

### 1.6 Promesa de marca

| Promesa Cuando una mascota necesite ayuda, cuidado o un servicio, LeoVer permitirá encontrar y coordinar la información, las personas y las acciones adecuadas desde una misma red. |
| --- |

## 2. Problema, oportunidad y contexto

### 2.1 Problema estructural

| Problema central La información, los procesos y los actores vinculados con una mascota se encuentran fragmentados entre herramientas que no comparten identidad, contexto ni trazabilidad. Esto obliga a repetir datos, dificulta la coordinación, reduce la confianza y hace perder la historia de la mascota. |
| --- |

### 2.2 Problemas por actor

| Actor | Problema prioritario |
| --- | --- |
| Familias | Información dispersa, reacción tardía ante pérdidas, privacidad y baja continuidad de datos. |
| Adoptantes | Procesos variables, repetición de formularios, poca trazabilidad y estado incierto. |
| Rescatistas | Saturación de mensajes, coordinación manual, dificultad para gestionar casos, tránsito y necesidades. |
| Refugios/organizaciones | Animales bajo cuidado, equipos, postulaciones, campañas, necesidades y resultados en herramientas separadas. |
| Hogares de tránsito | Disponibilidad, compatibilidad, custodia temporal, gastos y seguimiento sin sistema común. |
| Voluntarios | Dificultad para encontrar necesidades concretas cercanas y coordinar acciones. |
| Profesionales | Visibilidad, agenda, operación, reputación e información autorizada fragmentadas. |
| Comercios/marcas | Canales poco contextualizados y dificultad para crear publicidad útil sin invadir privacidad. |
| Municipios | Potencial futuro de coordinación y datos agregados; no son dependencia de V1. |

### 2.3 Señales de oportunidad

- Existe una base social amplia de convivencia con animales y demanda creciente de cuidado, seguridad y servicios.
- El comportamiento actual ya demuestra uso intensivo de redes sociales, mensajería, mapas y formularios para resolver los mismos problemas que LeoVer busca conectar.
- La validación de mercado de LeoVer no se presume por tendencias generales: requiere entrevistas, pilotos, uso recurrente y resultados medibles.
- La digitalización de prestadores, profesionales y emprendimientos permite una capa comercial sin cobrar por la ayuda social esencial.
- La IA visual y la geolocalización pueden mejorar pérdidas/hallazgos cuando operan sobre datos estructurados y con confirmación humana.
## 3. Tesis estratégica y ventaja buscada

### 3.1 Tesis

La ventaja de LeoVer surgirá de conectar necesidades que normalmente se resuelven por separado mediante una identidad persistente de la mascota, permisos por finalidad, trazabilidad de hechos y densidad local de actores activos. La calidad y continuidad de la red importan más que la cantidad bruta de perfiles.

### 3.2 VitaCora LeoVer

**Nombre canónico de producto y de dominio:** VitaCora.
**Módulo:** M14 — VitaCora.

Vita = vida. Cora = corazón. El nombre evoca también una bitácora: una historia que acompaña a la mascota a lo largo de toda su vida.

Descriptor: **“Su vida. Su historia. Sus cuidados.”**

VitaCora **no es solamente salud**. Integra identidad, cuidados, salud compartible, servicios autorizados y Momentos personales. No es una tabla monolítica ni una historia clínica oficial.

VitaCora es una **composición**: presenta e integra datos de dominios autoritativos. No duplica la fuente de verdad. Ejemplo: una vacuna aceptada permanece en el dominio sanitario/profesional; VitaCora la muestra con proveniencia. No existen dos fuentes (`vaccination` + `vitacora_vaccination`).

M14 es autoridad sobre: composición de VitaCora, grants, accesos, decisiones de integración, visibilidad, proveniencia de integración y actualizaciones pendientes. El dato autoritativo permanece en el dominio que lo origina cuando corresponda.

“Pasaporte” / “Passport” es terminología histórica/legacy. El nuevo baseline no crea conceptos `passport_*`. Las migraciones históricas no se reescriben en esta etapa; eso se decide en REBASE-03.

| Capa | Regla |
| --- | --- |
| Identidad | Un registro autoritativo de mascota (M08) evita duplicación entre módulos. |
| Responsabilidad | OWNER personales múltiples u organización responsable; creador ≠ autoridad eterna. |
| Custodia | Tránsito, hallazgo o servicio pueden generar custodia temporal sin transferir responsabilidad. |
| Proveniencia | Declarado, profesional, tercero/prestador, verificado, inferido y sistema se identifican separadamente. |
| Permisos | Acceso por mascota, propósito, actor y tiempo; mínimo necesario; grant revocable. |
| Documentos oficiales | LeoVer no suplanta registros, certificados ni sistemas oficiales. |
| Salud | Dimensión esencial de VitaCora; no se comparte automáticamente con terceros. |
| Momentos personales | Dimensión emocional; privados por defecto; no se incluyen en grants de servicio. |

### 3.3 Efectos de red buscados

1. Más mascotas con identidad útil aumentan el valor preventivo y de búsqueda.
1. Más organizaciones y rescatistas activos aumentan capacidad de resolución local.
1. Más familias y vecinos aumentan avistamientos, difusión, tránsito y ayuda.
1. Más profesionales activos aumentan utilidad cotidiana y recurrencia.
1. Más resultados confirmados generan confianza y contenido compartible.
1. Más densidad local mejora tiempos de respuesta sin depender de popularidad global.
### 3.4 Métrica norte

| North Star Mascotas únicas que, durante el mes, obtienen al menos un resultado de ayuda exitoso, confirmado, válido y no revertido. Los distintos eventos de ayuda se medirán por separado para evitar inflar el indicador. |
| --- |

### 3.5 Identidad humana y contexto activo

Una persona tiene **una identidad humana**. Refugio, hogar de tránsito, veterinaria, perfil profesional, prestador o comercio **no** son nuevas cuentas humanas.

```text
IDENTIDAD PERSONAL
  → capacidades / memberships / perfiles de dominio
  → contexto activo
  → experiencia operativa adaptada
```

El cambio de contexto:

- no cambia `user_id`;
- no crea otra identidad;
- no concede permisos por sí mismo;
- **no depende de `account_type`**.

`account_type` **no forma parte del modelo canónico** de identidad/capacidades. Una persona = una identidad. Las cuentas adolescentes **no** son otro AccountType: **no** existen `AccountType.TEEN` ni `AccountType.MINOR`. Edad, protección y supervisión son dimensiones de la misma PERSON. Las capacidades derivan de identidad, memberships, ownership/responsibility, permisos de dominio, age/protection y consentimiento contextual, según corresponda. REBASE-03 deberá auditar y eliminar `account_type` / AppMode legacy si ya no poseen dependencia funcional legítima. ActiveContext no depende de `account_type` ni de la franja etaria.

Age/protection **no es ActiveContext**. No se muestra “Usar LeoVer como Adolescente”. El adolescente usa Persona u otros contextos permitidos; las protecciones actúan de forma transversal.

El contexto solo puede activarse cuando existen ownership, membership o permisos reales. El contexto es traducción de una identidad única a una experiencia operativa; no es una identidad nueva.

La UI puede llamarlo **“Cambiar perfil”** o **“Usar LeoVer como”**. No se expone el término técnico ActiveContext al usuario.

**Contexto único de organización:** una organización aparece **una sola vez**. No se muestran “Mundo Mascota — Veterinaria”, “Mundo Mascota — Guardería” como identidades separadas. Se muestra **Mundo Mascota** y, dentro de su contexto, las áreas/capacidades correspondientes. ActiveContext representa la entidad operativa, no cada servicio como identidad nueva.

Una organización puede operar **múltiples actividades** (veterinaria, guardería, peluquería, otros). `organization.type` no es autoridad ni limita la entidad a una sola capacidad. Las capacidades reales derivan de perfiles de dominio, servicios, habilitaciones y membresías/permisos.

Autoridad de implementación: ADR-016, elevado a gobierno canónico en esta versión.

### 3.6 Responsabilidad de la mascota

Separar siempre:

- `created_by_user_id` = persona que creó originalmente la mascota (auditoría/proveniencia; **no** es autoridad eterna);
- responsable(s) actuales;
- autorizado(s);
- organización responsable, si aplica;
- custodio temporal;
- profesional que produjo información;
- `responsible_entity` + `actor_user_id` en toda decisión.

Al crear una mascota personalmente, el creador recibe inicialmente relación OWNER, pero ambos conceptos permanecen separados.

#### Responsabilidad personal múltiple

Pueden existir **uno o varios responsables OWNER** simultáneamente.

Ejemplo:

| Concepto | Valor |
| --- | --- |
| created_by | Verónica |
| owners actuales | Verónica, Leo |
| authorized | Carolina |

El responsable puede vincular personas como familia. Relaciones conceptuales: **OWNER** y **AUTHORIZED** (u nomenclatura técnica equivalente). No depender exclusivamente del rol nominal. Autoridad real mediante permisos granulares, por ejemplo:

`pet.view` · `pet.edit` · `vitacora.view` · `vitacora.manage` · `vitacora.share` · `health.manage_declared` · `services.authorize` · `privacy.manage` · `responsibility.manage`

Puede haber varios OWNER con capacidad equivalente.

Una persona adolescente puede ser OWNER o AUTHORIZED. Eso **no** equivale a capacidad jurídica/adulta. `PET PERMISSION ≠ AGE/LEGAL CAPABILITY`.

**Adulto responsable ≠ responsable de mascota.** La relación MINOR PERSON ↔ ADULT PERSON es independiente de M08. Ejemplo: Carolina es adulto responsable de Mateo; Mateo es OWNER de Toby; Carolina **no** se convierte automáticamente en OWNER de Toby. La responsabilidad de mascota se asigna explícitamente.

#### Responsabilidad organizacional

Para mascota bajo responsabilidad organizacional:

**responsable = ORGANIZATION**, no los miembros individuales.

Ejemplo: responsable = Refugio Patitas; `created_by` = Juan. Juan es actor/proveniencia. Los humanos actúan en nombre de la organización mediante membership + permisos (incluida `vitacora.manage`).

Si Juan deja la organización, Luna continúa bajo Refugio Patitas. **No se crea una mascota paralela “de refugio”.** La identidad M08 persiste.

#### Cambio de responsabilidad

La mascota mantiene la misma identidad M08 y la misma VitaCora. Ejemplo: Refugio → adopción → nueva familia. La historia estructurada continúa.

**No** se transfiere automáticamente: notas internas, mensajes privados, datos privados de miembros, contactos internos, contenido marcado privado ni secretos administrativos.

### 3.7 Custodia temporal

Tránsito, guardería, cuidador u otro servicio pueden generar **custodia temporal**.

La custodia temporal:

- no transfiere ownership ni responsabilidad principal;
- no duplica la mascota;
- tiene inicio y fin;
- tiene propósito;
- tiene actor o entidad custodio;
- es trazable.

Ejemplo: responsabilidad principal Refugio Patitas; custodia temporal Verónica — Hogar de tránsito. La finalización del tránsito cierra la custodia y no altera la identidad de Luna.

### 3.8 Compartir VitaCora con servicios (VitaCora Access Grant)

El responsable decide si comparte información con otro actor. El acceso **no es una copia** de VitaCora: es una autorización sobre la identidad viva.

Aplica como mínimo a veterinarias, veterinarios independientes, paseadores, cuidadores, adiestradores, guarderías, transportistas y otros prestadores autorizados.

Concepto formal: **VITACORA ACCESS GRANT**. El baseline nuevo **no** crea `passport_*`.

Campos conceptuales: mascota, grantor/responsable, recipient actor/entity, scope, purpose, created_at, duración, status/revocación, proveniencia.

El responsable consulta: Mascota → VitaCora → Accesos compartidos.

Ningún cliente puede autoconcederse acceso. Ningún provider puede ampliarse el scope. Los permisos sobreviven de forma segura al refresh de sesión.

#### Scopes visibles para SERVICIOS

Se elimina el modelo genérico anterior (NO COMPARTIR / DATOS BÁSICOS / PERSONALIZADO / PASAPORTE COMPLETO). Para servicios:

| Alcance | Significado |
| --- | --- |
| NO COMPARTIR | Abre contacto/conversación sin grant de VitaCora. |
| DATOS ESENCIALES | Identificación funcional: nombre, foto, especie, raza, sexo, edad, tamaño y otros datos básicos aplicables. |
| SALUD | Información sanitaria compartible: alergias, medicación, condiciones relevantes, vacunas, desparasitación, antiparasitarios, necesidades sanitarias aplicables. |
| DATOS ESENCIALES + SALUD | Ambos grupos. |
| VITACORA COMPLETA | Toda la información **funcional compartible** permitida para esa relación. |

Salud es una dimensión **esencial/estructural** de VitaCora. Eso **no** significa que todo prestador reciba Salud obligatoriamente. Compartir Salud requiere el alcance elegido por el responsable, salvo obligaciones de seguridad específicas que en el futuro deban definirse contractualmente.

**VITACORA COMPLETA no incluye automáticamente:** Momentos personales privados, mensajes, auditoría, IDs internos, secretos, datos protegidos de terceros ni contenido explícitamente privado.

#### Duración del acceso

La UX ofrece exactamente:

- **DAR ACCESO HASTA…** — el responsable selecciona una fecha (`UNTIL_DATE`).
- **DAR ACCESO POR TIEMPO INDETERMINADO** — activo hasta revocación (`INDEFINITE`).
- **QUITAR ACCESO** — revocación inmediata (`REVOKED`).

No se hardcodean períodos arbitrarios.

#### Datos de reserva ≠ grant de VitaCora

Separar:

- **A.** información mínima necesaria para identificar y ejecutar una reserva/servicio;
- **B.** acceso adicional a VitaCora.

“No compartir VitaCora” **no** significa que el prestador no sepa qué mascota tiene reservada.

La reserva/estadía debe conservar un **snapshot** de instrucciones y consentimientos relevantes aceptados para esa relación (alimentación, medicación indicada, consentimiento público, scope acordado, instrucciones especiales). No copiar VitaCora completa. VitaCora compartida puede seguir siendo información viva.

### 3.9 Contacto y mensajería LeoVer

Recorrido de primer contacto:

Persona → Comunidad → Prestador → Contactar → elegir mascota → decidir si comparte VitaCora → elegir alcance y duración → iniciar Mensajes LeoVer.

La conversación inicial ocurre **dentro de LeoVer**. No se obliga a WhatsApp. Las partes pueden intercambiar después sus propios datos de contacto por decisión mutua.

**Mensajería institucional:** contactar Happy Pets crea una conversación con la **entidad** Happy Pets, no una conversación privada con el empleado que la atiende. Los miembros autorizados pueden responder. Cada mensaje/respuesta institucional conserva `actor_user_id` para auditoría. Si María deja Happy Pets, la conversación permanece en Happy Pets.

**M20 se reutiliza.** No se crea otro chat exclusivo para servicios.

**Seguridad de menores en M20:** restricciones de mensajes de desconocidos; contacto institucional seguro; block/report; auditoría; minimización de teléfono/email expuesto. El adulto responsable **no** obtiene automáticamente acceso a todas las conversaciones privadas del adolescente. Una supervisión de mensajes requiere definición jurídica/producto específica.

### 3.10 Visualización, propuestas y decisión del responsable

Compartir VitaCora para **lectura no concede modificación directa**. Los terceros no editan arbitrariamente la fuente autoritativa.

```text
TERCERO → propone actualización
RESPONSABLE AUTORIZADO REVISA → acepta / descarta / solicita corrección
si acepta → el hecho se persiste o actualiza en el dominio autoritativo correcto
y VitaCora lo integra con proveniencia
```

Concepto formal: **VITACORA UPDATE PROPOSAL**. El baseline nuevo no crea `passport_*`.

Estados conceptuales: PENDING, ACCEPTED, REJECTED, CORRECTION_REQUESTED, CANCELLED si fuera necesario.

Debe registrar: mascota, proponente, rol/capacidad, organización/establecimiento si corresponde, tipo de información, payload, evidencia/documentos si aplica, fecha, decisión, actor que decide y proveniencia final (actor, entidad, profesional cuando aplique, fecha, procedencia).

Una propuesta rechazada no modifica VitaCora ni el dominio autoritativo. Una aceptada mantiene identificado quién la originó.

**El dueño/responsable decide.** No hay autoaceptación por defecto en V1.

#### Matriz conceptual de propuestas según servicio

Las capacidades se validan por **autoridad real**, no por AccountType.

| Prestador | Puede proponer | No puede |
| --- | --- | --- |
| Paseador | actividad, comportamiento observado, incidente, foto, observación general | diagnóstico, vacuna profesional, procedimiento clínico |
| Cuidador / guardería | alimentación realizada, actividad, descanso, comportamiento, administración de medicación según instrucción existente, incidencias, fotos, observaciones de estadía | prescripción clínica, diagnóstico |
| Adiestrador | observaciones conductuales, objetivos, progreso, ejercicios, recomendaciones de entrenamiento | diagnóstico veterinario, salvo que el mismo actor posea además capacidad profesional y opere en ese contexto |
| Transportista | información pertinente a traslado, custodia o incidente | datos clínicos no pertinentes |
| Veterinario / veterinaria | peso, atención, vacunas, desparasitación, antiparasitarios, procedimientos permitidos, indicaciones, documentos, próximos controles y otras áreas sanitarias habilitadas | firmar como profesional si el actor es dueño administrativo no veterinario |

Un dueño administrativo **no veterinario** no puede firmar información profesional solo por ser OWNER/ADMIN de la clínica.

### 3.11 Proveniencia y Momentos personales

Toda información incorporada desde un tercero debe mantener su origen. Ejemplos: “Juan Pérez · Paseador” o “Dra. Ana Pérez · Veterinaria San Martín” con fecha.

Distinguir: DECLARADO POR RESPONSABLE, PROFESIONAL, TERCERO/PRESTADOR, VERIFICADO, INFERIDO, SISTEMA.

**Momentos personales** forman parte de VitaCora: llegada a casa, cumpleaños, recuerdos, fotografías, viajes, primeros momentos, hitos importantes y acontecimientos personales. Se distinguen de salud, información profesional, servicios y registros operativos. Son **PRIVADOS POR DEFECTO** y no se comparten automáticamente con prestadores.

### 3.12 Veterinaria — tres entidades distintas

```text
Persona humana  ≠  profesional veterinario  ≠  establecimiento veterinario
```

| Capa | Autoridad |
| --- | --- |
| Organización / establecimiento | M03 |
| Operación / profesional sanitaria autorizada | M28 |
| Descubrimiento / oferta de servicios | M22 (sin crear una veterinaria duplicada) |
| Agenda / reservas | M12 / M23 reconciliadas |

Una clínica conserva sus registros operativos. Un profesional conserva autoría. LeoVer refleja información seleccionada y autorizada en VitaCora, pero **no reemplaza** la historia clínica oficial.

### 3.13 Guardería — modelo de producto

Guardería es una **categoría especializada de prestador M22/M23**. No se crea un módulo nuevo.

Navegación objetivo del contexto guardería:

Inicio | Reservas | + Publicar | Huéspedes | Perfil

La reserva contiene conceptualmente: mascota, responsable, servicio, ingreso previsto, retiro previsto, instrucciones, grant de VitaCora (si existe), snapshot de instrucciones/consentimientos, alimentación, medicación autorizada, contacto de emergencia autorizado y notas necesarias. Los estados son coherentes con M23.

Al check-in, una reserva confirmada se convierte en huésped activo y **comienza custodia temporal**. Se registran hora de ingreso, actor que recibe, condición inicial, pertenencias, alimento, instrucciones, medicación y observaciones. La mascota sigue respondiendo a su responsable original.

**Huéspedes** son mascotas actualmente bajo custodia del establecimiento. No son mascotas propiedad de la guardería.

Separar:

- **A.** acceso operativo privado;
- **B.** visibilidad/publicación pública;
- **C.** guardar estadía en VitaCora.

Visibilidad pública: **DEFAULT = NO**. El responsable puede revocarla. Si revoca: no se permiten nuevas publicaciones; LeoVer retira u oculta contenido público dependiente de ese consentimiento cuando esté bajo control de LeoVer; el contenido privado de la estadía permanece según permisos/retención. Una publicación pública debe poder relacionarse con el consentimiento que la habilitó.

La guardería puede registrar “medicación administrada” cuando existe instrucción o autorización válida. No modifica la prescripción ni crea un diagnóstico profesional.

Los incidentes quedan en la estadía, alertan al responsable cuando corresponda, pueden originar propuesta a VitaCora y mantienen actor/proveniencia. Una observación no se convierte en diagnóstico clínico.

El check-out registra hora real de retiro, actor, resumen, alimentación/medicación, incidentes y fotos/observaciones permitidas. Termina la custodia temporal. La estadía queda en historial.

Reservas, clientes, estadías y relación operativa pertenecen al **provider/organización**, no al empleado. Si el empleado abandona la organización, el historial permanece.

### 3.14 Recorrido canónico de servicios

```text
DESCUBRIMIENTO
→ CONTACTO
→ MASCOTA
→ VITACORA COMPARTIDA OPCIONAL
→ MENSAJERÍA LEOVER
→ RESERVA / RELACIÓN
→ SERVICIO / CUSTODIA
→ PROPUESTA DE INFORMACIÓN
→ DECISIÓN DEL RESPONSABLE
→ HECHO EN DOMINIO AUTORITATIVO + INTEGRACIÓN EN VITACORA
→ REPUTACIÓN SOBRE INTERACCIÓN REAL
```

Este recorrido se reutiliza para veterinaria, paseador, cuidador, guardería, adiestrador, transporte y otros prestadores compatibles.

### 3.15 Privacidad, “eliminar” y archivos

Refuerzo de sharing: mínimo necesario; finalidad específica; acceso revocable; ubicación precisa protegida; contacto privado por defecto; autorización explícita; trazabilidad; enforcement RLS/backend; ningún cliente se autoconcede acceso; ningún provider se amplía el scope; los permisos sobreviven al refresh de sesión; cierre o revocación corta el acceso.

**Cuenta Adolescente — defaults:** perfil privado; VitaCora privada; minimización de información visible; ubicación precisa nunca pública; controles reforzados de descubrimiento e interacción; block/report; moderación reforzada; protección frente a contacto adulto inapropiado. No se usan dark patterns para relajar protecciones. Omitir un tutorial **no** relaja estas reglas.

**PRODUCT DELETE ≠ PRIVACY ERASURE.**

`DELETE_IS_NON_DESTRUCTIVE = YES` se mantiene para operaciones normales de producto/negocio (eliminar de VitaCora, ocultar una estadía, cerrar una relación, quitar contenido, cerrar una mascota, revocar un acceso). Internamente: hide / archive / soft-delete / revoke / deactivate, según dominio.

La solicitud legal de supresión de **datos personales** es otro flujo. `PRIVACY_ERASURE_POLICY = DEFINED_PRELAUNCH` (`REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES`).

Cuando una persona ejerce su derecho de supresión/eliminación de cuenta, LeoVer debe poder: eliminar datos personales; anonimizar irreversiblemente; restringir/bloquear información conservada; conservar únicamente lo necesario cuando exista justificación legítima, obligación aplicable, seguridad, prevención de abuso/fraude, defensa de derechos o integridad de registros legítimos de terceros. Siempre **DATA MINIMIZATION**. No conservar datos personales “por si acaso”.

El baseline futuro debe soportar `privacy_requests` conceptuales: ACCESS / RECTIFICATION / UPDATE / ERASURE, con estados y auditoría. No se implementa en esta etapa.

**Registros de terceros.** Si un usuario elimina su cuenta, **no** puede destruir arbitrariamente registros autoritativos legítimos de veterinarias, refugios/ONG, guarderías, prestadores, organizaciones u otros actores. Distinguir BUSINESS RECORD RETENTION vs PERSONAL DATA RETENTION. Ejemplo: una consulta veterinaria puede permanecer como registro profesional/operativo; los datos personales del usuario se reducen, anonimizan o conservan solo con necesidad legítima.

**Archivos / media:** una única autoridad transversal. Evitar que cada módulo invente su propia semántica de `photoUrl`/path.

```text
asset / storage object → metadata estable → referencia desde dominio
```

Las signed URLs son mecanismos temporales de acceso/render. **No** son la identidad persistida del archivo. REBASE-03 determinará si M05 existente puede ser esta autoridad canónica.

### 3.16 Fechas, tiempo y baseline REBASE-03

Convención canónica:

| Tipo | Modelo |
| --- | --- |
| Fecha sin hora (nacimiento, vacuna, desparasitación) | SQL `date` / `LocalDate` |
| Hechos temporales (mensaje, check-in, check-out, acciones) | `timestamptz` como instante UTC |
| Agenda / reservas | preservar instante y zona horaria aplicable |

No hardcodear GMT-3 como supuesto global.

**Estrategia de backend para REBASE-03** (no se ejecuta en esta tarea documental):

- reconstrucción canónica preproducción;
- no preservar compatibilidad innecesaria;
- baseline limpio alineado a Master v1.2 / D01 v1.3;
- migraciones anteriores preservadas como historia, no necesariamente reproducidas en el nuevo baseline;
- no destruir primero el backend actual;
- evaluar nuevo Supabase limpio para staging/canonical;
- producción futura separada de staging.

### 3.17 Tienda / marketplace

Tienda transaccional, marketplace y checkout permanecen **fuera de V1**. M25 histórico no habilita checkout en producto. Tienda/marketplace se analizará para V2. Esta versión de gobierno **no** desarrolla Tienda.

### 3.18 Cuentas adolescentes y seguridad de menores

LeoVer soporta cuentas de adolescentes **sin** crear otra identidad humana ni un módulo “Teen Accounts”.

#### Franjas operativas de producto

| Banda | Cuenta |
| --- | --- |
| UNDER_13 | **No cuenta autónoma.** Un menor de 13 no opera una cuenta personal LeoVer. No se diseñan perfiles infantiles paralelos sin necesidad futura explícita. Puede, en el futuro, estar representado mediante un adulto responsable; eso no es autonomía. |
| 13_15 | Cuenta Adolescente LeoVer: protecciones reforzadas; adulto responsable vinculado; asentimiento/aceptación del adolescente en lenguaje adecuado; confirmación del adulto para creación/activación según flujo legal final. |
| 16_17 | Cuenta Adolescente LeoVer: mayor autonomía operativa; mantiene protecciones de menor. Acciones jurídicas, económicas o especialmente sensibles pueden requerir confirmación del adulto responsable. |
| 18_PLUS | Cuenta adulta estándar. Al cumplir 18: misma PERSON, mismo `user_id`, mismas mascotas, misma VitaCora, mismo historial; solo cambian age band, protection state y capabilities. |

`LEGAL_MINOR_CONSENT_POLICY = DEFINED_PRELAUNCH`. Es política canónica de producto para diseño técnico. `REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES`. **No** se presenta como ley vigente.

#### Edad, assurance y transición

El estado técnico actual **no** posee `birth_date` de persona. El baseline canónico deberá determinar la franja etaria sin crear otra identidad, con minimización de datos:

fecha de nacimiento o dato equivalente → age band derivado → protection state → age assurance state → mecanismo de verificación extensible.

Age assurance conceptual (nombres físicos no fijados): SELF_DECLARED → ACCOUNT_CONFIRMED → DOCUMENT_VERIFIED (o equivalente). **No** exigir DNI, foto de DNI, selfie documental ni documentación de tutela solo por crear Cuenta Adolescente. La verificación puede evolucionar sin reemplazar la cuenta.

Al cumplir años **no se recrea** la cuenta: misma PERSON, mismo `user_id`, mismas mascotas, misma VitaCora, mismo historial, mismas memberships vigentes. Solo cambian age band, protection state y capabilities aplicables.

#### Adulto responsable

`GUARDIAN_VERIFICATION_POLICY = DEFINED_FOR_V1_PRELAUNCH` (`REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES`).

Relación independiente MINOR PERSON ↔ ADULT PERSON. En V1 el Adulto Responsable debe ser: PERSON LeoVer; 18+; cuenta activa; email/cuenta verificada; vínculo aceptado bilateralmente.

Flujos posibles: (A) el adolescente invita al adulto → el adulto inicia sesión y acepta; (B) el adulto invita al adolescente → el adolescente confirma.

Registro conceptual (nombres físicos no fijados): `minor_user_id`, `adult_user_id`, `requested_by`, `accepted_at`, `ended_at`, `verification_method`, `status`, actor de auditoría.

**No** exigir DNI/foto de DNI/selfie documental/documentación de tutela por defecto. El modelo es extensible a verificación más fuerte si una exigencia jurídica o acción sensible lo requiere.

**UX:** “Adulto responsable”. **No** presentar “tutor legal verificado” si LeoVer no verificó legalmente esa condición. El vínculo registra un nivel/método de verificación sin afirmar más de lo comprobado.

Esa relación **no** es ownership de mascota (ver §3.6). Carolina = adulto responsable de Mateo; Mateo = OWNER de Toby; Carolina **no** se convierte automáticamente en OWNER de Toby. M08 sigue siendo autoridad de responsabilidad de mascota.

**El Adulto Responsable no ve todo.** El vínculo **no** otorga acceso automático a mensajes privados, VitaCora, mascotas, contenido, contactos ni historial privado del adolescente. Habilita únicamente protección, autorización, consentimiento y seguridad expresamente definidas. Cualquier supervisión adicional requiere fundamento y diseño explícito. **No** vigilancia total por defecto.

#### Evaluación de acciones sensibles

Antes de una acción sensible el backend (RLS/RPC) debe poder evaluar:

```text
IDENTITY + PET/ORG PERMISSION + AGE PROTECTION/CAPABILITY + CONTEXTUAL CONSENT
```

La UI **no** concede autoridad.

Acciones previstas para evaluación reforzada: FULL_SHAREABLE VitaCora; información especialmente sensible; contratación/pago; transferir responsabilidad de mascota; crear/administrar organizaciones; activar actividad profesional/comercial; modificar protecciones relevantes; consentimiento de publicación pública; operaciones económicas/donaciones; exposición de ubicación precisa.

La matriz exacta de cada acción 13–15 / 16–17 / 18+ se cierra con la revisión jurídica final previa a producción. El modelo de evaluación (identidad + permiso + age capability + consentimiento contextual) ya es canónico para REBASE-03B.

#### Ubicación adolescente

`precise location = PROTECTED`. Nunca pública directamente. Lost/Found puede usarla solo según finalidad y permisos. Evitar exposición innecesaria de domicilio, escuela, rutina, ubicación en tiempo real y lugares frecuentes.

#### Publicidad

`MINOR_ADVERTISING_POLICY = NO_SPONSORED_ADS_UNDER_18_V1`.

Cuentas UNDER_18: **no** reciben contenido patrocinado distribuido por M29/Brand Studio. **No** targeting publicitario personalizado. **No** segmentación comercial basada en VitaCora, salud, ubicación precisa, comportamiento sensible, inferencias sensibles ni datos privados.

Sí pueden ver contenido **orgánico** legítimo de refugios, veterinarias, prestadores, organizaciones y comercios, según reglas normales de comunidad. `SPONSORED DISTRIBUTION TO MINORS = NO` en V1.

#### Tutorial TEEN_ACCOUNT_TUTORIAL

Contextual; SKIPPABLE = YES; REOPENABLE = YES. Explica en lenguaje simple: privacidad, seguridad, contactos, adulto responsable, restricciones adicionales y por qué algunas acciones pueden requerir intervención adulta. Forma parte del modelo general de tutoriales (§3.19). Tutorial **≠** consentimiento legal.

#### Consentimientos de menores

`LEGAL_MINOR_CONSENT_POLICY = DEFINED_PRELAUNCH`. El baseline debe registrar documento, versión, persona afectada, persona que acepta, rol/capacidad, timestamp, evidencia, revocación/withdrawal y cambio de versión. Prever MINOR_ASSENT y GUARDIAN_CONSENT cuando corresponda. Distinguir persona que acepta para sí vs adulto que actúa respecto de un adolescente.

| Política | Estado de producto | Revisión jurídica |
| --- | --- | --- |
| PRIVACY_ERASURE_POLICY | DEFINED_PRELAUNCH | REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES |
| LEGAL_MINOR_CONSENT_POLICY | DEFINED_PRELAUNCH | REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES |
| GUARDIAN_VERIFICATION_POLICY | DEFINED_FOR_V1_PRELAUNCH | REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES |
| MINOR_ADVERTISING_POLICY | NO_SPONSORED_ADS_UNDER_18_V1 | REQUIRES_FINAL_LEGAL_REVIEW_BEFORE_PRODUCTION = YES |

DEFINED_PRELAUNCH = decisión de producto/arquitectura cerrada para diseño técnico (REBASE-03B). No es texto legal publicado ni ley vigente.

### 3.19 Tutoriales

LeoVer tendrá: (A) onboarding inicial común; (B) tutoriales contextuales por función/capacidad.

Ejemplos: Conociendo LeoVer; VitaCora; Compartir VitaCora; Familia; Perdidos/Encontrados; Adopción; Refugio/ONG; Rescatista; Tránsito; Veterinaria; Guardería; Prestadores; Cuenta Adolescente.

Todos: `SKIPPABLE = YES` · `REOPENABLE = YES`.

Omitir un tutorial: **no** bloquea funcionalidad, **no** concede permisos, **no** acepta términos, **no** genera consentimiento y **no** modifica privacidad. Tutorial **≠** consentimiento legal.

El futuro schema puede recordar por usuario, de forma independiente por tutorial: key, version, viewed, skipped, completed, timestamps. **No** un mega-estado global. No se implementa en esta etapa.

**VitaCora en onboarding.** Tutorial “VitaCora LeoVer”. Descriptor: “Su vida. Su historia. Sus cuidados.” Vita = vida; Cora = corazón; el nombre evoca además una bitácora: la historia que acompaña a una mascota durante toda su vida. Se enseña una vez y queda disponible en Ayuda / Tutoriales / ¿Qué es VitaCora? **No** repetirlo de forma invasiva en todas las pantallas.

### 3.20 Documentos legales, consentimientos y lanzamiento

Separar conceptualmente:

| Tipo | Qué es |
| --- | --- |
| CONTRACTUAL ACCEPTANCE | Términos y Condiciones. |
| PRIVACY INFORMATION / DATA PROCESSING BASIS | Política de Privacidad. |
| COMMUNITY RULES | Normas de Comunidad. |
| CONTEXTUAL CONSENT | VitaCora, ubicación, publicación, guardería, IA específica, cookies no esenciales, etc. |

**No** un único boolean `accepted = true`.

El baseline futuro debe soportar:

- **LEGAL DOCUMENT:** type, version, locale, effective date, content hash, status.
- **LEGAL / CONSENT EVENT:** subject user, actor user, document/version, action, timestamp, source, evidence, withdrawal/revocation cuando aplique. Distinguir aceptación para sí vs adulto actuando respecto de adolescente.
- **privacy_requests:** ACCESS / RECTIFICATION / UPDATE / ERASURE, con estados y auditoría.

**Alta de cuenta (conceptual):** identificación básica; fecha de nacimiento o determinación etaria; aceptación de Términos; acceso/conocimiento de Política de Privacidad; aceptación de Normas de Comunidad cuando corresponda; flujo adicional de Cuenta Adolescente cuando aplique. **No** pedir por adelantado consentimientos de funcionalidades que aún no se usan.

**Consentimiento just-in-time.** Los consentimientos específicos se solicitan cuando aparece la funcionalidad (compartir VitaCora, ubicación precisa, publicación pública, guardería, operaciones sensibles de menores, IA que requiera dato específico, cookies no esenciales web, otros tratamientos opcionales). **No** “acepto todo”.

**No marketing consent en el alta.** LeoVer **no** planea hoy newsletters, promociones, beneficios comerciales ni marketing directo. **No** incluir checkbox de marketing. No recopilar consentimiento para una finalidad inexistente. Las comunicaciones operativas (seguridad, verificación, recuperación, reservas, mensajes, cambios importantes de términos, avisos de privacidad) son funcionales del servicio y se documentan **separadas** del marketing.

**Estado de documentos:** DRAFT PRE-LAUNCH. COMUNIDAPP S.A.S. aún no está constituida. No presentar CUIT, domicilio, emails legales ni datos societarios inexistentes como definitivos.

`LEGAL_LAUNCH_GATE`: antes de abrir registro público real deberán existir entidad/responsable identificable; Términos vigentes; Política de Privacidad vigente; Normas de Comunidad; mecanismos de derechos de datos; matriz de consentimientos; política de menores final revisada; información de proveedores/subprocesadores cuando corresponda; revisión jurídica final.

### 3.21 Compromiso Comunidad LeoVer

Principio permanente de producto:

> LeoVer es y seguirá siendo gratuito para las funciones esenciales destinadas a personas, rescatistas, refugios y ONG en materia de identidad, bienestar, rescate, tránsito, adopción y participación comunitaria.

Cubre, como mínimo: identidad personal; perfiles de mascota; VitaCora esencial; familia/responsables; adopciones; perdidos/encontrados; rescate; tránsito; herramientas esenciales de refugios/ONG; participación comunitaria; comunicación comunitaria esencial; acceso a funciones de bienestar animal definidas como esenciales. **No** imponer subscription/entitlement pago para desbloquearlas.

El compromiso **no** impide monetizar herramientas profesionales, comerciales, Brand Studio, publicidad, capacidades empresariales avanzadas, IA comercial, suscripciones de prestadores/profesionales ni futuros servicios comerciales. Esas capas permanecen separadas de las herramientas comunitarias esenciales.

**Rescatista** = PERSON + capacidades/actividad contextual. **No** AccountType. **No** cuenta separada. **No** subscription obligatoria para funciones esenciales de rescate. Puede tener tutorial/contexto funcional sin crear otra identidad.

**Refugio / ONG** = organization + memberships + permissions. Herramientas esenciales de operación comunitaria **sin** subscription obligatoria. Capacidades comerciales adicionales futuras, si existieran, permanecen separadas.

**Donaciones a terceros:** alias/CBU directo al destinatario; comisión LeoVer = 0%. **No** convertir el apoyo comunitario en checkout obligatorio.

## 4. Usuarios y grupos de interés

LeoVer usa una identidad personal única. La persona no queda encerrada en un “rol” permanente: puede activar capacidades, pertenencias y acciones según contexto. Las organizaciones son entidades independientes con equipos y permisos. El contexto activo adapta la experiencia operativa; no crea otra cuenta ni concede permisos por sí mismo. Una Cuenta Adolescente es la misma PERSON con protecciones transversales; no es un contexto ni un AccountType. Ver §3.5, §3.18 y ADR-016.

### 4.1 Segmentos

| Segmento | Necesidad principal | Papel en la red |
| --- | --- | --- |
| Familias/responsables | Identidad, prevención, ayuda, privacidad y servicios. | Demanda, contenido, prevención. |
| Personas sin mascota | Ayudar, adoptar, transitar, difundir o participar. | Crecimiento y capacidad comunitaria. |
| Rescatistas independientes | Gestionar casos, necesidades, tránsito y adopciones. | Oferta social y conocimiento local. |
| Refugios/ONG/fundaciones | Gestionar animales bajo cuidado, equipos, campañas y resultados. | Capacidad institucional. |
| Hogares de tránsito | Declarar capacidad, compatibilidad, disponibilidad y seguimiento. | Custodia temporal. |
| Voluntarios | Ofrecer tiempo, transporte, difusión u otras capacidades. | Capacidad operativa. |
| Veterinarios/centros | Gestionar pacientes, agenda, atención y relación autorizada con VitaCora. | Oferta profesional. |
| Prestadores | Paseo, cuidado, guardería, transporte, educación y servicios. | Oferta de servicios. |
| Tiendas/emprendimientos | Productos y promociones dentro de reglas comerciales. | Oferta comercial. |
| Marcas/anunciantes | Campañas útiles y medibles mediante Brand Studio. | Financiación comercial. |
| Municipios/organismos | Posible coordinación y datos agregados en etapas futuras. | Alianzas futuras. |

### 4.2 Capacidades personales

- Interés en adopción.
- Disponibilidad para tránsito.
- Voluntariado.
- Transporte.
- Difusión.
- Colaboración en eventos.
- Pertenencia a una organización.
- Otras capacidades configurables por catálogo.
### 4.3 Onboarding

El onboarding será breve, opcional y orientado a intención, no a roles. Después de una introducción corta preguntará “¿Qué querés hacer primero?” y ofrecerá registrar mascota, reportar pérdida, informar hallazgo, adoptar, transitar, sumarse a una organización, ofrecer ayuda/servicio o explorar. Los permisos del dispositivo se solicitarán de manera contextual y siempre habrá salida/atrás.

El alta prevé identificación básica, determinación etaria, Términos, Política de Privacidad, Normas de Comunidad cuando corresponda y flujo de Cuenta Adolescente si aplica. **No** checkbox de marketing. Tutoriales skippables/reopenables, incluido VitaCora (“Su vida. Su historia. Sus cuidados.”). Ver §3.19–§3.20.

## 5. Propuesta de valor por segmento

| Segmento | Propuesta de valor | Prueba de valor |
| --- | --- | --- |
| Familias | Identidad persistente, prevención, privacidad y red de ayuda. | Uso recurrente, VitaCora útiles, reencuentros. |
| Adoptantes | Postulación reutilizable, proceso trazable y decisión humana. | Postulaciones útiles y adopciones consolidadas. |
| Rescatistas | Menos carga administrativa y mejor coordinación de casos/necesidades. | Tiempo ahorrado, respuesta y casos cerrados. |
| Organizaciones | Gestión integrada de animales, casos, equipos y colaboración. | Uso operativo recurrente. |
| Tránsitos | Disponibilidad, compatibilidad, custodia y seguimiento claros. | Ingresos/salidas y continuidad. |
| Voluntarios | Necesidades concretas cercanas y coordinación. | Acciones completadas. |
| Veterinarias | Gestión de pacientes, atención, agenda, visibilidad y conexión autorizada a VitaCora. | Pacientes activos, atenciones, retención profesional. |
| Prestadores | Perfil, agenda, reputación y operación simple. | Reservas/servicios completados. |
| Comercios/marcas | Presencia y Brand Studio con creación, distribución y analítica. | Campañas activas y resultados atribuidos. |

### 5.1 Beneficio emocional

Reducir ansiedad, incertidumbre y sensación de desorden. La experiencia debe mostrar qué pasó, quién está actuando y cuál es el siguiente paso.

### 5.2 Beneficio funcional

Unificar identidad, información, comunicación, estados y acciones coordinadas. Los pagos entre terceros no forman parte del valor inicial de los flujos sociales y de servicios.

## 6. Ecosistema de productos y aplicaciones

| Superficie | Propósito | Prioridad |
| --- | --- | --- |
| Android | Cliente móvil principal para comunidad, mascota y flujos de campo. | Piloto |
| iOS | Cliente nativo con paridad en flujos centrales. | Piloto |
| Web pública | Páginas compartibles, SEO, perdidos/encontrados, adopciones, organizaciones y campañas. | Fundamental |
| Portal organizaciones | Gestión de equipos, animales, casos, tránsito, adopción y campañas. | Progresivo |
| Portal profesional/veterinario | Gestión de pacientes, agenda, atenciones, documentos y servicios. | Progresivo |
| Portal comercial / Brand Studio | Campañas, creatividades, segmentación permitida, distribución y analítica. | Después de base social/comercial |
| Consola administrativa | Configuración, verificación, moderación, soporte, riesgo, publicidad y auditoría. | Transversal |
| API/backend | Supabase como fuente autoritativa y contratos comunes. | Transversal |

### 6.1 Principios de superficie

- Web-first para compartir e indexar; mobile-first para recurrencia y acciones de campo.
- La web se desarrollará con Next.js + React + TypeScript sobre el mismo backend Supabase.
- Android continuará nativo en Kotlin/Jetpack Compose; iOS se desarrollará en Swift + SwiftUI para el piloto.
- Profesional y comercial son superficies distintas, aunque un mismo actor pueda tener ambas capacidades.
- Las funciones críticas no deberán depender de instalar una app cuando una página pública pueda resolver el primer contacto.
## 7. Catálogo integral de módulos

Este catálogo define capacidades del ecosistema, no el orden exacto de implementación. **D01 v1.3** traduce estas decisiones a módulos Mxx y releases técnicos preservando identificadores existentes en el repositorio.

### 7.1 Identidad, autenticación y cuentas

Registro, login, recuperación, verificación, username público obligatorio y único al alta, sesiones, preferencias, privacidad, age/protection, adulto responsable y eliminación. Una identidad humana con capacidades, no roles rígidos. Soporta versionado de documentos legales, consent events y privacy_requests (ACCESS / RECTIFICATION / UPDATE / ERASURE) como arquitectura transversal, no como módulo Legal/Consent/Teen nuevo. PRODUCT DELETE ≠ PRIVACY ERASURE.

### 7.2 Perfil personal y red de confianza

Datos públicos mínimos, capacidades, pertenencias, bloqueos, reputación, verificaciones y controles de privacidad.

### 7.3 Perfil de mascota y VitaCora LeoVer

Identidad autoritativa, responsables OWNER múltiples o organización responsable, autorizados, fotos, características, salud declarada, Momentos personales, documentos, QR, historial, privacidad y permisos. Microchip fuera de V1/piloto. `created_by` es proveniencia, no autoridad eterna. VitaCora se comparte con servicios por grant (NO COMPARTIR / DATOS ESENCIALES / SALUD / ESENCIALES + SALUD / VITACORA COMPLETA compartible). Las actualizaciones de terceros son propuestas; el responsable decide. Ver §3.6–§3.11.

### 7.4 Red social y contenido

Posts, historias, reels, fotos, videos, humor, ternura, educación, ayuda y resultados. Popularidad nunca determina urgencia o confianza. Respeta privacidad adolescente. Cuentas UNDER_18 no reciben distribución patrocinada M29.

### 7.5 Perdidos y encontrados

Alertas, mapas, zonas protegidas, avistamientos, difusión, estados, reencuentros y matching manual/visual progresivo.

### 7.6 Adopciones

Publicación, requisitos, perfil reutilizable, postulaciones, entrevistas, decisión humana, entrega, transferencia trazable y seguimiento.

### 7.7 Hogares de tránsito

Capacidad configurable, compatibilidad, disponibilidad, custodia temporal, gastos registrables y cierre.

### 7.8 Organizaciones, refugios y equipos

Entidad organización, sedes, animales bajo cuidado, miembros, equipos, permisos, casos, campañas, necesidades, auditoría y estados de verificación. Los animales bajo cuidado institucional pertenecen a la organización responsable, no al miembro que los registró. Una organización puede operar múltiples capacidades; `organization.type` no limita la entidad a una sola actividad. En “Cambiar perfil / Usar LeoVer como” la organización aparece una sola vez. Herramientas esenciales de refugios/ONG: gratuitas, sin subscription obligatoria (Compromiso Comunidad LeoVer).

### 7.9 Donaciones y voluntariado

Dinero, bienes, tiempo, transporte y servicios separados. Donaciones monetarias a terceros por transferencia directa verificada (alias/CBU) y 0% comisión. No checkout obligatorio para el apoyo comunitario.

### 7.10 Eventos y campañas públicas

Ferias, adopción, vacunación, castración, charlas y campañas compartibles con inscripción, capacidad, recordatorios y resultados.

### 7.11 Veterinarias y gestión de salud

Centros, sedes, profesionales, pacientes, atenciones, vacunas, controles, procedimientos, estudios, documentos, indicaciones, seguimientos, recordatorios y propuestas seleccionadas a VitaCora. Persona humana, profesional veterinario y establecimiento veterinario son entidades distintas. Un dueño administrativo no veterinario no firma información profesional.

### 7.12 Educadores, adiestradores y etólogos

Perfiles, modalidades, agenda, materiales, servicios y reseñas dentro de criterios de bienestar.

### 7.13 Paseadores, cuidadores, guarderías y transporte

Zonas, horarios, capacidad, reservas, cadena de custodia, incidencias y reputación. Pago inicialmente directo entre partes. Guardería es categoría especializada de prestador (M22/M23), no un módulo nuevo: reserva, check-in, huéspedes, actualizaciones privadas, medicación administrada según instrucción, incidentes y check-out que cierra custodia temporal. Ver §3.13.

### 7.14 Tiendas y emprendimientos

Perfiles, productos y promociones. No venta de animales. Checkout/marketplace transaccional fuera de V1.

### 7.15 Agenda, reservas y disponibilidad

Calendarios, franjas, capacidad, confirmación, cancelación, reprogramación, recordatorios, idempotencia y prevención de doble reserva. Los datos mínimos de reserva identifican y ejecutan el servicio; no equivalen a un grant de VitaCora. La reserva/estadía conserva un snapshot de instrucciones y consentimientos acordados, no una copia completa de VitaCora.

### 7.16 Suscripciones y cobros de LeoVer

Suscripciones comerciales, Brand Studio y add-ons; Mercado Pago Suscripciones como proveedor inicial. Donaciones a terceros excluidas.

### 7.17 Búsqueda, descubrimiento y geoservicios

Búsqueda, filtros, radio, proximidad y ranking seguro. PostGIS es motor interno; Google Maps Platform capa inicial de mapas/geocodificación/rutas.

### 7.18 Comunicaciones

Notificaciones, chat y conversación de caso como conceptos distintos; preferencias, deduplicación, bloqueos, reportes y deep links. El primer contacto con prestadores ocurre en Mensajes LeoVer (M20) como conversación con la entidad, no con el empleado que atiende. Cada mensaje institucional conserva `actor_user_id`. No se crea un chat exclusivo para servicios ni se obliga a WhatsApp. M20 aplica reglas de seguridad para menores (ver §3.18); el adulto responsable no lee automáticamente los DMs del adolescente.

### 7.19 Reputación y verificaciones

Hechos verificables, servicios reales, distintivos proporcionales y sanciones con apelación. Popularidad no equivale a confianza.

### 7.20 Moderación, soporte y seguridad operacional

Reportes, revisión, suspensión, apelación, incidentes y soporte. LeoVer no es un servicio de emergencias.

### 7.21 Catálogos y configuración

Dropdowns y valores operativos gestionables por consola con códigos internos estables y sin borrado físico histórico. Las reglas fundacionales no son configurables.

### 7.22 Datos, analítica e inteligencia

Eventos, métricas, auditoría, calidad, IA asistida, feature flags, costos y evaluaciones.

### 7.23 Integraciones

Integraciones graduales mediante adaptadores: mapas, IA, pagos, email, push y futuras fuentes externas. Municipios no son dependencia de V1.

### 7.24 Brand Studio

Producto comercial para crear, publicar, distribuir y medir campañas patrocinadas con plantillas, IA, formatos sociales, Ayudas Concretas, segmentación no sensible, moderación, tutoriales y créditos de IA. `MINOR_ADVERTISING_POLICY = NO_SPONSORED_ADS_UNDER_18_V1`: UNDER_18 no recibe distribución patrocinada ni targeting personalizado/sensible. El contenido orgánico comunitario permanece visible según reglas normales.

### 7.25 Portal veterinario profesional

Producto operativo separado de Brand Studio. Permite gestión de pacientes y registros profesionales sin presentarse como archivo clínico oficial ni custodio legal permanente.

## 8. Experiencias y recorridos principales

### 8.1 Alta y primer valor

1. Crear cuenta: identificación básica, username público obligatorio y único, determinación etaria.
1. Aceptar Términos; acceder/conocer Política de Privacidad; aceptar Normas de Comunidad cuando corresponda. **No** checkbox de marketing.
1. Si aplica, flujo de Cuenta Adolescente (adulto responsable, asentimiento). Consentimientos específicos: just-in-time.
1. Recibir introducción breve y opcional (tutoriales skippables/reopenables, incluido VitaCora).
1. Elegir “¿Qué querés hacer primero?”.
1. Solicitar permisos de cámara/ubicación/notificaciones sólo cuando la acción lo requiera.
1. Completar la primera acción útil sin quedar atrapado en el tutorial. Omitir tutorial no acepta términos, no genera consentimiento y no relaja protecciones.
### 8.2 Crear o completar una mascota

1. Crear identidad de mascota.
1. Agregar fotos y características.
1. Definir responsable principal y autorizados.
1. Configurar privacidad y contactos.
1. Completar datos sanitarios/documentales opcionales.
1. Generar QR/vista pública limitada cuando corresponda.
1. Registrar cambios relevantes con trazabilidad.
### 8.3 Reportar una mascota perdida

1. Seleccionar mascota existente o crear ficha rápida.
1. Confirmar fecha/hora, zona y circunstancias.
1. Configurar qué información se hace pública.
1. Publicar página web compartible y alertar red local.
1. Filtrar encontrados activos por tiempo, geografía y características.
1. En V2, calcular similitud visual y sugerir candidatos.
1. Recibir avistamientos/mensajes y actualizar estado.
1. Confirmar reencuentro y cerrar con resultado.
### 8.4 Informar un animal encontrado

1. Cargar foto, características, fecha/hora, geolocalización y condición del animal.
1. Indicar si está resguardado y si existe urgencia.
1. Proteger ubicación exacta frente al público.
1. Buscar coincidencias con perdidos activos.
1. Seleccionar hasta 10 respondedores elegibles cercanos según cobertura, disponibilidad, especie, capacidad, alertas, suspensión y distancia/tiempo.
1. Enviar alerta sanitizada. La primera aceptación válida y atómica toma el caso; las demás se cancelan.
1. Registrar aceptación, llegada/toma del caso, coordinador, custodio, organización responsable y siguientes necesidades.
1. Cerrar por reencuentro, tránsito, adopción, derivación u otro resultado válido.
### 8.5 Adopción responsable

1. Publicar animal disponible con requisitos y contexto.
1. El interesado reutiliza su perfil de adopción.
1. La organización/persona revisa postulaciones y coordina entrevistas.
1. La decisión es humana; IA sólo puede asistir.
1. La entrega transfiere responsabilidad del mismo perfil de mascota de forma trazable.
1. Se programan seguimientos y se registra consolidación o reversión.
### 8.6 Reservar un servicio

1. Buscar por categoría, zona, disponibilidad y confianza.
1. Revisar perfil, condiciones y reseñas verificadas.
1. Contactar en Mensajes LeoVer, elegir mascota y decidir si comparte VitaCora y con qué alcance y duración.
1. Elegir mascota, servicio y horario.
1. Confirmar reserva y políticas.
1. El prestador confirma y ejecuta. Si hay estadía (p. ej. guardería), el check-in abre custodia temporal.
1. Registrar incidentes/evidencias autorizadas y, si corresponde, proponer información a VitaCora.
1. El responsable acepta, descarta o solicita corrección.
1. Pago directo entre las partes inicialmente.
1. Ambas partes pueden evaluar una interacción real.
### 8.7 Donar o colaborar

1. Explorar necesidades verificadas por zona/categoría.
1. Elegir dinero, bienes, tiempo, transporte, tránsito o servicio.
1. Para dinero a terceros, visualizar CBU/alias verificado del destinatario.
1. LeoVer registra intención/estado cuando corresponda, pero no cobra comisión ni intermedia el dinero de terceros.
1. El destinatario puede actualizar resultado y agradecimiento.
### 8.8 Atención veterinaria y VitaCora

1. La veterinaria gestiona paciente y atención en su portal profesional.
1. El profesional registra autoría, fecha y correcciones trazables.
1. El registro profesional permanece diferenciado de VitaCora.
1. El profesional puede proponer una actualización seleccionada a VitaCora.
1. El responsable autorizado aprueba, rechaza o solicita corrección según el tipo de dato. No hay autoaceptación por defecto en V1.
1. Si acepta, el hecho se persiste en el dominio autoritativo correcto y VitaCora lo integra.
1. La clínica puede exportar sus registros; es responsable de conservar externamente lo que deba preservar.

### 8.9 Crear una campaña en Brand Studio

1. Elegir objetivo: clientes, servicio, producto, descuento, novedad, ayuda concreta, evento o awareness.
1. Cargar material o usar plantillas/IA.
1. Generar copy, CTA, variantes y formatos.
1. Definir audiencia permitida y zona aproximada.
1. Previsualizar y enviar a moderación si corresponde.
1. Publicar y distribuir dentro de las reglas de frecuencia de LeoVer.
1. Consultar métricas y recomendaciones.

### 8.10 Compartir VitaCora con un prestador

1. Desde Comunidad, abrir el prestador y Contactar (conversación con la entidad, no con el empleado).
1. Elegir la mascota. La reserva puede identificar la mascota aunque no haya grant.
1. Decidir NO COMPARTIR, DATOS ESENCIALES, SALUD, ESENCIALES + SALUD o VITACORA COMPLETA (compartible).
1. Elegir DAR ACCESO HASTA una fecha, o por tiempo indeterminado.
1. Iniciar conversación en Mensajes LeoVer.
1. El prestador ve sólo el alcance autorizado. El responsable puede quitar el acceso de inmediato.

### 8.11 Estadía en guardería

1. Reserva confirmada (M23) con snapshot de instrucciones/consentimientos y grant de VitaCora si existe.
1. Check-in: huésped activo y custodia temporal; no transfiere responsabilidad.
1. Consentimiento público DEFAULT = NO; revocable.
1. Durante la estadía: ficha autorizada, actualizaciones privadas, medicación administrada según instrucción, incidentes trazables.
1. Check-out: cierra custodia, deja historial en el establecimiento.
1. Si hay información para VitaCora, se origina una propuesta; el responsable decide.

## 9. Confianza, seguridad y bienestar animal

### 9.1 Capas de confianza

| Capa | Regla |
| --- | --- |
| Identidad | Verificación proporcional al riesgo de la acción. |
| Profesional | Distintivos separados para profesión/establecimiento y actividad real. |
| Organización | Estados público, operativo y de verificación separados. |
| Reputación | Basada en hechos/interacciones; popularidad no equivale a confianza. |
| Moderación | Reportes, evidencia, medidas proporcionales, apelación y trazabilidad. |
| Publicidad | Siempre identificada como “Patrocinado”, con controles según riesgo de categoría. |
| Ubicación | Pública aproximada; exacta protegida y compartida sólo por finalidad/consentimiento. |
| Bienestar | Reglas de contenido y operación que priorizan seguridad animal. |

### 9.2 Privacidad de ubicación

LeoVer distinguirá al menos ubicación pública aproximada, zona operativa, ubicación exacta protegida y ubicación temporal de una acción. Teléfono, domicilio y coordenadas exactas nunca serán públicos por defecto.

### 9.3 Responsabilidad

LeoVer funciona como intermediario tecnológico y red de coordinación. No es veterinaria, servicio de emergencias, autoridad pública, banco ni garante de decisiones de terceros. Esta limitación no elimina sus propias obligaciones sobre plataforma, datos, seguridad, moderación, publicidad, consumo y cumplimiento aplicable.

## 10. Modelo de negocio y monetización

| Compromiso Comunidad LeoVer LeoVer es y seguirá siendo gratuito para las funciones esenciales destinadas a personas, rescatistas, refugios y ONG en materia de identidad, bienestar, rescate, tránsito, adopción y participación comunitaria. La monetización profesional/comercial (Brand Studio, suscripciones de prestadores, IA comercial) permanece separada. |
| --- |

| Fuente | Regla |
| --- | --- |
| Suscripción comercial | Para actores con actividad económica. Precio inicial aún pendiente. |
| Brand Studio | Suscripción/add-on separado para creación + publicación + distribución + analítica publicitaria. Precio pendiente. |
| Distribución/Boost | Incluida en Brand Studio; no se cobra adicionalmente por CPM/CPC/boost en el modelo actual. |
| Aportes voluntarios a LeoVer | LeoVer puede recibir aportes voluntarios destinados a sostener plataforma, infraestructura, operación, crecimiento e iniciativas de bienestar animal. |
| Donaciones a terceros | Transferencia directa al destinatario verificado; 0% de comisión siempre. |
| Comisiones sobre servicios | No inicialmente. |
| Marketplace transaccional | Fuera de V1 y del piloto; posible futuro condicionado a demanda. |

### 10.1 Cobro de suscripciones

Proveedor inicial: Mercado Pago Suscripciones. LeoVer almacenará referencias y estados de negocio, no datos de tarjeta. Mientras el flujo de caja lo permita se priorizarán esquemas de acreditación diferida para reducir comisión. La transferencia bancaria se mantiene como alternativa para empresas/casos particulares.

### 10.2 Brand Studio: alcance comercial

- Creación asistida de campañas, posts, historias, reels con material del anunciante y Ayudas Concretas.
- Plantillas por actividad, copy/títulos/CTA/hashtags, variantes multiformato y generación de imágenes dentro de cuota.
- Segmentación no sensible, vista previa, moderación, programación/publicación, distribución, analítica, tutoriales y recomendaciones. UNDER_18: sin distribución patrocinada ni targeting personalizado.
- Créditos/cuotas para controlar IA costosa; texto amplio, imagen controlada y video generativo especialmente limitado.
- No existe pago extra por boost; más campañas no garantizan más exposición.
## 11. Estrategia comercial y de lanzamiento

### 11.1 Territorio piloto

| Piloto Partido de San Vicente + Partido de Almirante Brown, Provincia de Buenos Aires. La activación puede ser progresiva y las métricas se medirán por partido; los algoritmos geográficos no se detendrán artificialmente en límites administrativos cuando la proximidad real indique otra cosa. |
| --- |

### 11.2 Secuencia

1. Construir red fundadora de organizaciones, rescatistas, tránsitos, voluntarios y aliados profesionales.
1. Activar identidad preventiva y VitaCora.
1. Activar pérdidas/hallazgos y red de respuesta.
1. Activar adopción, tránsito y operación organizacional.
1. Activar comunidad y colaboración.
1. Incorporar propuesta comercial y Brand Studio sobre una red con valor probado.
### 11.3 Piloto con organizaciones

Las organizaciones y actores comunitarios permanecen gratuitos. El piloto debe acompañar onboarding y operación, no convertir la ayuda social en una prueba de precio.

### 11.4 Piloto comercial

Puede ofrecerse un período inicial de 90 días sin costo, sin conversión automática a pago y con aviso previo antes de cualquier continuidad comercial. El precio y estructura final de planes siguen abiertos.

### 11.5 Importación inicial

La importación desde Excel/planillas creará borradores controlados con validación, referencias externas, detección de duplicados y proveniencia. No se importará PII innecesaria. La organización revisa y publica.

## 12. Competencia y diferenciación

La competencia real incluye redes sociales, WhatsApp, planillas, directorios, apps de adopción/pérdidas, software veterinario y herramientas comerciales. LeoVer no compite sólo contra una app equivalente, sino contra la fragmentación y el hábito.

| Diferencial | Por qué importa |
| --- | --- |
| Identidad persistente de mascota | Evita recrear datos y permite continuidad entre procesos. |
| Red familiar sin duplicación | Varios autorizados pueden colaborar sobre la misma mascota. |
| Procesos conectados | Pérdida, hallazgo, adopción, tránsito, salud y servicios comparten contexto. |
| Densidad local | El valor aumenta con actores elegibles activos en la zona. |
| Permisos y proveniencia | Mejoran privacidad, confianza y auditabilidad. |
| Web pública compartible | Reduce fricción para difusión fuera de la app. |
| Operación organizacional/profesional | Convierte la red en herramienta recurrente, no sólo en contenido. |

### 12.1 Fuera de V1

Microchip, marketplace transaccional, pagos integrados entre usuarios/prestadores, integraciones municipales y ciertas capacidades avanzadas de IA no forman parte del núcleo obligatorio de V1. El matching visual sí podrá incorporarse al piloto/V2 temprana sin convertirse en autoridad de identidad.

## 13. Marca y comunicación

| Elemento | Decisión |
| --- | --- |
| Nombre | LeoVer |
| Esencia | Protección conectada. |
| Slogan | Conectamos mascotas, personas y comunidad. |
| Isotipo | Perro naranja + gato verde enfrentados, composición circular, hoja verde y huella en la “o” del wordmark. |
| Paleta | Naranja #FF7A00 · Naranja suave #FFA64D · Verde #49B749 · Verde oscuro #247A3D · Crema #FFF6EA · Gris #2F3A37 · Blanco. |
| Tipografía operativa | Inter. |
| Comunicación social | Cálida, clara, útil, respetuosa y orientada a acciones. |
| Comunicación comercial | Visible y separada de contenido orgánico; todo patrocinado se identifica como tal. |

### 13.1 Procedencia de identidad visual

El proyecto conserva archivos originales y evidencia de decisiones de marca. Las piezas generadas con herramientas de IA se documentan como tales; no se harán afirmaciones de autoría humana exclusiva cuando no correspondan.

### 13.2 Registro marcario

La estrategia actual no exige un nuevo registro de logo/marca mixta para lanzar el piloto. La eventual protección adicional del isotipo puede evaluarse más adelante; antes de afirmaciones públicas sobre exclusividad se verificará el estado registral oficial de la marca denominativa.

## 14. Marco legal, privacidad y cumplimiento

| Nota Esta sección define la estrategia de producto y gobierno. Los instrumentos legales actuales son DRAFT PRE-LAUNCH. COMUNIDAPP S.A.S. aún no está constituida. No presentar CUIT, domicilio, emails legales ni datos societarios inexistentes como definitivos. Revisión jurídica final = LEGAL_LAUNCH_GATE. |
| --- |

### 14.1 Estructura de titularidad y explotación

| Elemento | Estructura aprobada |
| --- | --- |
| Producto/marca | LeoVer. |
| Sociedad operadora prevista | COMUNIDAPP S.A.S. — **aún no constituida**. Nombre y estructura planificados; no datos societarios definitivos hasta constitución, CUIT y domicilio legal. |
| Titularidad patrimonial prevista de LeoVer | 50% Leonardo Agustín Romero / 50% Verónica Luján Obregón, formalizada mediante instrumento adecuado. |
| Explotación por COMUNIDAPP | Contrato separado con facultades amplias de uso y explotación económica: operar, publicar, evolucionar, comercializar, licenciar a usuarios, contratar proveedores, usar marca, ofrecer suscripciones/Brand Studio y expandir territorios. |
| Límite | La sociedad no adquiere por esa licencia la titularidad de LeoVer ni puede vender/ceder/apropiarse del activo sin acuerdo expreso de ambos titulares. |
| IA/Cursor | Herramientas de apoyo; no son autor, titular ni socio. |

### 14.2 Software y DNDA

Antes del lanzamiento se congelará una release identificable (tag/commit, ZIP, inventario y hash). La estrategia prevista es depósito de software inédito poco antes del lanzamiento y registro del software publicado inmediatamente después, verificando requisitos y tasas vigentes al momento del trámite.

### 14.3 Términos y documentos legales

- Términos y Condiciones (CONTRACTUAL ACCEPTANCE); Política de Privacidad (base de tratamiento); Normas de Comunidad; consentimientos contextuales. **No** un único `accepted = true`.
- Términos para organizaciones, profesionales/veterinarias y actores comerciales.
- Condiciones de Brand Studio/publicidad y de aportes voluntarios a LeoVer.
- Condiciones para donaciones a terceros, adopción/tránsito, moderación/apelaciones y piloto.
- Versionado de documentos legales y consent events (subject vs actor; withdrawal). Ver §3.20.
- Consentimientos de menores: MINOR_ASSENT / GUARDIAN_CONSENT según `LEGAL_MINOR_CONSENT_POLICY = DEFINED_PRELAUNCH`.
- Estado actual: **DRAFT PRE-LAUNCH**. `LEGAL_LAUNCH_GATE` antes de registro público real.
### 14.4 Privacidad y datos personales

- Privacidad por diseño y por defecto; minimización y finalidad específica.
- Teléfono, domicilio y ubicación exacta protegidos; consentimiento específico y trazable cuando se compartan con un destinatario concreto. Para adolescentes, la ubicación precisa es PROTECTED y nunca pública de forma directa (ver §3.18).
- Las relaciones de una mascota con responsables pueden identificar personas y se tratan con la misma disciplina de privacidad.
- El grant de VitaCora es autorización por finalidad, actor, alcance y tiempo; revocable; enforcement en backend/RLS.
- Derechos de acceso, corrección, actualización y supresión mediante `privacy_requests`. PRODUCT DELETE (hide/archive/revoke) ≠ PRIVACY ERASURE REQUEST.
- `PRIVACY_ERASURE_POLICY = DEFINED_PRELAUNCH`: eliminar, anonimizar, restringir; conservar solo con justificación legítima. DATA MINIMIZATION. Registros de terceros no se destruyen arbitrariamente.
- Retención por categoría; no existe “guardar todo para siempre” ni “por si acaso”.
- **No** consentimiento de marketing en el alta. Comunicaciones operativas ≠ marketing.
- Analítica y entrenamiento de modelos minimizarán PII; el contenido privado de usuarios no se utilizará para entrenamiento general sin autoridad adicional.
### 14.5 Veterinaria y registros profesionales

LeoVer Vet es una herramienta de gestión profesional, no un sistema oficial ni custodio legal primario. Cada profesional/establecimiento debe exportar y conservar externamente los registros que deba mantener por sus obligaciones. LeoVer sí responde por seguridad, privacidad, acceso y tratamiento mientras los datos estén alojados en la plataforma.

### 14.6 Publicidad y actividades especiales

Las categorías publicitarias se clasifican por riesgo. Servicios generales siguen flujo normal; salud animal, suplementos y afirmaciones de bienestar requieren controles adicionales; medicamentos/productos sujetos a autorización y afirmaciones terapéuticas requieren revisión reforzada. Quedan prohibidos medicamentos ilegales/no autorizados, venta de animales, publicidad oculta y afirmaciones engañosas de curación. `MINOR_ADVERTISING_POLICY = NO_SPONSORED_ADS_UNDER_18_V1`.

## 15. Modelo operativo y organización

La operación inicial será lean y liderada por fundadores, con apoyo profesional externo cuando el riesgo o la carga lo justifiquen. Las responsabilidades se definen como capacidades operativas, no como necesidad inmediata de contratar una persona por rol.

| Capacidad | Responsabilidad inicial |
| --- | --- |
| Producto/gobierno | Prioridades, alcance, aceptación y métricas. |
| Tecnología | Arquitectura, implementación, seguridad, despliegue y calidad. |
| Comunidad/piloto | Onboarding, soporte de aliados, densidad territorial y feedback. |
| Moderación | Reportes, contenido, sanciones y apelaciones. |
| Verificación | Organizaciones, profesionales, anunciantes y categorías de riesgo. |
| Comercial | Onboarding de actores económicos y Brand Studio. |
| Legal/contable | Asesoramiento y revisión externa según necesidad. |

### 15.1 Soporte y disponibilidad

LeoVer no promete atención humana 24x7 ni opera como central de emergencias. Los incidentes críticos de seguridad, privacidad, bienestar y continuidad tienen prioridad y deben contar con escalamiento, registro y comunicación.

## 16. Visión tecnológica

### 16.1 Stack aprobado

| Capa | Tecnología / decisión |
| --- | --- |
| Android | Kotlin + Jetpack Compose + Material 3 + Navigation + MVVM/Flow + repositorios. |
| iOS | Swift + SwiftUI para el piloto; mismo backend y contratos. |
| Web | Next.js + React + TypeScript. |
| Hosting web | Cloudflare Workers mediante OpenNext; GitHub + Workers Builds y previews. |
| Backend | Supabase: Auth, PostgreSQL, RLS, RPC, Storage, Realtime cuando aporte valor y Edge Functions para secretos/integraciones. |
| Geoespacial | PostGIS en Supabase. |
| Mapas/geocodificación | Google Maps Platform como proveedor inicial visible; adaptadores desacoplados. |
| Vector search | pgvector en Supabase. |
| Push | Firebase Cloud Messaging con patrón outbox/eventos. |
| Cobro de suscripciones | Mercado Pago Suscripciones. |
| IA visual | Google Cloud / Gemini multimodal embeddings iniciales, desacoplados. |
| IA Brand Studio | OpenAI inicial para texto e imagen mediante proveedores abstractos. |

### 16.2 Arquitectura

- UI → ViewModel/estado → dominio/repositorios → Supabase/servicios externos.
- Supabase es la fuente autoritativa; no se introducirá un backend NestJS/Prisma ni una segunda base salvo decisión futura explícita.
- RLS deny-by-default y mínimo privilegio; service role jamás dentro de clientes móviles/web.
- RPC para flujos atómicos/idempotentes; Edge Functions para secretos, webhooks e integraciones.
- API-first significa contratos explícitos y estables; no obliga a REST si RPC/eventos son mejores para un caso.
- Arquitectura modular sin fragmentar prematuramente Gradle en múltiples módulos.
- Proveedores externos detrás de interfaces/adaptadores para evitar acoplamiento.
### 16.3 Cloudflare

Next.js se desplegará sobre Cloudflare Workers/OpenNext. La aplicación evitará dependencias exclusivas de Node.js en middleware/proxy y deberá probarse contra el runtime de Workers antes de producción. Vercel queda como alternativa de contingencia, no como proveedor inicial.

### 16.4 Entornos y observabilidad

Existirán entornos local, staging y producción con secretos separados, monitoreo de errores, eventos operativos, métricas, alertas y playbooks de incidentes proporcionales a la etapa.

## 17. Estrategia de datos y analítica

### 17.1 Principios

- La mascota es entidad canónica; procesos, publicaciones y atenciones la referencian sin duplicarla.
- Los dominios conservan su autoridad: VitaCora agrega vistas, no absorbe todas las tablas.
- Cada dato relevante conserva proveniencia: declarado, profesional, verificado, inferido o sistema.
- Los historiales sensibles se corrigen por eventos/versiones cuando sea necesario, no por sobrescritura silenciosa.
- No se crean registros falsos de pagos o donaciones que LeoVer no procesa.
- Las métricas deben poder segmentarse territorialmente sin exponer PII.
### 17.2 Clasificación

| Clase | Ejemplos | Regla |
| --- | --- | --- |
| Pública | Nombre público de mascota, foto autorizada, organización pública. | Sanitizada y explícitamente publicable. |
| Interna | Estados operativos, métricas, configuraciones. | Acceso por función. |
| Personal | Email, teléfono, relaciones con mascotas. | Mínimo necesario y RLS. |
| Alta sensibilidad operativa | Ubicación exacta, evidencia de incidentes, ciertos documentos. | Acceso muy restringido y auditado. |
| Agregada/disociada | Métricas territoriales, impacto. | Evitar reidentificación. |

### 17.3 Analítica, auditoría y observabilidad

Se distinguen tres sistemas: analítica de producto (comportamiento agregado), auditoría (quién hizo qué sobre recursos sensibles) y observabilidad técnica (errores, rendimiento, disponibilidad). No deben mezclarse ni retenerse igual.

### 17.4 Retención

PEN-016 queda cerrado en criterio: matriz por categoría, minimización y eliminación/anonimización al vencer finalidad. Los plazos numéricos concretos se definirán antes del piloto público con revisión legal. Ubicación exacta tendrá retención especialmente corta.

## 18. Inteligencia artificial

| Principio La IA asiste; no decide identidad de mascota, adopción, diagnóstico, tratamiento, sanción ni otra decisión sensible final. |
| --- |

### 18.1 V1 / riesgo bajo

- Asistencia para redactar publicaciones y campañas.
- Clasificación/etiquetado y soporte de moderación.
- Ayuda contextual y tutoriales.
- Resúmenes operativos no sensibles.
### 18.2 Matching visual de perdidos/encontrados

El flujo será bidireccional. Primero filtra por especie, tiempo, zona y características; luego compara embeddings visuales/textuales y presenta candidatos. La identidad siempre requiere confirmación humana. Proveedor inicial: Google Cloud con modelo multimodal de embeddings estable disponible al implementar; pgvector + PostGIS como infraestructura interna.

### 18.3 Brand Studio

Proveedor inicial: OpenAI para texto e imagen, detrás de interfaces GenerativeAIProvider e ImageGenerationProvider. Se registrará consumo/costo por anunciante y campaña. La generación costosa no será ilimitada; se usará una cuota/créditos creativos. El proveedor de video generativo queda abierto.

### 18.4 Salud

La IA veterinaria no diagnosticará, prescribirá ni indicará dosis. Puede asistir con organización, búsqueda interna o borradores que requieran validación profesional, siempre separando contenido generado de registro profesional confirmado.

### 18.5 Gobierno de modelos

- Versionado de modelos/prompts cuando afecten resultados.
- Feature flags y límites de costo.
- Evaluaciones de calidad y falsos positivos.
- Registro de proveedor/modelo/operación cuando sea necesario para trazabilidad.
- Mecanismos de corrección y feedback humano.
- No entrenamiento general con contenido privado sin base y autorización específicas.
## 19. Roadmap integral del producto

| Release estratégico | Resultado |
| --- | --- |
| R0 — Fundación | Arquitectura, seguridad, entornos, administración base, observabilidad. |
| R1 — Identidad | Cuenta, perfil personal, mascota y VitaCora base. |
| R2 — Rescate + web pública | Pérdidas/hallazgos, geoservicios, páginas compartibles y red de respuesta. |
| R3 — Adopción, tránsito y organizaciones | Casos, postulaciones, custodia, equipos y operación institucional. |
| R4 — Comunidad y colaboración | Contenido, historias, voluntariado, eventos, comunicación y reputación. |
| R5 — Servicios confiables | Agenda, prestadores, portal profesional y suscripción comercial base. |
| R5B — Brand Studio | Publicidad asistida, distribución, plantillas, IA, moderación y analítica. |
| R6 — Comercio futuro | Catálogo comercial más profundo y posibles flujos transaccionales si se justifican. |
| R7 — IA/V2 | Matching visual avanzado, recomendaciones y automatizaciones de bajo riesgo. |
| R8 — Integraciones/regional | Interoperabilidad, municipios y expansión territorial/regional. |

### 19.1 Web progresiva

R2 introduce web pública; R3 portal de organizaciones; R4 superficies comunitarias; R5 portal profesional/veterinario; R5B/R6 portal comercial/Brand Studio. Administración acompaña transversalmente.

### 19.2 iOS

iOS forma parte del piloto en flujos centrales. No se exige paridad total con Android desde el primer build, pero sí registro/login, onboarding, mascota/VitaCora básico, perdidos/encontrados, adopción, tránsito, organizaciones/casos, comunidad básica, notificaciones esenciales, ubicación/mapas y privacidad/permisos.

## 20. Métricas, objetivos y experimentos

### 20.1 Métrica norte

Mascotas únicas ayudadas exitosamente por mes, con evento confirmado y no revertido. Los tipos de ayuda se reportan también de forma desagregada.

### 20.2 Métricas por dominio

| Dominio | Métricas prioritarias |
| --- | --- |
| Identidad/VitaCora | Mascotas activas, utilidad del perfil, actualizaciones válidas, usuarios recurrentes. |
| Pérdidas/hallazgos | Tiempo a primera respuesta, respondedores válidos, posibles coincidencias, reencuentros. |
| Adopción | Postulaciones útiles, tiempo de proceso, consolidaciones, devoluciones/reversiones. |
| Organizaciones | Uso operativo recurrente, casos, animales bajo cuidado, acciones cerradas. |
| Veterinaria | Pacientes activos, atenciones, vacunas, documentos, propuestas a VitaCora, aceptación y retención de clínicas/profesionales. |
| Comunidad | Acciones útiles, colaboración y recurrencia; no sólo likes/seguidores. |
| Brand Studio | Campañas, alcance único, frecuencia, CTA, reservas/acciones atribuibles, ocultamientos/reportes y consumo de IA. |
| Territorio | Densidad de actores elegibles, cobertura, respuesta y resultados por partido. |

### 20.3 Umbrales iniciales del piloto

| Indicador | Umbral inicial orientativo |
| --- | --- |
| Mascotas registradas activas | ≥ 500 |
| Usuarios activos mensuales | ≥ 300 |
| Organizaciones/refugios/rescatistas activos | ≥ 10 |
| Actores profesionales/comerciales participantes | ≥ 10 |
| Casos reales de ayuda procesados | ≥ 50 |
| Casos urgentes con alguna respuesta válida | ≥ 60% |
| Resultados exitosos confirmados | ≥ 20 |
| Retención mensual de usuarios activos | ≥ 30% |
| Cobertura territorial | Actividad real en San Vicente y Almirante Brown, medida por separado. |

### 20.4 Experimentos

Cada experimento deberá indicar hipótesis, métrica primaria, umbral, muestra/período y decisión posible. Privacidad, seguridad, bienestar y cumplimiento son guardrails: un experimento no puede optimizar crecimiento a costa de ellos.

## 21. Modelo económico y financiación

### 21.1 Política económica

- Comunidad y ayuda social esencial gratuitas de forma permanente.
- Donaciones a terceros directas y 0% de comisión.
- Suscripción comercial como monetización base.
- Brand Studio como producto/add-on comercial separado.
- Aportes voluntarios a LeoVer permitidos y transparentes.
- Marketplace transaccional y comisiones por servicios fuera de V1.
### 21.2 Precio

PEN-008, PEN-009 y PEN-010 permanecen abiertos en precio/segmentación final. El piloto comercial podrá ser gratuito durante 90 días sin conversión automática. Inicialmente se parte de un único plan comercial base y Brand Studio separado; se definirán valores con uso real, costos y disposición a pagar.

### 21.3 Control de costos de IA

Brand Studio no ofrece IA pesada ilimitada. Texto puede ser amplio por su bajo costo relativo; imágenes consumen cuota; video generativo consume significativamente más créditos y requiere proveedor/estructura de precio futura. Se registrará costo estimado por campaña/anunciante.

### 21.4 Presupuesto de lanzamiento

| Concepto | Referencia aprobada |
| --- | --- |
| Infraestructura + desarrollo + reserva IA | ≈ USD 60/mes al inicio. |
| Runway técnico 6 meses + Play + dominio | ≈ USD 410 de referencia. |
| Objetivo de caja para lanzamiento | ≈ USD 1.500 equivalentes. |
| Colchón prudente | Hasta ≈ USD 2.000 equivalentes. |
| Constitución de COMUNIDAPP S.A.S. | Separada hasta confirmar jurisdicción y costos vigentes. |
| Legal/contable | Reserva a cotizar; revisión antes del piloto público. |

Los importes son presupuestos de planificación y deberán actualizarse al momento de contratar servicios o realizar trámites. El matching visual se considera cubierto dentro de la reserva de IA ya contemplada.

## 22. Riesgos y mitigaciones

| Riesgo | Escenario | Mitigación |
| --- | --- | --- |
| Seguridad/privacidad | Compromiso de cuentas, exposición de ubicación o PII. | RLS, mínimo privilegio, MFA/admin reforzado, auditoría, retención y respuesta a incidentes. |
| Bienestar animal | Uso abusivo, entrega incorrecta, contenido riesgoso. | Moderación, verificación proporcional, ubicación protegida y procesos trazables. |
| Hallazgo doble aceptación | Dos respondedores toman el mismo caso. | Aceptación atómica/idempotente y cancelación de restantes. |
| No-show / animal ya no está | Respuesta desactualizada. | Estados explícitos, expiración y reintentos/derivación. |
| Pérdida durante servicio | Incidente crítico de custodia. | Cadena de custodia, contacto de emergencia, incidentes y auditoría. |
| Datos veterinarios | Confusión entre registro profesional y VitaCora o pérdida por baja. | Separación de dominios, exportación y responsabilidad de conservación externa del profesional. |
| Publicidad engañosa | Promesas falsas, categorías reguladas, saturación. | Moderación por riesgo, “Patrocinado”, límites de frecuencia, prohibiciones y kill switch. |
| Fraude en donaciones | Campañas o CBU/alias falsos. | Verificación del destinatario y transferencias directas; LeoVer no concentra fondos de terceros. |
| Dependencia de proveedores | Cambio de costo/API de mapas, IA, pagos o hosting. | Adaptadores, límites de costo y plan de sustitución. |
| IA falsa coincidencia | Usuario interpreta sugerencia como certeza. | Candidatos, score explicable cuando sea posible y confirmación humana. |
| Crecimiento sin densidad | Muchos registros pero poca capacidad de ayuda. | Piloto territorial, métricas por partido y expansión por playbook. |
| Crisis reputacional | Incidente público o moderación controvertida. | Registro de incidentes, escalamiento y comunicación de crisis. |
| Continuidad | Caída de servicios críticos. | Backups, monitoreo, recuperación y degradación segura. |

### 22.1 Prioridad de decisiones

| Jerarquía Seguridad → privacidad → bienestar animal → cumplimiento legal → continuidad → confianza → experiencia → crecimiento → ingresos. |
| --- |

## 23. Gobierno del proyecto y documentación

### 23.1 Jerarquía de decisión

1. Principios fundacionales no configurables.
1. Decisiones estructurales aprobadas y ADR cuando corresponda.
1. Reglas de dominio.
1. Catálogos/configuración operativa dentro de límites.
1. Experimentos y ajustes temporales medibles.
### 23.2 Fuente de verdad documental

| Orden Documento Maestro v1.2 → decisiones/ADR posteriores aprobados → D01 v1.3 → especificaciones de módulo → código y pruebas. Si existe contradicción, se corrige el documento inferior o se registra una nueva decisión superior. La v1.1 se conserva como histórico y no resuelve contradicciones actuales. |
| --- |

### 23.3 Catálogos

Los valores de combos/listas serán configurables desde consola global con códigos internos estables, labels mutables, estados activo/inactivo y sin borrado físico cuando existan referencias históricas. Seguridad, privacidad, bienestar, límites de acceso y prohibiciones fundamentales no son catálogos editables.

### 23.4 Definición de terminado

Un dominio no está terminado por tener pantallas. Debe incluir reglas, estados, datos, permisos, errores, auditoría/analítica necesarias, pruebas, documentación y operación suficiente para el corte liberado.

### 23.5 Gobierno de Brand Studio

Plantillas, categorías, segmentaciones permitidas, límites de frecuencia, riesgo regulatorio, créditos de IA y kill switch publicitario son capacidades administrativas auditables. La suscripción no equivale a verificación ni recomendación editorial.

## 24. Plan de transición y acción

1. Conservar Documento Maestro v1.1 como histórico y adoptar v1.2 como fuente superior.
1. Conservar changelog v1.0 → v1.1; los cambios v1.1 → v1.2 quedan en el Anexo G de este documento.
1. **D01 v1.3** vigente — alineado con Maestro v1.2; no renumera Mxx; VitaCora (M14), propuestas y guardería se mapean a módulos existentes.
1. Auditar documentación existente y marcar cada archivo como vigente, a actualizar, supersedido o histórico.
1. Crear especificaciones nuevas sólo cuando vayan a ser implementadas: grants de VitaCora, propuestas, guardería operativa y Brand Studio / Portal Veterinario / Arquitectura Web, entre las prioritarias.
1. Preparar paquete legal y someterlo a revisión profesional antes del piloto público.
1. Planificar DNDA cerca del freeze de release de lanzamiento.
1. Ejecutar piloto territorial con métricas separadas para San Vicente y Almirante Brown.
1. No generar decenas de documentos preventivos sin necesidad: documentar antes de implementar el dominio correspondiente.
1. No desarrollar Tienda transaccional en esta etapa.

### 24.1 Documentos inmediatos después de v1.2

| Orden | Documento | Objetivo |
| --- | --- | --- |
| 1 | Documento Maestro v1.2 (este archivo) | Gobierno de identidad, VitaCora, propuestas, guardería, media, tiempo y baseline. |
| 2 | D01 v1.3 | Mapa oficial de módulos alineado con Maestro v1.2. |
| 3 | ADR-016 | Identidad, capacidades y contexto operativo (ya aprobado). |
| 4 | Especificaciones afectadas | Actualizar M08/M14/M20/M22/M23/M28 antes de implementar sharing. |
| 5 | Brand Studio / Vet Portal / Web | Especificaciones separadas antes de implementación amplia. |
| 6 | Paquete legal | Antes del piloto público. |

## 25. Decisiones pendientes y estados

La revisión PEN-001 a PEN-025 dejó varias decisiones cerradas y otras deliberadamente abiertas. “Abierto” no significa olvidado: indica que el dato debe decidirse con evidencia o validación externa en el momento correcto.

| ID | Tema | Estado | Decisión / próximo momento |
| --- | --- | --- | --- |
| PEN-001 | Sociedad operadora | CERRADO nombre / ABIERTO constitución | Nombre previsto: COMUNIDAPP S.A.S. Aún no constituida. LEGAL_LAUNCH_GATE. |
| PEN-002 | Titularidad LeoVer | CERRADO | 50% Leonardo Agustín Romero / 50% Verónica Luján Obregón; formalización pendiente. |
| PEN-003 | Explotación por COMUNIDAPP | CERRADO | Licencia/facultades amplias de explotación sin transferencia de titularidad. |
| PEN-004 | Marca | CERRADO etapa actual | Denominativa tramitada en clases 9/42/45; marca mixta opcional futura. |
| PEN-005 | Territorio piloto | CERRADO | San Vicente + Almirante Brown. |
| PEN-006 | Mapas/geocodificación | CERRADO | Google Maps Platform + PostGIS; adaptadores desacoplados. |
| PEN-007 | Hosting web | CERRADO | Cloudflare Workers + OpenNext; Supabase backend. |
| PEN-008 | Precio suscripción comercial | ABIERTO | Definir antes del primer cliente pago con uso real y prueba de precio. |
| PEN-009 | Estructura de planes | ABIERTO | Comenzar simple; múltiples planes sólo si la segmentación real lo exige. |
| PEN-010 | Precio Brand Studio | ABIERTO | Modelo de costos/créditos IA cerrado; precio con evidencia. |
| PEN-011 | Alcance Brand Studio | CERRADO | Creación + formatos + IA + segmentación permitida + distribución + analítica + tutoriales. |
| PEN-012 | Boost/distribución paga adicional | CERRADO | No se cobra boost separado; distribución incluida en Brand Studio. |
| PEN-013 | Frecuencia/límites publicidad | CERRADO criterio | Principios cerrados; valores exactos configurables y calibrados en piloto. |
| PEN-014 | Categorías reguladas | CERRADO | Modelo por riesgo y prohibiciones estructurales. |
| PEN-015 | Cobro automático | CERRADO | Mercado Pago Suscripciones; transferencia como alternativa. |
| PEN-016 | Retención general | CERRADO criterio | Matriz y minimización; plazos numéricos antes del piloto con revisión legal. |
| PEN-017 | Registros veterinarios | CERRADO | Herramienta operativa, exportación y conservación externa por profesional/establecimiento. |
| PEN-018 | Paquete legal | CERRADO estrategia | DRAFT PRE-LAUNCH + LEGAL_LAUNCH_GATE; revisión profesional obligatoria antes de registro público. |
| PEN-019 | Presupuesto lanzamiento | CERRADO | Objetivo USD 1.500 equivalentes; colchón hasta USD 2.000. |
| PEN-020 | Éxito piloto | CERRADO | Umbrales iniciales de actividad, resultados, retención y cobertura territorial. |
| PEN-021 | iOS | CERRADO | Obligatorio en flujos centrales del piloto; Swift + SwiftUI. |
| PEN-022 | Microchip | CERRADO | Fuera de V1/piloto; extensión futura opcional. |
| PEN-023 | IA visual | CERRADO | Google multimodal embeddings inicial + pgvector + PostGIS + confirmación humana. |
| PEN-024 | IA Brand Studio | CERRADO parcial | OpenAI inicial para texto/imagen; proveedor de video generativo abierto. |
| PEN-025 | Marketplace transaccional | CERRADO | Fuera de V1/piloto; futuro sólo si demanda/volumen lo justifican. |
| PEN-026 | Identidad única y ActiveContext | CERRADO | Una persona = una identidad; contexto no concede permisos; ADR-016. |
| PEN-027 | Responsabilidad institucional de mascotas | CERRADO | La mascota bajo organización pertenece a la organización, no al actor que la cargó. |
| PEN-028 | Custodia temporal | CERRADO | No transfiere responsabilidad principal ni duplica la mascota. |
| PEN-029 | VitaCora compartida con servicios | CERRADO | Grant: NO COMPARTIR / DATOS ESENCIALES / SALUD / ESENCIALES+SALUD / VITACORA COMPLETA; UNTIL_DATE / INDEFINITE / REVOKED. |
| PEN-030 | Propuestas a VitaCora | CERRADO | El responsable acepta, descarta o pide corrección; el hecho aceptado vive en el dominio autoritativo; sin autoaceptación V1. |
| PEN-031 | Matriz de propuestas por prestador | CERRADO | Capacidades por autoridad real, no por AccountType. |
| PEN-032 | Primer contacto en Mensajes LeoVer | CERRADO | Reutilizar M20; no chat paralelo ni WhatsApp obligatorio. |
| PEN-033 | Guardería | CERRADO | Categoría M22/M23; nav Inicio/Reservas/+ Publicar/Huéspedes/Perfil; custodia por estadía. |
| PEN-034 | VitaCora nombre canónico | CERRADO | Producto y dominio = VitaCora; M14 — VitaCora. Pasaporte = histórico/legacy. |
| PEN-035 | Multi-OWNER personal | CERRADO | Varios OWNER simultáneos; created_by separado de autoridad. |
| PEN-036 | Eliminar no destructivo | CERRADO | PRODUCT DELETE = hide/archive/revoke/deactivate. Distinto de PRIVACY ERASURE. |
| PEN-037 | Baseline REBASE-03 | CERRADO criterio | Reconstrucción canónica preproducción; staging ≠ producción; no destruir primero el backend actual. |
| PEN-038 | Cuentas adolescentes | CERRADO criterio | PERSON única; no AccountType.TEEN; UNDER_13 sin cuenta autónoma; 13–15 / 16–17 protegidas; 18+ adulta. Umbrales jurídicos = revisión legal. |
| PEN-039 | Adulto responsable ≠ OWNER | CERRADO | Relación persona–persona independiente de M08. |
| PEN-040 | Consentimiento de menores | CERRADO producto / ABIERTO legal | LEGAL_MINOR_CONSENT_POLICY = DEFINED_PRELAUNCH. Revisión jurídica final antes de producción. |
| PEN-041 | Publicidad a menores | CERRADO producto / ABIERTO legal | MINOR_ADVERTISING_POLICY = NO_SPONSORED_ADS_UNDER_18_V1. |
| PEN-042 | Verificación del adulto responsable | CERRADO V1 producto / ABIERTO legal | GUARDIAN_VERIFICATION_POLICY = DEFINED_FOR_V1_PRELAUNCH. Cuenta 18+ verificada; sin DNI por defecto. |
| PEN-043 | Privacy erasure | CERRADO producto / ABIERTO legal | PRIVACY_ERASURE_POLICY = DEFINED_PRELAUNCH. Distinto de PRODUCT DELETE. |
| PEN-044 | Tutoriales | CERRADO | Skippable/reopenable; independientes; ≠ consentimiento. VitaCora en onboarding. |
| PEN-045 | Compromiso Comunidad LeoVer | CERRADO | Esenciales gratis para personas, rescatistas, refugios y ONG. |
| PEN-046 | Legal launch gate | CERRADO criterio | Entidad, Términos, Privacidad, Comunidad, derechos, menores y revisión jurídica antes de registro público. |
| PEN-047 | Marketing en el alta | CERRADO | No checkbox de marketing. Comunicaciones operativas ≠ marketing. |

## Anexo A. Principios de permisos y responsabilidad

| Concepto | Regla |
| --- | --- |
| Responsable principal | Controla acciones sensibles sobre identidad de mascota, grants de VitaCora y transferencias, sujeto a disputas/controles. Puede haber varios OWNER personales. |
| Autorizado familiar | Acceso granular a acciones concretas, sin asumir automáticamente titularidad. |
| Custodio temporal | Puede actuar durante tránsito, hallazgo, guardería u otro servicio según permiso, propósito y tiempo. No adquiere responsabilidad principal. |
| Organización | Acceso mediante pertenencia/equipo/rol interno y propósito. Es responsable operativo de las mascotas bajo su cargo. |
| Creador / actor | Quien ejecutó una acción; queda en auditoría/proveniencia. No implica ownership. |
| Profesional veterinario | Acceso específico, revocable y mínimo; registra atención profesional y propone datos seleccionados a VitaCora. No firma un dueño administrativo no veterinario. |
| Prestador | Puede recibir grant de lectura y proponer según matriz de servicio. No edita directamente VitaCora. |
| Anunciante | No accede a PII ni audiencias sensibles; sólo a segmentación y métricas permitidas. |
| Administrador LeoVer | Mínimo privilegio, auditoría reforzada y separación de funciones cuando la escala lo requiera. |
| Adolescente | Misma PERSON; protecciones transversales. Puede ser OWNER/AUTHORIZED de mascota. Permiso de mascota ≠ capacidad por edad. |
| Adulto responsable | Vínculo con menor (UX: “Adulto responsable”, no “tutor legal verificado”). No es automáticamente OWNER de las mascotas del adolescente ni lector automático de mensajes, VitaCora, contenido ni historial privado. |

## Anexo B. Política de publicidad y Brand Studio

| Regla | Decisión |
| --- | --- |
| Identificación | Todo contenido comercial se muestra como “Patrocinado”. |
| Distribución | Incluida en la suscripción de Brand Studio; no boost pago separado. |
| Prioridad | Seguridad/bienestar/urgencias > acciones comunitarias > orgánico > patrocinado relevante. |
| Segmentación permitida | Zona aproximada, especie, etapa de vida, adopción/intereses declarados, capacidades no sensibles. |
| Segmentación prohibida | Ubicación exacta, chats, datos clínicos, moderación, información sensible, VitaCora privada, targeting sensible de menores. |
| Menores / M29 | NO_SPONSORED_ADS_UNDER_18_V1. Sin targeting personalizado/sensible. Orgánico comunitario permitido. |
| Frecuencia | Límites por usuario, separación entre anuncios, cooldown y fatiga; valores configurables. |
| Regulados | Revisión proporcional al riesgo y catálogo actualizable. |
| IA | Cuotas/créditos; publicación siempre revisable por humano. |

## Anexo C. Presupuesto inicial de referencia

| Rubro | Referencia | Observación |
| --- | --- | --- |
| Supabase Pro | ≈ USD 25/mes | Backend, Auth, DB, Storage, RLS. Verificar precio vigente. |
| Cloudflare Workers | ≈ USD 5/mes | Hosting web inicial. Verificar precio vigente. |
| Cursor | ≈ USD 20/mes | Herramienta de desarrollo; gasto operativo. |
| IA/API reserve | ≈ USD 10/mes | Matching + pruebas Brand Studio; límites duros. |
| Google Maps | Objetivo USD 0 en piloto | Usar cuotas/alertas; PostGIS evita llamadas innecesarias. |
| Google Play | Cargo único según precio vigente | Verificar antes de publicación. |
| Apple Developer | Cargo anual según precio vigente | Necesario para iOS/App Store. |
| Dominio | Variable anual | Verificar registrador. |
| Legal/contable | A cotizar | Revisión antes del piloto. |

Estas cifras son referencias de planificación y no compromisos contractuales. Toda contratación o trámite debe verificar la tarifa vigente y el impacto impositivo aplicable.

## Anexo D. Glosario

| Término | Definición |
| --- | --- |
| VitaCora | Nombre canónico de producto y dominio (M14). Vida + corazón + bitácora. Descriptor: “Su vida. Su historia. Sus cuidados.” No es solo salud ni historia clínica oficial. |
| Pasaporte / Passport (histórico) | Terminología legacy de implementaciones previas. El baseline nuevo no crea conceptos `passport_*`. |
| VitaCora Access Grant | Autorización de lectura sobre la identidad viva, con alcance de servicio, finalidad, actor y duración (UNTIL_DATE / INDEFINITE / REVOKED). |
| VitaCora Update Proposal | Propuesta de un tercero; si se acepta, el hecho vive en el dominio autoritativo y VitaCora lo integra. |
| Momentos personales | Dimensión emocional de VitaCora; privada por defecto; no se comparte automáticamente con prestadores. |
| OWNER / AUTHORIZED | Relaciones familiares conceptuales; la autoridad real es por permisos granulares. Puede haber varios OWNER. |
| created_by | Actor original de creación. Auditoría/proveniencia; no autoridad eterna. |
| Responsable | Persona(s) OWNER u organización con autoridad sobre acciones sensibles de la mascota. |
| Creador / actor | Quien ejecutó una acción (p. ej. alta). No implica ownership. |
| Custodia | Cuidado temporal sin transferencia automática de responsabilidad. |
| Contexto activo | Traducción operativa de una identidad única. Una organización aparece una sola vez. UI: “Cambiar perfil” / “Usar LeoVer como”. Age/protection no es ActiveContext. |
| Cuenta Adolescente | PERSON de 13–17 con protecciones. No es AccountType. UNDER_13 no opera cuenta autónoma. |
| Adulto responsable | Relación con un menor; independiente de M08. UX: “Adulto responsable”; no afirmar tutela legal no verificada. |
| Age band | UNDER_13 / 13_15 / 16_17 / 18_PLUS. Operativo de producto; efectos jurídicos = revisión legal final. |
| Adulto responsable (V1) | PERSON 18+, cuenta activa y verificada, vínculo bilateral. Sin DNI por defecto. |
| PRODUCT DELETE | Hide/archive/soft-delete/revoke/deactivate de producto. |
| PRIVACY ERASURE | Solicitud legal de supresión de datos personales. Distinto de PRODUCT DELETE. |
| Compromiso Comunidad LeoVer | Funciones comunitarias esenciales gratuitas para personas, rescatistas, refugios y ONG. |
| LEGAL_LAUNCH_GATE | Condiciones legales previas a registro público real. Documentos actuales = DRAFT PRE-LAUNCH. |
| Tutorial | Contextual, skippable y reopenable. No es consentimiento ni concede permisos. |
| Caso | Unidad trazable de ayuda/rescate con estado, actores, necesidades y resultado. |
| Respondedor | Actor elegible que puede aceptar una necesidad/caso según zona y capacidad. |
| Huésped | Mascota actualmente bajo custodia de una guardería u establecimiento de estadía. |
| Ayuda exitosa | Resultado confirmado, válido y no revertido medido por la North Star. |
| Brand Studio | Producto comercial de creación, publicación, distribución y analítica publicitaria. |
| Ayuda Concreta | Beneficio patrocinado útil y verificable, claramente identificado como comercial. |
| RLS | Row Level Security; control de acceso a filas en PostgreSQL/Supabase. |
| PostGIS | Extensión geoespacial usada para distancias, radios y proximidad. |
| pgvector | Extensión vectorial usada para similitud/embeddings. |

## Anexo E. Fuentes y referencias de gobierno

La versión v1.2 consolida el Documento Maestro v1.1 y las decisiones de reconciliación de contextos aprobadas. Para la ejecución deberán verificarse las fuentes oficiales vigentes en materias que cambian con el tiempo. Referencias institucionales prioritarias:

- Argentina.gob.ar / Agencia de Acceso a la Información Pública — Ley 25.326, derechos, obligaciones, privacidad y bases de datos personales.
- INPI — búsqueda, clases, estado y registro de marcas.
- DNDA — software inédito/publicado y requisitos de depósito/registro.
- SENASA y normativa profesional aplicable — registros, productos y publicidad veterinaria cuando corresponda.
- Mercado Pago Developers — Suscripciones y webhooks.
- Google Maps Platform — Maps/Geocoding/Routes y precios/cuotas.
- Supabase — Auth, RLS, PostGIS, pgvector, Storage y límites de plan.
- Cloudflare Workers/OpenNext — compatibilidad Next.js, despliegue y pricing.
- Apple Developer / Google Play — requisitos de distribución y privacidad.
- OpenAI / Google Cloud — modelos, costos y condiciones de uso de IA.
Las referencias de costo, compatibilidad o regulación son de naturaleza temporal. El Documento Maestro fija la decisión estratégica; la especificación de implementación debe confirmar la versión vigente antes de contratar, publicar o ejecutar un trámite.

## Anexo F. Cambios estructurales incorporados en v1.1

| Área | Cambio principal respecto de v1.0 |
| --- | --- |
| Modelo de plataforma | De “dos lados” a plataforma multilateral centrada en identidad de mascota. |
| Marca | LeoVer queda como nombre público; estado marcario actualizado a estrategia actual. |
| Piloto | San Vicente + Almirante Brown. |
| Monetización | Social esencial gratis; suscripciones comerciales, Brand Studio y aportes a LeoVer; 0% donaciones a terceros. |
| Pagos | Marketplace/comisiones fuera de V1; Mercado Pago sólo para suscripciones propias. |
| Web | Next.js en Cloudflare Workers/OpenNext sobre Supabase. |
| iOS | Incluido en el piloto para flujos centrales. |
| Veterinaria | Portal operativo con pacientes/registros; no archivo clínico oficial; exportación y custodia externa profesional. |
| IA | Matching visual con embeddings + pgvector/PostGIS; OpenAI inicial para Brand Studio texto/imagen. |
| Publicidad | Brand Studio incluye distribución; sin boost pago adicional; frecuencia y riesgo regulatorio controlados. |
| Legal/IP | Titularidad 50/50 de LeoVer y explotación amplia por COMUNIDAPP S.A.S. sin transferencia de título. |
| Microchip | Fuera de V1/piloto. |
| Gobierno | PEN-001 a PEN-025 clasificados y documentados. |

## Anexo G. Cambios estructurales incorporados en v1.2

| Área | Cambio principal respecto de v1.1 |
| --- | --- |
| Identidad | Una persona = una identidad. ActiveContext traduce experiencia; no concede permisos ni crea cuenta. `account_type` no es canónico. Una organización aparece una sola vez. Cuentas adolescentes = misma PERSON + age/protection; no AccountType.TEEN. |
| Mascotas personales | Uno o varios OWNER simultáneos. `created_by` ≠ autoridad. |
| Mascotas institucionales | Bajo organización, el responsable es la organización; el cargador es actor de auditoría. |
| Custodia | Tránsito, guardería y servicios generan custodia temporal sin transferir ownership. |
| VitaCora | Nombre canónico de producto/dominio (M14). Composición sobre datos autoritativos; no duplica fuentes. |
| Grants de servicio | NO COMPARTIR / DATOS ESENCIALES / SALUD / ESENCIALES+SALUD / VITACORA COMPLETA. UNTIL_DATE / INDEFINITE / REVOKED. Reserva ≠ grant. |
| Propuestas | Terceros proponen; el responsable decide; el hecho aceptado vive en el dominio autoritativo. Sin autoaceptación V1. |
| Matriz de prestadores | Qué puede proponer cada tipo de servicio, por autoridad real. |
| Mensajería | Primer contacto en M20 con la entidad; actor_user_id auditado; no chat paralelo ni WhatsApp obligatorio. |
| Veterinaria | Persona ≠ profesional ≠ establecimiento. M03 / M28 / M22 / M12–M23. |
| Guardería | Categoría M22/M23; consentimiento público DEFAULT=NO, revocable. |
| Eliminar | PRODUCT DELETE no destructivo. PRIVACY ERASURE = flujo legal distinto (DEFINED_PRELAUNCH). |
| Media | Asset + metadata estable; signed URL temporal. Candidato M05 en REBASE-03. |
| Tiempo | date/LocalDate vs timestamptz UTC; agenda con zona horaria; no GMT-3 global. |
| Recorrido de servicios | Descubrimiento → contacto → mascota → grant opcional → mensajería → reserva → custodia → propuesta → decisión → integración → reputación. |
| Tienda | Marketplace/checkout sigue fuera de V1; se analizará en V2. No se desarrolla en esta etapa. |
| Baseline | REBASE-03: reconstrucción canónica; staging ≠ producción; no destruir primero el backend actual. |
| Mapa técnico | D01 v1.3 alineado; no se crea M30 para VitaCora ni módulo Teen Accounts. |
| Menores | UNDER_13 sin autonomía; 13–15 / 16–17 protegidas; guardian ≠ OWNER; PET PERMISSION ≠ AGE CAPABILITY; cuatro políticas DEFINED_*_PRELAUNCH / NO_SPONSORED_ADS. |
| Tutoriales | Modelo general skippable/reopenable; VitaCora en onboarding; ≠ consentimiento. |
| Legal | Documentos versionados; consent events; privacy_requests; DRAFT PRE-LAUNCH; LEGAL_LAUNCH_GATE. |
| Comunidad | Compromiso Comunidad LeoVer: esenciales gratis; monetización profesional/comercial separada. |

## Aprobación y vigencia

| Vigencia La aprobación de esta versión convierte a LeoVer — Documento Maestro Integral de la Startup v1.2 en la fuente estratégica superior del proyecto. La v1.1 se conserva intacta como histórico y no debe utilizarse para resolver contradicciones actuales. |
| --- |

Siguiente secuencia documental: REBASE-03B (diseño de baseline canónico: legal_documents, consent_events, privacy_requests, guardian relationships, age capability, erasure/anonymization) sin inventar decisiones de producto.

Fin del documento.
