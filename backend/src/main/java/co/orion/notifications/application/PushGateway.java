package co.orion.notifications.application;

import java.util.Map;

/** El POST al servicio de push. Aparte para que las pruebas no salgan a Google ni a Mozilla. */
public interface PushGateway {

    /** @return el código HTTP de la respuesta; 404 y 410 significan que la suscripción murió */
    int enviar(String endpoint, byte[] cuerpo, Map<String, String> cabeceras);
}
