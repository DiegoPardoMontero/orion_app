package co.orion.identity.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

/**
 * Foto de perfil de cualquier usuario. Valida tipo y tamaño ANTES de subir (no gastamos cuota de
 * Cloudinary en basura), sube y persiste la URL en users.photo_url. El fallback de iniciales se
 * mantiene en la UI para quien no tenga foto.
 */
@Service
public class PhotoService {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository users;
    private final PhotoUploader uploader;

    public PhotoService(UserRepository users, PhotoUploader uploader) {
        this.users = users;
        this.uploader = uploader;
    }

    @Transactional
    public String updatePhoto(UUID userId, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleViolationException("El archivo llegó vacío");
        }
        if (bytes.length > MAX_BYTES) {
            throw new UnprocessableException("La imagen no puede superar 5 MB");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!ALLOWED.contains(type)) {
            throw new BusinessRuleViolationException("La foto debe ser JPEG, PNG o WEBP");
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String url = uploader.upload(bytes, type);
        user.changePhotoUrl(url);
        users.save(user);
        return url;
    }
}
