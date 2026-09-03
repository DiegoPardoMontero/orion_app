package co.orion.identity.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.catalog.domain.Language;
import co.orion.catalog.domain.RateBreakdown;
import co.orion.catalog.persistence.LanguageRepository;
import co.orion.identity.api.ProfessorDetail;
import co.orion.identity.api.ProfileLanguage;
import co.orion.identity.api.ProfileResponse;
import co.orion.identity.api.RateBreakdownResponse;
import co.orion.identity.api.UpdateProfileRequest;
import co.orion.identity.domain.ProfessorGoal;
import co.orion.identity.domain.ProfessorLanguage;
import co.orion.identity.domain.ProfessorLanguageLevel;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorGoalRepository;
import co.orion.identity.persistence.ProfessorLanguageLevelRepository;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.reputation.application.ProfessorRatingService;
import co.orion.reputation.application.RatingSummary;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

@Service
public class ProfessorProfileService {

    private static final String COMMISSION_KEY = "commission_rate_bps";

    private final ProfessorProfileRepository profiles;
    private final UserRepository users;
    private final ProfessorLanguageRepository languagesOf;
    private final ProfessorLanguageLevelRepository levelsOf;
    private final ProfessorGoalRepository goalsOf;
    private final LanguageRepository languageCatalog;
    private final PlatformSettingsService settings;
    private final ProfessorAccessService access;
    private final ProfessorRatingService ratings;

