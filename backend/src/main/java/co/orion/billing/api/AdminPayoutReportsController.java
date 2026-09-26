package co.orion.billing.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.orion.billing.application.PayoutCertificates;
import co.orion.billing.application.PayoutReports;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;

/**
 * Los reportes contables del mandato y el certificado anual (brief de liquidaciones, paso 6). Los CSV
 * van en UTF-8 con BOM para que Excel lea bien las tildes. Pardo concilia a mano con el libro de
 * mandato: no hay conciliación automática contra Wompi (fuera de alcance).
 */
@RestController
@RequestMapping("/api/v1/admin/payouts")
public class AdminPayoutReportsController {

    private static final String BOM = "﻿";

    private final PayoutReports reports;
    private final PayoutCertificates certificates;

    public AdminPayoutReportsController(PayoutReports reports, PayoutCertificates certificates) {
        this.reports = reports;
        this.certificates = certificates;
    }

    /** Libro de mandato: una fila por reserva con pago aprobado en el rango. */
    @GetMapping("/reports/ledger.csv")
    public ResponseEntity<byte[]> ledger(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleViolationException("La fecha inicial va antes que la final");
        }
        StringBuilder csv = new StringBuilder(BOM + "fecha_pago,referencia_wompi,estado_pago,estudiante,profe_documento,"
                + "profe_nombre,bruto_cop,comision_cop,neto_cop,liquidacion,estado_liquidacion,fecha_pago_liquidacion\n");
        for (PayoutReports.LedgerRow r : reports.ledger(from, to)) {
            csv.append(fechaHora(r.paidAt())).append(',').append(q(r.providerReference())).append(',')
                    .append(r.paymentStatus()).append(',').append(q(r.studentName())).append(',')
                    .append(q(r.professorDocument())).append(',').append(q(r.professorName())).append(',')
                    .append(r.grossCop()).append(',').append(r.commissionCop()).append(',').append(r.netCop()).append(',')
                    .append(r.payoutPeriodStart() != null ? r.payoutPeriodStart() + " a " + r.payoutPeriodEnd() : "").append(',')
                    .append(r.payoutStatus() != null ? r.payoutStatus() : "").append(',')
                    .append(r.payoutPaidOn() != null ? r.payoutPaidOn() : "").append('\n');
        }
        return csv(csv, "libro-de-mandato-" + from + "-a-" + to + ".csv");
    }

    /** Resumen anual por profe: recibido, comisión, entregado, pendiente al 31 de diciembre y retenciones. */
    @GetMapping("/reports/annual.csv")
    public ResponseEntity<byte[]> annual(@RequestParam int year) {
        StringBuilder csv = new StringBuilder(BOM + "profe_documento,profe_nombre,recibido_en_su_nombre_cop,comision_cop,"
                + "entregado_cop,pendiente_al_31_dic_cop,retenciones_cop\n");
        for (PayoutReports.AnnualRow r : reports.annual(year)) {
            csv.append(q(r.professorDocument())).append(',').append(q(r.professorName())).append(',')
                    .append(r.receivedCop()).append(',').append(r.commissionCop()).append(',')
                    .append(r.deliveredCop()).append(',').append(r.pendingCop()).append(',')
                    .append(r.withheldCop()).append('\n');
        }
        return csv(csv, "resumen-anual-" + year + ".csv");
    }

    /** Comisiones por mes: el ingreso propio de Orión. */
    @GetMapping("/reports/commissions.csv")
    public ResponseEntity<byte[]> commissions(@RequestParam int year) {
        StringBuilder csv = new StringBuilder(BOM + "mes,clases,recibido_cop,comision_cop\n");
        for (PayoutReports.MonthRow r : reports.commissionsByMonth(year)) {
            csv.append(year).append('-').append(String.format("%02d", r.month())).append(',').append(r.classes())
                    .append(',').append(r.receivedCop()).append(',').append(r.commissionCop()).append('\n');
        }
        return csv(csv, "comisiones-" + year + ".csv");
    }

    /** El indicador del panel: comisiones y recaudo del año, este en UVT junto a la referencia de 3.500. */
    @GetMapping("/year-summary")
    public PayoutReports.YearSummary yearSummary(@RequestParam(required = false) Integer year) {
        return reports.yearSummary(year != null ? year : LocalDate.now(BusinessZone.BOGOTA).getYear());
    }

    /** Los profes con movimiento en el año, con su resumen y si ya tienen el certificado firmado. */
    @GetMapping("/certificates")
    public List<CertificateRow> certificates(@RequestParam int year) {
        return reports.annual(year).stream().map(r -> new CertificateRow(r.professorId(), r.professorName(),
                r.receivedCop(), r.commissionCop(), r.deliveredCop(), r.pendingCop(),
                certificates.of(r.professorId()).stream().filter(c -> c.year() == year)
                        .map(PayoutCertificates.Uploaded::uploadedAt).findFirst().orElse(null))).toList();
    }

    /** El borrador para imprimir y llevar al contador. */
    @GetMapping("/certificates/{professorId}/{year}/draft")
    public PayoutReports.CertificateDraft draft(@PathVariable UUID professorId, @PathVariable int year) {
        return reports.certificateDraft(professorId, year);
    }

    /** El PDF firmado por el contador. Reemplaza al anterior del mismo año, si lo había. */
    @PostMapping("/certificates/{professorId}/{year}")
    public Map<String, Object> upload(@PathVariable UUID professorId, @PathVariable int year,
                                      @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal OrionUserDetails principal) throws IOException {
        certificates.upload(professorId, year, file.getBytes(), file.getContentType(), file.getOriginalFilename(),
                principal.user().getId());
        return Map.of("year", year, "uploaded", true);
    }

    @GetMapping("/certificates/{professorId}/{year}/url")
    public Map<String, String> url(@PathVariable UUID professorId, @PathVariable int year) {
        return Map.of("url", certificates.signedUrl(professorId, year));
    }

    private static ResponseEntity<byte[]> csv(StringBuilder csv, String nombre) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String fechaHora(Instant instante) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(BusinessZone.BOGOTA).format(instante);
    }

    /** Un nombre con coma partiría la columna del CSV en dos. */
    private static String q(String valor) {
        return valor == null ? "" : '"' + valor.replace("\"", "\"\"") + '"';
    }

    public record CertificateRow(UUID professorId, String professorName, long receivedCop, long commissionCop,
                                 long deliveredCop, long pendingCop, Instant uploadedAt) {
    }
}
