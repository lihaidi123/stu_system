package edu.stusystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import edu.stusystem.security.JwtService;
import edu.stusystem.service.IdentityService;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".*stu_stage3_test.*")
class GradeConsistencyMySqlTest {
    private static final String URL = System.getenv("MYSQL_TEST_URL");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        if(URL==null||!URL.matches("jdbc:mysql://[^/]+/stu_stage3_test(?:_[A-Za-z0-9]+)?(?:\\?.*)?"))
            throw new IllegalStateException("MYSQL_TEST_URL must target a dedicated stu_stage3_test database");
        registry.add("spring.datasource.url",()->URL);
        registry.add("spring.datasource.username",()->System.getenv("MYSQL_TEST_USERNAME"));
        registry.add("spring.datasource.password",()->System.getenv("MYSQL_TEST_PASSWORD"));
        registry.add("security.jwt.secret",()->"stage-3-integration-secret-at-least-32-bytes");
    }

    @Autowired JdbcTemplate db;
    @Autowired DomainService domain;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired IdentityService identity;
    @Autowired PasswordEncoder passwordEncoder;

    private long adminId;
    private long departmentId;
    private long classOneId;
    private long classTwoId;
    private long studentOneId;
    private long studentTwoId;
    private long courseOneId;
    private long courseTwoId;
    private long termId;
    private long teacherId;
    private long counselorId;
    private long studentAccountId;

    @BeforeEach
    void seedIsolatedSchema() {
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

        db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role) VALUES('test-admin','test-admin','not-used','Test Admin','ADMIN')");
        adminId=db.queryForObject("SELECT id FROM iam_account WHERE business_no='test-admin'",Long.class);
        teacherId=insertAccount("test-teacher","TEACHER",null);
        counselorId=insertAccount("test-counselor","COUNSELOR",null);
        db.update("INSERT INTO academic_department(name) VALUES('Stage 3 Test Department')");
        departmentId=db.queryForObject("SELECT id FROM academic_department WHERE name='Stage 3 Test Department'",Long.class);
        db.update("INSERT INTO academic_class(name,entry_year,department_id) VALUES('Stage 3 Test Class A',2099,?)",departmentId);
        db.update("INSERT INTO academic_class(name,entry_year,department_id) VALUES('Stage 3 Test Class B',2099,?)",departmentId);
        classOneId=db.queryForObject("SELECT id FROM academic_class WHERE name='Stage 3 Test Class A'",Long.class);
        classTwoId=db.queryForObject("SELECT id FROM academic_class WHERE name='Stage 3 Test Class B'",Long.class);
        insertStudent("TEST-STUDENT-1","Test Student One",classOneId);
        insertStudent("TEST-STUDENT-2","Test Student Two",classOneId);
        studentOneId=db.queryForObject("SELECT id FROM student_profile WHERE student_no='TEST-STUDENT-1'",Long.class);
        studentTwoId=db.queryForObject("SELECT id FROM student_profile WHERE student_no='TEST-STUDENT-2'",Long.class);
        db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role,student_id) VALUES('TEST-STUDENT-1','test-student',?,'Test Student One','STUDENT',?)",passwordEncoder.encode("Stage3Pass!234"),studentOneId);
        studentAccountId=db.queryForObject("SELECT id FROM iam_account WHERE username='test-student'",Long.class);
        db.update("UPDATE student_profile SET account_id=? WHERE id=?",studentAccountId,studentOneId);
        db.update("UPDATE academic_class SET student_count=2 WHERE id=?",classOneId);
        db.update("INSERT INTO course_catalog(name,default_credits,department_id) VALUES('Stage 3 Test Course A',3,?)",departmentId);
        db.update("INSERT INTO course_catalog(name,default_credits,department_id) VALUES('Stage 3 Test Course B',2,?)",departmentId);
        courseOneId=db.queryForObject("SELECT id FROM course_catalog WHERE name='Stage 3 Test Course A'",Long.class);
        courseTwoId=db.queryForObject("SELECT id FROM course_catalog WHERE name='Stage 3 Test Course B'",Long.class);
        db.update("INSERT INTO academic_term(code,name,academic_year,term_order) VALUES('STAGE3-TEST','Stage 3 Test Term','2099-2100',1)");
        termId=db.queryForObject("SELECT id FROM academic_term WHERE code='STAGE3-TEST'",Long.class);
        db.update("INSERT INTO staff_class_scope(staff_account_id,class_id) VALUES(?,?),(?,?),(?,?)",teacherId,classOneId,counselorId,classOneId,counselorId,classTwoId);
        db.update("INSERT INTO teacher_course_scope(teacher_account_id,course_id) VALUES(?,?)",teacherId,courseOneId);
    }

    @Test
    void transferMovesHeadcountAndTermRankingAtomically() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));

        domain.updateStudent(admin(),studentOneId,Map.of("classId",classTwoId));

        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classOneId));
        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classTwoId));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking WHERE class_id=? AND term_id=?",Integer.class,classOneId,termId));
        assertEquals(1,db.queryForObject("SELECT competition_rank FROM class_term_ranking WHERE class_id=? AND term_id=? AND student_id=?",Integer.class,classTwoId,termId,studentOneId));
    }

    @Test
    void gradeReferenceAndAccountBindingProtectStudentDeletion() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        assertThrows(ApiException.class,()->domain.deleteStudent(admin(),studentOneId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM student_profile WHERE id=?",Integer.class,studentOneId));

        db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role) VALUES('test-student-2','test-student-2','not-used','Test Student Two','STUDENT')");
        long boundAccount=db.queryForObject("SELECT id FROM iam_account WHERE username='test-student-2'",Long.class);
        domain.bindStudentAccount(admin(),studentTwoId,Map.of("accountId",Long.toString(boundAccount)));
        assertEquals(studentTwoId,db.queryForObject("SELECT student_id FROM iam_account WHERE id=?",Long.class,boundAccount));
        assertThrows(ApiException.class,()->domain.deleteStudent(admin(),studentTwoId));
        var unlink=new java.util.HashMap<String,Object>();unlink.put("accountId",null);
        domain.bindStudentAccount(admin(),studentTwoId,unlink);
        assertEquals(2L,db.queryForObject("SELECT auth_version FROM iam_account WHERE id=?",Long.class,boundAccount));
        domain.deleteStudent(admin(),studentTwoId);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM student_profile WHERE id=?",Integer.class,studentTwoId));
        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classOneId));
    }

    @Test
    void invalidBatchLeavesNoFactsOrDerivedRows() {
        var first=grade(studentOneId,courseOneId,"88");
        var invalid=grade(studentTwoId,courseTwoId,"101");
        ApiException error=assertThrows(ApiException.class,()->domain.addGradesBatch(admin(),List.of(first,invalid)));

        assertTrue(error.getMessage().contains("第2条"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM grade_record",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM student_term_summary",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking",Integer.class));
    }

    @Test
    void sameClassConcurrentWritesLeaveProjectionConsistent() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{await(start);domain.addGrade(admin(),grade(studentOneId,courseOneId,"92"));});
            var second=pool.submit(()->{await(start);domain.addGrade(admin(),grade(studentTwoId,courseTwoId,"86"));});
            start.countDown();
            first.get(20,TimeUnit.SECONDS);
            second.get(20,TimeUnit.SECONDS);
        }

        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking WHERE class_id=? AND term_id=?",Integer.class,classOneId,termId));
        assertEquals(1,db.queryForObject("SELECT record_count FROM student_term_summary WHERE student_id=? AND term_id=?",Integer.class,studentOneId,termId));
        assertEquals(1,db.queryForObject("SELECT record_count FROM student_term_summary WHERE student_id=? AND term_id=?",Integer.class,studentTwoId,termId));
    }

    @Test
    void concurrentDuplicateGradeCreatesProduceOneCommittedFact() throws Exception {
        var start=new CountDownLatch(1);var outcomes=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{await(start);try{domain.addGrade(admin(),grade(studentOneId,courseOneId,"91"));outcomes.incrementAndGet();}catch(ApiException ignored){}});
            var second=pool.submit(()->{await(start);try{domain.addGrade(admin(),grade(studentOneId,courseOneId,"92"));outcomes.incrementAndGet();}catch(ApiException ignored){}});
            start.countDown();first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
        }
        assertEquals(1,outcomes.get());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM grade_record WHERE student_id=? AND course_id=? AND term_id=?",Integer.class,studentOneId,courseOneId,termId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking WHERE student_id=? AND term_id=?",Integer.class,studentOneId,termId));
    }

    @Test
    void gradeScopeAndPaginationAreEnforcedBeforePaging() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        domain.addGrade(admin(),grade(studentTwoId,courseTwoId,"80"));
        CurrentUser teacher=new CurrentUser(teacherId,"test-teacher","Teacher","TEACHER",0,null);
        CurrentUser counselor=new CurrentUser(counselorId,"test-counselor","Counselor","COUNSELOR",0,null);
        CurrentUser student=new CurrentUser(studentAccountId,"test-student","Student","STUDENT",0,studentOneId);

        assertEquals(1L,domain.gradePage(teacher,null,termId,classOneId,null,null,null,null,1,1).get("total"));
        assertEquals(0,domain.grades(teacher,null,termId,classOneId,courseTwoId,null,null,null).size());
        assertEquals(2L,domain.gradePage(counselor,null,termId,classOneId,null,null,null,null,1,1).get("total"));
        assertEquals(1L,domain.gradePage(student,studentTwoId,termId,null,null,null,null,null,1,10).get("total"));
    }

    @Test
    void authenticatedHttpScopesCoverWritesDetailsRankingsAndStudentPaging() throws Exception {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        domain.addGrade(admin(),grade(studentOneId,courseTwoId,"70"));
        var adminToken=jwt.issue(admin());
        var teacherToken=jwt.issue(new CurrentUser(teacherId,"test-teacher","Teacher","TEACHER",0,null));
        var counselorToken=jwt.issue(new CurrentUser(counselorId,"test-counselor","Counselor","COUNSELOR",0,null));
        var studentToken=jwt.issue(new CurrentUser(studentAccountId,"test-student","Student","STUDENT",0,studentOneId));

        mvc.perform(get("/api/v1/me/grades").header("Authorization","Bearer "+studentToken).param("pageSize","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/v1/me/grades").header("Authorization","Bearer "+studentToken).param("pageSize","101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/"+studentOneId+"/grade-summary").header("Authorization","Bearer "+counselorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(2));
        mvc.perform(get("/api/v1/students/"+studentOneId+"/grade-summary").header("Authorization","Bearer "+studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(2));
        mvc.perform(get("/api/v1/students/"+studentTwoId+"/grade-summary").header("Authorization","Bearer "+studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(2)).andExpect(jsonPath("$.data.records[0].student_id").value(Long.toString(studentOneId)));
        mvc.perform(get("/api/v1/classes/"+classOneId+"/rankings").header("Authorization","Bearer "+counselorToken).param("termId",Long.toString(termId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        db.update("DELETE FROM staff_class_scope WHERE staff_account_id=? AND class_id=?",counselorId,classTwoId);
        mvc.perform(get("/api/v1/classes/"+classTwoId+"/rankings").header("Authorization","Bearer "+counselorToken).param("termId",Long.toString(termId)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/"+studentOneId+"/grade-summary").header("Authorization","Bearer "+teacherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(1));
        mvc.perform(post("/api/v1/grades").header("Authorization","Bearer "+teacherToken).contentType("application/json").content("{\"studentId\":\""+studentOneId+"\",\"courseId\":\""+courseTwoId+"\",\"termId\":\""+termId+"\",\"score\":\"88\",\"assessmentType\":\"EXAM\"}"))
                .andExpect(status().isForbidden());
        String validGrade="{\"studentId\":\""+studentOneId+"\",\"courseId\":\""+courseOneId+"\",\"termId\":\""+termId+"\",\"score\":\"88\",\"assessmentType\":\"EXAM\"}";
        mvc.perform(post("/api/v1/grades").header("Authorization","Bearer "+counselorToken).contentType("application/json").content(validGrade))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/grades").header("Authorization","Bearer "+studentToken).contentType("application/json").content(validGrade))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/classes/statistics").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/derived-data/rebuild").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/audit-events").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void httpGradeMutationRejectsStaleVersionAndBatchReportsFailingRow() throws Exception {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        var adminToken=jwt.issue(admin());
        long id=db.queryForObject("SELECT id FROM grade_record WHERE student_id=?",Long.class,studentOneId);
        mvc.perform(patch("/api/v1/grades/"+id).header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"version\":1,\"score\":\"70\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        var rows=List.of(grade(studentTwoId,courseOneId,"80"),grade(studentTwoId,courseTwoId,"101"));
        mvc.perform(post("/api/v1/grades/batch").header("Authorization","Bearer "+adminToken).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("第2条")));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM grade_record",Integer.class));
    }

    @Test
    void gradeGpaAspectAndCensusAspectAreActiveAndTransactional() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(contextBean(GradeGpaCalculator.class)));
        assertEquals(new java.math.BigDecimal("4.0"),contextBean(GradeGpaCalculator.class).calculate(new java.math.BigDecimal("90")).gradePoint());
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        domain.updateStudent(admin(),studentOneId,Map.of("classId",classTwoId));
        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classOneId));
        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classTwoId));
    }

    @Test
    void allTermRankingQueryKeepsTermPartitionsAndStableOrder() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        db.update("INSERT INTO academic_term(code,name,academic_year,term_order) VALUES('STAGE3-TEST-2','Stage 3 Test Term 2','2099-2100',2)");
        long secondTerm=db.queryForObject("SELECT id FROM academic_term WHERE code='STAGE3-TEST-2'",Long.class);
        var second=new java.util.HashMap<>(grade(studentOneId,courseTwoId,"80"));second.put("termId",Long.toString(secondTerm));
        domain.addGrade(admin(),second);
        var rows=domain.allTermRankings(admin(),classOneId);
        assertEquals(4,rows.size());
        assertEquals(Long.toString(termId),rows.get(0).get("term_id"));
        assertEquals(Long.toString(secondTerm),rows.get(2).get("term_id"));
        assertEquals(1,rows.get(0).get("competition_rank"));
        assertEquals(1,rows.get(2).get("competition_rank"));
    }

    @Test
    void gradeUpdateMoveAndDeleteRefreshOldAndNewScopes() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        long gradeId=db.queryForObject("SELECT id FROM grade_record WHERE student_id=?",Long.class,studentOneId);
        db.update("INSERT INTO academic_term(code,name,academic_year,term_order) VALUES('STAGE3-MOVE','Stage 3 Move Term','2099-2100',2)");
        long newTerm=db.queryForObject("SELECT id FROM academic_term WHERE code='STAGE3-MOVE'",Long.class);
        var change=new java.util.HashMap<String,Object>();change.put("version",0);change.put("studentId",Long.toString(studentTwoId));change.put("courseId",Long.toString(courseTwoId));change.put("termId",Long.toString(newTerm));change.put("score","75");
        domain.updateGrade(admin(),gradeId,change);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM student_term_summary WHERE student_id=? AND term_id=?",Integer.class,studentOneId,termId));
        assertEquals(1,db.queryForObject("SELECT record_count FROM student_term_summary WHERE student_id=? AND term_id=?",Integer.class,studentTwoId,newTerm));
        assertEquals(classOneId,db.queryForObject("SELECT class_id FROM class_term_ranking WHERE student_id=? AND term_id=?",Long.class,studentTwoId,newTerm));
        domain.deleteGrade(admin(),gradeId,1);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM grade_record",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM student_term_summary",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking",Integer.class));
    }

    @Test
    void emptyAndZeroCreditScopesKeepNullAveragesDistinctFromZeroScore() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"0"));
        var empty=domain.studentGradeDetail(admin(),studentTwoId,termId,null);
        @SuppressWarnings("unchecked") var emptySummaries=(List<Map<String,Object>>)empty.get("summaries");
        assertEquals(0,emptySummaries.getFirst().get("recordCount"));
        assertEquals(null,emptySummaries.getFirst().get("weightedAverage"));
        assertEquals(new java.math.BigDecimal("0.00"),db.queryForObject("SELECT weighted_average FROM student_term_summary WHERE student_id=? AND term_id=?",java.math.BigDecimal.class,studentOneId,termId));
    }

    @Test
    void studentLifecycleHttpRoutesTransferBindUnbindAndDelete() throws Exception {
        domain.addStudentsBatch(admin(),List.of(student("TEST-LIFECYCLE",classOneId)));
        long profile=db.queryForObject("SELECT id FROM student_profile WHERE student_no='TEST-LIFECYCLE'",Long.class);
        db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role) VALUES('test-lifecycle','test-lifecycle','not-used','Test Lifecycle','STUDENT')");
        long account=db.queryForObject("SELECT id FROM iam_account WHERE business_no='test-lifecycle'",Long.class);
        var adminToken=jwt.issue(admin());
        mvc.perform(patch("/api/v1/admin/students/"+profile).header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"classId\":\""+classTwoId+"\"}"))
                .andExpect(status().isOk());
        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classTwoId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/students/"+profile+"/account").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"accountId\":\""+account+"\"}"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/students/"+profile).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_ACCOUNT_BOUND"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/students/"+profile+"/account").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"accountId\":null}"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/students/"+profile).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void httpRoutesEnforceRoleAndExposeFilteredPageEnvelope() throws Exception {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        var teacher=new CurrentUser(teacherId,"test-teacher","Teacher","TEACHER",0,null);
        var adminToken=jwt.issue(admin());var teacherToken=jwt.issue(teacher);

        mvc.perform(get("/api/v1/grades").header("Authorization","Bearer "+adminToken).param("page","1").param("pageSize","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/v1/admin/audit-events").header("Authorization","Bearer "+teacherToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/grades").header("Authorization","Bearer "+adminToken).param("pageSize","101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/students").header("Authorization","Bearer "+teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherHttpListOnlyIncludesGrantedCourseAndPageTotalIsScoped() throws Exception {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        domain.addGrade(admin(),grade(studentTwoId,courseTwoId,"80"));
        var token=jwt.issue(new CurrentUser(teacherId,"test-teacher","Teacher","TEACHER",0,null));
        mvc.perform(get("/api/v1/grades").header("Authorization","Bearer "+token).param("page","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].course_id").value(Long.toString(courseOneId)));
    }

    @Test
    void studentNumberRenameInvalidatesPriorCredentialAndKeepsNewLoginAlias() {
        assertTrue(identity.isCredentialCurrent(studentAccountId,0,"no-revocation-row"));
        identity.authenticate("TEST-STUDENT-1","Stage3Pass!234");
        domain.updateStudent(admin(),studentOneId,Map.of("studentNo","TEST-STUDENT-1-RENAMED","fullName","Renamed Student"));
        assertEquals(false,identity.isCredentialCurrent(studentAccountId,0,"no-revocation-row"));
        assertEquals("STUDENT",identity.authenticate("TEST-STUDENT-1-RENAMED","Stage3Pass!234").role());
    }

    @Test
    void derivedDataRebuildRepairsClassCensusFromStudentFacts(){
        db.update("UPDATE academic_class SET student_count=99 WHERE id=?",classOneId);
        domain.rebuildDerivedData(admin());
        assertEquals(2,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classOneId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='DERIVED_DATA_REBUILD' AND outcome='SUCCESS'",Integer.class));
    }

    @Test
    void optimisticVersionConflictLeavesGradeAndProjectionsUntouched() {
        domain.addGrade(admin(),grade(studentOneId,courseOneId,"90"));
        long id=db.queryForObject("SELECT id FROM grade_record WHERE student_id=?",Long.class,studentOneId);
        var error=assertThrows(ApiException.class,()->domain.updateGrade(admin(),id,Map.of("version",1,"score","70")));
        assertEquals("VERSION_CONFLICT",error.code());
        assertEquals(90,db.queryForObject("SELECT score_value FROM grade_record WHERE id=?",java.math.BigDecimal.class,id).intValue());
        assertEquals(90,db.queryForObject("SELECT weighted_average FROM student_term_summary WHERE student_id=? AND term_id=?",java.math.BigDecimal.class,studentOneId,termId).intValue());
    }

    @Test
    void studentBatchIsAtomicAndRecountsEachAffectedClass() {
        var one=student("TEST-BATCH-1",classOneId);
        var invalid=student("TEST-BATCH-2",classTwoId);
        invalid.put("departmentId",Long.toString(departmentId+1000));

        assertThrows(ApiException.class,()->domain.addStudentsBatch(admin(),List.of(one,invalid)));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM student_profile",Integer.class));

        domain.addStudentsBatch(admin(),List.of(one,student("TEST-BATCH-3",classTwoId)));
        assertEquals(3,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classOneId));
        assertEquals(1,db.queryForObject("SELECT student_count FROM academic_class WHERE id=?",Integer.class,classTwoId));
    }

    @Test
    void projectionFailureRollsBackGradeAndSummary() {
        db.execute("CREATE TRIGGER stage3_fail_ranking BEFORE INSERT ON class_term_ranking FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ranking failure'");
        try {
            assertThrows(RuntimeException.class,()->domain.addGrade(admin(),grade(studentOneId,courseOneId,"90")));
        } finally {
            db.execute("DROP TRIGGER IF EXISTS stage3_fail_ranking");
        }
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM grade_record",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM student_term_summary",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM class_term_ranking",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='GRADE_CREATE' AND outcome='FAILURE'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='GRADE_CREATE' AND outcome='SUCCESS'",Integer.class));
    }

    @Test
    void scopedDetailAndAnalyticsUseDocumentedMetrics() {
        var first=grade(studentOneId,courseOneId,"90");
        var second=new java.util.HashMap<>(grade(studentOneId,courseTwoId,"60"));
        second.put("assessmentType","ASSESSMENT");second.put("credits","2");
        domain.addGrade(admin(),first);domain.addGrade(admin(),second);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='GRADE_CREATE' AND outcome='SUCCESS'",Integer.class));

        var detail=domain.studentGradeDetail(admin(),studentOneId,termId,null);
        @SuppressWarnings("unchecked") var summaries=(List<Map<String,Object>>)detail.get("summaries");
        assertEquals(2,summaries.getFirst().get("recordCount"));
        assertEquals(new java.math.BigDecimal("75.00"),summaries.getFirst().get("arithmeticAverage"));
        assertEquals(new java.math.BigDecimal("82.50"),summaries.getFirst().get("weightedAverage"));
        assertEquals(new java.math.BigDecimal("3.25"),summaries.getFirst().get("gpa"));

        var analytics=domain.analyticsOverview(admin(),termId,classOneId,null);
        assertEquals(1,analytics.get("studentCount"));
        assertEquals(2,analytics.get("recordCount"));
        assertEquals(new java.math.BigDecimal("75.0"),analytics.get("arithmeticAverage"));
        assertEquals(new java.math.BigDecimal("100.0"),analytics.get("passRate"));
        assertEquals(new java.math.BigDecimal("82.5"),analytics.get("weightedAverage"));
        assertEquals(new java.math.BigDecimal("3.25"),analytics.get("gpa"));
    }

    private void insertStudent(String number,String name,long classId) {
        db.update("INSERT INTO student_profile(student_no,full_name,department_id,class_id,major) VALUES(?,?,?,?, 'CS')",number,name,departmentId,classId);
    }
    private long insertAccount(String name,String role,Long student){db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role,student_id) VALUES(?,?, 'not-used',?,?,?)",name,name,name,role,student);return db.queryForObject("SELECT id FROM iam_account WHERE business_no=?",Long.class,name);}

    private Map<String,Object> grade(long studentId,long courseId,String score) {
        return Map.of("studentId",Long.toString(studentId),"courseId",Long.toString(courseId),"termId",Long.toString(termId),"score",score,"assessmentType","EXAM");
    }

    private Map<String,Object> student(String number,long classId) {
        return new java.util.HashMap<>(Map.of("studentNo",number,"fullName",number,"departmentId",Long.toString(departmentId),"classId",Long.toString(classId),"major","CS"));
    }

    private CurrentUser admin() { return new CurrentUser(adminId,"test-admin","Test Admin","ADMIN",0,null); }

    @Autowired org.springframework.context.ApplicationContext context;
    private <T> T contextBean(Class<T> type) { return context.getBean(type); }

    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent test start timed out"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
    }
}
