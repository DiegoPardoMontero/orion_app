# Lo que dijo el abogado, y la megarrevisión del flujo del profesor antes del lanzamiento

| | |
|---|---|
| **Fecha** | 27/09/2026, 10:02 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo, con instrucciones de su abogado entre `<AB>` y `</AB>` |
| **Relacionado** | `2026-09-26-1649-con-fluidez-y-revision-de-textos.md` (de ahí salieron las preguntas al abogado) |
| **Estado** | En curso |

## El pedido, tal cual

> Hablé con mi abogado, cada que te vaya a decir lo que el diga te colocaré AB. Entonces, <AB> No, TODO es virtual, modifica los términos 1.1. La política debe modificarse y contemplar la plataforma WhatsApp como obbligatoria. Que digan lo mismo entonces, aclarar que es el 15 y el último día del mes. </AB>  No usaremos la función de cambiar horario por ahora. ¿Puedes revisar de e2e el flujo del profesor? Tenemos ganas de hacer un lanzamiento lo más pronto posible y queremos que los profes que se registren y que no vayan a tener ningún bug. Haz un megareview a esto y asegurame que todo va bien.

## Convención

**Lo que va entre `<AB>` y `</AB>` es lo que dijo el abogado de Pardo.** Es una instrucción legal: se
aplica tal cual y se cita en el commit y en el registro.

## Resumen ejecutivo

### 1. Los textos legales, según el abogado
- **Historia:** como usuario leo en los Términos exactamente cómo funciona Orión: todas las clases
  son virtuales.
  **Feature:** los Términos 1.1 se corrigen en su sitio: fuera «virtual o presencial», «empiezan en
  punto» y «si la clase es virtual».
- **Historia:** como usuario sé que la Política me pide el WhatsApp como dato obligatorio.
  **Feature:** la Política 1.1 trata el WhatsApp como obligatorio.
- **Historia:** como profe leo la misma fecha de corte en el acuerdo y en la app: el 15 y el último
  día del mes.
  **Feature:** la app nombra el corte por el día 15 y el último día del mes (hoy dice «del 1» y
  «del 16», que es el mismo instante visto desde el día siguiente).

### 2. Cambiar el horario de una clase
- **Decisión:** no se usa por ahora. El backend la tiene, pero no hay pantalla. No se toca.

### 3. Megarrevisión del flujo del profesor
- **Historia:** como profe que se registra para el lanzamiento, paso sin tropiezos por todo el
  camino: registro («Quiero enseñar» o invitación), postulación, aprobación, primera entrada
  (acuerdos, datos de pago, bienvenida), perfil y horarios, primera reserva, clase, acta,
  ganancias y liquidación.
- **Feature:** revisión de punta a punta:
  - código del frontend y del backend;
  - recorrido real en el navegador, en celular y en escritorio;
  - una prueba e2e nueva que recorra el camino completo;
  - arreglo de todo lo que aparezca.

## Respuestas de Pardo

- 27/09, 21:44, al ver que la sesión de la mañana se había cortado a las 11:27 con los arreglos de la
  megarrevisión sin integrar: «Eso, sigue trabajando en TODO por favor. Hasta que termines.» → Se
  retoma: integrar los arreglos de backend y frontend, terminar la prueba e2e del camino completo y
  cerrar con `./mvnw verify` en verde.
