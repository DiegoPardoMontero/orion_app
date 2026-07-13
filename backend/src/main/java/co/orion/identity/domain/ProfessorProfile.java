package co.orion.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfessorProfile() {
        // exigido por JPA
    }

    public ProfessorProfile(User user) {
        this.user = Objects.requireNonNull(user, "user");
        this.published = false;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
