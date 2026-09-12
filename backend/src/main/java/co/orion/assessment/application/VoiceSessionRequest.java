package co.orion.assessment.application;

/**
 * Lo que hace falta para abrir una conversación.
 *
 * @param languageCode   el idioma que la persona quiere aprender (EN | FR | ES)
 * @param scenarioPrompt el guion versionado, leído de un archivo y nunca incrustado en el código
 * @param maxSeconds     corte duro; el proveedor no debe sostener la sesión más allá
 * @param userFirstName  solo el nombre de pila. La IA saluda por su nombre; no necesita más, y
 *                       cuanto menos dato personal viaje a un tercero, mejor
 */
public record VoiceSessionRequest(String languageCode,
                                  String scenarioPrompt,
                                  int maxSeconds,
                                  String userFirstName) {
}
