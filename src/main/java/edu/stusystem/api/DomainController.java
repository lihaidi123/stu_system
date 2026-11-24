package edu.stusystem.api;

import edu.stusystem.security.CurrentUser;
import edu.stusystem.service.DomainService;
import edu.stusystem.service.IdentityService;
import edu.stusystem.service.AccountImportService;
import edu.stusystem.service.XlsxWorkbookWriter;
import edu.stusystem.service.GradeImportService;
import edu.stusystem.service.AvatarService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DomainController {
    private final DomainService domain;
    private final IdentityService identity;
    private final AccountImportService accountImports;
    private final XlsxWorkbookWriter workbooks;
    private final GradeImportService gradeImports;
    private final AvatarService avatars;
    public DomainController(DomainService domain, IdentityService identity, AccountImportService accountImports, XlsxWorkbookWriter workbooks, GradeImportService gradeImports,AvatarService avatars) { this.domain = domain; this.identity = identity; this.accountImports = accountImports; this.workbooks = workbooks; this.gradeImports=gradeImports;this.avatars=avatars; }
    @GetMapping("/admin/{kind}") @PreAuthorize("hasRole('ADMIN')") Object list(@PathVariable String kind) { return Map.of("success",true,"data",domain.list(kind)); }
    @GetMapping("/admin/students") @PreAuthorize("hasRole('ADMIN')") Object adminStudents(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long classId){return Map.of("success",true,"data",domain.studentAdminList(page,pageSize,keyword,classId));}
    @PostMapping("/admin/{kind}") @PreAuthorize("hasRole('ADMIN')") Object create(@AuthenticationPrincipal CurrentUser user,@PathVariable String kind,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",domain.create(user,kind,body)); }
    @GetMapping("/admin/audit-events") @PreAuthorize("hasRole('ADMIN')") Object auditEvents(@RequestParam(defaultValue="100") int limit) { return Map.of("success",true,"data",domain.auditEvents(limit)); }
    @GetMapping("/admin/classes/statistics") @PreAuthorize("hasRole('ADMIN')") Object classStatistics(@RequestParam(required=false) Long departmentId,@RequestParam(required=false) Integer entryYear) { return Map.of("success",true,"data",domain.classStatistics(departmentId,entryYear)); }
    @PostMapping("/admin/derived-data/rebuild") @PreAuthorize("hasRole('ADMIN')") Object rebuildDerivedData(@AuthenticationPrincipal CurrentUser user) { return Map.of("success",true,"data",domain.rebuildDerivedData(user)); }
    @PostMapping("/admin/students/batch") @PreAuthorize("hasRole('ADMIN')") Object addStudentsBatch(@AuthenticationPrincipal CurrentUser user,@RequestBody List<Map<String,Object>> items) { return Map.of("success",true,"data",domain.addStudentsBatch(user,items)); }
    @PatchMapping("/admin/students/{id}") @PreAuthorize("hasRole('ADMIN')") Object updateStudent(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",domain.updateStudent(user,id,body)); }
    @DeleteMapping("/admin/students/{id}") @PreAuthorize("hasRole('ADMIN')") Object deleteStudent(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return Map.of("success",true,"data",domain.deleteStudent(user,id)); }
    @PutMapping("/admin/students/{id}/account") @PreAuthorize("hasRole('ADMIN')") Object bindStudentAccount(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",domain.bindStudentAccount(user,id,body)); }
    @GetMapping("/admin/accounts") @PreAuthorize("hasRole('ADMIN')") Object accounts() { return Map.of("success",true,"data",domain.accounts()); }
    @GetMapping("/admin/accounts/import-template") @PreAuthorize("hasRole('ADMIN')") ResponseEntity<byte[]> accountImportTemplate() {
        var headers=List.of("学号/工号", "用户名", "姓名", "邮箱", "手机号", "角色", "状态");
        return xlsx("accounts-import-template.xlsx",workbooks.write("用户导入",headers,List.of()));
    }
    @PostMapping(value = "/admin/accounts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')") Object importAccounts(@AuthenticationPrincipal CurrentUser user,
            @RequestPart("file") MultipartFile file, @RequestParam("temporaryPassword") String temporaryPassword) {
        return Map.of("success", true, "data", accountImports.importAccounts(user, file, temporaryPassword));
    }
    @GetMapping("/admin/accounts/export") @PreAuthorize("hasRole('ADMIN')") ResponseEntity<byte[]> exportAccounts(
            @AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String role,@RequestParam(required=false) String status) {
        var headers=List.of("学号/工号", "用户名", "姓名", "邮箱", "手机号", "角色", "状态");
        var rows=domain.accountExportRows(user,keyword,role,status).stream().map(row->List.of(
                text(row.get("business_no")),text(row.get("username")),text(row.get("real_name")),
                text(row.get("email")),text(row.get("phone")),text(row.get("role")),text(row.get("status")))).toList();
        return xlsx("accounts.xlsx",workbooks.write("用户",headers,rows));
    }
    @PutMapping("/admin/accounts/{id}/role") @PreAuthorize("hasRole('ADMIN')") Object changeAccountRole(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",identity.changeRole(user,id,stringField(body,"role"))); }
    @PutMapping("/admin/accounts/{id}/status") @PreAuthorize("hasRole('ADMIN')") Object changeAccountStatus(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",identity.changeStatus(user,id,stringField(body,"status"))); }
    @PutMapping("/admin/accounts/{id}/password") @PreAuthorize("hasRole('ADMIN')") Object resetAccountPassword(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",identity.resetPassword(user,id,stringField(body,"newPassword"))); }
    @PutMapping("/admin/accounts/{id}/{scopeType}/{targetId}") @PreAuthorize("hasRole('ADMIN')") Object assign(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@PathVariable String scopeType,@PathVariable long targetId) { domain.assign(user,id,scopeType,targetId); return Map.of("success",true,"data",Map.of("assigned",true)); }
    @GetMapping("/scope/options") Object options(@AuthenticationPrincipal CurrentUser user) { return Map.of("success",true,"data",domain.options(user)); }
    @GetMapping("/me/profile") Object myProfile(@AuthenticationPrincipal CurrentUser user){return Map.of("success",true,"data",identity.profile(user));}
    @PatchMapping("/me/username") Object changeMyUsername(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",identity.changeUsername(user,stringField(body,"username")));}
    @PatchMapping("/me/contact") Object changeMyContact(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",identity.changeContact(user,body));}
    @GetMapping("/me/availability") Object checkMyAvailability(@AuthenticationPrincipal CurrentUser user,@RequestParam String field,@RequestParam String value){return Map.of("success",true,"data",identity.availability(user,field,value));}
    @PostMapping("/me/password-verifications") Object verifyMyPassword(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",identity.verifySensitivePassword(user,stringField(body,"password")));}
    @PutMapping("/me/password") Object changeMyPassword(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",identity.changePassword(user,stringField(body,"oldPassword"),stringField(body,"newPassword")));}
    @PostMapping(value="/me/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) Object replaceMyAvatar(@AuthenticationPrincipal CurrentUser user,@RequestPart("file") MultipartFile file){return Map.of("success",true,"data",avatars.replace(user,file));}
    @DeleteMapping("/me/avatar") Object removeMyAvatar(@AuthenticationPrincipal CurrentUser user){return Map.of("success",true,"data",avatars.remove(user));}
    @GetMapping("/me/avatar") ResponseEntity<byte[]> myAvatar(@AuthenticationPrincipal CurrentUser user){
        var image=avatars.download(user);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(image.bytes());
    }
    @GetMapping("/students") Object students(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long classId) { return Map.of("success",true,"data",domain.students(user,page,pageSize,keyword,classId)); }
    @GetMapping("/grades") Object grades(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) Long studentId,@RequestParam(required=false) Long termId,@RequestParam(required=false) Long classId,@RequestParam(required=false) Long courseId,@RequestParam(required=false) java.math.BigDecimal scoreMin,@RequestParam(required=false) java.math.BigDecimal scoreMax,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return Map.of("success",true,"data",domain.gradePage(user,studentId,termId,classId,courseId,scoreMin,scoreMax,keyword,page,pageSize)); }
    @GetMapping("/grades/import-template") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") ResponseEntity<byte[]> gradeImportTemplate(){
        var headers=List.of("学号","课程名称","学分","考核性质","学期编码","成绩");
        return xlsx("grades-import-template.xlsx",workbooks.write("成绩导入",headers,List.of()));
    }
    @PostMapping(value="/grades/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") Object importGrades(@AuthenticationPrincipal CurrentUser user,@RequestPart("file") MultipartFile file){
        return Map.of("success",true,"data",gradeImports.importGrades(user,file));
    }
    @GetMapping("/grades/export") ResponseEntity<byte[]> exportGrades(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required=false) Long studentId,@RequestParam(required=false) Long termId,
            @RequestParam(required=false) Long classId,@RequestParam(required=false) Long courseId,
            @RequestParam(required=false) java.math.BigDecimal scoreMin,@RequestParam(required=false) java.math.BigDecimal scoreMax,
            @RequestParam(required=false) String keyword){
        var headers=List.of("学号","姓名","课程名称","学期","学分","考核性质","成绩","GPA");
        var rows=domain.gradeExportRows(user,studentId,termId,classId,courseId,scoreMin,scoreMax,keyword).stream().map(row->List.of(
                text(row.get("student_no")),text(row.get("full_name")),text(row.get("course_name")),text(row.get("term_name")),
                text(row.get("credits_snapshot")),"EXAM".equals(row.get("assessment_type"))?"考试":"考查",
                text(row.get("score_value")),text(row.get("grade_point")))).toList();
        return xlsx("grades.xlsx",workbooks.write("成绩",headers,rows));
    }
    @PostMapping("/grades") Object addGrade(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",domain.addGrade(user,body)); }
    @PostMapping("/grades/batch") Object addGradesBatch(@AuthenticationPrincipal CurrentUser user,@RequestBody List<Map<String,Object>> items) { return Map.of("success",true,"data",domain.addGradesBatch(user,items)); }
    @PatchMapping("/grades/{id}") Object updateGrade(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestBody Map<String,Object> body) { return Map.of("success",true,"data",domain.updateGrade(user,id,body)); }
    @DeleteMapping("/grades/{id}") Object deleteGrade(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@RequestParam long version) { return Map.of("success",true,"data",domain.deleteGrade(user,id,version)); }
    @GetMapping("/classes/{classId}/rankings") Object rankings(@AuthenticationPrincipal CurrentUser user,@PathVariable long classId,@RequestParam long termId) { return Map.of("success",true,"data",domain.rankings(user,classId,termId)); }
    @GetMapping("/classes/{classId}/rankings/all-terms") Object allTermRankings(@AuthenticationPrincipal CurrentUser user,@PathVariable long classId) { return Map.of("success",true,"data",domain.allTermRankings(user,classId)); }
    @GetMapping("/me/grades") Object myGrades(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) Long termId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { if(!"STUDENT".equals(user.role())) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"ROLE_REQUIRED","仅学生可访问");return Map.of("success",true,"data",domain.gradePageForStudent(user,termId,page,pageSize)); }
    @GetMapping("/me/grade-summary") Object mySummary(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) Long termId,@RequestParam(required=false) Long courseId) { return Map.of("success",true,"data",domain.studentGradeDetail(user,user.studentId(),termId,courseId)); }
    @GetMapping("/students/{id}/grade-summary") Object summary(@AuthenticationPrincipal CurrentUser user,@PathVariable Long id,@RequestParam(required=false) Long termId,@RequestParam(required=false) Long courseId) { return Map.of("success",true,"data",domain.studentGradeDetail(user,id,termId,courseId)); }
    @GetMapping("/analytics/overview") Object analytics(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) Long termId,@RequestParam(required=false) Long classId,@RequestParam(required=false) Long courseId) { return Map.of("success",true,"data",domain.analyticsOverview(user,termId,classId,courseId)); }

    private static String stringField(Map<String,Object> body,String field) {
        Object value=body.get(field);
        if(!(value instanceof String text)||text.isBlank())throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"FIELD_REQUIRED",field+"不能为空");
        return text;
    }
    private static String text(Object value){return value==null?"":value.toString();}
    private static ResponseEntity<byte[]> xlsx(String filename,byte[] bytes){
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+filename).body(bytes);
    }
}
