package co.orion.identity.api;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.orion.identity.application.PhotoService;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;

/** Foto de perfil del usuario autenticado (cualquier rol). Multipart: campo `file`. */
@RestController
@RequestMapping("/api/v1/me/photo")
public class MePhotoController {

    private final PhotoService photoService;

    public MePhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping
    public Map<String, String> upload(@AuthenticationPrincipal OrionUserDetails principal,
                                      @RequestParam("file") MultipartFile file) {
        String url = photoService.updatePhoto(principal.user().getId(), readBytes(file), file.getContentType());
        return Map.of("photoUrl", url);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleViolationException("No se pudo leer el archivo");
        }
    }
}
