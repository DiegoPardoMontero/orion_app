package co.orion.identity.domain;

/** Tipo de documento que sube un aspirante. El CV es el único obligatorio para enviar a revisión. */
public enum DocumentType {
    CV,
    TEACHING_CERTIFICATE,
    UNIVERSITY_DEGREE,
    LANGUAGE_CERTIFICATION,
    /** La consulta del Registro de inhabilidades por delitos sexuales contra menores (Ley 1918 de 2018), para la habilitación con adolescentes. */
    REGISTRY_CHECK,
    /** El certificado de antecedentes judiciales, para la misma habilitación. */
    BACKGROUND_CHECK,
    OTHER
}
