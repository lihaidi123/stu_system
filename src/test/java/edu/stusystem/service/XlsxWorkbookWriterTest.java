package edu.stusystem.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XlsxWorkbookWriterTest {
    @Test
    void writesUntrustedTextAsStringCells() throws Exception {
        byte[] bytes = new XlsxWorkbookWriter().write("export", List.of("value"),
                List.of(List.of("=HYPERLINK(\"https://example.invalid\",\"open\")")));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var cell = workbook.getSheetAt(0).getRow(1).getCell(0);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("=HYPERLINK(\"https://example.invalid\",\"open\")", cell.getStringCellValue());
        }
    }

    @Test
    void rejectsExportsBeyondTheConfiguredRowBoundary() {
        var writer=new XlsxWorkbookWriter();
        var rows=java.util.Collections.nCopies(SpreadsheetUploadPolicy.MAX_DATA_ROWS+1,List.of("value"));

        var error=assertThrows(edu.stusystem.api.ApiException.class,()->writer.write("export",List.of("value"),rows));

        assertEquals("FILE_TOO_MANY_ROWS",error.code());
    }
}
