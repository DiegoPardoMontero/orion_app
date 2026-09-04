package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    /** Lo genera Postgres con gen_random_uuid(); Hibernate lo lee de vuelta tras el INSERT. */
    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "whatsapp_phone", length = 20)
    private String whatsappPhone;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_intent", nullable = false, length = 10)
    private SignupIntent signupIntent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // exigido por JPA
    }

    public User(String email, String passwordHash, String fullName, UserRole role) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.role = Objects.requireNonNull(role, "role");
        this.status = UserStatus.ACTIVE;
        this.signupIntent = SignupIntent.LEARN;
    }

    /** Única dirección donde se decide la forma canónica del email. */
    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase();
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public void changeWhatsappPhone(String whatsappPhone) {
        this.whatsappPhone = whatsappPhone;
    }

    public void changePhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void changeFullName(String fullName) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
    }

    public SignupIntent getSignupIntent() {
        return signupIntent;
    }

    /**
     * Marca que esta cuenta vino a enseñar. Solo el auto-registro la llama, y una sola vez: quien
     * ya está usando Orión como estudiante no se convierte en aspirante por postularse — tiene
     * clases reservadas y saldo pagado, y quitárselos sería un castigo por querer enseñar.
     */
    public void intendsToTeach() {
        this.signupIntent = SignupIntent.TEACH;
    }

    /**
     * Vuelve a la intención de aprender. Se usa cuando la postulación se rechaza: en vez de dejar
     * la cuenta en un callejón sin salida, sigue siendo una cuenta de estudiante perfectamente
     * normal.
     */
    public void intendsToLearn() {
        this.signupIntent = SignupIntent.LEARN;
    }

    /**
     * La cuenta pasa a ser de profesor. Solo lo llama la aprobación de una postulación: es la única
     * puerta por la que alguien empieza a enseñar en Orión.
     */
    public void becomeProfessor() {
        this.role = UserRole.PROFESSOR;
        this.signupIntent = SignupIntent.LEARN;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getWhatsappPhone() {
        return whatsappPhone;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
