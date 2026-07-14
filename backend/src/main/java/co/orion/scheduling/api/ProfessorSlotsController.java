package co.orion.scheduling.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.SlotQueryService;

/** El endpoint estrella del MVP: qué cupos tiene libres un profesor. */
@RestController
@RequestMapping("/api/v1/professors/{id}/slots")
public class ProfessorSlotsController {

    private final SlotQueryService slotQueryService;

    public ProfessorSlotsController(SlotQueryService slotQueryService) {
        this.slotQueryService = slotQueryService;
    }

    @GetMapping
    public SlotsResponse slots(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return SlotsResponse.of(id, slotQueryService.availableSlots(id, from, to));
    }
}
