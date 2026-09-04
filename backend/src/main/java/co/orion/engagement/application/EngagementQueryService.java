package co.orion.engagement.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.engagement.domain.Achievement;
import co.orion.engagement.domain.Cosmetic;
import co.orion.engagement.domain.CosmeticId;
import co.orion.engagement.domain.CosmeticKind;
import co.orion.engagement.domain.StreakCalculator;
import co.orion.engagement.domain.StreakProtection;
import co.orion.engagement.domain.UserAchievement;
import co.orion.engagement.persistence.AchievementRepository;
import co.orion.engagement.persistence.CosmeticRepository;
import co.orion.engagement.persistence.PointEventRepository;
import co.orion.engagement.persistence.StreakProtectionRepository;
import co.orion.engagement.persistence.UserAchievementRepository;
import co.orion.identity.application.StudentProfileService;
import co.orion.identity.persistence.StudentProfileRepository;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.LearningProgress;
import co.orion.scheduling.domain.LearningProgress.Tomada;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.UnprocessableException;

/**
 * Lo que el estudiante lee de su gamificación: puntos, racha, estrellas y cosméticos.
 *
 * <p>Nada de esto se almacena calculado. Los puntos se suman del libro, la racha sale del cálculo
 * puro y el nivel del sello es <strong>derivado</strong> de los logros: guardarlo sería una tercera
 * copia de la misma verdad, y las tres copias acabarían discrepando.
 */
@Service
public class EngagementQueryService {

    /** El mapa de constancia. Doce semanas y no un año: ver {@code MyStreakResponse}. */
    public static final int SEMANAS_DEL_MAPA = 12;

    private final BookingRepository bookings;
    private final AchievementRepository achievements;
    private final UserAchievementRepository userAchievements;
    private final PointEventRepository pointEvents;
    private final StreakProtectionRepository protections;
    private final CosmeticRepository cosmetics;
    private final StudentProfileRepository studentProfiles;
    private final StudentProfileService studentProfileService;
    private final Clock clock;

    public EngagementQueryService(BookingRepository bookings,
                                  AchievementRepository achievements,
                                  UserAchievementRepository userAchievements,
                                  PointEventRepository pointEvents,
                                  StreakProtectionRepository protections,
                                  CosmeticRepository cosmetics,
                                  StudentProfileRepository studentProfiles,
                                  StudentProfileService studentProfileService,
                                  Clock clock) {
        this.bookings = bookings;
        this.achievements = achievements;
        this.userAchievements = userAchievements;
        this.pointEvents = pointEvents;
        this.protections = protections;
        this.cosmetics = cosmetics;
        this.studentProfiles = studentProfiles;
        this.studentProfileService = studentProfileService;
        this.clock = clock;
    }

    public record Resumen(long points, int currentStreakWeeks, int bestStreakWeeks,
                          int protectedWeeks, int sealLevel, int unlockedCount, int totalCount) {
    }

    public record LogroConEstado(Achievement achievement, int progress, boolean unlocked,
                                 Instant unlockedAt) {
    }

    public record CosmeticoConEstado(Cosmetic cosmetic, boolean unlocked, String unlockCondition,
                                     boolean equipped) {
    }

    @Transactional(readOnly = true)
    public Resumen resumen(UUID studentId) {
        Instant ahora = clock.instant();
        List<Tomada> tomadas = clasesDe(studentId, ahora);
        Set<LocalDate> mesesProtegidos = protections.findByUserId(studentId).stream()
                .map(StreakProtection::getGrantedFor).collect(Collectors.toSet());

        StreakCalculator.Racha racha = StreakCalculator.calcular(tomadas, mesesProtegidos, ahora);
        Set<String> encendidos = codigosEncendidos(studentId);

        return new Resumen(
                pointEvents.totalPointsOf(studentId),
                racha.actual(),
                racha.mejor(),
                protections.findByUserId(studentId).size(),
                nivelDelSello(encendidos),
                encendidos.size(),
                achievements.findByActiveTrueOrderByDisplayOrderAsc().size());
    }

    /**
     * El nivel del sello se deriva de los logros: 1 al registrarse, 2 con dos meses seguidos y 3
     * con medio año. No se almacena — sería una tercera copia de una verdad que ya está en dos
     * sitios, y las copias discrepan.
     */
    private int nivelDelSello(Set<String> encendidos) {
        if (encendidos.contains("constancia-24-semanas")) {
            return 3;
        }
        if (encendidos.contains("constancia-8-semanas")) {
            return 2;
        }
        return 1;
    }

