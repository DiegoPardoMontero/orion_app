package co.orion.assessment.domain;

/**
 * Un turno de la persona, ya medido. Es lo único que entra al cálculo: ni audio, ni texto, ni
 * modelo — solo señales, que es lo que hace el puntaje reproducible.
 *
 * @param latencyMs        cuánto tardó en empezar a responder
 * @param wordCount        palabras que dijo
 * @param selfCorrections  veces que se corrigió a mitad de frase
 * @param abandonedClauses frases que empezó y soltó
 * @param fillerCount      muletillas
 * @param nativeSwitch     si se devolvió a su idioma en este turno
 */
public record TurnoDelUsuario(int latencyMs,
                              int wordCount,
                              int selfCorrections,
                              int abandonedClauses,
                              int fillerCount,
                              boolean nativeSwitch) {
}
