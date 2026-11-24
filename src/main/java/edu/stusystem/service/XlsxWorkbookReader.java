package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class XlsxWorkbookReader {
    private static final int MAX_COLUMNS = 64;
    private final SpreadsheetUploadPolicy policy;

    public XlsxWorkbookReader(SpreadsheetUploadPolicy policy) {
        this.policy = policy;
    }

    public List<WorkbookRow> read(MultipartFile file) {
        policy.validateUpload(file);
        try (var input = file.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() != 1) {
                throw invalid("FILE_SHEET_COUNT_INVALID", "文件必须且只能包含一个工作表");
            }
            var sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > SpreadsheetUploadPolicy.MAX_DATA_ROWS) {
                throw invalid("FILE_TOO_MANY_ROWS", "工作簿数据行超过 10000 行限制");
            }
            var result = new ArrayList<WorkbookRow>();
            for (int index = sheet.getFirstRowNum(); index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null) continue;
                short lastCell = row.getLastCellNum();
                if (lastCell < 0) continue;
                if (lastCell > MAX_COLUMNS) throw invalid("FILE_TOO_MANY_COLUMNS", "工作表列数超过限制");
                var cells = new ArrayList<WorkbookCell>(lastCell);
                boolean nonBlank = false;
                for (int column = 0; column < lastCell; column++) {
                    Cell cell = row.getCell(column, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    WorkbookCell value = cell == null ? new WorkbookCell(CellType.BLANK, "") : readCell(cell);
                    cells.add(value);
                    nonBlank |= !value.text().isBlank();
                }
                if (nonBlank) result.add(new WorkbookRow(index + 1, List.copyOf(cells)));
            }
            if (result.size() > SpreadsheetUploadPolicy.MAX_DATA_ROWS + 1) {
                throw invalid("FILE_TOO_MANY_ROWS", "工作簿数据行超过 10000 行限制");
            }
            return List.copyOf(result);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("FILE_CONTENT_INVALID", "无法读取 XLSX 工作簿");
        }
    }

    private WorkbookCell readCell(Cell cell) {
        if (cell.getCellType() == CellType.FORMULA) {
            throw invalid("FILE_FORMULA_NOT_ALLOWED", "工作簿不允许包含公式");
        }
        return switch (cell.getCellType()) {
            case STRING -> new WorkbookCell(CellType.STRING, cell.getStringCellValue());
            case NUMERIC -> new WorkbookCell(CellType.NUMERIC,
                    BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString());
            case BOOLEAN -> new WorkbookCell(CellType.BOOLEAN, Boolean.toString(cell.getBooleanCellValue()));
            case BLANK, _NONE -> new WorkbookCell(CellType.BLANK, "");
            case ERROR -> throw invalid("FILE_CELL_INVALID", "工作簿包含错误单元格");
            case FORMULA -> throw invalid("FILE_FORMULA_NOT_ALLOWED", "工作簿不允许包含公式");
        };
    }

    private ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record WorkbookCell(CellType type, String text) {}
    public record WorkbookRow(int sourceRow, List<WorkbookCell> cells) {}
}
