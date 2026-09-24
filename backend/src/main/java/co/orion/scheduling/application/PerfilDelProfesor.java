package co.orion.scheduling.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;

/**
 * Qué le falta al profesor para recibir estudiantes (24/09/2026: «lo mismo que con la ficha del
 * estudiante, hasta que llene su disponibilidad y complete su perfil y lo publique»): foto, titular,
 * descripción, tarifa, idiomas, horarios y publicarlo. Vive en {@code scheduling} porque los horarios
 * son suyos e {@code identity} no puede leerlos.
 */
@Service
public class PerfilDelProfesor {

    /** En el orden en que se le piden. */
    public static final List<String> TODO =
            List.of("FOTO", "TITULAR", "DESCRIPCION", "TARIFA", "IDIOMAS", "HORARIOS", "PUBLICAR");

    private final ProfessorProfileRepository profiles;
    private final ProfessorLanguageRepository languages;
    private final AvailabilityRuleRepository rules;

    public PerfilDelProfesor(ProfessorProfileRepository profiles, ProfessorLanguageRepository languages,
                             AvailabilityRuleRepository rules) {
        this.profiles = profiles;
        this.languages = languages;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<String> faltan(UUID professorId) {
        ProfessorProfile p = profiles.findByIdWithUser(professorId).orElse(null);
        if (p == null) {
            return TODO;
        }
        List<String> faltan = new ArrayList<>();
        if (vacio(p.getUser().getPhotoUrl())) {
            faltan.add("FOTO");
        }
        if (vacio(p.getHeadline())) {
            faltan.add("TITULAR");
        }
        if (vacio(p.getBio())) {
            faltan.add("DESCRIPCION");
        }
        if (p.getHourlyRateCop() == null) {
            faltan.add("TARIFA");
        }
        if (languages.findByProfessorId(professorId).isEmpty()) {
            faltan.add("IDIOMAS");
        }
        if (rules.findByProfessorIdAndActiveTrue(professorId).isEmpty()) {
            faltan.add("HORARIOS");
        }
        if (!p.isPublished()) {
            faltan.add("PUBLICAR");
        }
        return faltan;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
