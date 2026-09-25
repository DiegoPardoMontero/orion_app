package co.orion.messaging.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import co.orion.messaging.domain.RigelKind;

/**
 * Lo que dice Rigel en cada mensaje, y a dónde llevan sus botones.
 *
 * <p>Clase pura: el tipo y sus datos entran, el texto sale. Vive aquí y no en la base para que
 * corregir una frase no sea una migración. Los botones son siempre rutas de la propia app: Rigel no
 * manda a nadie fuera de Orión.
 *
 * <p>La voz es la de Rigel en el resto de la app: cercana, corta y en segunda persona, sin
 * mayúsculas ni signos de más. Cada mensaje dice qué pasó, qué sigue y deja el botón a mano.
 */
public final class TextosDeRigel {

    private TextosDeRigel() {
    }

    public record Boton(String etiqueta, String ruta) {
    }

    public record Mensaje(String titulo, String cuerpo, List<Boton> botones) {
    }

    /**
     * @param nombre el primer nombre de quien lo lee
     * @param datos  lo que se guardó con el mensaje: «profesor», «profesorId», «estudiante»
     */
    public static Mensaje de(RigelKind tipo, String nombre, Map<String, String> datos) {
        String profesor = datos.getOrDefault("profesor", "tu profe");
        String estudiante = datos.getOrDefault("estudiante", "tu estudiante");
        String hola = nombre == null || nombre.isBlank() ? "¡Hola! Soy Rigel ✨" : "¡Hola, " + nombre + "! Soy Rigel ✨";
        return switch (tipo) {
            case WELCOME_STUDENT -> new Mensaje(hola,
                    """
                    Por aquí te escribo lo importante de Orión, sin ruido. Así funciona:
                    1. Eliges profe y reservas una hora de su agenda.
                    2. La clase es por videollamada, aquí mismo, desde «Mis clases».
                    3. Después te llega el resumen de tu profe y una práctica corta para que no se te olvide.
                    Cada clase y cada práctica encienden estrellas en tu cielo.""",
                    List.of(new Boton("Buscar profesor", "/profesores"),
                            new Boton("Completar mi ficha", "/cuenta?seccion=ficha"),
                            new Boton("Hacer el diagnóstico", "/diagnostico")));
            case WELCOME_PROFESSOR -> new Mensaje(hola,
                    """
                    Por aquí te escribo lo importante de Orión, sin ruido. Para recibir estudiantes:
                    1. Marca tus horarios de la semana.
                    2. Completa y publica tu perfil: foto, titular y tarifa.
                    3. Comparte tu enlace con tus estudiantes.
                    Las clases se dan aquí mismo por videollamada. Al terminar escribes el acta, y de ella sale la práctica de tu estudiante.""",
                    List.of(new Boton("Mis horarios", "/perfil?seccion=horarios"),
                            new Boton("Mi perfil", "/perfil"),
                            new Boton("Invitar estudiantes", "/invitar")));
            case FIRST_BOOKING_STUDENT -> new Mensaje("Tu primera clase está en camino 🎉",
                    "Reservaste con " + profesor + ". El día de la clase entras desde «Mis clases»: el aula se "
                            + "abre unos minutos antes. Si quieres contarle algo antes, escríbele por Mensajes. "
                            + "¿Un imprevisto? Puedes cancelar desde la misma clase.",
                    List.of(new Boton("Ver mis clases", "/mis-clases"),
                            new Boton("Cómo funciona una clase", "/ayuda")));
            case FIRST_BOOKING_PROFESSOR -> new Mensaje("¡Tu primera reserva! 🎉",
                    estudiante + " reservó contigo. Mira su ficha antes de la clase para prepararla; el aula se "
                            + "abre en tu agenda unos minutos antes. Al terminar, escribe el acta: con ella armo "
                            + "la práctica de tu estudiante.",
                    List.of(new Boton("Ver mi agenda", "/mis-clases")));
            case FIRST_CLASS_STUDENT -> new Mensaje("¡Primera clase lista! ⭐",
                    "Encendiste tu primera estrella. " + profesor + " te deja un resumen de la clase y, con él, "
                            + "te preparo una práctica de cinco ejercicios. Practicar también cuenta para tu racha "
                            + "de la semana.",
                    List.of(new Boton("Ver el resumen", "/mis-clases?scope=past"),
                            new Boton("Ver mi racha", "/cuenta?seccion=resumen")));
            case FIRST_CLASS_PROFESSOR -> new Mensaje("Diste tu primera clase en Orión ⭐",
                    "Ahora escribe el acta de la clase con " + estudiante + ": la lee tu estudiante y de ella "
                            + "salen sus ejercicios. Lo que ganaste queda retenido hasta que la clase se cierre y "
                            + "después pasa a «Por cobrar».",
                    List.of(new Boton("Escribir el acta", "/mis-clases?scope=past"),
                            new Boton("Ver mis ganancias", "/ganancias")));
            case COME_BACK -> {
                List<Boton> botones = new ArrayList<>();
                String profesorId = datos.get("profesorId");
                if (profesorId != null && datos.containsKey("profesor")) {
                    botones.add(new Boton("Reservar con " + profesor, "/profesores/" + profesorId));
                } else {
                    botones.add(new Boton("Buscar profesor", "/profesores"));
                }
                botones.add(new Boton("Ver mi racha", "/cuenta?seccion=resumen"));
                yield new Mensaje("¿Seguimos? 🌙",
                        "Hace unas semanas que no tienes clase. Una esta semana vuelve a encender tu racha."
                                + (datos.containsKey("profesor")
                                        ? " Puedes reservar otra vez con " + profesor + " o probar con alguien nuevo."
                                        : ""),
                        List.copyOf(botones));
            }
        };
    }
}
