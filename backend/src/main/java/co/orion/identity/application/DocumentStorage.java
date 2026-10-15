package co.orion.identity.application;

import java.time.Duration;

import co.orion.shared.error.ServiceUnavailableException;
import java.util.UUID;

/**
 * Almacenamiento de documentos privados de aspirantes. Guarda con visibilidad {@code authenticated}
 * (no público) y devuelve el {@code storageKey} (public_id), nunca una URL. La lectura es siempre
 * por una URL firmada de corta vida.
 */
public interface DocumentStorage {

    /** Sube el archivo y devuelve el storage_key (public_id de Cloudinary). */
    String upload(byte[] bytes, String contentType, UUID userId, String fileName);

    /**
     * URL firmada y temporal para descargar el documento.
     *
     * <p>El {@code contentType} entra porque Cloudinary necesita el formato del archivo: el
     * {@code public_id} que guardamos no lleva extensión, y sin ella el enlace no resuelve.
     */
    String signedUrl(String storageKey, String contentType, Duration ttl);

    /**
     * Los bytes del documento, para servirlos desde Orión con la sesión de quien lo mira: así el
     * previsualizador no depende de que el navegador acepte una URL ajena, y el enlace de Cloudinary
     * nunca sale del servidor. Por defecto no está disponible: los dobles de prueba que no lo
     * necesitan no tienen que implementarlo.
     */
    default byte[] download(String storageKey, String contentType) {
        throw new ServiceUnavailableException("No podemos abrir documentos en este momento. Inténtalo más tarde.");
    }
}
