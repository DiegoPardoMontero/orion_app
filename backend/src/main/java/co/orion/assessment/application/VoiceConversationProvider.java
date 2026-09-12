package co.orion.assessment.application;

/**
 * Quién sostiene la conversación por voz. Mismo patrón que {@code MeetingLinkProvider} y
 * {@code PaymentProvider}: cambiar de proveedor no puede tocar el dominio.
 *
 * <p>El audio <strong>no pasa por Orión</strong>. Este servicio solo emite una credencial de corta
 * vida y el navegador se conecta directo al proveedor. Es lo correcto para datos sensibles —lo que
 * no atraviesa nuestros servidores no lo podemos filtrar— y de paso ahorra el ancho de banda de
 * siete minutos de audio por diagnóstico.
 */
public interface VoiceConversationProvider {

    /** Abre una sesión y devuelve con qué conectarse. No transporta audio. */
    VoiceSession start(VoiceSessionRequest request);

    /** Cierra la sesión del lado del proveedor. Idempotente: cerrar dos veces no es un error. */
    void stop(String sessionRef);

    /** Cómo se llama este proveedor en {@code ai_usage_log}. */
    String name();
}
