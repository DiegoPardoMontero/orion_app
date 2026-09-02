import type { components } from "./schema";

/**
 * Los tipos del API no se escriben a mano: salen de schema.d.ts, que genera
 * `npm run types:api` contra el OpenAPI del backend vivo. Si un DTO cambia allá, aquí
 * deja de compilar — que es exactamente lo que queremos.
 */
type Schemas = components["schemas"];

export type ProfessorCard = Schemas["ProfessorCard"];
export type PagedProfessors = Schemas["PagedProfessors"];
export type ProfessorDetail = Schemas["ProfessorDetail"];
export type ProfileLanguage = Schemas["ProfileLanguage"];
export type LanguageResponse = Schemas["LanguageResponse"];
export type GoalResponse = Schemas["GoalResponse"];
export type RateBreakdownResponse = Schemas["RateBreakdownResponse"];
export type SlotsResponse = Schemas["SlotsResponse"];
export type SlotView = Schemas["SlotView"];
export type BookingResponse = Schemas["BookingResponse"];
export type CreateBookingRequest = Schemas["CreateBookingRequest"];

/**
 * La contraparte de una reserva (profesor o estudiante). El backend expone dos DTOs anidados con
 * el mismo nombre simple `Counterpart` —el de reservas (con whatsappPhone/headline) y el de la
 * mensajería (con role)—, así que el OpenAPI los colapsa en un solo esquema y la generación pierde
 * los campos de reservas. Hasta que el backend desambigüe ese nombre de esquema, restauramos aquí
 * la forma real de la contraparte de reservas; es la capa hecha a mano y el sitio correcto para el
 * parche. `schema.d.ts` se deja intacto como fuente generada.
 */
export type BookingCounterpart = {
  id?: string;
  fullName?: string;
  whatsappPhone?: string;
  photoUrl?: string;
  headline?: string;
};
export type MyBookingResponse = Omit<Schemas["MyBookingResponse"], "counterpart"> & {
  counterpart?: BookingCounterpart;
};
export type RuleResponse = Schemas["RuleResponse"];
export type ExceptionResponse = Schemas["ExceptionResponse"];
export type ProfileResponse = Schemas["ProfileResponse"];

export type AdminUserResponse = Schemas["AdminUserResponse"];
export type AdminBookingResponse = Schemas["AdminBookingResponse"];
export type MetricsResponse = Schemas["MetricsResponse"];

export type PagedReviews = Schemas["PagedReviews"];
export type PublicReviewResponse = Schemas["PublicReviewResponse"];

export type ConversationSummary = Schemas["ConversationSummaryResponse"];
export type MessageResponse = Schemas["MessageResponse"];
export type NotificationResponse = Schemas["NotificationResponse"];
export type UnreadCountResponse = Schemas["UnreadCountResponse"];

export type TeacherApplicationView = Schemas["TeacherApplicationView"];
export type DocumentView = Schemas["DocumentView"];
export type AdminApplicationSummary = Schemas["AdminApplicationSummary"];
export type PagedApplications = Schemas["PagedApplications"];
export type AdminApplicationDetail = Schemas["AdminApplicationDetail"];
export type ApplicationEventView = Schemas["ApplicationEventView"];
export type ReviewDecisionRequest = Schemas["ReviewDecisionRequest"];

export type Modality = "VIRTUAL" | "IN_PERSON";
