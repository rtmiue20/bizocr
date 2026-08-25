package com.company.bizocr.service;

import com.company.bizocr.model.entity.DocumentRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final ObjectMapper objectMapper;

    public Mono<byte[]> exportToExcel(List<DocumentRecord> documents) {
        return Mono.fromCallable(() -> {
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Tối ưu bộ nhớ với SXSSF
                SXSSFSheet sheet = workbook.createSheet("Du Lieu Ke Toan");

                // Tạo Header
                SXSSFRow headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("ID");
                headerRow.createCell(1).setCellValue("Tên File");
                headerRow.createCell(2).setCellValue("Trạng Thái");
                headerRow.createCell(3).setCellValue("Mã Số Thuế");
                headerRow.createCell(4).setCellValue("Tổng Tiền");

                // Ghi dữ liệu
                int rowIndex = 1;
                for (DocumentRecord doc : documents) {
                    SXSSFRow row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(doc.getId());
                    row.createCell(1).setCellValue(doc.getFileName());
                    row.createCell(2).setCellValue(doc.getStatus());

                    // Bóc tách JSON
                    if (doc.getExtractedFields() != null && !doc.getExtractedFields().equals("{}")) {
                        try {
                            JsonNode jsonNode = objectMapper.readTree(doc.getExtractedFields());
                            row.createCell(3).setCellValue(jsonNode.path("seller_tax_code").asText(""));
                            row.createCell(4).setCellValue(jsonNode.path("total_amount").asText(""));
                        } catch (Exception e) {
                            row.createCell(3).setCellValue("Lỗi JSON");
                        }
                    }
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                workbook.write(out);
                return out.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic()); // Chạy I/O trên thread riêng tránh block WebFlux
    }
}