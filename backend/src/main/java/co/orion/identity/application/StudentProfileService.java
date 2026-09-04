package co.orion.identity.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProficiencyLevel;
import co.orion.identity.domain.StudentGoal;
import co.orion.identity.domain.StudentProfile;
import co.orion.identity.domain.StudentProfileUpdatedEvent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.StudentAccessoryRepository;
import co.orion.identity.persistence.StudentGoalRepository;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.time.BusinessZone;

/**
 * La ficha del estudiante: lo que él declara de sí mismo, y quién puede verlo.
 *
 * <p>La regla de visibilidad tiene tres capas y <strong>las tres se aplican aquí</strong>, nunca en
 * el frontend:
 *
 * <ol>
 *   <li>Su dueño siempre lo ve completo.</li>
 *   <li>Un profesor lo ve solo si tiene relación con él —una clase o una conversación—.</li>
 *   <li>Otro estudiante lo ve solo si el perfil es público.</li>
 * </ol>
 *
 * <p>Cuando no se puede ver, la respuesta es <strong>404 y no 403</strong>: un 403 confirmaría que
 * ese perfil existe, y eso ya es información sobre una persona.
 */
@Service
public class StudentProfileService {

    private final StudentProfileRepository profiles;
    private final StudentGoalRepository goals;
    private final StudentAccessoryRepository accessories;
    private final UserRepository users;
    private final StudentAudience audience;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StudentProfileService(StudentProfileRepository profiles,
                                 StudentGoalRepository goals,
                                 StudentAccessoryRepository accessories,
                                 UserRepository users,
                                 StudentAudience audience,
                                 ApplicationEventPublisher events,
                                 Clock clock) {
        this.profiles = profiles;
        this.goals = goals;
        this.accessories = accessories;
        this.users = users;
        this.audience = audience;
        this.events = events;
        this.clock = clock;
    }

    /** La ficha con todo lo que la acompaña, para no repetir tres consultas en cada llamada. */
    public record Ficha(StudentProfile profile, List<String> goalCodes,
                        List<StudentAccessoryView> accessories) {
    }

    public record StudentAccessoryView(String zone, String accessoryCode) {
    }

    /**
     * La ficha siempre existe: la V21 la creó para los estudiantes que ya estaban y el registro la
     * crea para los nuevos. Si aun así falta —un estudiante creado por una vía que se olvidó—, se
     * crea al vuelo en vez de estallar: el estudiante no tiene la culpa de eso.
     */
    @Transactional
    public StudentProfile ensureProfile(UUID userId) {
        return profiles.findByIdWithUser(userId).orElseGet(() -> {
            User user = users.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
            return profiles.save(new StudentProfile(user));
        });
    }

    @Transactional(readOnly = true)
    public Ficha own(UUID userId) {
        StudentProfile profile = profiles.findByIdWithUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
        return fichaDe(profile);
    }

    private Ficha fichaDe(StudentProfile profile) {
        return new Ficha(
                profile,
                goals.findByUserId(profile.getUserId()).stream().map(StudentGoal::getGoalCode).toList(),
                accessories.findByUserId(profile.getUserId()).stream()
                        .map(a -> new StudentAccessoryView(a.getZone(), a.getAccessoryCode()))
                        .toList());
    }

    /**
     * Nivel, idioma, motivación y objetivos. Todo opcional: una ficha a medias es válida, y
     * obligar a rellenarla entera antes de guardar nada dejaría el formulario sin salida.
     */
    @Transactional
    public Ficha update(UUID userId, ProficiencyLevel level, String primaryLanguage,
                        String motivation, List<String> goalCodes) {
        StudentProfile profile = ensureProfile(userId);
        profile.describe(level, primaryLanguage, motivation);
        profiles.save(profile);

        goals.deleteByUserId(userId);
        if (goalCodes != null) {
            goalCodes.stream().distinct()
                    .forEach(code -> goals.save(new StudentGoal(userId, code)));
        }

        // El logro «Objetivo declarado» y «Perfil listo» se evalúan con esto. `engagement` escucha;
        // aquí no se sabe qué es un punto.
        events.publishEvent(new StudentProfileUpdatedEvent(userId));

        return fichaDe(profiles.findByIdWithUser(userId).orElseThrow());
    }

    /**
     * El switch del perfil público. Activarlo exige fecha de nacimiento y mayoría de edad;
     * desactivarlo no pide nada, porque retirar el consentimiento tiene que ser más fácil que darlo.
     */
    @Transactional
    public Ficha setVisibility(UUID userId, boolean isPublic, LocalDate birthDate) {
        StudentProfile profile = ensureProfile(userId);
        if (isPublic) {
            LocalDate hoy = LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
            profile.enablePublicProfile(
                    birthDate != null ? birthDate : profile.getBirthDate(), hoy);
        } else {
            profile.disablePublicProfile();
        }
        profiles.save(profile);
        return fichaDe(profile);
    }

    /**
     * La vista de otra persona, con las tres capas aplicadas. Devuelve 404 —y no 403— cuando no
     * hay derecho a verlo: confirmar que el perfil existe ya sería decir algo de esa persona.
     */
    @Transactional(readOnly = true)
    public Ficha visibleTo(UUID studentId, User viewer) {
        StudentProfile profile = profiles.findByIdWithUser(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));

        boolean puedeVerlo = switch (viewer.getRole()) {
            case ADMIN -> true;
            case PROFESSOR -> audience.professorHasRelationWith(viewer.getId(), studentId);
            case STUDENT -> viewer.getId().equals(studentId) || profile.isPublicProfile();
        };
        if (!puedeVerlo) {
            throw new ResourceNotFoundException("Perfil no encontrado");
        }
        return fichaDe(profile);
    }

    /** Guarda los accesorios equipados. Que estén desbloqueados lo comprueba `engagement`. */
    @Transactional
    public void replaceAccessories(UUID userId, List<StudentAccessoryView> equipados) {
        accessories.deleteByUserId(userId);
        if (equipados == null) {
            return;
        }
        for (StudentAccessoryView pieza : equipados) {
            if (!List.of("z1", "z2", "z3").contains(pieza.zone())) {
                throw new UnprocessableException("Zona de accesorio desconocida: " + pieza.zone());
            }
            accessories.save(new co.orion.identity.domain.StudentAccessory(
                    userId, pieza.zone(), pieza.accessoryCode()));
        }
    }

    /** Cuántos de los tres campos de «Perfil listo» están puestos: foto, objetivo e idioma. */
    @Transactional(readOnly = true)
    public int profileCompleteness(UUID userId) {
        StudentProfile profile = profiles.findByIdWithUser(userId).orElse(null);
        if (profile == null) {
            return 0;
        }
        int hechos = 0;
        if (profile.getUser().getPhotoUrl() != null && !profile.getUser().getPhotoUrl().isBlank()) {
            hechos++;
        }
        if (!goals.findByUserId(userId).isEmpty()) {
            hechos++;
        }
        if (profile.getPrimaryLanguage() != null) {
            hechos++;
        }
        return hechos;
    }

    /** Al registrarse un estudiante nace su ficha, con los cosméticos iniciales. */
    @Transactional
    public void createFor(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            return;
        }
        if (!profiles.existsById(user.getId())) {
            profiles.save(new StudentProfile(user));
        }
    }
}
