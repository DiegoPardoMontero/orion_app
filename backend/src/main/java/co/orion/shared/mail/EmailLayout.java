package co.orion.shared.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Envuelve el cuerpo de un correo en la plantilla de marca: cabecera con el logo, tarjeta blanca y
 * pie. Quien redacta un correo sigue escribiendo solo su fragmento ({@code <p>}, {@code <ul>}…) y
 * no sabe nada de esto; el envoltorio lo aplica {@link BrandedMailTransport} en el camino de salida.
 *
 * <p>Tres decisiones que en HTML de correo no son estilísticas sino de supervivencia:
 *
 * <ul>
 *   <li><strong>Tablas, no divs.</strong> Outlook de escritorio renderiza con el motor de Word, que
 *       ignora {@code flex}, {@code grid} y buena parte de {@code max-width}. La tabla centrada de
 *       600 px es el único armazón que se comporta igual en todos los clientes.</li>
 *   <li><strong>El logo es una URL, no un adjunto embebido.</strong> Un {@code cid:} obligaría a
 *       adjuntar el PNG en cada correo y la API HTTP de Resend no lo entrega igual que el SMTP
 *       local; una URL pública funciona idéntica en los dos transportes. El precio es que los
 *       clientes que bloquean imágenes remotas no lo muestren: por eso el {@code alt} dice "Orión"
 *       y el pie repite el nombre en texto.</li>
 *   <li><strong>El PNG viene aplanado sobre el crema de marca.</strong> Los clientes en modo oscuro
 *       invierten el HTML pero no las imágenes: con fondo transparente, el logotipo ciruela quedaría
 *       oscuro sobre oscuro. Con su propio fondo claro se lee siempre.</li>
 * </ul>
 */
@Component
public class EmailLayout {

    /** Paleta del sistema v2 "Amanecer": los mismos tokens que `globals.css`, aquí en literales. */
    private static final String CREMA = "#fff6ee";
    private static final String BLANCO = "#ffffff";
    private static final String BORDE = "#eadfd4";
    private static final String TINTA = "#33203b";
    private static final String TINTA_TENUE = "#7a6b85";
    private static final String CORAL = "#c0341f";

    private static final int LOGO_ANCHO = 180;
    private static final int LOGO_ALTO = 62;

    private final String logoUrl;

    public EmailLayout(@Value("${orion.app.base-url}") String baseUrl) {
        this.logoUrl = baseUrl.replaceAll("/+$", "") + "/email/orion-logo.png";
    }

    /**
     * @param cuerpo fragmento HTML del correo (párrafos y listas), sin {@code <html>} ni estilos
     * @return el documento completo, listo para enviar
     */
    public String wrap(String cuerpo) {
        return """
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" \
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Orión</title>
                <style type="text/css">
                  body { margin:0; padding:0; background-color:%1$s; }
                  p { margin:0 0 14px 0; }
                  ul { margin:0 0 14px 0; padding-left:20px; }
                  li { margin:0 0 6px 0; }
                  a { color:%6$s; }
                  @media only screen and (max-width:620px) {
                    .orion-tarjeta { padding:24px 20px !important; }
                  }
                </style>
                </head>
                <body style="margin:0; padding:0; background-color:%1$s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" \
                style="background-color:%1$s;">
                  <tr>
                    <td align="center" style="padding:28px 12px 32px 12px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" \
                style="width:600px; max-width:600px;">
                        <tr>
                          <td align="center" style="padding-bottom:20px;">
                            <img src="%7$s" width="%8$d" height="%9$d" alt="Orión" \
                style="display:block; border:0; outline:none; text-decoration:none; \
                width:%8$dpx; height:%9$dpx;" />
                          </td>
                        </tr>
                        <tr>
                          <td class="orion-tarjeta" style="background-color:%2$s; border:1px solid %3$s; \
                border-radius:16px; padding:32px; font-family:'Segoe UI',Helvetica,Arial,sans-serif; \
                font-size:15px; line-height:1.6; color:%4$s;">
                %10$s
                          </td>
                        </tr>
                        <tr>
                          <td align="center" style="padding:20px 16px 0 16px; \
                font-family:'Segoe UI',Helvetica,Arial,sans-serif; font-size:12px; line-height:1.6; \
                color:%5$s;">
                            <p style="margin:0 0 4px 0; color:%5$s;">Find your right teacher, learn your way.</p>
                            <p style="margin:0; color:%5$s;">Recibiste este correo porque tienes una \
                cuenta en Orión.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(CREMA, BLANCO, BORDE, TINTA, TINTA_TENUE, CORAL,
                logoUrl, LOGO_ANCHO, LOGO_ALTO, cuerpo);
    }
}
