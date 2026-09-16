package co.orion.admin.api;

import java.util.List;

/**
 * Qué integraciones externas están realmente configuradas en este despliegue.
 *
 * <p>Existe porque el silencio no era prueba de nada. Si una variable de Railway falta o está mal
 * escrita, Orión arranca igual, responde 200 en el health, y la integración se apaga sin decirlo:
 * el aula no abre, la foto no sube, el pago no arranca. Desde fuera, una aplicación sana y una con
 * el aula muerta se ven idénticas, y la diferencia aparecía cuando se quejaba un usuario — el peor
 * momento y el peor mensajero.
 *
 * <p>No expone ningún secreto: solo si hay algo puesto, y de qué forma. Un booleano no filtra una
 * llave, y saber que Wompi apunta al sandbox no es un dato sensible sino justo lo que hay que ver
 * antes de anunciar que se cobra de verdad.
 */
public record SystemStatusResponse(List<Integracion> integraciones) {

    public record Integracion(
            /** Nombre corto, tal como se lee en la pantalla: «Videollamada (JaaS)». */
            String nombre,
            /** Si está lista para usarse. */
            boolean configurada,
            /** Qué pasa si no lo está, en una frase. */
            String siFalta,
            /** Detalle útil sin secreto: el entorno, el cloud, el modo. Puede ser nulo. */
            String detalle,
            /** Las variables de Railway que la encienden, para saber dónde mirar. */
            List<String> variables) {
    }
}
