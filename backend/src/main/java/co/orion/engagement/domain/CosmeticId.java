package co.orion.engagement.domain;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta: el diseño reutiliza nombres entre familias («trazo» es marco y paleta). */
public class CosmeticId implements Serializable {

    private CosmeticKind kind;
    private String code;

    public CosmeticId() {
    }

    public CosmeticId(CosmeticKind kind, String code) {
        this.kind = kind;
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CosmeticId that)) {
            return false;
        }
        return kind == that.kind && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, code);
    }
}
