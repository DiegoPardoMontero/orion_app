# Sexta tanda: landing, sin mi nombre, un solo «aceptar acuerdos» y datos de pago obligatorios

| | |
|---|---|
| **Fecha** | 26/09/2026, mañana (antes de las 11:14; la hora exacta no quedó registrada: la regla llegó después) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Brief** | `docs/briefs/orion-bloque-11-refinamiento.md` → «Sexta tanda (26/09)», pasos 41–45 |
| **Commits** | `9f0a440`, `2a2faf8`, `1098c9b` (los 6 archivos) · `e4d3072` (brief) · `52afdac` (41) · `fa520ae` (43) · `3062ac2` (44) · paso 45 al cerrar |
| **Estado** | En curso: código de los cinco pasos hecho y el backend verificado; faltan el e2e, el wireflow, subirlo y el correo |

## El pedido, tal cual

> Quita esto del landing: Dos minutos de conversación, gratis y sin crear cuenta. Al terminar sabes en qué nivel estás y te mostramos tres profesores que encajan contigo. Puedes hacerlo en español si prefieres.
> Sí, haz commit y subelos. Tengo una duda, eso sí, ¿podrías de los acuerdos (y en general) no mencionar mi nombre sino solo decir "Orion" y "Los representantes legales de Orion"? Así mismo, me gusta más cuando se coloca un aceptar nuevos acuerdos y un botón que sí los expande todos, pero RECUERDA. NO HAY PROFES EN PRODUCCIÓN, por ahora todos van a entrar a aceptar los nuevos acuerdos. Otra cosa, los DATOS DE PAGO, son IMPORTANTÍSIMOS, los profes sí o sí deben llenarlos.

## Resumen ejecutivo

### 1. La portada sin el párrafo del diagnóstico (paso 41)
- **Historia:** como visitante veo bajo los botones del inicio solo lo esencial, sin el párrafo «Dos
  minutos de conversación…».
- **Feature:** se quitó el párrafo. `/diagnostico` no cambia: Pardo dijo que se deja como está.

### 2. Los 6 archivos de la sesión anterior (paso 42)
- **Historia:** como dueño quiero que esos arreglos ya probados queden guardados y en producción.
- **Feature:** tres commits, subidos:
  - el aviso de fin de fundador ya no se pierde;
  - la limpieza de `accept-invite`;
  - el registro con una invitación vencida deja elegir «Quiero aprender».

### 3. Sin el nombre de Pardo en los acuerdos «y en general» (paso 43)
- **Historia:** como dueño quiero que los textos digan «Orión» y «los representantes legales de
  Orión», no mi nombre.
- **Feature:**
  - Términos y Política pasan a la 1.1, sin el nombre en la redacción.
  - El acuerdo del profesor 2.0 dice «le encargas a Orión».
  - El comprobante y el correo de pago dicen «Orión».
- **Límite de ley:** el nombre sigue solo en dos sitios, porque la ley obliga a decir quién
  responde. Sale de `ORION_LEGAL_*`, así que con una sociedad basta cambiar esas variables.
  - La tabla de identificación de los Términos (art. 50 de la Ley 1480) y de la Política (art. 13
    del Decreto 1377).
  - El certificado anual, que es un documento tributario.
- **Para el abogado:** «representantes legales» supone una sociedad; hoy el responsable es una
  persona natural.

### 4. Un solo «Aceptar los nuevos acuerdos» (paso 44)
- **Historia:** como usuario que entra, veo una sola ventana con los acuerdos que me faltan, los
  acepto con un botón y puedo desplegarlos todos para leerlos.
- **Historia:** como profe aprobado, en esa ventana acepto también el acuerdo del profesor con el
  mandato de recaudo.
- **Feature:**
  - La ventana «Acepta los nuevos acuerdos» no se cierra ni se aplaza: ya no hay «Ahora no».
  - «Ver los acuerdos completos» despliega todos los textos.
  - La autorización de datos lleva su propia casilla, como en el registro (Decreto 1377).
  - Se les pide a todos menos al admin, incluidas las cuentas que crea el admin, que nunca habían
    aceptado los Términos.

### 5. Datos de pago obligatorios (paso 45)
- **Historia:** como dueño quiero que ningún profe aprobado pueda seguir sin decirnos a dónde le
  pagamos.
- **Feature:**
  - Ventana «Falta a dónde te pagamos», sin salida, después de los acuerdos y antes de la
    bienvenida.
  - Reusa el formulario de la llave Bre-B.
