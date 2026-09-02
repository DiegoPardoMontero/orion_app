package co.orion.identity.domain;

/** Tipo de documento que sube un aspirante. El CV es el único obligatorio para enviar a revisión. */
public enum DocumentType {
    CV,
    TEACHING_CERTIFICATE,
    UNIVERSITY_DEGREE,
    LANGUAGE_CERTIFICATION,
    OTHER
}
