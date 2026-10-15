package co.orion.identity.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import co.orion.identity.application.TeacherDocumentService;

/**
 * Cómo se sirve un documento privado desde Orión: con su tipo real, en línea (para el
 * previsualizador) o como descarga, sin caché y sin que el navegador adivine el tipo. Lo usan el
 * dueño y el admin; la diferencia entre los dos es quién puede pedirlo, no cómo se entrega.
 */
final class DocumentoEnLinea {

    private DocumentoEnLinea() {
    }

    static ResponseEntity<byte[]> respuesta(TeacherDocumentService.Contenido contenido, boolean descargar) {
        var doc = contenido.document();
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(doc.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(contenido.bytes());
    }
}
