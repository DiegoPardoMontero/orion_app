package co.orion.notifications.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/** El envío real, con plazos cortos: un servicio de push lento no puede retener a nadie. */
@Component
public class HttpPushGateway implements PushGateway {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    @Override
    public int enviar(String endpoint, byte[] cuerpo, Map<String, String> cabeceras) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo));
        cabeceras.forEach(req::header);
        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}
