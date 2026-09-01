package co.orion.identity.application;

/**
 * Sube una imagen y devuelve la URL segura (https) ya recortada como avatar. Interfaz para que el
 * endpoint se pueda probar con un doble sin llamar a Cloudinary.
 */
public interface PhotoUploader {

    String upload(byte[] bytes, String contentType);
}
