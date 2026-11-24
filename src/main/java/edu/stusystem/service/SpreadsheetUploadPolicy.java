package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class SpreadsheetUploadPolicy {
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 10_000;
    public static final long MAX_ZIP_ENTRIES = 256;
    public static final long MAX_EXPANDED_BYTES = 64L * 1024 * 1024;
    private static final long MAX_ENTRY_EXPANDED_BYTES = 10L * 1024 * 1024;
    private static final int EXPANSION_BUFFER_BYTES = 8 * 1024;
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public SpreadsheetUploadPolicy() {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(10L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(10L * 1024 * 1024);
        ZipSecureFile.setMaxFileCount(MAX_ZIP_ENTRIES);
    }

    public void validate(MultipartFile file) {
        validateUpload(file);
        try (var input=file.getInputStream();var workbook=WorkbookFactory.create(input)) {
            if(workbook.getNumberOfSheets()==0)throw invalid("FILE_CONTENT_INVALID","文件内容不是有效的 XLSX 工作簿");
        } catch(ApiException e){throw e;} catch(IOException|RuntimeException e){throw invalid("FILE_CONTENT_INVALID","文件内容不是有效的 XLSX 工作簿");}
    }

    public void validateUpload(MultipartFile file) {
        validateUploadMetadata(file);
        validateArchiveExpansion(file);
    }

    public void validateUploadMetadata(MultipartFile file){
        if (file == null || file.isEmpty()) {
            throw invalid("FILE_EMPTY", "请选择非空的 XLSX 文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw invalid("FILE_TOO_LARGE", "文件超过 10 MiB 限制");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalid("FILE_TYPE_INVALID", "仅支持 XLSX 文件");
        }
        if (!XLSX_MIME.equalsIgnoreCase(file.getContentType())) {
            throw invalid("FILE_TYPE_INVALID", "文件类型与 XLSX 不匹配");
        }
        try (PushbackInputStream input = new PushbackInputStream(file.getInputStream(), 4)) {
            byte[] signature = input.readNBytes(4);
            if (!hasOoxmlPackageSignature(signature)) {
                throw invalid("FILE_CONTENT_INVALID", "文件内容不是有效的 XLSX 工作簿");
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("FILE_READ_FAILED", "文件无法读取");
        }
    }

    private void validateArchiveExpansion(MultipartFile file) {
        byte[] buffer = new byte[EXPANSION_BUFFER_BYTES];
        long expandedBytes = 0;
        long entryCount = 0;
        try (ZipInputStream archive = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw invalid("FILE_CONTENT_INVALID", "XLSX 文件包含过多 ZIP 条目");
                }
                long entryExpandedBytes = 0;
                int bytesRead;
                while ((bytesRead = archive.read(buffer)) != -1) {
                    if (bytesRead == 0) continue;
                    entryExpandedBytes += bytesRead;
                    expandedBytes += bytesRead;
                    if (entryExpandedBytes > MAX_ENTRY_EXPANDED_BYTES
                            || expandedBytes > MAX_EXPANDED_BYTES) {
                        throw invalid("FILE_EXPANDED_TOO_LARGE", "XLSX 解压数据超过资源限制");
                    }
                }
                archive.closeEntry();
            }
            if (entryCount == 0) {
                throw invalid("FILE_CONTENT_INVALID", "文件内容不是有效的 XLSX 工作簿");
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("FILE_CONTENT_INVALID", "文件内容不是有效的 XLSX 工作簿");
        }
    }

    private boolean hasOoxmlPackageSignature(byte[] signature) {
        return signature.length == 4 && signature[0] == 'P' && signature[1] == 'K'
                && signature[2] == 3 && signature[3] == 4;
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
