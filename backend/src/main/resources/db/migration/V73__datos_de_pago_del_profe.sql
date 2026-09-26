-- Brief de liquidaciones bajo mandato, paso 2: a dónde se le transfiere a cada profe.
--
-- Una fila por profe. Pardo paga a mano por Bre-B desde su banco, así que lo que hace falta es la
-- llave, el documento y el nombre del titular: al pagar, el admin confirma que el nombre que muestra
-- su banco coincide con el titular. La llave tiene que estar a nombre del profe.
--
-- No guarda historial de cambios: cada cambio le manda un correo al profe (protección contra un
-- cambio que no hizo él), y cada liquidación pagada guarda la llave y el titular con los que se
-- pagó, que es la constancia que importa.

CREATE TABLE professor_payout_details (
    professor_id    UUID PRIMARY KEY REFERENCES users(id),
    key_type        VARCHAR(20)  NOT NULL CHECK (key_type IN ('PHONE', 'ID_NUMBER', 'EMAIL', 'ALPHANUMERIC')),
    key_value       VARCHAR(100) NOT NULL,
    document_type   VARCHAR(10)  NOT NULL CHECK (document_type IN ('CC', 'CE', 'PPT', 'PAS')),
    document_number VARCHAR(20)  NOT NULL,
    holder_name     VARCHAR(150) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
