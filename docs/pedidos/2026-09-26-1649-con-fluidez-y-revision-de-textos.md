# «Con fluidez» en vez de «Con soltura», y revisar que los textos tengan sentido en toda la plataforma

| | |
|---|---|
| **Fecha** | 26/09/2026, 16:49 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `EtiquetaDelPuntaje` (resultado del diagnóstico); wireflow v11 |
| **Commits** | `c55644f` («Con fluidez») · `9677181` (preguntas frecuentes, «Enseña con Orión», inasistencia) · `daa5b6f` (servidor e invitación) · `bbdc5b7` (pantallas), en `master` |
| **Estado** | Hecho y subido. Verificación: `./mvnw verify` 436 + 644; Vitest 140; e2e 97 en verde en la corrida completa, más las 2 que fallaron por citar textos viejos, corregidas y en verde después; 2 saltadas por el estado de la base |

## El pedido, tal cual

> Odio este texto después del diagnostico: "Con soltura", colocale "Con fluidez". Haz una revisión donde puedas ver correctamente que los textos tengan sentido en TODA la plataforma y en TODAS las pantallas.

## Resumen ejecutivo

### 1. «Con fluidez» en el resultado del diagnóstico
- **Historia:** como estudiante que termina el diagnóstico con un puntaje de 65 a 84, leo «Con
  fluidez», un nombre que entiendo y que me suena bien, en vez de «Con soltura».
- **Feature:** el tramo 65–84 de `EtiquetaDelPuntaje` se llama «Con fluidez». Se calcula al leer,
  así que también cambia en los resultados que ya existían.

### 2. Revisión de los textos de toda la plataforma
- **Historia:** como dueño quiero que cada texto de cada pantalla, correo y aviso tenga sentido, esté
  bien escrito y cuente lo que la app hace hoy, para lanzar sin frases rotas, viejas o
  contradictorias.
- **Feature:** una revisión de todos los textos, tanto en el código como en las pantallas:
  - las pantallas de los cuatro roles, más las públicas;
  - los correos, los avisos de la campana y los mensajes de Rigel;
  - los mensajes de error del servidor;
  - las etiquetas de Ajustes.
- **Qué se corrige:** errores de ortografía y de concordancia; frases que no se entienden; textos
  que contradicen cómo funciona hoy (clase de 55 min, WhatsApp obligatorio, acuerdos, comisión,
  mandato); mezcla de «tú» y «usted»; inglés que se coló en la interfaz; el nombre de Pardo.
- **Qué no se toca:** los textos legales (van al abogado) y los de Sofía que Pardo ya aprobó, salvo
  un error evidente.

## Cómo se hizo

1. **Revisión** (solo lectura), en siete partes paralelas: pantallas públicas y de cuenta; app del
   estudiante; app del profe y del aspirante; panel del admin con Ajustes; correos y avisos del
   servidor; mensajes y etiquetas del servidor; y las capturas de todas las pantallas del wireflow.
2. **Veredicto** de Claude sobre cada hallazgo, verificado contra el código antes de aplicarlo.
3. **Aplicación** por áreas que no se pisan, revisión del diff, verificación completa y subida.

## Lo que no se toca, y por qué

- **«Inglés» en la invitación a estudiantes y en el acta**: un revisor creyó que había profes de
  francés, pero Orión enseña solo inglés desde la V42.
- **«Tarifa por hora» / «/ hora»**: es la convención del producto en todas las pantallas. Cambiarla a
  «por clase» es una decisión de Pardo.
- **Textos legales**: van al abogado. Dos cosas para él:
  - Los Términos 1.1 (§ 4, paso 2) dicen «la modalidad: virtual o presencial». Todas las clases son
    virtuales desde la V30.
  - El acuerdo del profesor 2.0 habla de «cortes el día 15 y el último día de cada mes», mientras la
    app dice «corte del 16» y «del 1» (es el mismo corte: a las 00:00 del día siguiente).

## Lo más importante que se encontró y se arregló

**Roto o falso** (lo veía la gente y no era cierto):
- El botón «Unirse a la videollamada» del correo de confirmación y el .ics llevaban una ruta sin
  dominio: no abrían.
- La pantalla de pago le decía «No se te cobró nada» a quien se retractó y espera su devolución.
- La agenda del profe decía «No asististe» cuando el que faltó fue el estudiante.
- La hoja de «se cayó la conexión» le decía al profe que se le cayó al estudiante, cuando fue él.
- Errores del servidor en inglés («Unexpected error», «Access denied»).
- «De dónde viene tu saldo» mostraba el código crudo «CANCELLED_BY_STUDENT».
- El logro «Dos idiomas» no se podía ganar desde que Orión enseña solo inglés (se apaga en la V77).
- El profe veía como suyo el dinero de una clase devuelta al estudiante.
- La tabla de los Términos mostraba «${ORION_LEGAL_DOMICILIO:Carrera 32»: el « #» de la dirección
  cortaba el valor en el YAML (solo en local; en producción la variable está puesta).

**Viejo** (contradecía cómo funciona hoy la app):
- Que el profe «confirma» las reservas.
- Que las clases «empiezan en punto» (salen cada 30 minutos).
- Que a los profes se les paga «cuando la clase se dicta» (es cada quincena, por Bre-B).
- Que el ajuste de cancelación impide cancelar.
- «Disponibilidad» como nombre de pantalla.
- «Si la clase es virtual».

**Sin sentido o mal escrito:**
- «Faltan 5546 min».
- «conoceros».
- «Te falta tu foto, tu título, publicar tu perfil».
- Fechas «2026-12-25».
- «1 días».
- Logros en tercera persona.
- «Sobran 1 caracteres».
- «María Gómez, identificado con…, (mandante)».
- Mezcla de «solicitud» y «postulación».
- «marketplace» y «landings» en pantalla.
- Códigos crudos en el panel del admin (OK, TIMEOUT, payout-cut…).

**Regla del nombre:** la invitación a profes deja de mostrar el nombre y el cargo de quien invita:
firma «el equipo de Orión».
