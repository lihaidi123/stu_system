package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IdentityService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final AtomicRateLimitService rateLimits;
    private final SensitivePasswordVerificationService sensitivePasswords;
    public IdentityService(JdbcTemplate db, PasswordEncoder passwords, AtomicRateLimitService rateLimits) {
        this(db,passwords,rateLimits,new SensitivePasswordVerificationService(db,passwords,rateLimits));
    }
    @Autowired public IdentityService(JdbcTemplate db, PasswordEncoder passwords, AtomicRateLimitService rateLimits,
            SensitivePasswordVerificationService sensitivePasswords) {
        this.db=db;this.passwords=passwords;this.rateLimits=rateLimits;this.sensitivePasswords=sensitivePasswords;
    }

    @Audited(action="AUTH_LOGIN",targetType="account")
    public CurrentUser authenticate(String identifier, String password) {
        String key = identifier.trim().toLowerCase(Locale.ROOT);
        var rows = db.query("SELECT id,username,password_hash,real_name,role,status,auth_version,student_id,failed_login_count,locked_until FROM iam_account WHERE LOWER(username)=? OR LOWER(business_no)=?", this::account, key, key);
        if (rows.size() != 1) {
            String failureKey = AtomicRateLimitService.opaqueKey(key);
            String lockKey = failureKey + ":locked";
            if (rateLimits.isLimited(lockKey)) throw unauthorized();
            rateLimits.recordFailure(failureKey, lockKey, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
            throw unauthorized();
        }
        CurrentUser user = rows.getFirst();
        String failureKey = "auth:login:account:" + user.id() + ":failures";
        String lockKey = "auth:login:account:" + user.id() + ":locked";
        if (rateLimits.isLimited(lockKey)) throw unauthorized();
        var state = db.queryForMap("SELECT password_hash,status FROM iam_account WHERE id=?", user.id());
        if (!"ACTIVE".equals(state.get("status"))) throw unauthorized();
        if (!passwords.matches(password, (String) state.get("password_hash"))) {
            rateLimits.recordFailure(failureKey, lockKey, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
            throw unauthorized();
        }
        rateLimits.clear(failureKey, lockKey);
        return user;
    }
    private CurrentUser account(ResultSet rs, int row) throws java.sql.SQLException {
        long studentId = rs.getLong("student_id");
        return new CurrentUser(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"), rs.getString("role"), rs.getLong("auth_version"), rs.wasNull() ? null : studentId);
    }
    public CurrentUser currentUser(long id) {
        return db.query("SELECT id,username,password_hash,real_name,role,status,auth_version,student_id,failed_login_count,locked_until FROM iam_account WHERE id=? AND status='ACTIVE'", this::account, id)
                .stream().findFirst().orElseThrow(IdentityService::unauthorized);
    }
    public boolean isCredentialCurrent(long id, long version, String jti) {
        Integer valid = db.queryForObject("SELECT COUNT(*) FROM iam_account a WHERE a.id=? AND a.status='ACTIVE' AND a.auth_version=? AND NOT EXISTS (SELECT 1 FROM token_revocation r WHERE r.jti=?)", Integer.class, id, version, jti);
        return valid != null && valid == 1;
    }
    public CurrentUser currentCredential(long id, long version, String role, String jti) {
        return db.query("SELECT a.id,a.username,a.real_name,a.role,a.auth_version,a.student_id FROM iam_account a " +
                        "WHERE a.id=? AND a.status='ACTIVE' AND a.auth_version=? AND a.role=? " +
                        "AND NOT EXISTS (SELECT 1 FROM token_revocation r WHERE r.jti=?)",
                this::account, id, version, role, jti).stream().findFirst().orElse(null);
    }
    public Map<String,Object> profile(CurrentUser user) {
        List<Map<String,Object>> rows=db.queryForList("SELECT a.id,a.business_no,a.username,a.real_name,a.email,a.phone,a.role,a.status,a.student_id,a.created_at,v.id avatar_id FROM iam_account a LEFT JOIN avatar_asset v ON v.owner_account_id=a.id WHERE a.id=? AND a.status='ACTIVE'",user.id());
        if(rows.isEmpty())throw unauthorized();
        Map<String,Object> row=rows.getFirst();var result=new java.util.LinkedHashMap<String,Object>();
        result.put("id",String.valueOf(row.get("id")));result.put("businessNo",row.get("business_no"));result.put("username",row.get("username"));
        result.put("realName",row.get("real_name"));result.put("email",row.get("email"));result.put("phone",row.get("phone"));result.put("role",row.get("role"));
        result.put("studentId",row.get("student_id")==null?null:String.valueOf(row.get("student_id")));result.put("createdAt",String.valueOf(row.get("created_at")));
        result.put("avatarUrl",row.get("avatar_id")==null?null:"/api/v1/me/avatar");
        return result;
    }
    @Transactional
    @Audited(action="PROFILE_USERNAME_CHANGE",targetType="account")
    public Map<String,Object> changeUsername(CurrentUser actor,String input){
        Map<String,Object> account=lockAccount(actor.id());
        String username=normalizeUsername(input);
        Integer used=db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE id<>? AND (LOWER(username)=? OR LOWER(business_no)=?)",Integer.class,actor.id(),username,username);
        if(used!=null&&used>0)throw new ApiException(HttpStatus.CONFLICT,"USERNAME_TAKEN","用户名已被使用");
        if(username.equalsIgnoreCase((String)account.get("username")))return Map.of("changed",false);
        try{db.update("UPDATE iam_account SET username=?,auth_version=auth_version+1 WHERE id=?",username,actor.id());}
        catch(org.springframework.dao.DuplicateKeyException conflict){throw new ApiException(HttpStatus.CONFLICT,"USERNAME_TAKEN","用户名已被使用");}
        return Map.of("changed",true,"requiresLogin",true);
    }
    @Transactional
    @Audited(action="PROFILE_CONTACT_CHANGE",targetType="account")
    public Map<String,Object> changeContact(CurrentUser actor,Map<String,Object> body){
        Map<String,Object> account=lockAccount(actor.id());
        String email=body.containsKey("email")?normalizeEmail(body.get("email")):nullableString(account.get("email"));
        String phone=body.containsKey("phone")?normalizePhone(body.get("phone")):nullableString(account.get("phone"));
        if(email!=null){Integer count=db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE id<>? AND email=?",Integer.class,actor.id(),email);if(count!=null&&count>0)throw new ApiException(HttpStatus.CONFLICT,"EMAIL_TAKEN","邮箱已被使用");}
        if(phone!=null){Integer count=db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE id<>? AND phone=?",Integer.class,actor.id(),phone);if(count!=null&&count>0)throw new ApiException(HttpStatus.CONFLICT,"PHONE_TAKEN","手机号已被使用");}
        try{db.update("UPDATE iam_account SET email=?,phone=? WHERE id=?",email,phone,actor.id());}
        catch(org.springframework.dao.DuplicateKeyException conflict){throw new ApiException(HttpStatus.CONFLICT,"CONTACT_TAKEN","联系方式已被使用");}
        return Map.of("updated",true);
    }
    @Transactional
    @Audited(action="PROFILE_PASSWORD_CHANGE",targetType="account")
    public Map<String,Object> changePassword(CurrentUser actor,String currentPassword,String newPassword){
        Map<String,Object> account=lockAccount(actor.id());
        sensitivePasswords.verify(actor,currentPassword);
        PasswordPolicy.validate(newPassword,(String)account.get("username"));
        String oldHash=db.queryForObject("SELECT password_hash FROM iam_account WHERE id=?",String.class,actor.id());
        if(passwords.matches(newPassword,oldHash))throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_REUSE","新密码不能与当前密码相同");
        db.update("UPDATE iam_account SET password_hash=?,auth_version=auth_version+1 WHERE id=?",passwords.encode(newPassword),actor.id());
        return Map.of("changed",true,"requiresLogin",true);
    }
    public Map<String,Object> verifySensitivePassword(CurrentUser actor,String currentPassword){return sensitivePasswords.verify(actor,currentPassword);}
    public Map<String,Object> availability(CurrentUser actor,String field,String rawValue){
        String value=switch(field){case "username"->normalizeUsername(rawValue);case "email"->normalizeEmail(rawValue);case "phone"->normalizePhone(rawValue);default->throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FIELD","可用性字段无效");};
        String sql=switch(field){case "username"->"SELECT COUNT(*) FROM iam_account WHERE id<>? AND (LOWER(username)=? OR LOWER(business_no)=?)";case "email"->"SELECT COUNT(*) FROM iam_account WHERE id<>? AND email=?";default->"SELECT COUNT(*) FROM iam_account WHERE id<>? AND phone=?";};
        Integer count=field.equals("username")?db.queryForObject(sql,Integer.class,actor.id(),value,value):db.queryForObject(sql,Integer.class,actor.id(),value);
        return Map.of("field",field,"available",count==null||count==0);
    }
    @Audited(action="AUTH_LOGOUT",targetType="account",targetIdArgument=0)
    public void revoke(long accountId, Claims claims) {
        db.update("INSERT IGNORE INTO token_revocation(jti,account_id,expires_at) VALUES(?,?,?)", claims.getId(), accountId, new java.sql.Timestamp(claims.getExpiration().getTime()));
    }
    @Transactional
    @Audited(action="ADMIN_BOOTSTRAP",targetType="account")
    public void initializeAdmin(String businessNo, String username, String realName, String password) {
        PasswordPolicy.validate(password, username);
        Long count = db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE role='ADMIN'", Long.class);
        if (count != null && count > 0) throw new IllegalStateException("An administrator already exists");
        db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role) VALUES(?,?,?,?,'ADMIN')", businessNo, username.trim(), passwords.encode(password), realName);
    }

    public long singleActiveAdministratorId() {
        List<Long> ids = db.queryForList("SELECT id FROM iam_account WHERE role='ADMIN' AND status='ACTIVE' ORDER BY id", Long.class);
        if (ids.size() != 1) {
            throw new IllegalStateException("Local recovery requires exactly one active administrator; found " + ids.size());
        }
        return ids.getFirst();
    }

    @Transactional
    @Audited(action="ADMIN_PASSWORD_RECOVERY",targetType="account",targetIdArgument=0)
    public String recoverAdminPassword(long accountId, String newPassword) {
        Map<String,Object> account = lockAccount(accountId);
        if (!"ADMIN".equals(account.get("role")) || !"ACTIVE".equals(account.get("status"))) {
            throw new IllegalStateException("Local recovery target is not an active administrator");
        }
        String username = (String) account.get("username");
        PasswordPolicy.validate(newPassword, username);
        String oldHash = db.queryForObject("SELECT password_hash FROM iam_account WHERE id=?", String.class, accountId);
        if (passwords.matches(newPassword, oldHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSE", "新密码不能与当前密码相同");
        }
        db.update("UPDATE iam_account SET password_hash=?,auth_version=auth_version+1 WHERE id=?", passwords.encode(newPassword), accountId);
        return username;
    }

    @Transactional
    @Audited(action="ACCOUNT_ROLE_CHANGE",targetType="account",targetIdArgument=1)
    public Map<String,Object> changeRole(CurrentUser actor, long accountId, String role) {
        requireAdmin(actor);
        if (!List.of("ADMIN", "TEACHER", "COUNSELOR", "STUDENT").contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "账户角色无效");
        }
        Map<String,Object> account = lockAccount(accountId);
        String oldRole = (String) account.get("role");
        if (oldRole.equals(role)) return Map.of("changed", false);
        if ("STUDENT".equals(oldRole) && account.get("student_id") != null) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_ACCOUNT_BOUND", "请先显式解除学生档案绑定");
        }
        if ("STUDENT".equals(role)) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_PROFILE_REQUIRED", "学生角色必须通过学生档案绑定流程设置");
        }
        if ("ADMIN".equals(oldRole)) ensureAnotherActiveAdmin(accountId);
        db.update("DELETE FROM teacher_course_scope WHERE teacher_account_id=?", accountId);
        db.update("DELETE FROM staff_class_scope WHERE staff_account_id=?", accountId);
        db.update("UPDATE iam_account SET role=?,auth_version=auth_version+1 WHERE id=?", role, accountId);
        return Map.of("changed", true, "role", role);
    }

    @Transactional
    @Audited(action="ACCOUNT_STATUS_CHANGE",targetType="account",targetIdArgument=1)
    public Map<String,Object> changeStatus(CurrentUser actor, long accountId, String status) {
        requireAdmin(actor);
        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "账户状态无效");
        }
        Map<String,Object> account = lockAccount(accountId);
        String oldStatus = (String) account.get("status");
        if (oldStatus.equals(status)) return Map.of("changed", false);
        if ("ADMIN".equals(account.get("role")) && "ACTIVE".equals(oldStatus)) ensureAnotherActiveAdmin(accountId);
        db.update("UPDATE iam_account SET status=?,auth_version=auth_version+1 WHERE id=?", status, accountId);
        return Map.of("changed", true, "status", status);
    }

    @Transactional
    @Audited(action="ACCOUNT_PASSWORD_RESET",targetType="account",targetIdArgument=1)
    public Map<String,Object> resetPassword(CurrentUser actor, long accountId, String newPassword) {
        requireAdmin(actor);
        Map<String,Object> account = lockAccount(accountId);
        String username = (String) account.get("username");
        PasswordPolicy.validate(newPassword, username);
        String oldHash = db.queryForObject("SELECT password_hash FROM iam_account WHERE id=?", String.class, accountId);
        if (passwords.matches(newPassword, oldHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSE", "新密码不能与当前密码相同");
        }
        db.update("UPDATE iam_account SET password_hash=?,auth_version=auth_version+1 WHERE id=?", passwords.encode(newPassword), accountId);
        return Map.of("changed", true, "requiresLogin", true);
    }

    private Map<String,Object> lockAccount(long accountId) {
        List<Map<String,Object>> rows = db.query("SELECT id,role,status,username,student_id,email,phone FROM iam_account WHERE id=? FOR UPDATE", (rs,n) -> {
            var row = new java.util.HashMap<String,Object>();
            row.put("id", rs.getLong("id")); row.put("role", rs.getString("role"));
            row.put("status", rs.getString("status")); row.put("username", rs.getString("username"));
            row.put("email",rs.getString("email"));row.put("phone",rs.getString("phone"));
            long studentId = rs.getLong("student_id"); row.put("student_id", rs.wasNull() ? null : studentId);
            return row;
        }, accountId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账户不存在");
        return rows.getFirst();
    }

    private void ensureAnotherActiveAdmin(long accountId) {
        List<Long> admins = db.queryForList("SELECT id FROM iam_account WHERE role='ADMIN' AND status='ACTIVE' ORDER BY id FOR UPDATE", Long.class);
        if (admins.stream().filter(id -> id != accountId).count() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN", "不能停用或降级最后一个可用管理员");
        }
    }

    private static void requireAdmin(CurrentUser actor) {
        if (!"ADMIN".equals(actor.role())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此操作");
    }
    private static ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "登录信息无效或账户暂不可用"); }

    private static String normalizeUsername(String input){
        if(input==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_USERNAME","用户名须为3至30个字符");
        String value=input.trim().toLowerCase(Locale.ROOT);
        if(value.length()<3||value.length()>30)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_USERNAME","用户名须为3至30个字符");
        return value;
    }
    private static String normalizeEmail(Object input){
        if(input==null)return null;
        if(!(input instanceof String text))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EMAIL","邮箱格式无效");
        String value=text.trim().toLowerCase(Locale.ROOT);if(value.isEmpty())return null;
        if(value.length()>254||!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EMAIL","邮箱格式无效");
        return value;
    }
    private static String normalizePhone(Object input){
        if(input==null)return null;
        if(!(input instanceof String text))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHONE","手机号格式无效");
        String value=text.trim();if(value.isEmpty())return null;
        if(!value.matches("^1[3-9]\\d{9}$"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHONE","手机号格式无效");
        return value;
    }
    private static String nullableString(Object value){return value==null?null:value.toString();}
}
