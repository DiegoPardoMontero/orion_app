package co.orion.admin.application;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.orion.admin.api.SystemStatusResponse;
import co.orion.admin.api.SystemStatusResponse.Integracion;
import co.orion.scheduling.application.JaasTokenMinter;

/**
 * Reúne el estado de las integraciones externas para la pantalla de admin.
 *
 * <p>Cada una se comprueba como se pueda sin gastar una llamada de red: JaaS sabe decir si su llave
 * cargó, y del resto se mira si la variable llegó con algo dentro. No es una prueba de que el
 * secreto sea el correcto —eso solo lo dice usarlo— pero sí distingue los dos casos que antes se
 * confundían: «está apagado» y «está encendido y falló».
 */
@Service
public class SystemStatusService {

    private final JaasTokenMinter jaas;
    private final String cloudinaryUrl;
    private final String wompiPublicKey;
    private final String wompiIntegrity;
    private final String wompiEvents;
    private final String wompiApiBase;
    private final String resendKey;
    private final String openAiKey;
    private final String voiceProvider;

    public SystemStatusService(JaasTokenMinter jaas,
                               @Value("${CLOUDINARY_URL:}") String cloudinaryUrl,
                               @Value("${orion.payments.wompi.public-key:}") String wompiPublicKey,
                               @Value("${orion.payments.wompi.integrity-secret:}") String wompiIntegrity,
                               @Value("${orion.payments.wompi.events-secret:}") String wompiEvents,
                               @Value("${orion.payments.wompi.api-base-url:}") String wompiApiBase,
                               @Value("${RESEND_API_KEY:}") String resendKey,
                               @Value("${OPENAI_API_KEY:}") String openAiKey,
                               @Value("${orion.assessment.voice.provider:scripted}")
                               String voiceProvider) {
        this.jaas = jaas;
        this.cloudinaryUrl = cloudinaryUrl;
        this.wompiPublicKey = wompiPublicKey;
        this.wompiIntegrity = wompiIntegrity;
        this.wompiEvents = wompiEvents;
        this.wompiApiBase = wompiApiBase;
        this.resendKey = resendKey;
        this.openAiKey = openAiKey;
        this.voiceProvider = voiceProvider;
    }

    public SystemStatusResponse status() {
        boolean wompiCompleto = hay(wompiPublicKey) && hay(wompiIntegrity) && hay(wompiEvents);
        boolean sandbox = wompiApiBase != null && wompiApiBase.contains("sandbox");

        return new SystemStatusResponse(List.of(
                new Integracion("Videollamada (JaaS)", jaas.disponible(),
                        "El aula no abre: «La videollamada no está disponible ahora mismo».",
                        jaas.disponible() ? "Llave RSA cargada" : null,
                        List.of("JAAS_APP_ID", "JAAS_KEY_ID", "JAAS_PRIVATE_KEY")),

                new Integracion("Pagos (Wompi)", wompiCompleto,
                        "Reservar responde 422 y ninguna clase se confirma.",
                        sandbox ? "Apuntando al SANDBOX — no se cobra de verdad" : "Producción",
                        List.of("WOMPI_PUBLIC_KEY", "WOMPI_INTEGRITY_SECRET",
                                "WOMPI_EVENTS_SECRET", "WOMPI_API_BASE_URL")),

                new Integracion("Fotos y documentos (Cloudinary)", hay(cloudinaryUrl),
                        "Las fotos de perfil y los documentos de las postulaciones no suben.",
                        hay(cloudinaryUrl) ? cloudName() : null,
                        List.of("CLOUDINARY_URL")),

                new Integracion("Correo (Resend)", hay(resendKey),
                        "No sale ningún correo: ni verificación, ni confirmación de clase.",
                        null, List.of("RESEND_API_KEY")),

                new Integracion("Diagnóstico de voz (OpenAI)",
                        hay(openAiKey) && "openai".equals(voiceProvider),
                        elMotivo(),
                        "openai".equals(voiceProvider) ? "Conversación real" : null,
                        List.of("OPENAI_API_KEY", "ORION_VOICE_PROVIDER"))));
    }

    /**
     * Por qué el diagnóstico no está listo. Se distinguen los dos casos porque se arreglan distinto,
     * y el segundo es el peligroso: con el proveedor falso la conversación ocurre y es de mentira.
     */
    private String elMotivo() {
        if (!"openai".equals(voiceProvider)) {
            return "ORION_VOICE_PROVIDER no es «openai»: la conversación sería simulada, no real.";
        }
        return "Falta la llave: el diagnóstico no puede iniciar la conversación.";
    }

    /** El cloud, que no es secreto y es justo lo que se compara con la consola de Cloudinary. */
    private String cloudName() {
        int arroba = cloudinaryUrl.lastIndexOf('@');
        return arroba < 0 ? null : "cloud " + cloudinaryUrl.substring(arroba + 1);
    }

    private static boolean hay(String valor) {
        return valor != null && !valor.isBlank();
    }
}
