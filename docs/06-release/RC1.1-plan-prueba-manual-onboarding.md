# RC1.1 — Plan de prueba manual — Onboarding

**Entorno:** APK debug posterior a RC1.1 (no generada en esta entrega).  
**Estado global:** prueba física **PENDIENTE**.

| ID | Recorrido | Pasos resumidos | Estado |
|----|-----------|-----------------|--------|
| OB-01 | Primera apertura | Login → sesión nueva → verificar bienvenida LeoVer | PENDIENTE |
| OB-02 | Comenzar | Comenzar → pantallas 1–3 → intención → setup → privacidad → final | PENDIENTE |
| OB-03 | Omitir | Desde pantalla informativa → Omitir tutorial → llegada a inicio | PENDIENTE |
| OB-04 | Explorar primero | Bienvenida → Explorar primero → inicio sin bloqueo | PENDIENTE |
| OB-05 | Cerrar y continuar | Avanzar a paso 2, cerrar app, reabrir → mismo paso | PENDIENTE |
| OB-06 | Registrar mascota | Intención mascota → CTA final → `ADD_PET` | PENDIENTE |
| OB-07 | Pérdida | Intención pérdida → alerta → `PUBLISH_LOST_FOUND` | PENDIENTE |
| OB-08 | Hallazgo | Intención hallazgo → informar → `PUBLISH_LOST_FOUND` | PENDIENTE |
| OB-09 | Adopciones | Intención adoptar → `SUMATE` | PENDIENTE |
| OB-10 | Tránsito | Intención tránsito → `PUBLISH_FOSTER` | PENDIENTE |
| OB-11 | Organización | Intención organización → `MY_ORGANIZATIONS` | PENDIENTE |
| OB-12 | Voluntariado | Intención voluntario → `M17_HUB` | PENDIENTE |
| OB-13 | Privacidad | Pantalla privacidad → Revisar privacidad → documento legal | PENDIENTE |
| OB-14 | Permisos no anticipados | Completar onboarding sin diálogos de sistema de permisos | PENDIENTE |
| OB-15 | Ayuda contextual | Primera visita a mascotas/alertas/adopciones/refugios → banner; segunda visita sin banner | PENDIENTE |
| OB-16 | Reiniciar tutorial | Perfil → Ver tutorial de inicio → flujo desde bienvenida | PENDIENTE |
| OB-17 | Texto ampliado / TalkBack | Ampliar texto del sistema; recorrer con TalkBack | PENDIENTE |

## Criterios de aceptación

- Onboarding opcional y omitible
- Progreso recordado localmente
- Intención no crea rol ni restringe módulos
- Permisos solo en contexto de uso posterior
- Marca visible: **LeoVer**
