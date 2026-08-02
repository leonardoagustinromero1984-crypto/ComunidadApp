# M22 — Auditoría inicial

Bloque 1 implementa una base local para perfiles de prestadores, sedes, cobertura y catálogo de servicios.

- Persistencia: memoria determinista; no hay SQL ni wiring Supabase.
- Integración M03: `organizationId` opcional, sin duplicar conceptos organizacionales.
- Integración M12: el catálogo M22 no reutiliza ni crea tablas veterinarias.
- Privacidad: las proyecciones públicas no incluyen IDs internos, propietario ni organización.
