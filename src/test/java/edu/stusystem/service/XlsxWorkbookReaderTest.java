package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XlsxWorkbookReaderTest {
    private final XlsxWorkbookReader reader = new XlsxWorkbookReader(new SpreadsheetUploadPolicy());

    @Test
    void preservesTextIdentifiersAndOriginalRowNumbers() throws Exception {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("accounts");
        sheet.createRow(0).createCell(0).setCellValue("学号/工号");
        sheet.createRow(1).createCell(0).setCellValue("000012345678901234");
        MockMultipartFile file = xlsx(workbook);

        var rows = reader.read(file);

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(1).sourceRow());
        assertEquals("000012345678901234", rows.get(1).cells().get(0).text());
        assertEquals(CellType.STRING, rows.get(1).cells().get(0).type());
    }

    @Test
    void rejectsFormulaCellsInsteadOfEvaluatingThem() throws Exception {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("accounts");
        sheet.createRow(0).createCell(0).setCellValue("name");
        sheet.createRow(1).createCell(0).setCellFormula("1+1");

        ApiException error = assertThrows(ApiException.class, () -> reader.read(xlsx(workbook)));

        assertEquals("FILE_FORMULA_NOT_ALLOWED", error.code());
    }

    @Test
    void rejectsDataRowsBeyondTheConfiguredLimit() throws Exception {
        var workbook = new XSSFWorkbook();
        workbook.createSheet("accounts").createRow(SpreadsheetUploadPolicy.MAX_DATA_ROWS + 1).createCell(0).setCellValue("x");

        ApiException error = assertThrows(ApiException.class, () -> reader.read(xlsx(workbook)));

        assertEquals("FILE_TOO_MANY_ROWS", error.code());
    }

    private static MockMultipartFile xlsx(XSSFWorkbook workbook) throws Exception {
        try (workbook; var output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return new MockMultipartFile("file", "input.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
