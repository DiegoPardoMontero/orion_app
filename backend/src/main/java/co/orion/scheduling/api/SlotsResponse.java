package co.orion.scheduling.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import co.orion.scheduling.domain.Slot;
import co.orion.shared.time.BusinessZone;

public record SlotsResponse(UUID professorId, String timezone, List<SlotView> slots) {

    public static SlotsResponse of(UUID professorId, List<Slot> slots) {
        return new SlotsResponse(
                professorId,
                BusinessZone.BOGOTA.getId(),
                slots.stream().map(SlotView::from).toList());
    }

    public record SlotView(ZonedDateTime startsAt, ZonedDateTime endsAt) {

        static SlotView from(Slot slot) {
            return new SlotView(slot.startsAt(), slot.endsAt());
        }
    }
}
