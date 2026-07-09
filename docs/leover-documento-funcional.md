# Leover — Documento Funcional v1.0

**Fecha:** 8 de julio de 2026  
**Estado:** Especificación oficial de producto

> Este archivo es la referencia canónica del producto en el repositorio.  
> Para el estado de implementación en código, ver [`leover-roadmap-implementacion.md`](leover-roadmap-implementacion.md).

---

## Índice

1. [Introducción](#1-introducción)
2. [Visión del producto](#2-visión-del-producto)
3. [Objetivos](#3-objetivos)
4. [Tipos de usuarios](#4-tipos-de-usuarios)
5. [Arquitectura de módulos](#5-arquitectura-de-módulos)
6. [Módulo Social](#6-módulo-social)
7. [Perfil de Mascota](#7-perfil-de-mascota)
8. [Módulo de Adopciones](#8-módulo-de-adopciones)
9. [Hogares de Tránsito](#9-hogares-de-tránsito)
10. [Refugios](#10-refugios)
11. [Veterinarias](#11-veterinarias)
12. [Educadores y Adiestradores](#12-educadores-y-adiestradores)
13. [Paseadores](#13-paseadores)
14. [Tiendas y Emprendimientos](#14-tiendas-y-emprendimientos)
15. [Mascotas Perdidas y Encontradas](#15-mascotas-perdidas-y-encontradas)
16. [Donaciones y Voluntariado](#16-donaciones-y-voluntariado)
17. [Eventos](#17-eventos)
18. [Sistema de Reputación](#18-sistema-de-reputación)
19. [Sistema de Insignias](#19-sistema-de-insignias)
20. [Matriz de Permisos](#20-matriz-de-permisos)
21. [Roles Administrativos](#21-roles-administrativos)
22. [Roadmap de Desarrollo](#22-roadmap-de-desarrollo)

---

## 1. Introducción

Leover es una plataforma que busca convertirse en el principal ecosistema digital para el bienestar animal.

No es únicamente una aplicación de adopciones, sino un espacio donde personas, organizaciones, profesionales y empresas relacionadas con los animales puedan conectarse, colaborar y ofrecer servicios dentro de una misma comunidad.

La plataforma está diseñada para crecer de forma modular, permitiendo incorporar nuevas funcionalidades sin modificar la experiencia principal del usuario.

## 2. Visión del Producto

Crear la comunidad animal más grande de Latinoamérica, donde cualquier persona pueda:

- Adoptar mascotas
- Encontrar animales perdidos
- Colaborar con refugios
- Realizar donaciones
- Contratar profesionales
- Compartir experiencias
- Acceder a información confiable
- Generar una red de ayuda para mejorar la vida de los animales

## 3. Objetivos

**Objetivo general:** Centralizar todos los servicios relacionados con mascotas en una única plataforma.

**Objetivos específicos:**

- Facilitar adopciones responsables
- Reducir el abandono animal
- Mejorar la comunicación entre refugios y la comunidad
- Digitalizar procesos actualmente dispersos
- Generar una comunidad activa
- Promover el bienestar animal mediante educación y colaboración
- Permitir que profesionales y emprendimientos lleguen a más personas

## 4. Tipos de Usuarios

Todos los usuarios comienzan con un **único perfil**. Sobre ese perfil pueden activar uno o varios módulos según sus necesidades.

| Categoría | Perfiles |
|-----------|----------|
| **Usuario** | Amantes de los animales: publicar, mascotas, comentar, seguir, chatear, adoptar, donar, eventos |
| **Organización** | Refugios, fundaciones, ONG, asociaciones |
| **Profesional** | Veterinarios, educadores, adiestradores, paseadores, etólogos |
| **Empresa** | Veterinarias, tiendas, marcas, peluquerías, laboratorios |

## 5. Arquitectura de Módulos

Módulos previstos: Red Social, Perfil de Mascotas, Adopciones, Hogares de Tránsito, Refugios, Veterinarias, Educadores, Paseadores, Tiendas, Mascotas Perdidas, Donaciones, Eventos.

**Servicios compartidos:** Perfil, Chat, Notificaciones, Seguidores, Reputación, Búsquedas, Configuración.

**Implementación:** `domain/LeoverModule.kt`, `User.resolvedModules`, columna `users.active_modules` en Supabase.

## 6–21. Módulos y sistemas

Ver documento completo en PDF o [`leover-requirements.md`](leover-requirements.md) para historias de usuario detalladas (US-*).

La **matriz de permisos (§20)** está implementada en `domain/ModulePermissions.kt`.

## 22. Roadmap de Desarrollo

| Fase | Alcance |
|------|---------|
| **Fase 1 — MVP** | Registro, login, perfil usuario/mascota, red social, adopciones, perdidos, búsquedas |
| **Fase 2 — Comunidad** | Tránsito, refugios, donaciones, eventos, reputación, insignias |
| **Fase 3 — Servicios** | Veterinarias, educadores, paseadores, tiendas, agenda, reservas, pagos |
| **Fase 4 — Inteligente** | IA, matching, historial clínico, analíticas, API pública |

---

## Conclusión

Leover está concebida como una plataforma modular, escalable y centrada en la comunidad. Cada usuario mantiene una única identidad digital y puede activar distintos módulos según sus necesidades, compartiendo reputación, seguidores, historial y comunicación en un único ecosistema.
