package com.company.bizocr.service;

import com.company.bizocr.extract.DocumentFieldSchema;
import com.company.bizocr.model.entity.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Xuất Excel: mỗi loại chứng từ = 1 sheet, bộ cột lấy từ DocumentFieldSchema (cùng form với Bước 5).
 * Các chứng từ cùng loại được ghi nối tiếp xuống dưới tiêu đề, đánh STT 1, 2, 3...
 *
 * - Loại có schema        -> cột cố định; dữ liệu cũ (key khác tên) được gom về đúng cột.
 * - Loại tùy biến (không có schema) -> cột động theo key thực tế, bỏ key nội bộ.
 * - Chứng từ chưa phân loại -> sheet CHUA_PHAN_LOAI, dùng form chung (OTHER).
 */
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final String UNCLASSIFIED = "CHUA_PHAN_LOAI";
    private static final int FIXED_COLS = 4; // STT, ID, Tên File, Trạng Thái

    private final ObjectMapper objectMapper;

    private record Col(String header, String key, int width) {
    }

    private record Parsed(Document doc, JsonNode fields) {
    }

    // ------------------------------------------------------------------
    // Xuất file
    // ------------------------------------------------------------------

    public Mono<byte[]> exportToExcel(List<Document> documents) {
        return Mono.fromCallable(() -> {
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                if (documents == null || documents.isEmpty()) {
                    SXSSFSheet sheet = workbook.createSheet("Dữ Liệu Trống");
                    sheet.createRow(0).createCell(0).setCellValue("Không có dữ liệu chứng từ");
                } else {
                    CellStyle headerStyle = createHeaderStyle(workbook);

                    // Sắp theo ID tăng dần để STT trong từng sheet đi theo thứ tự nhận chứng từ
                    List<Document> sorted = new ArrayList<>(documents);
                    sorted.sort(Comparator.comparing(Document::getId, Comparator.nullsLast(Comparator.naturalOrder())));

                    Map<String, List<Document>> bySheet = new LinkedHashMap<>();
                    for (Document d : sorted) {
                        bySheet.computeIfAbsent(sheetKey(d.getDocumentType()), k -> new ArrayList<>()).add(d);
                    }

                    for (Map.Entry<String, List<Document>> e : bySheet.entrySet()) {
                        writeSheet(workbook, headerStyle, e.getKey(), e.getValue());
                    }
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                workbook.write(out);
                return out.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void writeSheet(SXSSFWorkbook wb, CellStyle headerStyle, String sheetKey, List<Document> docs) {
        String schemaType = UNCLASSIFIED.equals(sheetKey) ? "OTHER" : sheetKey;
        List<DocumentFieldSchema.Field> schema = DocumentFieldSchema.fieldsOf(schemaType);

        // Parse + chuẩn hóa từng chứng từ về bộ key chuẩn (nếu loại đó có schema)
        List<Parsed> rows = new ArrayList<>();
        for (Document d : docs) {
            JsonNode fields = parseJsonRobust(d.getExtractedFields());
            if (schema != null && fields != null && fields.isObject()) {
                ObjectNode normalized = DocumentFieldSchema.normalizeToNode(schemaType, fields, objectMapper);
                if (normalized != null) fields = normalized;
            }
            rows.add(new Parsed(d, fields));
        }

        List<Col> cols = new ArrayList<>();
        if (schema != null) {
            for (DocumentFieldSchema.Field f : schema) {
                cols.add(new Col(f.label(), f.key(), widthOf(f.key())));
            }
        } else {
            cols = dynamicCols(rows);
        }

        SXSSFSheet sheet = wb.createSheet(uniqueSheetName(wb, sheetKey));
        int noteCol = FIXED_COLS + cols.size();

        // ---- Header ----
        SXSSFRow header = sheet.createRow(0);
        setText(header, 0, "STT", headerStyle);
        setText(header, 1, "ID", headerStyle);
        setText(header, 2, "Tên File", headerStyle);
        setText(header, 3, "Trạng Thái", headerStyle);
        for (int i = 0; i < cols.size(); i++) {
            setText(header, FIXED_COLS + i, cols.get(i).header(), headerStyle);
        }
        setText(header, noteCol, "Ghi chú", headerStyle);

        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 32 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        for (int i = 0; i < cols.size(); i++) {
            sheet.setColumnWidth(FIXED_COLS + i, Math.min(cols.get(i).width(), 60) * 256);
        }
        sheet.setColumnWidth(noteCol, 30 * 256);
        sheet.createFreezePane(0, 1);

        // ---- Dữ liệu: ghi nối tiếp xuống dưới, STT 1, 2, 3... ----
        int rowIndex = 1;
        int stt = 1;
        for (Parsed p : rows) {
            SXSSFRow row = sheet.createRow(rowIndex++);
            Document doc = p.doc();

            row.createCell(0).setCellValue(stt++);
            row.createCell(1).setCellValue(doc.getId());
            setText(row, 2, doc.getFileName() != null ? doc.getFileName() : "", null);
            setText(row, 3, doc.getStatus() != null ? doc.getStatus() : "", null);

            for (int i = 0; i < cols.size(); i++) {
                String val = textOf(p.fields() == null ? null : p.fields().get(cols.get(i).key()));
                if (!val.isEmpty()) {
                    setText(row, FIXED_COLS + i, val, null); // luôn ghi dạng chữ để giữ số 0 đầu của MST/SĐT
                }
            }

            String note = noteFor(doc, p.fields());
            if (!note.isEmpty()) {
                setText(row, noteCol, note, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Tên sheet: mã loại chuẩn; chưa phân loại (rỗng / UNKNOWN) -> CHUA_PHAN_LOAI. */
    private String sheetKey(String rawType) {
        if (rawType == null || rawType.isBlank() || "UNKNOWN".equalsIgnoreCase(rawType.trim())) {
            return UNCLASSIFIED;
        }
        return DocumentFieldSchema.canonicalType(rawType);
    }

    /** Cột động cho loại tùy biến: gộp key thực tế theo thứ tự gặp, bỏ key nội bộ. */
    private List<Col> dynamicCols(List<Parsed> rows) {
        Set<String> keys = new LinkedHashSet<>();
        for (Parsed p : rows) {
            JsonNode node = p.fields();
            if (node == null || !node.isObject()) continue;
            node.fieldNames().forEachRemaining(name -> {
                if (!DocumentFieldSchema.isInternalKey(name)) keys.add(name);
            });
        }
        List<Col> cols = new ArrayList<>();
        for (String k : keys) {
            cols.add(new Col(k, k, 24));
        }
        return cols;
    }

    private int widthOf(String key) {
        return (key.contains("address") || key.contains("place_of") || key.equals("content")
                || key.equals("note") || key.endsWith("_name")) ? 36 : 22;
    }

    private String textOf(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return "";
        if (v.isContainerNode()) return v.toString();
        return v.asText().trim();
    }

    private String noteFor(Document doc, JsonNode fields) {
        String raw = doc.getExtractedFields();
        if (raw == null || raw.isBlank() || raw.trim().equals("{}")) {
            return "Chưa có dữ liệu trích xuất";
        }
        if (fields == null || !fields.isObject()) {
            return "Dữ liệu trường không đọc được";
        }
        return "";
    }

    private String uniqueSheetName(SXSSFWorkbook wb, String type) {
        String base = WorkbookUtil.createSafeSheetName(type);
        String name = base;
        int i = 2;
        while (wb.getSheet(name) != null) {
            String suffix = "_" + i++;
            name = (base.length() + suffix.length() > 31)
                    ? base.substring(0, 31 - suffix.length()) + suffix
                    : base + suffix;
        }
        return name;
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void setText(SXSSFRow row, int idx, String value, CellStyle style) {
        var cell = row.createCell(idx);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    // Xử lý JSON bị bọc nhiều lớp chuỗi (tối đa 3 lớp)
    private JsonNode parseJsonRobust(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(raw);
            for (int i = 0; i < 3 && node != null && node.isTextual(); i++) {
                String text = node.asText().trim();
                if (!(text.startsWith("{") || text.startsWith("["))) break;
                node = objectMapper.readTree(text);
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }
}