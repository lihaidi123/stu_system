package edu.stusystem.service;

import edu.stusystem.security.CurrentUser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountImportServiceTest {
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final CurrentUser ADMIN = new CurrentUser(1, "admin", "Admin", "ADMIN", 0, null);

    @Test
    void createsValidRowsAndSkipsRepeatedIdentityWithoutReturningPassword() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        DomainService domain = mock(DomainService.class);
        when(db.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(domain.create(eq(ADMIN), eq("accounts"), any(Map.class))).thenReturn(Map.of("created", true));
        AccountImportService service = new AccountImportService(db, new XlsxWorkbookReader(new SpreadsheetUploadPolicy()), domain);
        var file = workbook(List.of(
                List.of("000001", "teacher1", "Teacher", "", "", "TEACHER", "启用"),
                List.of("000001", "teacher2", "Teacher Two", "", "", "TEACHER", "启用")
        ));

        Map<String, Object> result = service.importAccounts(ADMIN, file, "TmpPass123!");

        assertEquals(1, result.get("created"));
        assertEquals(1, result.get("skipped"));
        assertEquals(0, result.get("failed"));
        assertFalse(result.toString().contains("TmpPass123!"));
        verify(domain).create(eq(ADMIN), eq("accounts"), any(Map.class));
    }

    @Test
    void rejectsMissingOrInvalidRoleBeforeCreatingAnyAccount() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        DomainService domain = mock(DomainService.class);
        AccountImportService service = new AccountImportService(db, new XlsxWorkbookReader(new SpreadsheetUploadPolicy()), domain);
        var file = workbook(List.of(List.of("000001", "user1", "Name", "", "", "ROOT", "启用")));

        Map<String, Object> result = service.importAccounts(ADMIN, file, "TmpPass123!");

        assertEquals(0, result.get("created"));
        assertEquals(1, result.get("failed"));
        verify(domain, never()).create(any(), anyString(), any(Map.class));
    }

    private static MockMultipartFile workbook(List<List<String>> data) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("accounts");
            var headers = List.of("学号/工号", "用户名", "姓名", "邮箱", "手机号", "角色", "状态");
            write(sheet.createRow(0), headers);
            for (int i = 0; i < data.size(); i++) write(sheet.createRow(i + 1), data.get(i));
            workbook.write(output);
            return new MockMultipartFile("file", "accounts.xlsx", MIME, output.toByteArray());
        }
    }

    private static void write(org.apache.poi.ss.usermodel.Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
    }
}
