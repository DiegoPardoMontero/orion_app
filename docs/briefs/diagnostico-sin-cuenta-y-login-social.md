# Diagnóstico sin cuenta, login social y Meissa

> Encargado por Pardo el 22/09/2026 en el chat, con el handoff «Meissa y Diagnostico Handoff»
> (en la raíz del repo, `.zip`). Las decisiones de abajo las tomó él, respondiendo a preguntas
> directas; no se vuelven a abrir.

## Por qué

El diagnóstico es la puerta de la portada y **pedía cuenta antes de hablar**. Cada paso antes de la
conversación cuesta gente, y el peor sitio para pedir algo es justo antes de hablar un idioma que no
dominas. Además, el resultado no ayudaba: cuando la conversación se iba al español decía «esta vez
no te ponemos número» y a veces «todavía no tenemos tres para ti», que es cerrar la puerta a quien
acaba de atreverse.

## Decisiones

| Tema | Decisión |
|---|---|
| Cuenta | **Ninguna hasta reservar.** Nombre + dos casillas (mayor de 18; permiso de voz), separadas porque juntar consentimientos los vicia. La cuenta se pide al reservar y el diagnóstico pasa a ella. |
| Guardar el resultado | «¿Te lo guardamos?» opcional en el resultado: Google o correo. |
| Abuso | Límite por dispositivo, además del tope diario de gasto que ya existe. |
| Datos sin dueño | El diagnóstico de quien nunca crea cuenta se borra a los 30 días. |
| Login social | Google, Apple y Facebook. **Google primero.** Apple queda listo y apagado hasta que se paguen los 99 USD/año. Un botón solo aparece si su proveedor está configurado, y su estado se ve en Administración → Sistema. |
| Tarjeta del resultado | Confidence Score + etiqueta de su tramo, con los nombres del handoff: «Primeros pasos», «Básico con ganas», «Ya te defiendes», «Con soltura», «Casi sin pensarlo». **Nunca A1–C2**: no lo medimos. |
| Rama en español | Etiqueta sin número, resumen de lo que contó, ánimo y 3 profesores. |
| Profesores | **Siempre tres.** Si los criterios no alcanzan, se completa con los mejor rankeados. Solo si la plataforma entera tiene menos de tres, se muestran los que haya. |
| Resumen | Personalizado: qué contó en la llamada y por qué Orión le sirve para eso. Lo redacta la IA a partir de la transcripción. |
| Elogio | Del esfuerzo, no de cómo habló («Hablaste dos minutos en inglés… eso ya es un gran paso»). La regla de marca y su test se quedan. |
| Durante | Conversación libre como hoy, con lo visual del handoff: Meissa y sus estados, turno X de 6, reloj, la pregunta escrita y «Salir» con confirmación. No por turnos. |
| Mascota | **Meissa** en todo el flujo del diagnóstico, incluida `/diagnostico`. Rigel recibe en el registro. |
| «Te llamamos» | Sí: nombre + WhatsApp + autorización; correo a la academia y lista en el admin. |

## Pasos (un commit cada uno, `./mvnw verify` en verde)

1. **Portada.** Héroe: «Orión es una academia de inglés especializada; antes de empezar, toma tu
   diagnóstico de inglés». Botón del diagnóstico a casi todo el ancho; «Ver profesores», «Crear
   cuenta» y «Ya tengo cuenta» en una sola fila, del mismo ancho y más pequeños.
2. **Resultado.** Siempre tres profesores, score con etiqueta, rama en español sin número, resumen
   personalizado y ánimo. Una pantalla corta.
3. **Meissa.** El personaje (SVG literal del handoff) con sus cuatro estados; `/diagnostico` sin
   scroll y sin pasos; la pantalla «durante» con sus estados.
4. **Sin cuenta.** El diagnóstico como lead: nombre y casillas, límite por dispositivo, paso a la
   cuenta al registrarse, borrado a los 30 días.
5. **Login social.** Google, Facebook y Apple con Spring Security OAuth2; las casillas legales se
   piden una vez tras el primer ingreso; vinculación con una cuenta existente del mismo correo
   verificado.
6. **«Te llamamos».**

## Fuera de alcance

Niveles MCER, pantalla por turnos, traducción de las preguntas, app nativa.
