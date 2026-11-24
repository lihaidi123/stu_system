package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class XlsxWorkbookWriter {
    private static final int MAX_CELL_CHARS = 32_767;
    private static final int MAX_CELLS = 100_000;
    private static final long MAX_TEXT_BYTES = 8L * 1024 * 1024;

    public byte[] write(String sheetName, List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty() || headers.size() > 64) {
            throw invalid("FILE_COLUMNS_INVALID", "导出列数无效");
        }
        if (rows == null || rows.size() > SpreadsheetUploadPolicy.MAX_DATA_ROWS) {
            throw invalid("FILE_TOO_MANY_ROWS", "导出数据超过 10000 行限制");
        }
        if ((long) headers.size() * (rows.size() + 1L) > MAX_CELLS) {
            throw invalid("FILE_TOO_LARGE", "导出单元格数量超过限制");
        }
        long bytes = textBytes(headers);
        for (var row : rows) {
            if (row == null || row.size() != headers.size()) throw invalid("FILE_ROW_INVALID", "导出数据列数不一致");
            bytes = Math.addExact(bytes, textBytes(row));
            if (bytes > MAX_TEXT_BYTES) throw invalid("FILE_TOO_LARGE", "导出内容超过限制");
        }
        if (bytes > MAX_TEXT_BYTES) throw invalid("FILE_TOO_LARGE", "导出内容超过限制");
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(sheetName);
            writeRow(sheet.createRow(0), headers);
            for (int i = 0; i < rows.size(); i++) writeRow(sheet.createRow(i + 1), rows.get(i));
            workbook.write(output);
            if (output.size() > SpreadsheetUploadPolicy.MAX_UPLOAD_BYTES) {
                throw invalid("FILE_TOO_LARGE", "生成文件超过 10 MiB 限制");
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_GENERATION_FAILED", "文件生成失败");
        }
    }

    private void writeRow(org.apache.poi.ss.usermodel.Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i) == null ? "" : values.get(i);
            if (value.length() > MAX_CELL_CHARS) throw invalid("FILE_CELL_TOO_LARGE", "单元格内容超过限制");
            row.createCell(i, CellType.STRING).setCellValue(value);
        }
    }

    private long textBytes(List<String> values) {
        long total = 0;
        for (String value : values) {
            if (value != null && value.length() > MAX_CELL_CHARS) throw invalid("FILE_CELL_TOO_LARGE", "单元格内容超过限制");
            total = Math.addExact(total, value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length);
            if (total > MAX_TEXT_BYTES) throw invalid("FILE_TOO_LARGE", "导出内容超过限制");
        }
        return total;
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
