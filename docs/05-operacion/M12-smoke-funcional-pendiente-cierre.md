# M12 — Smoke funcional pendiente antes del cierre final

## Estado actual

```text
M12 BLOQUE 4 CERRADO LOCALMENTE
M12 BLOQUE 3 VALIDACIÓN ESTRUCTURAL REMOTA 13/13 PASS
M12 BLOQUE 3 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

## Reglas

- Ejecutar contra Supabase de pruebas.
- Usar una organización M03 activa con una veterinaria M12 activa.
- Usar un usuario gestor autorizado.
- Usar una mascota M08 autorizada y otra ajena para la prueba negativa.
- No alterar migraciones.
- Registrar PASS o FALLO real.
- No declarar M12 cerrado si existe algún FALLO.

## Checklist

```text
Guardar settings de agenda: PASS/FALLO
Crear regla de disponibilidad: PASS/FALLO
Listar slots calculados: PASS/FALLO
Excepción CLOSED elimina slots: PASS/FALLO
Desactivar excepción restaura slots: PASS/FALLO
Solicitar turno con mascota autorizada: PASS/FALLO
Mascota ajena rechazada: PASS/FALLO
REQUESTED consume cupo: PASS/FALLO
CONFIRMED consume cupo: PASS/FALLO
Sobrecupo rechazado: PASS/FALLO
Confirmar turno: PASS/FALLO
Rechazar turno: PASS/FALLO
Cancelar como usuario: PASS/FALLO
Cancelar como clínica: PASS/FALLO
Historial de estados: PASS/FALLO
Privacidad requester/gestor: PASS/FALLO
Notas privadas protegidas: PASS/FALLO
Sin pagos: PASS/FALLO
Sin historia clínica: PASS/FALLO
```

## Resultado permitido

Si todos los puntos dan PASS:

```text
M12 BLOQUE 3 SMOKE FUNCIONAL PASS
M12 LISTO PARA CIERRE FINAL
```

Si alguno falla:

```text
M12 CIERRE FINAL BLOQUEADO
```

Documentar el defecto y corregirlo en un bloque separado. No modificar la migración 047 ya aplicada; cualquier corrección SQL posterior comienza en 048.
