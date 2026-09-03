package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.orion.shared.error.UnprocessableException;
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

    /**
     * Los mínimos de la ficha pública, en palabras. Un titular de dos palabras ("Profesor inglés")
     * y una descripción de una línea no dejan elegir a nadie: el estudiante compara perfiles antes
     * de gastar su dinero, y una ficha vacía traslada esa decisión a la foto.
     */
    public static final int MIN_PALABRAS_TITULAR = 5;
    public static final int MIN_PALABRAS_BIO = 20;
    public static final int MAX_PALABRAS_BIO = 100;

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

    /**
     * Titular y descripción, con sus mínimos y su máximo. La regla vive aquí y no en los servicios
     * porque hay cuatro caminos que escriben esto —el perfil propio, la postulación, la invitación
     * del admin y el sembrador de desarrollo— y basta con que uno se olvide para que el directorio
     * se llene de fichas de tres palabras con las que no se puede elegir a nadie.
     *
     * Lanza {@link UnprocessableException} y no una excepción de programación porque esto no es un
     * fallo del código sino algo que el profesor puede corregir escribiendo un poco más; el mensaje
     * es lo que va a leer. El mismo camino que sigue {@code TeacherApplication} con su conflicto.
     *
     * Todo se mide en PALABRAS y no en caracteres: el límite es sobre lo que el estudiante tiene
     * que leer antes de decidir. Un tope en caracteres cortaría a mitad de frase a quien use
     * palabras largas y premiaría al que abrevia.
     *
     * Vacío sigue valiendo: un perfil recién creado todavía no ha escrito nada, y obligarle a
     * redactar antes de poder guardar cualquier otro campo dejaría el formulario sin salida. Lo que
     * no vale es escribir poco.
     */
    public void describe(String headline, String bio) {
        int titular = contarPalabras(headline);
        if (titular > 0 && titular < MIN_PALABRAS_TITULAR) {
            throw new UnprocessableException("Tu titular tiene " + palabras(titular)
                    + ": el mínimo son " + MIN_PALABRAS_TITULAR
                    + ". Cuenta un poco más de lo que enseñas.");
        }

        int descripcion = contarPalabras(bio);
        if (descripcion > 0 && descripcion < MIN_PALABRAS_BIO) {
            throw new UnprocessableException("Tu descripción tiene " + palabras(descripcion)
                    + ": el mínimo son " + MIN_PALABRAS_BIO + ". Cuéntales cómo son tus clases.");
        }
        if (descripcion > MAX_PALABRAS_BIO) {
            throw new UnprocessableException("Tu descripción tiene " + palabras(descripcion)
                    + ": el máximo son " + MAX_PALABRAS_BIO + ". Resúmela un poco.");
        }

        this.headline = headline;
        this.bio = bio;
    }

    private static String palabras(int n) {
        return n + (n == 1 ? " palabra" : " palabras");
    }

    /** Cuenta palabras, no caracteres: es la unidad en la que se mide "esto dice poco". */
    public static int contarPalabras(String texto) {
        return texto == null || texto.isBlank() ? 0 : texto.trim().split("\\s+").length;
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
