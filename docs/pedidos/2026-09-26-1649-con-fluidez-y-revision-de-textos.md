# «Con fluidez» en vez de «Con soltura», y revisar que los textos tengan sentido en toda la plataforma

| | |
|---|---|
| **Fecha** | 26/09/2026, 16:49 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `EtiquetaDelPuntaje` (resultado del diagnóstico) |
| **Estado** | En curso |

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
