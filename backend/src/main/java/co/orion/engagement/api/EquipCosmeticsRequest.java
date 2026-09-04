package co.orion.engagement.api;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Qué se equipa. El servidor comprueba que cada pieza esté desbloqueada: confiar en que el frontend
 * solo muestre lo desbloqueado es cómo alguien se pone la corona con un `curl`.
 */
public record EquipCosmeticsRequest(@NotBlank @Size(max = 40) String frameCode,
                                    @NotBlank @Size(max = 40) String paletteCode,
                                    @NotBlank @Size(max = 40) String skyCode,
                                    List<Accessory> accessories) {

    public record Accessory(@NotBlank @Size(max = 10) String zone,
                            @NotBlank @Size(max = 40) String accessoryCode) {
    }
}
