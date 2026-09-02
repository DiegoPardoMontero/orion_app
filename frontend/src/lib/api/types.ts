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
/**
 * La respuesta de POST /bookings trae, además de la reserva, lo que hay que pagar por ella. El
 * esquema generado todavía no lo refleja (se regenera contra un backend vivo), así que se añade
 * aquí igual que la contraparte de más abajo.
 */
export type BookingResponse = Schemas["BookingResponse"] & {
  expiresAt?: string | null;
  payment?: PaymentTicket | null;
};
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

/* --------------------------------------------------------------------------------------------
 * Pagos, créditos y liquidación (Bloque 4)
 * --------------------------------------------------------------------------------------------
 * Escritos a mano, como la contraparte de reservas de arriba: `schema.d.ts` se regenera con
 * `npm run types:api` contra un backend levantado, y estos tipos tienen que existir antes.
 * Cuando se regenere, se sustituyen por sus Schemas[...] correspondientes.
 */

/** Lo que hay que pagar por una reserva. Sin comisión: el estudiante no la ve nunca. */
export type PaymentTicket = {
  paymentId: string;
  amountCop: number;
  creditAppliedCop: number;
  chargedCop: number;
  /** Null cuando el crédito cubrió la clase entera: no hay a dónde ir a pagar. */
  checkoutUrl: string | null;
};

export type PaymentStatusResponse = {
  bookingId: string;
  bookingStatus: string;
  paymentStatus: "PENDING" | "PAID" | "RELEASED" | "REFUNDED" | "DISPUTED" | "CANCELLED";
  amountCop: number;
  creditAppliedCop: number;
  chargedCop: number;
  paidAt: string | null;
  expiresAt: string | null;
  /** Solo mientras el pago siga pendiente: la vuelta a la pasarela sin perder el cupo. */
  checkoutUrl: string | null;
};

export type CreditResponse = {
  id: string;
  amountCop: number;
  remainingCop: number;
  reason: string;
  bookingId: string | null;
  expiresAt: string | null;
  createdAt: string;
};

export type CreditBalanceResponse = { balanceCop: number; credits: CreditResponse[] };

export type MyPaymentResponse = {
  paymentId: string;
  bookingId: string;
  classAt: string | null;
  professorName: string | null;
  amountCop: number;
  creditAppliedCop: number;
  chargedCop: number;
  status: string;
  paidAt: string | null;
};

export type EarningsResponse = {
  heldCop: number;
  payableCop: number;
  transferredCop: number;
  totalCop: number;
  lines: {
    bookingId: string;
    classAt: string | null;
    studentName: string | null;
    amountCop: number;
    commissionCop: number;
    earningsCop: number;
    status: string;
  }[];
};

export type AdminPaymentResponse = {
  paymentId: string;
  bookingId: string;
  classAt: string | null;
  bookingStatus: string | null;
  studentId: string;
  studentName: string | null;
  professorId: string;
  professorName: string | null;
  amountCop: number;
  creditAppliedCop: number;
  chargedCop: number;
  commissionRateBps: number;
  commissionCop: number;
  professorEarningsCop: number;
  status: string;
  provider: string | null;
  providerReference: string | null;
  paidAt: string | null;
  releasedAt: string | null;
  /** Pagada pero sin clase: alguien tiene que decidir entre saldo o devolución por Wompi. */
  needsReview: boolean;
  /** Cuánto abonarle al estudiante para dejarlo indemne. Sugerencia del backend, editable. */
  suggestedCreditCop: number;
};

export type PayoutResponse = {
  id: string;
  professorId: string;
  professorName: string | null;
  periodStart: string;
  periodEnd: string;
  amountCop: number;
  status: "PENDING" | "PAID" | "CANCELLED";
  reference: string | null;
  paidAt: string | null;
  createdAt: string;
};
