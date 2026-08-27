package com.kce.kmrl.pdf;

import com.kce.kmrl.dto.LiveSummaryDto;
import com.kce.kmrl.entity.Report;
import com.kce.kmrl.entity.ReportCategory;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ReportPdfGenerator {

    private static final Color TEAL = new Color(0, 150, 136);
    private static final Color INK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color TABLE_HEADER_BG = new Color(240, 244, 248);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    public byte[] generate(Report report, LiveSummaryDto liveSummary) {
        Document document = new Document(PageSize.A4, 50, 50, 60, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, report);
            addMetadataTable(document, report);
            addSummary(document, report);

            if (liveSummary != null) {
                addLiveDataTables(document, liveSummary, report.getCategory());
            }

            addFooter(document);
            document.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render report PDF: " + ex.getMessage(), ex);
        }

        return out.toByteArray();
    }

    private void addHeader(Document document, Report report) throws DocumentException {
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, TEAL);
        Paragraph brand = new Paragraph("MetroMind KMRL", brandFont);
        document.add(brand);

        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED);
        Paragraph sub = new Paragraph("Fleet induction & scheduling console — Report", subFont);
        sub.setSpacingAfter(16);
        document.add(sub);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
        Paragraph title = new Paragraph(report.getTitle(), titleFont);
        title.setSpacingAfter(10);
        document.add(title);
    }

    private void addMetadataTable(Document document, Report report) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);
        table.setWidths(new float[]{1f, 2f});

        addMetaRow(table, "Category", report.getCategory() != null ? report.getCategory().name() : "-");
        addMetaRow(table, "Status", report.getStatus() != null ? report.getStatus().name() : "-");
        addMetaRow(table, "Author", report.getAuthor());
        addMetaRow(table, "Generated", report.getGeneratedDate() != null ? DATE_FMT.format(report.getGeneratedDate()) : "-");
        addMetaRow(table, "Report ID", report.getId() != null ? report.getId() : "(assigned on save)");

        document.add(table);
    }

    private void addMetaRow(PdfPTable table, String label, String value) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MUTED);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label.toUpperCase(), labelFont));
        labelCell.setBorder(0);
        labelCell.setPaddingBottom(6);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(0);
        valueCell.setPaddingBottom(6);
        table.addCell(valueCell);
    }

    private void addSummary(Document document, Report report) throws DocumentException {
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
        Paragraph heading = new Paragraph("Summary", headingFont);
        heading.setSpacingAfter(6);
        document.add(heading);

        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, INK);
        Paragraph body = new Paragraph(
                report.getSummary() != null ? report.getSummary() : "No summary provided.", bodyFont);
        body.setSpacingAfter(18);
        body.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(body);
    }

    private void addLiveDataTables(Document document, LiveSummaryDto live, ReportCategory category) throws DocumentException {
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
        Paragraph heading = new Paragraph("Live Network Data", headingFont);
        heading.setSpacingAfter(8);
        document.add(heading);

        if (category == null) {
            return;
        }

        if (category == ReportCategory.FLEET || category == ReportCategory.OVERALL || category == ReportCategory.ALERTS || category == ReportCategory.SAFETY) {
            addFleetTable(document, live);
        }
        if (category == ReportCategory.MAINTENANCE || category == ReportCategory.OVERALL || category == ReportCategory.ALERTS || category == ReportCategory.SAFETY) {
            addMaintenanceTable(document, live);
        }
        if (category == ReportCategory.SCHEDULE || category == ReportCategory.OVERALL || category == ReportCategory.ALERTS || category == ReportCategory.SAFETY) {
            addScheduleTable(document, live);
        }
    }

    private void addFleetTable(Document document, LiveSummaryDto live) throws DocumentException {
        if (live.getFleet() != null) {
            addDataTable(document, "Fleet", new String[]{"Active", "Standby", "Maintenance", "Total"},
                    new String[]{
                        String.valueOf(live.getFleet().getActive()),
                        String.valueOf(live.getFleet().getStandby()),
                        String.valueOf(live.getFleet().getMaintenance()),
                        String.valueOf(live.getFleet().getTotal())
                    });
        } else {
            addUnavailableNote(document, "Fleet");
        }
    }

    private void addMaintenanceTable(Document document, LiveSummaryDto live) throws DocumentException {
        if (live.getMaintenance() != null) {
            addDataTable(document, "Maintenance", new String[]{"Open", "In Progress", "Completed", "Critical", "High", "Total"},
                    new String[]{
                        String.valueOf(live.getMaintenance().getOpen()),
                        String.valueOf(live.getMaintenance().getInProgress()),
                        String.valueOf(live.getMaintenance().getCompleted()),
                        String.valueOf(live.getMaintenance().getCritical()),
                        String.valueOf(live.getMaintenance().getHigh()),
                        String.valueOf(live.getMaintenance().getTotal())
                    });
        } else {
            addUnavailableNote(document, "Maintenance");
        }
    }

    private void addScheduleTable(Document document, LiveSummaryDto live) throws DocumentException {
        if (live.getSchedule() != null) {
            addDataTable(document, "Schedule (current window)", new String[]{"Active", "Completed", "Delayed", "Planned", "Total"},
                    new String[]{
                        String.valueOf(live.getSchedule().getActive()),
                        String.valueOf(live.getSchedule().getCompleted()),
                        String.valueOf(live.getSchedule().getDelayed()),
                        String.valueOf(live.getSchedule().getPlanned()),
                        String.valueOf(live.getSchedule().getTotal())
                    });
        } else {
            addUnavailableNote(document, "Schedule");
        }
    }

    private void addDataTable(Document document, String sectionTitle, String[] columns, String[] values) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, TEAL);
        Paragraph section = new Paragraph(sectionTitle, sectionFont);
        section.setSpacingBefore(6);
        section.setSpacingAfter(4);
        document.add(section);

        PdfPTable table = new PdfPTable(columns.length);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MUTED);
        for (String col : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(col.toUpperCase(), headerFont));
            cell.setBackgroundColor(TABLE_HEADER_BG);
            cell.setPadding(6);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }

        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
        for (String val : values) {
            PdfPCell cell = new PdfPCell(new Phrase(val, valueFont));
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }

        document.add(table);
    }

    private void addUnavailableNote(Document document, String sectionTitle) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, TEAL);
        Paragraph section = new Paragraph(sectionTitle, sectionFont);
        section.setSpacingBefore(6);
        section.setSpacingAfter(2);
        document.add(section);

        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, MUTED);
        Paragraph note = new Paragraph(
                sectionTitle + "-service was unreachable when this report was generated; this section is unavailable.",
                noteFont);
        note.setSpacingAfter(12);
        document.add(note);
    }

    private void addFooter(Document document) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, MUTED);
        Paragraph footer = new Paragraph(
                "Generated by report-service — MetroMind KMRL Internal Systems", footerFont);
        footer.setSpacingBefore(24);
        document.add(footer);
    }
}
