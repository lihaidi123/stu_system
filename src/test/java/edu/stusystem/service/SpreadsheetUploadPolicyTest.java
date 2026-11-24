package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpreadsheetUploadPolicyTest {
    private final SpreadsheetUploadPolicy policy = new SpreadsheetUploadPolicy();

    @Test
    void rejectsZipHeaderThatIsNotAnOoxmlWorkbook() {
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 3, 4, 1, 2, 3});

        ApiException error = assertThrows(ApiException.class, () -> policy.validate(file));

        assertEquals("FILE_CONTENT_INVALID", error.code());
    }

    @Test
    void acceptsGeneratedXlsxWorkbook() throws Exception {
        byte[] content;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("accounts").createRow(0).createCell(0).setCellValue("学号/工号");
            workbook.write(output);
            content = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        policy.validate(file);
    }

    @Test
    void rejectsWorkbookWithTooManyZipEntries() throws Exception {
        byte[] content = workbookWithExtraEntries((int) SpreadsheetUploadPolicy.MAX_ZIP_ENTRIES + 1);
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        ApiException error = assertThrows(ApiException.class, () -> policy.validate(file));

        assertEquals("FILE_CONTENT_INVALID", error.code());
    }

    @Test
    void rejectsHighlyCompressedWorkbookEntry() throws Exception {
        byte[] content;
        String repeated = "x".repeat(30_000);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("accounts");
            for (int index = 0; index < 60; index++) {
                sheet.createRow(index).createCell(0).setCellValue(repeated + index);
            }
            workbook.write(output);
            content = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        assertTrue(content.length < SpreadsheetUploadPolicy.MAX_UPLOAD_BYTES);
        ApiException error = assertThrows(ApiException.class, () -> policy.validate(file));

        assertEquals("FILE_CONTENT_INVALID", error.code());
    }

    @Test
    void rejectsWorkbookWhoseCombinedExpandedEntriesExceedPackageBudget() throws Exception {
        byte[] content = workbookWithExpandedEntries(8, 8 * 1024 * 1024 + 512 * 1024);
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        assertTrue(content.length < SpreadsheetUploadPolicy.MAX_UPLOAD_BYTES);
        ApiException error = assertThrows(ApiException.class, () -> policy.validate(file));

        assertEquals("FILE_EXPANDED_TOO_LARGE", error.code());
    }

    @Test
    void acceptsWorkbookWithinCombinedExpandedEntryBudget() throws Exception {
        byte[] content = workbookWithExpandedEntries(6, 8 * 1024 * 1024 + 512 * 1024);
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        assertTrue(content.length < SpreadsheetUploadPolicy.MAX_UPLOAD_BYTES);
        policy.validateUpload(file);
    }

    @Test
    void rejectsSingleExpandedEntryAboveEntryBudget() throws Exception {
        byte[] content = workbookWithExpandedEntries(1, 11 * 1024 * 1024);
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        ApiException error = assertThrows(ApiException.class, () -> policy.validateUpload(file));

        assertEquals("FILE_EXPANDED_TOO_LARGE", error.code());
    }

    @Test
    void rejectsZipSignaturePrefixWithInvalidArchiveBody() {
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 3, 4, 'n', 'o', 't', '-', 'z', 'i', 'p'});

        ApiException error = assertThrows(ApiException.class, () -> policy.validateUpload(file));

        assertEquals("FILE_CONTENT_INVALID", error.code());
    }

    private byte[] workbookWithExtraEntries(int count) throws Exception {
        byte[] base;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("accounts").createRow(0).createCell(0).setCellValue("学号/工号");
            workbook.write(output);
            base = output.toByteArray();
        }
        try (var input = new ZipInputStream(new ByteArrayInputStream(base));
             var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                byte[] bytes = input.readAllBytes();
                if (entry.getName().equals("[Content_Types].xml")) {
                    String types = new String(bytes, StandardCharsets.UTF_8);
                    types = types.replace("</Types>", "<Default Extension=\"txt\" ContentType=\"text/plain\"/></Types>");
                    bytes = types.getBytes(StandardCharsets.UTF_8);
                }
                zip.write(bytes);
                zip.closeEntry();
            }
            for (int index = 0; index < count; index++) {
                zip.putNextEntry(new ZipEntry("extra/entry-" + index + ".txt"));
                zip.write(1);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private byte[] workbookWithExpandedEntries(int count, int bytesPerEntry) throws Exception {
        byte[] base;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("accounts").createRow(0).createCell(0).setCellValue("学号/工号");
            workbook.write(output);
            base = output.toByteArray();
        }
        byte[] randomBytes = new byte[3 * 1024];
        new Random(27).nextBytes(randomBytes);
        byte[] zeroBytes = new byte[90 * 1024 - randomBytes.length];
        try (var input = new ZipInputStream(new ByteArrayInputStream(base));
             var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                byte[] bytes = input.readAllBytes();
                if (entry.getName().equals("[Content_Types].xml")) {
                    String types = new String(bytes, StandardCharsets.UTF_8);
                    types = types.replace("</Types>", "<Default Extension=\"bin\" ContentType=\"application/octet-stream\"/></Types>");
                    bytes = types.getBytes(StandardCharsets.UTF_8);
                }
                zip.write(bytes);
                zip.closeEntry();
            }
            for (int index = 0; index < count; index++) {
                zip.putNextEntry(new ZipEntry("extra/entry-" + index + ".bin"));
                int written = 0;
                while (written < bytesPerEntry) {
                    int randomLength = Math.min(randomBytes.length, bytesPerEntry - written);
                    zip.write(randomBytes, 0, randomLength);
                    written += randomLength;
                    int zeroLength = Math.min(zeroBytes.length, bytesPerEntry - written);
                    if (zeroLength > 0) {
                        zip.write(zeroBytes, 0, zeroLength);
                        written += zeroLength;
                    }
                }
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    @Test
    void rejectsOversizedUploadBeforeParsing() {
        var file = new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[(int) SpreadsheetUploadPolicy.MAX_UPLOAD_BYTES + 1]);

        ApiException error = assertThrows(ApiException.class, () -> policy.validate(file));

        assertEquals("FILE_TOO_LARGE", error.code());
    }

    @Test
    void rejectsLegacyExcelExtension() {
        var file = new MockMultipartFile("file", "accounts.xls",
                "application/vnd.ms-excel", new byte[]{'P', 'K', 3, 4});

        ApiException error = assertThrows(ApiException.class, () -> policy.validate(file));

        assertEquals("FILE_TYPE_INVALID", error.code());
    }
}