    @Transactional(readOnly = true)
    public List<LogroConEstado> logros(UUID studentId) {
        Map<String, UserAchievement> estado = userAchievements.findByUserId(studentId).stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementCode, u -> u));

        return achievements.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(logro -> {
                    UserAchievement suyo = estado.get(logro.getCode());
                    return new LogroConEstado(
                            logro,
                            suyo == null ? 0 : suyo.getProgress(),
                            suyo != null && suyo.isUnlocked(),
                            suyo == null ? null : suyo.getUnlockedAt());
                })
                .toList();
    }

    /**
     * El catálogo de cosméticos con su condición <strong>en texto</strong>, no el código del logro:
     * el estudiante tiene que leer «Con diez clases», no {@code volumen-10-clases}.
     */
    @Transactional(readOnly = true)
    public List<CosmeticoConEstado> cosmeticos(UUID studentId) {
        Set<String> encendidos = codigosEncendidos(studentId);
        Map<String, String> textos = achievements.findAll().stream()
                .collect(Collectors.toMap(Achievement::getCode, Achievement::getDescription));

        var ficha = studentProfiles.findById(studentId).orElse(null);
        var accesorios = studentProfileService.own(studentId).accessories().stream()
                .map(a -> a.accessoryCode()).collect(Collectors.toSet());

        return cosmetics.findAllByOrderByKindAscDisplayOrderAsc().stream()
                .map(pieza -> new CosmeticoConEstado(
                        pieza,
                        pieza.isDefaultPiece() || encendidos.contains(pieza.getUnlockAchievement()),
                        pieza.isDefaultPiece()
                                ? "Lo tienes desde el principio"
                                : textos.getOrDefault(pieza.getUnlockAchievement(), ""),
                        estaEquipado(pieza, ficha, accesorios)))
                .toList();
    }

    private boolean estaEquipado(Cosmetic pieza,
                                 co.orion.identity.domain.StudentProfile ficha,
                                 Set<String> accesorios) {
        if (ficha == null) {
            return false;
        }
        return switch (pieza.getKind()) {
            case FRAME -> pieza.getCode().equals(ficha.getFrameCode());
            case PALETTE -> pieza.getCode().equals(ficha.getPaletteCode());
            case SKY -> pieza.getCode().equals(ficha.getSkyCode());
            case ACCESSORY -> accesorios.contains(pieza.getCode());
        };
    }

    /**
     * Equipa las piezas, comprobando <strong>en el servidor</strong> que cada una esté desbloqueada.
     * Confiar en que el frontend solo muestre lo desbloqueado es cómo alguien se pone la corona con
     * un {@code curl}.
     */
    @Transactional
    public void equipar(UUID studentId, String frame, String palette, String sky,
                        List<StudentProfileService.StudentAccessoryView> accesorios) {
        Set<String> encendidos = codigosEncendidos(studentId);

        exigirDesbloqueado(CosmeticKind.FRAME, frame, encendidos);
        exigirDesbloqueado(CosmeticKind.PALETTE, palette, encendidos);
        exigirDesbloqueado(CosmeticKind.SKY, sky, encendidos);
        if (accesorios != null) {
            for (var pieza : accesorios) {
                exigirDesbloqueado(CosmeticKind.ACCESSORY, pieza.accessoryCode(), encendidos);
            }
        }

        var ficha = studentProfileService.ensureProfile(studentId);
        ficha.equip(frame, palette, sky);
        studentProfiles.save(ficha);
        studentProfileService.replaceAccessories(studentId, accesorios);
    }

    private void exigirDesbloqueado(CosmeticKind kind, String code, Set<String> encendidos) {
        Cosmetic pieza = cosmetics.findById(new CosmeticId(kind, code))
                .orElseThrow(() -> new UnprocessableException("Esa pieza no existe: " + code));
        if (pieza.isDefaultPiece() || encendidos.contains(pieza.getUnlockAchievement())) {
            return;
        }
        throw new UnprocessableException("Todavía no has desbloqueado «" + pieza.getName() + "».");
    }

    /** Las últimas semanas con su estado, para el mapa de constancia. */
    @Transactional(readOnly = true)
    public List<StreakCalculator.SemanaDelMapa> mapaDeConstancia(UUID studentId, int semanas) {
        Instant ahora = clock.instant();
        Set<LocalDate> protegidas = protections.findByUserId(studentId).stream()
                .map(StreakProtection::getWeekStart).collect(Collectors.toSet());
        return StreakCalculator.mapa(clasesDe(studentId, ahora), protegidas, semanas, ahora);
    }

    private Set<String> codigosEncendidos(UUID studentId) {
        return userAchievements.findByUserId(studentId).stream()
                .filter(UserAchievement::isUnlocked)
                .map(UserAchievement::getAchievementCode)
                .collect(Collectors.toSet());
    }

    private List<Tomada> clasesDe(UUID studentId, Instant ahora) {
        List<BookingStatus> activas = List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT);
        return bookings.findPastOfStudent(studentId, activas, ahora).stream()
                .filter(b -> LearningProgress.cuentaComoTomada(b.getStatus(), b.getEndsAt(), ahora))
                .map(b -> new Tomada(b.getProfessorId(), b.getStartsAt(), b.getEndsAt()))
                .toList();
    }
}
