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
export type MyBookingResponse = Schemas["MyBookingResponse"];
export type RuleResponse = Schemas["RuleResponse"];
export type ExceptionResponse = Schemas["ExceptionResponse"];
export type ProfileResponse = Schemas["ProfileResponse"];

export type AdminUserResponse = Schemas["AdminUserResponse"];
export type AdminBookingResponse = Schemas["AdminBookingResponse"];
export type MetricsResponse = Schemas["MetricsResponse"];

export type TeacherApplicationView = Schemas["TeacherApplicationView"];
export type DocumentView = Schemas["DocumentView"];
export type AdminApplicationSummary = Schemas["AdminApplicationSummary"];
export type PagedApplications = Schemas["PagedApplications"];
export type AdminApplicationDetail = Schemas["AdminApplicationDetail"];
export type ApplicationEventView = Schemas["ApplicationEventView"];
export type ReviewDecisionRequest = Schemas["ReviewDecisionRequest"];

export type Modality = "VIRTUAL" | "IN_PERSON";
