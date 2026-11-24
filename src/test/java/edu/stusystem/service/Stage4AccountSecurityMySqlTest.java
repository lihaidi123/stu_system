package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import edu.stusystem.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".*stu_stage4_test.*")
class Stage4AccountSecurityMySqlTest {
    private static final String URL = System.getenv("MYSQL_TEST_URL");
    private static final String FIXTURE_GUARD = "jdbc:mysql://[^/]+/stu_stage4_test(?:_[A-Za-z0-9]+)*(?:\\?.*)?";
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
    private static final String REDIS_PORT = System.getenv().getOrDefault("REDIS_TEST_PORT", "6379");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches(FIXTURE_GUARD)) {
            throw new IllegalStateException("MYSQL_TEST_URL must target a dedicated stu_stage4_test database");
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> System.getenv("MYSQL_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("MYSQL_TEST_PASSWORD"));
        registry.add("security.jwt.secret", () -> "stage-4-integration-secret-at-least-32-bytes");
        registry.add("spring.data.redis.host", () -> REDIS_HOST);
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    @Autowired JdbcTemplate db;
    @Autowired IdentityService identity;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired AtomicRateLimitService rateLimits;
    @Autowired StringRedisTemplate redis;
    @Autowired AccountImportService accountImports;
    @Autowired GradeImportService gradeImports;
    @Autowired AvatarService avatars;
    @Autowired AvatarMetadataService avatarMetadata;
    @Autowired AvatarStorageProperties avatarProperties;

    private long adminId;
    private long teacherId;
    private long studentId;
    private long studentAccountId;
    private long classId;
    private long courseId;
    private long termId;

    @BeforeEach
    void seedDedicatedSchema() {
        db.update("DELETE FROM avatar_cleanup_job");
        db.update("DELETE FROM avatar_asset");
        db.update("UPDATE student_profile SET account_id=NULL");
        db.update("UPDATE iam_account SET student_id=NULL");
        db.update("DELETE FROM token_revocation");
        db.update("DELETE FROM grade_record");
        db.update("DELETE FROM student_term_summary");
        db.update("DELETE FROM class_term_ranking");
        db.update("DELETE FROM student_profile");
        db.update("DELETE FROM staff_class_scope");
        db.update("DELETE FROM teacher_course_scope");
        db.update("DELETE FROM audit_event");
        db.update("DELETE FROM iam_account");
        db.update("DELETE FROM course_catalog");
        db.update("DELETE FROM academic_term");
        db.update("DELETE FROM academic_class");
        db.update("DELETE FROM academic_department");

        adminId = insertAccount("stage4-admin", "admin", "ADMIN", "AdminPass!234");
        insertAccount("stage4-admin-two", "admin-two", "ADMIN", "OtherAdmin!234");
        teacherId = insertAccount("stage4-teacher", "teacher", "TEACHER", "TeacherPass!234");
        db.update("INSERT INTO academic_department(name) VALUES('Stage 4 Test Department')");
        long departmentId = db.queryForObject("SELECT id FROM academic_department WHERE name='Stage 4 Test Department'", Long.class);
        db.update("INSERT INTO academic_class(name,entry_year,department_id) VALUES('Stage 4 Test Class',2099,?)", departmentId);
        classId = db.queryForObject("SELECT id FROM academic_class WHERE name='Stage 4 Test Class'", Long.class);
        db.update("INSERT INTO course_catalog(name,default_credits,department_id) VALUES('Stage 4 Test Course',3,?)", departmentId);
        courseId = db.queryForObject("SELECT id FROM course_catalog WHERE name='Stage 4 Test Course'", Long.class);
        db.update("INSERT INTO academic_term(code,name,academic_year,term_order) VALUES('stage4-2099-fall','Stage 4 Fall','2099-2100',1)");
        termId=db.queryForObject("SELECT id FROM academic_term WHERE code='stage4-2099-fall'",Long.class);
        db.update("INSERT INTO student_profile(student_no,full_name,department_id,class_id,major) VALUES('stage4-student','Stage 4 Student',?,?, 'Testing')", departmentId, classId);
        studentId = db.queryForObject("SELECT id FROM student_profile WHERE student_no='stage4-student'", Long.class);
        studentAccountId = insertLinkedStudentAccount(studentId);
        db.update("INSERT INTO teacher_course_scope(teacher_account_id,course_id) VALUES(?,?)", teacherId, courseId);
        db.update("INSERT INTO staff_class_scope(staff_account_id,class_id) VALUES(?,?)", teacherId, classId);
    }

    @Test
    void roleChangeRemovesGrantsAndInvalidatesOldToken() throws Exception {
        String token = tokenFor(teacherId);

        mvc.perform(put("/api/v1/admin/accounts/{id}/role", teacherId)
                        .header("Authorization", "Bearer " + tokenFor(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"COUNSELOR\"}"))
                .andExpect(status().isOk());

        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM teacher_course_scope WHERE teacher_account_id=?", Integer.class, teacherId));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM staff_class_scope WHERE staff_account_id=?", Integer.class, teacherId));
        assertEquals(1L, db.queryForObject("SELECT auth_version FROM iam_account WHERE id=?", Long.class, teacherId));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void accountImportCreatesSafeRowAndRepeatedImportDoesNotReplacePassword() throws Exception {
        var file = accountWorkbook(List.of(List.of("stage4-import", "import-teacher", "Imported Teacher",
                "TEACHER@EXAMPLE.ORG", "13800138000", "TEACHER", "启用")));
        Map<String, Object> first = accountImports.importAccounts(admin(), file, "ImportPass!234");
        long importedId = db.queryForObject("SELECT id FROM iam_account WHERE business_no='stage4-import'", Long.class);
        String originalHash = db.queryForObject("SELECT password_hash FROM iam_account WHERE id=?", String.class, importedId);

        assertEquals(1, first.get("created"));
        assertEquals("teacher@example.org", db.queryForObject("SELECT email FROM iam_account WHERE id=?", String.class, importedId));
        assertEquals("13800138000", db.queryForObject("SELECT phone FROM iam_account WHERE id=?", String.class, importedId));
        Map<String, Object> repeated = accountImports.importAccounts(admin(), file, "OtherPass!345");

        assertEquals(1, repeated.get("skipped"));
        assertEquals(originalHash, db.queryForObject("SELECT password_hash FROM iam_account WHERE id=?", String.class, importedId));
        assertTrue(!first.toString().contains("ImportPass!234"));
    }

    @Test
    void concurrentDuplicateAccountImportsCreateOnlyOneIdentity() throws Exception {
        var file=accountWorkbook(List.of(List.of("stage4-race-no","race-user","Race User","","","TEACHER","启用")));
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->importAtBarrier(file,"RacePass!123",ready,start));
            var second=pool.submit(()->importAtBarrier(file,"RacePass!456",ready,start));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            var a=first.get(20,TimeUnit.SECONDS);var b=second.get(20,TimeUnit.SECONDS);
            assertEquals(1,((Number)a.get("created")).intValue()+((Number)b.get("created")).intValue());
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE business_no='stage4-race-no' OR username='race-user'",Integer.class));
        }
    }

    private Map<String,Object> importAtBarrier(MockMultipartFile file,String temporaryPassword,CountDownLatch ready,CountDownLatch start)throws Exception{
        ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("race start timed out");
        return accountImports.importAccounts(admin(),file,temporaryPassword);
    }

    @Test
    void accountImportRouteIsAdministratorOnly() throws Exception {
        mvc.perform(multipart("/api/v1/admin/accounts/import")
                        .file(accountWorkbook(List.of()))
                        .param("temporaryPassword", "ImportPass!234")
                        .header("Authorization", "Bearer " + tokenFor(teacherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountTemplateAndFilteredExportAreXlsxAndExcludeCredentialColumns() throws Exception {
        byte[] template=mvc.perform(get("/api/v1/admin/accounts/import-template").header("Authorization","Bearer "+tokenFor(adminId)))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try(var workbook=WorkbookFactory.create(new java.io.ByteArrayInputStream(template))){
            var header=workbook.getSheetAt(0).getRow(0);
            assertEquals("学号/工号",header.getCell(0).getStringCellValue());
            assertEquals("状态",header.getCell(6).getStringCellValue());
        }
        byte[] export=mvc.perform(get("/api/v1/admin/accounts/export").param("role","TEACHER").header("Authorization","Bearer "+tokenFor(adminId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var workbook=WorkbookFactory.create(new java.io.ByteArrayInputStream(export))){
            var sheet=workbook.getSheetAt(0);
            assertEquals("学号/工号",sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("stage4-teacher",sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(7,sheet.getRow(0).getLastCellNum());
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING,sheet.getRow(1).getCell(0).getCellType());
        }
        mvc.perform(get("/api/v1/admin/accounts/export").header("Authorization","Bearer "+tokenFor(teacherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiListsStageFourFileAndProfileOperations() throws Exception {
        String document=mvc.perform(get("/v3/api-docs").header("Authorization","Bearer "+tokenFor(adminId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(document.contains("/api/v1/admin/accounts/import"));
        assertTrue(document.contains("/api/v1/grades/export"));
        assertTrue(document.contains("/api/v1/me/password-verifications"));
        assertTrue(document.contains("/api/v1/me/avatar"));
    }

    @Test
    void gradeExportUsesScopedFullFilterAndDoesNotRequirePageParameters() throws Exception {
        var gradeRows=new java.util.ArrayList<List<String>>();
        for(int index=0;index<25;index++){
            String studentNo="stage4-export-"+index;
            db.update("INSERT INTO student_profile(student_no,full_name,department_id,class_id,major) VALUES(?,?,(SELECT id FROM academic_department WHERE name='Stage 4 Test Department'),?,'Testing')",studentNo,"Export Student "+index,classId);
            gradeRows.add(List.of(studentNo,"Stage 4 Test Course","3","考试","stage4-2099-fall",Integer.toString(70+index%31)));
        }
        gradeImports.importGrades(identity.currentUser(teacherId),gradeWorkbook(gradeRows));

        byte[] export=mvc.perform(get("/api/v1/grades/export").param("termId",Long.toString(termId))
                        .header("Authorization","Bearer "+tokenFor(teacherId)))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try(var workbook=WorkbookFactory.create(new java.io.ByteArrayInputStream(export))){
            var sheet=workbook.getSheetAt(0);assertEquals(25,sheet.getLastRowNum());
            assertEquals("stage4-export-0",sheet.getRow(1).getCell(0).getStringCellValue());
            var exportedNumbers=new java.util.HashSet<String>();
            for(int row=1;row<=sheet.getLastRowNum();row++)exportedNumbers.add(sheet.getRow(row).getCell(0).getStringCellValue());
            assertTrue(exportedNumbers.contains("stage4-export-24"));
        }
        mvc.perform(get("/api/v1/grades/export").header("Authorization","Bearer "+tokenFor(studentAccountId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gradeImportUsesTheTransactionalBatchPathAndRefreshesDerivedRows() throws Exception {
        var file=gradeWorkbook(List.of(List.of("stage4-student","Stage 4 Test Course","3","考试","stage4-2099-fall","95.5")));

        var result=gradeImports.importGrades(identity.currentUser(teacherId),file);

        assertEquals(1,result.get("created"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM grade_record WHERE student_id=? AND course_id=? AND term_id=?",Integer.class,studentId,courseId,termId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM student_term_summary WHERE student_id=? AND term_id=?",Integer.class,studentId,termId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking WHERE student_id=? AND term_id=?",Integer.class,studentId,termId));
    }

    @Test
    void gradeImportScopeFailureRollsBackEveryRow() throws Exception {
        db.update("INSERT INTO course_catalog(name,default_credits,department_id) VALUES('Unauthorized Stage 4 Course',3,(SELECT id FROM academic_department WHERE name='Stage 4 Test Department'))");
        var file=gradeWorkbook(List.of(
                List.of("stage4-student","Stage 4 Test Course","3","考试","stage4-2099-fall","95"),
                List.of("stage4-student","Unauthorized Stage 4 Course","3","考试","stage4-2099-fall","80")
        ));

        ApiException error=assertThrows(ApiException.class,()->gradeImports.importGrades(identity.currentUser(teacherId),file));

        assertEquals("GRADE_IMPORT_REJECTED",error.code());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM grade_record WHERE student_id=?",Integer.class,studentId));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM student_term_summary WHERE student_id=?",Integer.class,studentId));
    }

    @Test
    void profileReturnsNoCredentialFieldsAndContactUpdatesNormalizeAndClear() throws Exception {
        String token=tokenFor(teacherId);
        mvc.perform(get("/api/v1/me/profile").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("teacher"))
                .andExpect(jsonPath("$.data.password_hash").doesNotExist());

        mvc.perform(patch("/api/v1/me/contact").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\" TEACHER@EXAMPLE.ORG \",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());
        assertEquals("teacher@example.org",db.queryForObject("SELECT email FROM iam_account WHERE id=?",String.class,teacherId));
        mvc.perform(get("/api/v1/me/availability").param("field","email").param("value","teacher@example.org").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true));

        mvc.perform(patch("/api/v1/me/contact").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":null,\"phone\":null}"))
                .andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE id=? AND email IS NULL AND phone IS NULL",Integer.class,teacherId));
    }

    @Test
    void profileAuditCapturesOutcomesWithoutPasswordsOrBearerTokens() throws Exception {
        String token = tokenFor(teacherId);
        String rejectedPassword = "WrongPass!234";
        String proposedPassword = "Browser!Next123";
        String failureKey = "auth:sensitive:account:" + teacherId + ":failures";
        String lockKey = "auth:sensitive:account:" + teacherId + ":locked";
        try {
            mvc.perform(put("/api/v1/me/password").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"" + rejectedPassword + "\",\"newPassword\":\"" + proposedPassword + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

            Map<String, Object> failure = db.queryForMap(
                    "SELECT * FROM audit_event WHERE actor_id=? AND action='PROFILE_PASSWORD_CHANGE' ORDER BY id DESC LIMIT 1",
                    teacherId);
            assertEquals("FAILURE", failure.get("outcome"));
            assertEquals("CURRENT_PASSWORD_INVALID", failure.get("error_code"));
            assertFalse(failure.toString().contains(rejectedPassword));
            assertFalse(failure.toString().contains(proposedPassword));
            assertFalse(failure.toString().contains(token));

            mvc.perform(patch("/api/v1/me/contact").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"audit-success@example.org\",\"phone\":null}"))
                    .andExpect(status().isOk());
            Map<String, Object> success = db.queryForMap(
                    "SELECT outcome,error_code FROM audit_event WHERE actor_id=? AND action='PROFILE_CONTACT_CHANGE' ORDER BY id DESC LIMIT 1",
                    teacherId);
            assertEquals("SUCCESS", success.get("outcome"));
            assertEquals(null, success.get("error_code"));
        } finally {
            redis.delete(List.of(failureKey, lockKey));
        }
    }

    @Test
    void usernameAndPasswordChangesInvalidateOldBearerCredentials() throws Exception {
        String oldToken=tokenFor(teacherId);
        mvc.perform(patch("/api/v1/me/username").header("Authorization","Bearer "+oldToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\" Teacher-New \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiresLogin").value(true));
        mvc.perform(get("/api/v1/me/profile").header("Authorization","Bearer "+oldToken)).andExpect(status().isUnauthorized());
        assertEquals("teacher-new",db.queryForObject("SELECT username FROM iam_account WHERE id=?",String.class,teacherId));

        String newToken=tokenFor(teacherId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/me/password")
                        .header("Authorization","Bearer "+newToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"TeacherPass!234\",\"newPassword\":\"Replacement!345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiresLogin").value(true));
        mvc.perform(get("/api/v1/me/profile").header("Authorization","Bearer "+newToken)).andExpect(status().isUnauthorized());
        assertEquals("TEACHER",identity.authenticate("teacher-new","Replacement!345").role());
    }

    @Test
    void sensitivePasswordFailuresLockAccountForFurtherAttempts() throws Exception {
        String token=tokenFor(teacherId);
        String failureKey="auth:sensitive:account:"+teacherId+":failures",lockKey="auth:sensitive:account:"+teacherId+":locked";
        try {
            for(int attempt=0;attempt<3;attempt++)mvc.perform(post("/api/v1/me/password-verifications")
                            .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"WrongPass!234\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
            mvc.perform(post("/api/v1/me/password-verifications").header("Authorization","Bearer "+token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"TeacherPass!234\"}"))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SENSITIVE_VERIFICATION_LOCKED"));
        } finally { redis.delete(List.of(failureKey,lockKey)); }
    }

    @Test
    void avatarUploadReadAndRemovalAreBoundToAuthenticatedOwner() throws Exception {
        byte[] png;
        try(var output=new ByteArrayOutputStream()){
            ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB),"png",output);png=output.toByteArray();
        }
        mvc.perform(multipart("/api/v1/me/avatar").file(new MockMultipartFile("file","face.bin","image/png",png))
                        .header("Authorization","Bearer "+tokenFor(teacherId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/me/avatar"));
        assertEquals(teacherId,db.queryForObject("SELECT owner_account_id FROM avatar_asset",Long.class));

        mvc.perform(get("/api/v1/me/avatar").header("Authorization","Bearer "+tokenFor(teacherId)))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));
        mvc.perform(get("/api/v1/me/avatar").header("Authorization","Bearer "+tokenFor(adminId))).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/me/avatar")
                        .header("Authorization","Bearer "+tokenFor(teacherId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(""));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM avatar_asset WHERE owner_account_id=?",Integer.class,teacherId));
    }

    @Test
    void failedOldAvatarCleanupLeavesPersistentRetryJobAndKeepsReplacementActive() throws Exception {
        byte[] png;
        try(var output=new ByteArrayOutputStream()){
            ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB),"png",output);png=output.toByteArray();
        }
        var upload=new MockMultipartFile("file","face.png","image/png",png);
        Path root=Path.of(avatarProperties.getLocalRoot()).toAbsolutePath().normalize();
        AvatarMetadataService.AvatarAsset old=null;
        Path oldDirectory=null;
        try {
            avatars.replace(identity.currentUser(teacherId),upload);
            old=avatarMetadata.find(teacherId);
            assertTrue(old!=null);
            Path oldFile=root.resolve(old.objectKey());Files.delete(oldFile);
            oldDirectory=Files.createDirectory(oldFile);Files.writeString(oldDirectory.resolve("keep.txt"),"block cleanup");

            avatars.replace(identity.currentUser(teacherId),upload);

            var current=avatarMetadata.find(teacherId);
            assertTrue(current!=null&&!current.objectKey().equals(old.objectKey()));
            assertEquals(1,db.queryForObject("SELECT attempt_count FROM avatar_cleanup_job WHERE storage_provider=? AND object_key=?",Integer.class,old.provider(),old.objectKey()));
        } finally {
            avatars.remove(identity.currentUser(teacherId));
            if(oldDirectory!=null&&Files.exists(oldDirectory))try(var walk=Files.walk(oldDirectory)){walk.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(java.io.IOException ignored){}});}
        }
    }

    @Test
    void statusAndPasswordChangesInvalidatePreviouslyIssuedTokens() throws Exception {
        String token = tokenFor(teacherId);

        identity.changeStatus(admin(), teacherId, "DISABLED");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        identity.changeStatus(admin(), teacherId, "ACTIVE");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokenFor(teacherId))).andExpect(status().isOk());

        String beforeReset = tokenFor(teacherId);
        identity.resetPassword(admin(), teacherId, "ChangedPass!345");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + beforeReset)).andExpect(status().isUnauthorized());
        assertEquals("TEACHER", identity.authenticate("stage4-teacher", "ChangedPass!345").role());
    }

    @Test
    void fifthFailureAcrossUsernameAndBusinessNumberAliasesLocksAccount() {
        for (int i = 0; i < 4; i++) {
            assertThrows(ApiException.class, () -> identity.authenticate("teacher", "WrongPass!234"));
        }
        assertThrows(ApiException.class, () -> identity.authenticate("stage4-teacher", "WrongPass!234"));
        ApiException locked = assertThrows(ApiException.class,
                () -> identity.authenticate("teacher", "TeacherPass!234"));

        assertEquals("INVALID_CREDENTIALS", locked.code());
        assertEquals(0, db.queryForObject("SELECT failed_login_count FROM iam_account WHERE id=?", Integer.class, teacherId));
    }

    @Test
    void boundStudentCannotChangeRoleUntilExplicitlyUnlinked() {
        ApiException error = assertThrows(ApiException.class,
                () -> identity.changeRole(admin(), studentAccountId, "TEACHER"));

        assertEquals("STUDENT_ACCOUNT_BOUND", error.code());
        assertEquals("STUDENT", db.queryForObject("SELECT role FROM iam_account WHERE id=?", String.class, studentAccountId));
        assertEquals(studentId, db.queryForObject("SELECT student_id FROM iam_account WHERE id=?", Long.class, studentAccountId));
    }

    @Test
    void tokenRoleMustMatchCurrentDatabaseRoleEvenWhenVersionMatches() throws Exception {
        CurrentUser teacher = identity.currentUser(teacherId);
        String mismatchedToken = jwt.issue(new CurrentUser(teacher.id(), teacher.username(), teacher.realName(),
                "COUNSELOR", teacher.authVersion(), teacher.studentId()));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + mismatchedToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void lastActiveAdministratorCannotBeDisabledOrDemoted() {
        db.update("UPDATE iam_account SET status='DISABLED' WHERE username='admin-two'");

        ApiException statusError = assertThrows(ApiException.class,
                () -> identity.changeStatus(admin(), adminId, "DISABLED"));
        ApiException roleError = assertThrows(ApiException.class,
                () -> identity.changeRole(admin(), adminId, "TEACHER"));

        assertEquals("LAST_ACTIVE_ADMIN", statusError.code());
        assertEquals("LAST_ACTIVE_ADMIN", roleError.code());
        assertTrue("ACTIVE".equals(db.queryForObject("SELECT status FROM iam_account WHERE id=?", String.class, adminId)));
        assertEquals("ADMIN", db.queryForObject("SELECT role FROM iam_account WHERE id=?", String.class, adminId));
    }

    @Test
    void concurrentAdministratorDemotionsLeaveOneActiveAdministrator() throws Exception {
        long secondAdminId = db.queryForObject("SELECT id FROM iam_account WHERE username='admin-two'", Long.class);
        CurrentUser firstActor = identity.currentUser(adminId);
        CurrentUser secondActor = identity.currentUser(secondAdminId);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> demoteAfter(start, firstActor, adminId));
            var second = pool.submit(() -> demoteAfter(start, secondActor, secondAdminId));
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM iam_account WHERE role='ADMIN' AND status='ACTIVE'", Integer.class));
    }

    @Test
    void rateLimitLockSurvivesApplicationContextRestart() {
        String suffix = UUID.randomUUID().toString();
        String failureKey = "stage4:test:restart:" + suffix + ":failures";
        String lockKey = "stage4:test:restart:" + suffix + ":locked";
        rateLimits.recordFailure(failureKey, lockKey, 1, Duration.ofMinutes(5), Duration.ofMinutes(5));
        ConfigurableApplicationContext restarted = null;
        try {
            Map<String,Object> properties = Map.of(
                    "spring.datasource.url", URL,
                    "spring.datasource.username", System.getenv("MYSQL_TEST_USERNAME"),
                    "spring.datasource.password", System.getenv("MYSQL_TEST_PASSWORD"),
                    "spring.data.redis.host", REDIS_HOST,
                    "spring.data.redis.port", REDIS_PORT,
                    "spring.flyway.enabled", "false",
                    "server.port", "0",
                    "security.jwt.secret", "stage-4-restart-test-secret-at-least-32-bytes",
                    "spring.main.banner-mode", "off");
            SpringApplication application = new SpringApplication(edu.stusystem.StudentSystemApplication.class);
            application.setWebApplicationType(WebApplicationType.SERVLET);
            application.setAdditionalProfiles("test");
            application.setDefaultProperties(properties);
            restarted = application.run("--server.port=0");
            assertTrue(restarted.getBean(AtomicRateLimitService.class).isLimited(lockKey));
        } finally {
            if (restarted != null) restarted.close();
            redis.delete(List.of(failureKey, lockKey));
        }
    }

    @Test
    void revokedBearerCredentialStaysRejectedAfterApplicationProcessRestart() throws Exception {
        String token = tokenFor(adminId);
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        Process first = startApplicationProcess(port);
        try {
            assertEquals(200, processRequest(port, "/api/v1/me", token));
        } finally {
            first.destroyForcibly();
            assertTrue(first.waitFor(10, TimeUnit.SECONDS));
        }

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Process restarted = startApplicationProcess(port);
        try {
            assertEquals(401, processRequest(port, "/api/v1/me", token));
        } finally {
            restarted.destroyForcibly();
            assertTrue(restarted.waitFor(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void rateLimitLockIsVisibleToASeparateJvmProcess() throws Exception {
        String suffix=UUID.randomUUID().toString();String failures="stage4:test:process:"+suffix+":failures";String lock="stage4:test:process:"+suffix+":locked";
        rateLimits.recordFailure(failures,lock,1,Duration.ofMinutes(5),Duration.ofMinutes(5));
        var javaExe=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        Process child=new ProcessBuilder(javaExe,"-cp",System.getProperty("java.class.path"),RedisLockProbe.class.getName(),lock).redirectErrorStream(true).start();
        try {
            assertTrue(child.waitFor(15,TimeUnit.SECONDS));
            assertEquals(0,child.exitValue());
        } finally {
            if(child.isAlive())child.destroyForcibly();
            redis.delete(List.of(failures,lock));
        }
    }

    private boolean demoteAfter(CountDownLatch start, CurrentUser actor, long targetId) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent test did not start");
            identity.changeRole(actor, targetId, "TEACHER");
            return true;
        } catch (RuntimeException failure) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private Process startApplicationProcess(int port) throws Exception {
        String javaExe = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        ProcessBuilder builder = new ProcessBuilder(
                javaExe, "-cp", System.getProperty("java.class.path"),
                edu.stusystem.StudentSystemApplication.class.getName(),
                "--spring.profiles.active=test", "--spring.flyway.enabled=false",
                "--server.address=127.0.0.1", "--server.port=" + port);
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Map<String, String> environment = builder.environment();
        environment.put("SPRING_DATASOURCE_URL", URL);
        environment.put("SPRING_DATASOURCE_USERNAME", System.getenv("MYSQL_TEST_USERNAME"));
        environment.put("SPRING_DATASOURCE_PASSWORD", System.getenv("MYSQL_TEST_PASSWORD"));
        environment.put("SPRING_DATA_REDIS_HOST", REDIS_HOST);
        environment.put("SPRING_DATA_REDIS_PORT", REDIS_PORT);
        environment.put("SECURITY_JWT_SECRET", "stage-4-integration-secret-at-least-32-bytes");
        Process process = builder.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) throw new IllegalStateException("Application process exited during startup");
                try {
                    if (processRequest(port, "/actuator/health", null) == 200) return process;
                } catch (java.io.IOException ignored) {
                    Thread.sleep(200);
                }
            }
            throw new IllegalStateException("Application process did not become healthy");
        } catch (Exception failure) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw failure;
        }
    }

    private int processRequest(int port, String path, String token) throws java.io.IOException {
        var connection = (java.net.HttpURLConnection) new java.net.URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(2000);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private long insertAccount(String businessNo, String username, String role, String password) {
        db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role) VALUES(?,?,?,?,?)",
                businessNo, username, passwords.encode(password), username, role);
        return db.queryForObject("SELECT id FROM iam_account WHERE business_no=?", Long.class, businessNo);
    }

    private MockMultipartFile accountWorkbook(List<List<String>> data) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("accounts");
            var headers = List.of("学号/工号", "用户名", "姓名", "邮箱", "手机号", "角色", "状态");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) header.createCell(column).setCellValue(headers.get(column));
            for (int row = 0; row < data.size(); row++) {
                var target = sheet.createRow(row + 1);
                for (int column = 0; column < headers.size(); column++) target.createCell(column).setCellValue(data.get(row).get(column));
            }
            workbook.write(output);
            return new MockMultipartFile("file", "accounts.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile gradeWorkbook(List<List<String>> data) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=workbook.createSheet("grades");var headers=List.of("学号","课程名称","学分","考核性质","学期编码","成绩");var header=sheet.createRow(0);
            for(int i=0;i<headers.size();i++)header.createCell(i).setCellValue(headers.get(i));
            for(int row=0;row<data.size();row++){var target=sheet.createRow(row+1);for(int column=0;column<headers.size();column++)target.createCell(column).setCellValue(data.get(row).get(column));}
            workbook.write(output);return new MockMultipartFile("file","grades.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }

    private long insertLinkedStudentAccount(long profileId) {
        long accountId = insertAccount("stage4-student", "student", "STUDENT", "StudentPass!234");
        db.update("UPDATE iam_account SET student_id=? WHERE id=?", profileId, accountId);
        db.update("UPDATE student_profile SET account_id=? WHERE id=?", accountId, profileId);
        return accountId;
    }

    private CurrentUser admin() { return identity.currentUser(adminId); }
    private String tokenFor(long accountId) { return jwt.issue(identity.currentUser(accountId)); }
}
