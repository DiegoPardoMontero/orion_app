# Avisar a Pardo, por correo y notificación, de cada profe que se registre o postule

## Metadatos

- **Fecha y hora:** 2026-10-15 08:45 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Commits relacionados:** `e8df8a1` (en `develop`, sin desplegar)
- **Contexto:** el correo de invitación a profes **ya se envió**; las postulaciones pueden llegar en
  cualquier momento, con usuarios reales en producción.

## El pedido exacto

> Asegurate de que cuando se empiecen a registrar nuevos profes (ya envíamos correo), me envíes notificaciones y correos a diegopardomontero@gmail.com, para no perder ninguno.

## Resumen ejecutivo

- **Como** dueño de Orión, **quiero** recibir un correo en diegopardomontero@gmail.com y una
  notificación dentro de Orión cada vez que un profe se registre y cada vez que envíe su postulación
  **para** no perder ninguno y revisarlos el mismo día.

Features: (1) correo al equipo (`ORION_ALERTS_TO`) cuando alguien crea una cuenta de aspirante a
profesor y cuando envía la postulación a revisión; (2) notificación en la campana del admin por los
mismos dos hechos; (3) comprobar que en producción `ORION_ALERTS_TO` apunta a
diegopardomontero@gmail.com.

## Cómo quedó

- **Lo que ya había:** al enviar una postulación (o reenviarla con cambios) salía un correo a cada
  cuenta de admin activa. Si la cuenta de admin de producción no tiene el correo de Pardo, ese aviso
  no le llegaba a él.
- **Ahora:** los correos del equipo van a los admins **y además a `ORION_ALERTS_TO`** (por defecto
  diegopardomontero@gmail.com; en Railway puede estar puesta), sin repetir si es el mismo correo.
- **Nuevo aviso «profe nuevo»:** en cuanto alguien abre su postulación por primera vez (es lo que pasa
  justo después de crear la cuenta por «Quiero enseñar» o por invitación), correo al equipo con nombre
  y correo del profe, y notificación en la campana del admin (`TEACHER_APPLICANT_REGISTERED`, enlaza
  a Usuarios). Al enviar la postulación, otra notificación (`TEACHER_APPLICATION_SUBMITTED`, enlaza a
  la postulación) además del correo que ya existía.
- **Para comprobar en producción:** en Railway, `ORION_ALERTS_TO` vacía o con
  diegopardomontero@gmail.com (las dos valen lo mismo; `ORION_ALERTS_ENABLED` no interviene aquí: solo
  apaga las alertas de operación), y que el remitente de correo (Resend) siga vivo, que es el mismo que
  manda las invitaciones.

## Estado

**Hecho en `develop`, sin desplegar.** Este es el pedido urgente del lote: las postulaciones pueden
llegar ya. Claude propone desplegar el lote completo cuando Pardo lo apruebe.
