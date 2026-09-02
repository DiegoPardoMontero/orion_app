package co.orion.identity.application;

import java.time.Duration;
import java.util.UUID;

/**
 * Almacenamiento de documentos privados de aspirantes. Guarda con visibilidad {@code authenticated}
 * (no público) y devuelve el {@code storageKey} (public_id), nunca una URL. La lectura es siempre
 * por una URL firmada de corta vida.
 */
public interface DocumentStorage {

    /** Sube el archivo y devuelve el storage_key (public_id de Cloudinary). */
    String upload(byte[] bytes, String contentType, UUID userId, String fileName);

    /** URL firmada y temporal para descargar el documento. */
    String signedUrl(String storageKey, Duration ttl);
}
