package co.orion.identity.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ResourceNotFoundException;

@Service
public class ProfessorProfileService {

    private final ProfessorProfileRepository profiles;
    private final UserRepository users;

    public ProfessorProfileService(ProfessorProfileRepository profiles, UserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    /**
     * El perfil propio, publicado o no. Hace falta para poder editarlo: getPublished() no sirve
     * (responde 404 a los no publicados, que es precisamente quien va a publicarse por primera vez).
     */
    @Transactional(readOnly = true)
    public ProfessorProfile getOwnProfile(UUID professorId) {
        return profiles.findByIdWithUser(professorId)
                .orElseGet(() -> createEmptyProfileFor(professorId));
    }

    @Transactional
    public ProfessorProfile updateOwnProfile(UUID professorId, String headline, String bio,
                                             boolean published) {
        // findByIdWithUser y no findById: el controlador lee profile.getUser() ya fuera de la
        // transacción, y un proxy perezoso sin sesión explotaría con LazyInitializationException.
        ProfessorProfile profile = profiles.findByIdWithUser(professorId)
                .orElseGet(() -> createEmptyProfileFor(professorId));

        profile.describe(headline, bio);
        if (published) {
            profile.publish();
        } else {
            profile.unpublish();
        }
        return profiles.save(profile);
    }

    /** Solo perfiles publicados de usuarios activos: el directorio público del MVP. */
    @Transactional(readOnly = true)
    public List<ProfessorProfile> listPublished() {
        return profiles.findPublished();
    }

    /** No publicado e inexistente responden igual (404): no revelamos perfiles ocultos. */
    @Transactional(readOnly = true)
    public ProfessorProfile getPublished(UUID professorId) {
        return profiles.findPublishedById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
    }

    private ProfessorProfile createEmptyProfileFor(UUID professorId) {
        User professor = users.findById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
        return new ProfessorProfile(professor);
    }
}