    public ProfessorProfileService(ProfessorProfileRepository profiles,
                                   UserRepository users,
                                   ProfessorLanguageRepository languagesOf,
                                   ProfessorLanguageLevelRepository levelsOf,
                                   ProfessorGoalRepository goalsOf,
                                   LanguageRepository languageCatalog,
                                   PlatformSettingsService settings,
                                   ProfessorAccessService access,
                                   ProfessorRatingService ratings) {
        this.profiles = profiles;
        this.users = users;
        this.languagesOf = languagesOf;
        this.levelsOf = levelsOf;
        this.goalsOf = goalsOf;
        this.languageCatalog = languageCatalog;
        this.settings = settings;
        this.access = access;
        this.ratings = ratings;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getOwnProfile(UUID professorId) {
        ProfessorProfile profile = profiles.findByIdWithUser(professorId)
                .orElseGet(() -> createEmptyProfileFor(professorId));
        return toOwnResponse(profile);
    }

    /** Tope de la descripción pública. Cien palabras es una presentación, no una hoja de vida. */
    public static final int MAX_PALABRAS_BIO = 100;

    /**
     * Se cuenta en PALABRAS y no en caracteres a propósito: el límite es sobre lo que el estudiante
     * tiene que leer antes de decidir, y eso se mide en palabras. Un tope en caracteres cortaría a
     * mitad de frase a quien escriba palabras largas y premiaría al que abrevia.
     */
    private void requireBioWithinLimit(String bio) {
        if (bio == null || bio.isBlank()) {
            return;
        }
        int palabras = bio.trim().split("\\s+").length;
        if (palabras > MAX_PALABRAS_BIO) {
            throw new UnprocessableException("Tu descripción tiene " + palabras + " palabras: el máximo son "
                    + MAX_PALABRAS_BIO + ". Resúmela un poco.");
        }
    }

    @Transactional
    public ProfileResponse updateOwnProfile(UUID professorId, UpdateProfileRequest req) {
        ProfessorProfile profile = profiles.findByIdWithUser(professorId)
                .orElseGet(() -> createEmptyProfileFor(professorId));

        requireBioWithinLimit(req.bio());
        profile.describe(req.headline(), req.bio());
        profile.enrich(req.countryCode(), req.city(), req.nativeLanguage(),
                req.yearsExperience(), req.education(), req.certified(), req.acceptsTrial());

        if (req.isPublished()) {
            // El gate: un profesor sin postulación APPROVED no puede publicarse (403), antes de la tarifa.
            access.assertCanTeach(professorId);
            if (!profile.canPublish()) {
                throw new UnprocessableException(
                        "Fija tu tarifa por hora antes de publicar tu perfil.");
            }
            profile.publish();
        } else {
            profile.unpublish();
        }
        // saveAndFlush: la fila del perfil es el padre de las FK de idiomas; debe existir antes.
        profiles.saveAndFlush(profile);

        replaceSelections(professorId, req);
        return toOwnResponse(profile);
    }

    /**
     * Guarda el avance del perfil desde el wizard de postulación: mismos datos que el perfil normal
     * (titular, bio, idiomas, objetivos, país...), pero NUNCA publica — publicar es un paso aparte
     * que exige estar aprobado. Reutiliza el modelo del profesor: la postulación solo lleva estado.
     */
    @Transactional
    public ProfileResponse saveApplicationProfile(UUID professorId, UpdateProfileRequest req) {
        ProfessorProfile profile = profiles.findByIdWithUser(professorId)
                .orElseGet(() -> createEmptyProfileFor(professorId));

        requireBioWithinLimit(req.bio());
        profile.describe(req.headline(), req.bio());
        profile.enrich(req.countryCode(), req.city(), req.nativeLanguage(),
                req.yearsExperience(), req.education(), req.certified(), req.acceptsTrial());
        profiles.saveAndFlush(profile);

        replaceSelections(professorId, req);
        return toOwnResponse(profile);
    }

    @Transactional
    public RateBreakdownResponse setRate(UUID professorId, long hourlyRateCop) {
        requireValidRate(hourlyRateCop);
        ProfessorProfile profile = profiles.findByIdWithUser(professorId)
                .orElseGet(() -> createEmptyProfileFor(professorId));
        profile.changeRate(hourlyRateCop);
        profiles.save(profile);
        return breakdown(hourlyRateCop);
    }

    @Transactional(readOnly = true)
    public RateBreakdownResponse ratePreview(long hourlyRateCop) {
        return breakdown(hourlyRateCop);
    }

    @Transactional(readOnly = true)
    public ProfessorDetail publicDetail(UUID professorId) {
        ProfessorProfile profile = profiles.findPublishedById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
        // No aprobado: 404, no revelamos que el perfil existe.
        if (!access.isApproved(professorId)) {
            throw new ResourceNotFoundException("Profesor no encontrado");
        }
        RatingSummary rating = ratings.summaryFor(professorId);
        return new ProfessorDetail(
                profile.getUserId(),
                profile.getUser().getFullName(),
                profile.getUser().getPhotoUrl(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getCity(),
                profile.getCountryCode(),
                profile.getYearsExperience(),
                profile.getEducation(),
                profile.isCertified(),
                profile.acceptsTrial(),
                profile.getHourlyRateCop(),
                rating.ratingAvg(),
                rating.ratingCount(),
                loadLanguages(professorId),
                loadGoals(professorId));
    }

    /** 404 si el profesor no está publicado. La regla "oculto sin tarifa" vive en el buscador. */
    @Transactional(readOnly = true)
    public void ensurePublished(UUID professorId) {
        profiles.findPublishedById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
    }

    // --- helpers ---

    private void replaceSelections(UUID professorId, UpdateProfileRequest req) {
        levelsOf.deleteByProfessorId(professorId);
        languagesOf.deleteByProfessorId(professorId);
        goalsOf.deleteByProfessorId(professorId);

        List<ProfessorLanguage> newLanguages = new ArrayList<>();
        List<ProfessorLanguageLevel> newLevels = new ArrayList<>();
        if (req.languages() != null) {
            for (UpdateProfileRequest.LanguageEntry entry : req.languages()) {
                if (entry == null || entry.code() == null || entry.code().isBlank()) {
                    continue;
                }
                newLanguages.add(new ProfessorLanguage(professorId, entry.code(), entry.isNative()));
                if (entry.levels() != null) {
                    for (String level : entry.levels()) {
                        newLevels.add(new ProfessorLanguageLevel(professorId, entry.code(), level));
                    }
                }
            }
        }
        // Idiomas primero (y flush) porque los niveles tienen FK compuesta a professor_languages.
        languagesOf.saveAllAndFlush(newLanguages);
        levelsOf.saveAll(newLevels);

        if (req.goals() != null) {
            List<ProfessorGoal> newGoals = req.goals().stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> new ProfessorGoal(professorId, code))
                    .toList();
            goalsOf.saveAll(newGoals);
        }
    }

    private ProfileResponse toOwnResponse(ProfessorProfile profile) {
        UUID id = profile.getUserId();
        RateBreakdownResponse rate = profile.getHourlyRateCop() == null
                ? null : breakdown(profile.getHourlyRateCop());
        return new ProfileResponse(
                id,
                profile.getUser().getFullName(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getUser().getPhotoUrl(),
                profile.getCountryCode(),
                profile.getCity(),
                profile.getNativeLanguage(),
                profile.getYearsExperience(),
                profile.getEducation(),
                profile.isCertified(),
                profile.acceptsTrial(),
                profile.getHourlyRateCop(),
                profile.getCompensationModel().name(),
                id == null ? List.of() : loadLanguages(id),
                id == null ? List.of() : loadGoals(id),
                rate,
                profile.isPublished(),
                profile.canPublish());
    }

    private List<ProfileLanguage> loadLanguages(UUID professorId) {
        List<ProfessorLanguage> langs = languagesOf.findByProfessorId(professorId);
        Map<String, List<String>> levelsByCode = levelsOf.findByProfessorId(professorId).stream()
                .collect(Collectors.groupingBy(ProfessorLanguageLevel::getLanguageCode,
                        Collectors.mapping(ProfessorLanguageLevel::getLevel, Collectors.toList())));
        Map<String, Language> catalog = languageCatalog.findAll().stream()
                .collect(Collectors.toMap(Language::getCode, l -> l));

        return langs.stream().map(pl -> {
            Language l = catalog.get(pl.getLanguageCode());
            List<String> levels = levelsByCode.getOrDefault(pl.getLanguageCode(), List.of())
                    .stream().sorted().toList();
            return new ProfileLanguage(
                    pl.getLanguageCode(),
                    l == null ? pl.getLanguageCode() : l.getNameEs(),
                    l == null ? pl.getLanguageCode() : l.getNameEn(),
                    l == null ? null : l.getFlagEmoji(),
                    pl.isNative(),
                    levels);
        }).toList();
    }

    private List<String> loadGoals(UUID professorId) {
        return goalsOf.findByProfessorId(professorId).stream()
                .map(ProfessorGoal::getGoalCode).sorted().toList();
    }

    /**
     * Las mismas dos franjas que el CHECK de la base: 0 (clase gratuita) o entre 20.000 y 500.000.
     * El chequeo se repite aquí para dar un 422 con un mensaje legible en vez del 500 que saldría
     * de una violación de constraint; la constraint sigue siendo el árbitro final.
     *
     * El hueco entre 1 y 19.999 no es una tarifa barata: queda por debajo del mínimo que acepta la
     * pasarela, así que una clase así no se podría cobrar.
     */
    private void requireValidRate(long hourlyRateCop) {
        if (hourlyRateCop == 0) {
            return;
        }
        if (hourlyRateCop < 20_000 || hourlyRateCop > 500_000) {
            throw new UnprocessableException(
                    "La tarifa debe ser 0 (clase gratuita) o estar entre $20.000 y $500.000.");
        }
    }

    private RateBreakdownResponse breakdown(long hourlyRateCop) {
        int bps = settings.getInt(COMMISSION_KEY);
        return RateBreakdownResponse.from(RateBreakdown.of(hourlyRateCop, bps));
    }

    private ProfessorProfile createEmptyProfileFor(UUID professorId) {
        User professor = users.findById(professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
        return new ProfessorProfile(professor);
    }
}
