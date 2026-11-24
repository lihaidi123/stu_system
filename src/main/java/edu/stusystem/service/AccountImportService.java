package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AccountImportService {
    private static final List<String> HEADERS = List.of("学号/工号", "用户名", "姓名", "邮箱", "手机号", "角色", "状态");
    private static final Set<String> ROLES = Set.of("ADMIN", "TEACHER", "COUNSELOR", "STUDENT");
    private final JdbcTemplate db;
    private final XlsxWorkbookReader reader;
    private final DomainService domain;

    public AccountImportService(JdbcTemplate db, XlsxWorkbookReader reader, DomainService domain) {
        this.db = db;
        this.reader = reader;
        this.domain = domain;
    }

    @Audited(action = "ACCOUNT_IMPORT", targetType = "account_import")
    public Map<String, Object> importAccounts(CurrentUser actor, MultipartFile file, String temporaryPassword) {
        if (actor == null || !"ADMIN".equals(actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_REQUIRED", "仅管理员可导入账户");
        }
        PasswordPolicy.validate(temporaryPassword, null);
        List<XlsxWorkbookReader.WorkbookRow> rows = reader.read(file);
        validateHeader(rows);
        var reports = new ArrayList<Map<String, Object>>();
        var seenIdentifiers = new HashSet<String>();
        int created = 0, skipped = 0, failed = 0;
        for (int i = 1; i < rows.size(); i++) {
            var row = rows.get(i);
            ParsedAccount parsed;
            try {
                parsed = parse(row, temporaryPassword);
            } catch (ApiException error) {
                reports.add(report(row.sourceRow(), error.code(), safeMessage(error.code())));
                failed++;
                continue;
            }
            String businessNo = parsed.businessNo().toLowerCase(Locale.ROOT);
            String username = parsed.username();
            if (businessNo.equals(username) || seenIdentifiers.contains(businessNo) || seenIdentifiers.contains(username)) {
                reports.add(report(row.sourceRow(), "DUPLICATE_SKIPPED", "账户标识重复，已跳过"));
                skipped++;
                continue;
            }
            seenIdentifiers.add(businessNo);
            seenIdentifiers.add(username);
            if (identityExists(parsed.businessNo(), username)) {
                reports.add(report(row.sourceRow(), "DUPLICATE_SKIPPED", "账户标识已存在，已跳过"));
                skipped++;
                continue;
            }
            if ("STUDENT".equals(parsed.role())) {
                List<Map<String, Object>> profiles = db.queryForList("SELECT id,account_id FROM student_profile WHERE student_no=?", parsed.businessNo());
                if (profiles.isEmpty()) {
                    reports.add(report(row.sourceRow(), "STUDENT_PROFILE_NOT_FOUND", "未找到对应学生档案"));
                    failed++;
                    continue;
                }
                if (profiles.getFirst().get("account_id") != null) {
                    reports.add(report(row.sourceRow(), "STUDENT_ALREADY_BOUND", "学生档案已绑定账户"));
                    failed++;
                    continue;
                }
                parsed = parsed.withStudentId(((Number) profiles.getFirst().get("id")).longValue());
            }
            try {
                domain.create(actor, "accounts", parsed.toBody(temporaryPassword));
                reports.add(report(row.sourceRow(), "CREATED", "账户已创建"));
                created++;
            } catch (DuplicateKeyException conflict) {
                reports.add(report(row.sourceRow(), "IDENTITY_CONFLICT", "账户标识发生并发冲突"));
                failed++;
            } catch (ApiException error) {
                String code = "STUDENT_ALREADY_BOUND".equals(error.code()) ? "STUDENT_ALREADY_BOUND" : "ACCOUNT_ROW_REJECTED";
                reports.add(report(row.sourceRow(), code, safeMessage(code)));
                failed++;
            }
        }
        return Map.of("total", rows.size() - 1, "created", created, "skipped", skipped, "failed", failed, "rows", List.copyOf(reports));
    }

    private ParsedAccount parse(XlsxWorkbookReader.WorkbookRow row, String password) {
        if (row.cells().size() != HEADERS.size()) throw invalid("ACCOUNT_ROW_INVALID");
        var values = new ArrayList<String>(HEADERS.size());
        for (var cell : row.cells()) {
            if (cell.type() != org.apache.poi.ss.usermodel.CellType.STRING && cell.type() != org.apache.poi.ss.usermodel.CellType.BLANK) {
                throw invalid("ACCOUNT_ROW_INVALID");
            }
            values.add(cell.text().trim());
        }
        String businessNo = required(values.get(0));
        String username = required(values.get(1)).toLowerCase(Locale.ROOT);
        String realName = required(values.get(2));
        String email = blankToNull(values.get(3));
        String phone = blankToNull(values.get(4));
        String role = required(values.get(5));
        String status = switch (required(values.get(6))) {
            case "启用" -> "ACTIVE";
            case "禁用" -> "DISABLED";
            default -> throw invalid("ACCOUNT_ROW_INVALID");
        };
        if (businessNo.length() > 32 || username.length() < 3 || username.length() > 30 || realName.length() > 80 || !ROLES.contains(role)) {
            throw invalid("ACCOUNT_ROW_INVALID");
        }
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw invalid("ACCOUNT_ROW_INVALID");
        }
        if (phone != null && !phone.matches("^1[3-9]\\d{9}$")) throw invalid("ACCOUNT_ROW_INVALID");
        if (businessNo.equalsIgnoreCase(username)) throw invalid("ACCOUNT_ROW_INVALID");
        try {
            PasswordPolicy.validate(password, username);
        } catch (ApiException e) {
            throw invalid("TEMPORARY_PASSWORD_INVALID");
        }
        return new ParsedAccount(businessNo, username, realName, email, phone, role, status, null);
    }

    private boolean identityExists(String businessNo, String username) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE LOWER(business_no) IN (LOWER(?),LOWER(?)) OR LOWER(username) IN (LOWER(?),LOWER(?))", Integer.class,
                businessNo, username, businessNo, username);
        return count != null && count > 0;
    }

    private void validateHeader(List<XlsxWorkbookReader.WorkbookRow> rows) {
        if (rows.isEmpty() || rows.getFirst().sourceRow() != 1 || rows.getFirst().cells().size() != HEADERS.size()) {
            throw invalid("ACCOUNT_TEMPLATE_INVALID");
        }
        for (int i = 0; i < HEADERS.size(); i++) {
            var cell = rows.getFirst().cells().get(i);
            if (cell.type() != org.apache.poi.ss.usermodel.CellType.STRING || !HEADERS.get(i).equals(cell.text())) {
                throw invalid("ACCOUNT_TEMPLATE_INVALID");
            }
        }
    }

    private static String required(String value) {
        if (value.isBlank()) throw invalid("ACCOUNT_ROW_INVALID");
        return value;
    }

    private static String blankToNull(String value) { return value.isBlank() ? null : value; }
    private static ApiException invalid(String code) { return new ApiException(HttpStatus.BAD_REQUEST, code, safeMessage(code)); }
    private static Map<String, Object> report(int sourceRow, String code, String message) {
        var item = new LinkedHashMap<String, Object>();
        item.put("sourceRow", sourceRow);
        item.put("code", code);
        item.put("message", message);
        return Map.copyOf(item);
    }

    private static String safeMessage(String code) {
        return switch (code) {
            case "TEMPORARY_PASSWORD_INVALID" -> "临时密码不符合账户密码规则";
            case "STUDENT_PROFILE_NOT_FOUND" -> "未找到对应学生档案";
            case "STUDENT_ALREADY_BOUND" -> "学生档案已绑定账户";
            case "DUPLICATE_SKIPPED" -> "账户标识重复，已跳过";
            case "IDENTITY_CONFLICT" -> "账户标识发生并发冲突";
            case "ACCOUNT_TEMPLATE_INVALID" -> "模板列名或顺序不正确";
            default -> "该行数据无效";
        };
    }

    private record ParsedAccount(String businessNo, String username, String realName, String email, String phone,
                                 String role, String status, Long studentId) {
        ParsedAccount withStudentId(long id) { return new ParsedAccount(businessNo, username, realName, email, phone, role, status, id); }
        Map<String, Object> toBody(String password) {
            var body = new HashMap<String, Object>();
            body.put("businessNo", businessNo); body.put("username", username); body.put("realName", realName);
            body.put("email", email); body.put("phone", phone); body.put("role", role); body.put("status", status); body.put("password", password);
            if (studentId != null) body.put("studentId", studentId.toString());
            return body;
        }
    }
}
