-- Sala de videollamada por reserva. Las clases VIRTUAL generan una sala Jitsi al confirmarse;
-- las presenciales la dejan en null (su "dónde" va en location_note).
ALTER TABLE bookings ADD COLUMN meeting_link VARCHAR(300);
