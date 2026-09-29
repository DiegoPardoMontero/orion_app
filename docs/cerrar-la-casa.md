# Cerrar la casa: guía paso a paso

Pedido de Pardo del 29/09/2026 (`docs/pedidos/2026-09-29-1201-estado-actual-de-la-plataforma.md`). Es
la semana 1 de `docs/plan-de-lanzamiento.md`: lo que hay que dejar listo en producción antes de
cobrarle a alguien que no sea de la familia. Casi todo es configuración en paneles; nada exige
tocar código.

## 0. Tu lista de control: Administración → Sistema

Cada integración aparece encendida o apagada. Si está apagada, dice qué pasa sin ella y qué
variable de Railway falta. **Al terminar deben estar encendidas todas estas**:
- videollamada;
- presencia en el aula;
- pagos;
- fotos y documentos;
- correo;
- avisos en el dispositivo;
- las dos de OpenAI.

Los botones de «Entrar con Google, Microsoft, Facebook o Apple» son opcionales; Apple espera al
programa de desarrollador. Todas las variables de abajo van en Railway, en el servicio del
**backend**. Al guardarlas, Railway vuelve a desplegar solo.

## 1. Wompi en producción (lo más importante)

1. En el panel de comercios de Wompi, en la sección de desarrolladores, copia las llaves de
   **producción**. En Railway:
   - `WOMPI_PUBLIC_KEY`: la pública (`pub_prod_…`);
   - `WOMPI_INTEGRITY_SECRET`: el secreto de integridad;
   - `WOMPI_EVENTS_SECRET`: el secreto de eventos.
2. `WOMPI_API_BASE_URL` = `https://production.wompi.co/v1`. **Sin esta variable, Orión cobra en el
   sandbox**, sin avisar a nadie.
3. En Wompi, la URL de eventos de producción:
   `https://orionidiomas.com/api/v1/webhooks/payments/wompi`.
4. Regenera la **llave privada** que pasó por el chat. Orión no la usa, pero ya no es secreta. Si el
   panel no deja regenerarla, pídeselo al soporte de Wompi.
5. Comprueba en Sistema que «Pagos (Wompi)» diga **«Producción»** y no «SANDBOX».

## 2. OpenAI (el diagnóstico con Meissa, el acta y la práctica)

1. En platform.openai.com → API keys, crea una llave nueva y ponla en Railway como
   `OPENAI_API_KEY`. Cuando el despliegue termine, **revoca la vieja**, que pasó por la terminal.
2. `ORION_VOICE_PROVIDER` = `openai`. Si no está, Meissa usa un guion fijo y el acta se arma sin IA.
3. En OpenAI → Settings → Limits, pon un **tope de gasto mensual**. Para decenas de usuarios, unos
   USD 20 alcanzan para empezar; se sube si hace falta.
4. Si quieres que siga probando la IA en local, cambia también la llave de `~/.orion/openai-key`.

## 3. JaaS (la videollamada)

1. Comprueba que «Videollamada (JaaS)» esté encendida en Sistema: usa `JAAS_APP_ID`, `JAAS_KEY_ID` y
   `JAAS_PRIVATE_KEY`.
2. En la consola de JaaS (jaas.8x8.vc) → Webhooks, crea un endpoint:
   - URL: `https://orionidiomas.com/api/v1/webhooks/video/jaas`;
   - eventos: `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT` y `SPEAKER_STATS`;
   - su secreto va en Railway como `JAAS_WEBHOOK_SECRET`.

   Sin esto, la antesala dice siempre «aún no ha entrado», y no hay tiempo hablado ni puntualidad
   del profe.
3. Comprueba que «Presencia en el aula» esté encendida. Si los eventos llegan con error 401, avísame:
   la documentación de 8x8 firma de dos maneras y el primer evento real dice cuál usa.

## 4. Fotos, correo y avisos

- **Cloudinary** (`CLOUDINARY_URL`): pruébalo subiendo una foto de perfil.
- **Correo** (`RESEND_API_KEY`): en Sistema, «Correo de prueba» te manda uno. Si no llega, revisa en
  Resend que el dominio del remitente esté verificado.
- **Avisos en el dispositivo**: las claves VAPID ya están (28/09). No hay que hacer nada.

## 5. Ajustes y limpieza

- **El perfil «Diego Pardo Test 28/09»** se ve en el catálogo público. Entra con esa cuenta, ve a Mi
  perfil, apaga «Perfil visible» y guarda.
- **Plazo de revisión de postulaciones**: hoy se le prometen 3 días hábiles al aspirante. O Sofía
  revisa el mismo día, o bájalo a 1 en Ajustes → «Plazo para revisar una postulación».
- **El mandatario**: escribe tu nombre y documento según el RUT en Ajustes → «Nombre / Documento del
  mandatario». Salen en el certificado anual de los profes.

## 6. Una transacción real de punta a punta

Con el primer profe, y tú de estudiante con tu propio medio de pago:

1. El profe entra (invitado como fundador desde Usuarios, o por «Enseña con Orión»). Tú apruebas su
   postulación. Él publica su perfil, abre horarios y registra su llave Bre-B.
2. Reservas y pagas una clase de verdad: tarjeta, PSE o Nequi.
3. Dan la clase en la sala. Si la antesala muestra al otro cuando entra, el webhook de JaaS
   funciona.
4. El profe marca la asistencia y escribe el acta; tú la ves y haces la práctica.
5. Reservas y pagas una segunda clase y la cancelas con más de 12 h: el valor tiene que volver
   como saldo a favor.
6. En el corte siguiente (00:00 del 1 o del 16), la liquidación del profe aparece en Administración.
   Le pagas por Bre-B, la marcas pagada y él ve su comprobante.

Si algo de esto falla, avísame con la hora y la pantalla: el registro de producción lo explica.

## 7. Personas que hay que agendar esta semana

- **Sofía**: que lea el texto del resultado del diagnóstico.
- **Contador**: facturación electrónica de la comisión, retención en la fuente, y si alcanza con tu
  nombre como mandatario o hace falta una sociedad.
- **Abogado laboral**: que la relación con el profe sea de contratista.
- **Abogado**: la política de datos 1.2 tiene que nombrar a OpenAI y a 8x8, y decir qué se toma al
  entrar con Google o Facebook.

## 8. Lo que me toca a mí

- Probar si la IP real llega detrás del proxy de Railway (`X-Forwarded-For`). De eso dependen los
  frenos por IP del login. Lo hago cuando me digas.
