package co.orion.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * Un proveedor de mentira para probar los clientes de OpenAI sin red: responde lo que se le diga,
 * tarda lo que se le diga y guarda lo que recibió. Con hilos propios, para que una respuesta lenta
 * no bloquee al servidor mismo (eso fue lo que confundió la primera lectura del corte del acta).
 */
public final class ServidorDePrueba implements AutoCloseable {

    private final HttpServer servidor;
    private final AtomicInteger llamadas = new AtomicInteger();
    private final List<String> cuerpos = new CopyOnWriteArrayList<>();
    private volatile int esperaMs;
    private volatile int estado = 200;
    private volatile String respuesta = "{}";

    public ServidorDePrueba() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.createContext("/", intercambio -> {
            llamadas.incrementAndGet();
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    public String url(String ruta) {
        return "http://localhost:" + servidor.getAddress().getPort() + ruta;
    }

    public ServidorDePrueba responde(int estado, String cuerpo) {
        this.estado = estado;
        this.respuesta = cuerpo;
        return this;
    }

    public ServidorDePrueba tarda(int ms) {
        this.esperaMs = ms;
        return this;
    }

    /** Una respuesta de chat completions con ese contenido y un uso fijo de tokens. */
    public static String chat(String contenido) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + contenido.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20}}";
    }

    public int llamadas() {
        return llamadas.get();
    }

    public List<String> cuerpos() {
        return cuerpos;
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
