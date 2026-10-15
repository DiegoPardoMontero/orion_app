package co.orion.legal.domain;

/**
 * Los documentos legales con versión. Toda cuenta acepta los dos primeros al registrarse; el tercero
 * lo acepta quien enseña.
 */
public enum LegalDocumentCode {

    /** Términos y condiciones del servicio (Ley 1480 de 2011). */
    TERMS,

    /** Política de tratamiento de la información (art. 13 del Decreto 1377 de 2013). */
    PRIVACY,

    /**
     * El acuerdo del profesor. Desde la 2.0 lleva el mandato de recaudo: Orión recibe el dinero de
     * las clases por cuenta del profe (brief de liquidaciones, decisión de Pardo del 25/09/2026).
     */
    TEACHER_AGREEMENT,
    /**
     * La autorización del representante legal para tratar los datos de un adolescente (Bloque 12).
     * La acepta el adulto al crear cada perfil; su constancia va en {@code guardian_authorizations},
     * no en las aceptaciones por usuario, porque es por hijo.
     */
    GUARDIAN_AUTHORIZATION,
    /** El código de conducta del profesor para clases con menores (Bloque 12). Lo acepta quien pide la habilitación. */
    MINORS_CODE_OF_CONDUCT
}
