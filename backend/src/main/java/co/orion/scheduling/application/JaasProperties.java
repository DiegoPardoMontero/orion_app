package co.orion.scheduling.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Las credenciales de Jitsi as a Service (JaaS), el proveedor de videollamada.
 *
 * <p>Se pasó de {@code meet.jit.si} público a JaaS por una razón concreta: en la sala pública manda
 * quien entra primero, así que un estudiante que llegaba antes podía silenciar o expulsar a su
 * propio profesor. Eso no se arregla desde la URL — el control de moderador exige un token firmado.
 * Con JaaS, Orión firma un JWT por participante y decide quién es moderador.
 *
 * <p><strong>La llave privada nunca vive en el repositorio.</strong> En producción va por variable
 * de entorno; en local, en {@code ~/.orion/jaas/}. Si falta, {@link #configurado()} da falso y el
 * aula se apaga con un mensaje claro en vez de arrancar y fallar al primer clic.
 *
 * <p>La llave se acepta en PEM de una sola línea (con {@code \n} literales, que es como sobrevive a
 * una variable de entorno de Railway) o en PEM de varias líneas.
 */
@ConfigurationProperties(prefix = "orion.jaas")
public record JaasProperties(String appId, String keyId, String privateKey, String domain,
                             String webhookSecret) {

    public JaasProperties {
        domain = domain == null || domain.isBlank() ? "8x8.vc" : domain;
    }

    /**
     * Si llegan los webhooks: el secreto del endpoint en la consola de JaaS (Webhooks → «Reveal
     * secret»). Sin él no se puede comprobar quién manda los eventos, y entonces no se procesa
     * ninguno: la antesala sigue diciendo «aún no ha entrado», que es la única respuesta honesta.
     */
    public boolean webhooksConfigurados() {
        return notBlank(webhookSecret);
    }

    public boolean configurado() {
        return notBlank(appId) && notBlank(keyId) && notBlank(privateKey);
    }

    /** El PEM tal como lo espera un {@code KeyFactory}: sin cabeceras y sin saltos. */
    public String privateKeyBase64() {
        return privateKey
                .replace("\\n", "\n")
                .replaceAll("-----BEGIN (RSA )?PRIVATE KEY-----", "")
                .replaceAll("-----END (RSA )?PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
