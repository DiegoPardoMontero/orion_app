package co.orion.billing.domain;

/** El documento del titular de la llave. Personas naturales: el profe cobra a su nombre. */
public enum IdDocumentType {

    /** Cédula de ciudadanía. */
    CC,

    /** Cédula de extranjería. */
    CE,

    /** Permiso por protección temporal. */
    PPT,

    /** Pasaporte. */
    PAS
}
