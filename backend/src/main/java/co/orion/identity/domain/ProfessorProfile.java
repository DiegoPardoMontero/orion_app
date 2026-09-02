package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "professor_profiles")
@EntityListeners(AuditingEntityListener.class)
public class ProfessorProfile {

    /** No hay id propio: la PK es la del usuario (@MapsId la copia desde la relación). */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "headline", length = 120)
    private String headline;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    // --- Marketplace (Bloque 1): precio y datos enriquecidos ---

    /** Tarifa por hora en pesos colombianos enteros. NULL = aún no fijada (no publicable bajo COMMISSION). */
    @Column(name = "hourly_rate_cop")
    private Long hourlyRateCop;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_model", nullable = false, length = 20)
    private CompensationModel compensationModel;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "native_language", length = 5)
    private String nativeLanguage;

    @Column(name = "years_experience")
    private Short yearsExperience;

    @Column(name = "education", length = 300)
    private String education;

    @Column(name = "is_certified", nullable = false)
    private boolean certified;

    @Column(name = "accepts_trial", nullable = false)
    private boolean acceptsTrial;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfessorProfile() {
        // exigido por JPA
    }

    public ProfessorProfile(User user) {
        this.user = Objects.requireNonNull(user, "user");
        this.published = false;
        // Q1: los profesores nuevos nacen bajo el modelo de comisión.
        this.compensationModel = CompensationModel.COMMISSION;
        this.acceptsTrial = true;
        this.certified = false;
    }

    public void describe(String headline, String bio) {
        this.headline = headline;
        this.bio = bio;
    }

    public void changePhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    /** Datos enriquecidos del perfil (todo opcional salvo lo que valide el servicio). */
    public void enrich(String countryCode, String city, String nativeLanguage, Short yearsExperience,
                       String education, boolean certified, boolean acceptsTrial) {
        this.countryCode = countryCode;
        this.city = city;
        this.nativeLanguage = nativeLanguage;
        this.yearsExperience = yearsExperience;
        this.education = education;
        this.certified = certified;
        this.acceptsTrial = acceptsTrial;
    }

    public void changeRate(Long hourlyRateCop) {
        this.hourlyRateCop = hourlyRateCop;
    }

    /**
     * Bajo COMMISSION, un profesor sin tarifa no puede publicarse: aparecería en el buscador sin
     * precio. Bajo FIXED_FEE (legado) no aplica. La regla depende del modelo, por eso vive aquí y
     * no en un CHECK de la base.
     */
    public boolean canPublish() {
        return compensationModel != CompensationModel.COMMISSION || hourlyRateCop != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getHeadline() {
        return headline;
    }

    public String getBio() {
        return bio;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public boolean isPublished() {
        return published;
    }

    public Long getHourlyRateCop() {
        return hourlyRateCop;
    }

    public CompensationModel getCompensationModel() {
        return compensationModel;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCity() {
        return city;
    }

    public String getNativeLanguage() {
        return nativeLanguage;
    }

    public Short getYearsExperience() {
        return yearsExperience;
    }

    public String getEducation() {
        return education;
    }

    public boolean isCertified() {
        return certified;
    }

    public boolean acceptsTrial() {
        return acceptsTrial;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
