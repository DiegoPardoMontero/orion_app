package co.orion.identity.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import co.orion.identity.application.StudentProfileService;
import co.orion.identity.domain.StudentProfile;

/**
 * La ficha del estudiante en sus dos versiones.
 *
 * <p>La pública <strong>excluye</strong> correo, teléfono, saldo, historial de pagos y con qué
 * profesores ha practicado. No es un filtro del frontend: son dos constructoras distintas, y la
 * pública no tiene por dónde colar un campo privado aunque alguien lo añada más adelante.
 *
 * <p>{@code @JsonProperty} en {@code isPublic}: sin él Jackson serializa "public" pero al leer
 * espera "isPublic", y el viaje de ida y vuelta se rompe. Es el mismo caso de {@code ProfileResponse}.
 */
public record StudentProfileResponse(UUID id,
                                     String fullName,
                                     String photoUrl,
                                     String selfDeclaredLevel,
                                     String primaryLanguage,
                                     String motivation,
                                     List<String> goalCodes,
                                     String frameCode,
                                     String paletteCode,
                                     String skyCode,
                                     List<Accessory> accessories,
                                     @JsonProperty("isPublic") Boolean isPublic,
                                     LocalDate birthDate,
                                     boolean ownView) {

    public record Accessory(String zone, String accessoryCode) {
    }

    /** La vista del dueño: lo lleva todo, incluido si su perfil es público y su fecha. */
    public static StudentProfileResponse own(StudentProfileService.Ficha ficha) {
        StudentProfile p = ficha.profile();
        return new StudentProfileResponse(
                p.getUserId(),
                p.getUser().getFullName(),
                p.getUser().getPhotoUrl(),
                p.getSelfDeclaredLevel() == null ? null : p.getSelfDeclaredLevel().name(),
                p.getPrimaryLanguage(),
                p.getMotivation(),
                ficha.goalCodes(),
                p.getFrameCode(),
                p.getPaletteCode(),
                p.getSkyCode(),
                accesorios(ficha),
                p.isPublicProfile(),
                p.getBirthDate(),
                true);
    }

    /**
     * La vista de otra persona. `isPublic` y `birthDate` van en null a propósito: la primera es un
     * ajuste suyo y la segunda es un dato personal que nadie más necesita.
     */
    public static StudentProfileResponse publicView(StudentProfileService.Ficha ficha) {
        StudentProfile p = ficha.profile();
        return new StudentProfileResponse(
                p.getUserId(),
                p.getUser().getFullName(),
                p.getUser().getPhotoUrl(),
                p.getSelfDeclaredLevel() == null ? null : p.getSelfDeclaredLevel().name(),
                p.getPrimaryLanguage(),
                p.getMotivation(),
                ficha.goalCodes(),
                p.getFrameCode(),
                p.getPaletteCode(),
                p.getSkyCode(),
                accesorios(ficha),
                null,
                null,
                false);
    }

    private static List<Accessory> accesorios(StudentProfileService.Ficha ficha) {
        return ficha.accessories().stream()
                .map(a -> new Accessory(a.zone(), a.accessoryCode()))
                .toList();
    }
}
