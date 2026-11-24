package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeImportServiceTest {
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final CurrentUser TEACHER = new CurrentUser(9, "teacher", "Teacher", "TEACHER", 0, null);

    @Test
    void resolvesTextStudentNumberAndDelegatesFullBatchToGradeTransactionPath() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        DomainService domain = mock(DomainService.class);
        when(db.queryForObject(contains("student_profile"), eq(Long.class), eq("000000000123456789"))).thenReturn(11L);
        when(db.queryForObject(contains("course_catalog"), eq(Long.class), eq("Mathematics"))).thenReturn(22L);
        when(db.queryForObject(contains("academic_term"), eq(Long.class), eq("2026-FALL"))).thenReturn(33L);
        GradeImportService service = new GradeImportService(db, new XlsxWorkbookReader(new SpreadsheetUploadPolicy()), domain);
        var file = gradeWorkbook("000000000123456789", "Mathematics", "3", "考试", "2026-FALL", "95.50");

        service.importGrades(TEACHER, file);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(domain).addGradesBatch(eq(TEACHER), captor.capture());
        Map<?, ?> row = (Map<?, ?>) captor.getValue().getFirst();
        assertEquals("11", row.get("studentId"));
        assertEquals("22", row.get("courseId"));
        assertEquals("33", row.get("termId"));
        assertEquals("EXAM", row.get("assessmentType"));
        assertEquals(0, new BigDecimal("95.50").compareTo(new BigDecimal(row.get("score").toString())));
    }

    @Test
    void rejectsNumericStudentNumberInsteadOfCoercingIt() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        DomainService domain = mock(DomainService.class);
        GradeImportService service = new GradeImportService(db, new XlsxWorkbookReader(new SpreadsheetUploadPolicy()), domain);
        var file = gradeWorkbook(123456789L, "Mathematics", "3", "考试", "2026-FALL", "95.5");

        ApiException error = assertThrows(ApiException.class, () -> service.importGrades(TEACHER, file));

        assertEquals("GRADE_IMPORT_INVALID_ROW", error.code());
        verify(domain, never()).addGradesBatch(eq(TEACHER), anyList());
    }

    private static MockMultipartFile gradeWorkbook(Object studentNo, String course, String credits,
            String assessment, String term, String score) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("grades");
            var headers = List.of("学号", "课程名称", "学分", "考核性质", "学期编码", "成绩");
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) header.createCell(i).setCellValue(headers.get(i));
            var row = sheet.createRow(1);
            if (studentNo instanceof String text) row.createCell(0, CellType.STRING).setCellValue(text);
            else row.createCell(0, CellType.NUMERIC).setCellValue(((Number) studentNo).doubleValue());
            row.createCell(1).setCellValue(course);
            row.createCell(2).setCellValue(new BigDecimal(credits).doubleValue());
            row.createCell(3).setCellValue(assessment);
            row.createCell(4).setCellValue(term);
            row.createCell(5).setCellValue(new BigDecimal(score).doubleValue());
            workbook.write(output);
            return new MockMultipartFile("file", "grades.xlsx", MIME, output.toByteArray());
        }
    }
}
