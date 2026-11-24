package edu.stusystem.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserAcceptanceWorkbookFixtureTest {
    private static final Path OUTPUT = Path.of("target", "browser-acceptance-fixtures");

    @Test
    void generatesAccountAndGradeWorkbooksForIsolatedBrowserAcceptance() throws Exception {
        Files.createDirectories(OUTPUT);
        byte[] accounts = workbook(
                List.of("学号/工号", "用户名", "姓名", "邮箱", "手机号", "角色", "状态"),
                List.of("stage4-browser-import", "browser-import", "Browser Imported", "", "", "TEACHER", "启用"));
        byte[] grades = workbook(
                List.of("学号", "课程名称", "学分", "考核性质", "学期编码", "成绩"),
                List.of("stage4-student", "Stage 4 Test Course", "3", "考试", "stage4-2099-fall", "88.25"));
        Path accountFile = OUTPUT.resolve("stage4-browser-accounts.xlsx");
        Path gradeFile = OUTPUT.resolve("stage4-browser-grades.xlsx");
        Files.write(accountFile, accounts);
        Files.write(gradeFile, grades);

        try (var accountBook = WorkbookFactory.create(new ByteArrayInputStream(Files.readAllBytes(accountFile)));
             var gradeBook = WorkbookFactory.create(new ByteArrayInputStream(Files.readAllBytes(gradeFile)))) {
            assertEquals("stage4-browser-import", accountBook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("stage4-student", gradeBook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
        assertTrue(Files.size(accountFile) > 0);
        assertTrue(Files.size(gradeFile) > 0);
    }

    private byte[] workbook(List<String> header, List<String> row) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("import");
            var headerRow = sheet.createRow(0);
            for (int index = 0; index < header.size(); index++) {
                headerRow.createCell(index).setCellValue(header.get(index));
            }
            var data = sheet.createRow(1);
            for (int index = 0; index < row.size(); index++) {
                data.createCell(index).setCellValue(row.get(index));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
