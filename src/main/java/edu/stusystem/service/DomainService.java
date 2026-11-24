package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class DomainService {
    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;
    private final GradeGpaCalculator gpaCalculator;
    private final PasswordEncoder passwords;
    public DomainService(JdbcTemplate db, ApplicationEventPublisher events, GradeGpaCalculator gpaCalculator, PasswordEncoder passwords) { this.db = db; this.events = events; this.gpaCalculator = gpaCalculator; this.passwords = passwords; }
    public List<Map<String,Object>> list(String kind) {
        String sql = switch (kind) {
            case "departments" -> "SELECT id,name FROM academic_department ORDER BY name";
            case "classes" -> "SELECT id,name,entry_year,department_id,student_count FROM academic_class ORDER BY entry_year DESC,name";
            case "terms" -> "SELECT id,code,name,academic_year,term_order FROM academic_term ORDER BY academic_year DESC,term_order";
            case "courses" -> "SELECT id,name,default_credits,department_id FROM course_catalog ORDER BY name";
            default -> throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","资源不存在");
        };
        return db.query(sql,(rs,n)->{var row=new java.util.LinkedHashMap<String,Object>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++){String label=rs.getMetaData().getColumnLabel(i);Object value=rs.getObject(i);row.put(label,(value instanceof Number && label.equals("id"))?value.toString():value);}return row;});
    }
    @Transactional
    @Audited(action="DOMAIN_CREATE",targetType="domain",targetIdArgument=1)
    public Map<String,Object> create(CurrentUser actor,String kind, Map<String,Object> body) {
        requireAdmin(actor);
        switch (kind) {
            case "departments" -> db.update("INSERT INTO academic_department(name) VALUES(?)", required(body,"name"));
            case "classes" -> db.update("INSERT INTO academic_class(name,entry_year,department_id) VALUES(?,?,?)", required(body,"name"), body.get("entryYear"), body.get("departmentId"));
            case "terms" -> db.update("INSERT INTO academic_term(code,name,academic_year,term_order) VALUES(?,?,?,?)", required(body,"code"), required(body,"name"), required(body,"academicYear"), body.get("termOrder"));
            case "courses" -> db.update("INSERT INTO course_catalog(name,default_credits,department_id) VALUES(?,?,?)", required(body,"name"), body.get("defaultCredits"), body.get("departmentId"));
            case "students" -> createStudent(body);
            case "accounts" -> {
                String role=required(body,"role");
                if(!List.of("ADMIN","TEACHER","COUNSELOR","STUDENT").contains(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROLE","账户角色无效");
                String username=required(body,"username").toLowerCase(java.util.Locale.ROOT);
                String password=rawRequired(body,"password");
                PasswordPolicy.validate(password, username);
                String businessNo=required(body,"businessNo");
                String realName=required(body,"realName");
                String email=optional(body,"email");
                String phone=optional(body,"phone");
                if(email!=null){email=email.toLowerCase(java.util.Locale.ROOT);if(email.length()>254||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EMAIL","邮箱格式无效");}
                if(phone!=null&& !phone.matches("^1[3-9]\\d{9}$"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHONE","手机号格式无效");
                String status=body.get("status")==null?"ACTIVE":body.get("status").toString();
                if(!List.of("ACTIVE","DISABLED").contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATUS","账户状态无效");
                if(role.equals("STUDENT")){
                    Long profileId=Long.parseLong(required(body,"studentId"));
                    Map<String,Object> profile=db.queryForMap("SELECT student_no,full_name,account_id FROM student_profile WHERE id=? FOR UPDATE",profileId);
                    if(profile.get("account_id")!=null)throw new ApiException(HttpStatus.CONFLICT,"STUDENT_ALREADY_BOUND","学生档案已绑定账户");
                    if(!businessNo.equals(profile.get("student_no")))throw new ApiException(HttpStatus.BAD_REQUEST,"STUDENT_NUMBER_MISMATCH","学生账户业务号必须与档案学号一致");
                    db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role,status,email,phone,student_id) VALUES(?,?,?,?,?,?,?,?,?)",businessNo,username,passwords.encode(password),profile.get("full_name"),role,status,email,phone,profileId);
                    Long accountId=db.queryForObject("SELECT id FROM iam_account WHERE business_no=?",Long.class,profile.get("student_no"));
                    db.update("UPDATE student_profile SET account_id=? WHERE id=?",accountId,profileId);
                } else {
                    db.update("INSERT INTO iam_account(business_no,username,password_hash,real_name,role,status,email,phone) VALUES(?,?,?,?,?,?,?,?)",businessNo,username,passwords.encode(password),realName,role,status,email,phone);
                }
            }
            case "scopes" -> {
                long accountId=Long.parseLong(required(body,"accountId"));
                String role=db.queryForObject("SELECT role FROM iam_account WHERE id=?",String.class,accountId);
                if("course".equals(required(body,"scopeType"))){if(!"TEACHER".equals(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","课程授权对象必须是教师");db.update("INSERT IGNORE INTO teacher_course_scope(teacher_account_id,course_id) VALUES(?,?)",accountId,body.get("targetId"));}
                else {if(!List.of("TEACHER","COUNSELOR").contains(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","班级授权对象角色无效");db.update("INSERT IGNORE INTO staff_class_scope(staff_account_id,class_id) VALUES(?,?)",accountId,body.get("targetId"));}
            }
            default -> throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","资源不存在");
        }
        return Map.of("created",true);
    }
    private void createStudent(Map<String,Object> body) {
        StudentInput student=validateStudent(body);
        if(studentNumberExists(student.studentNo(),null))throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","学号已存在");
        lockClasses(List.of(student.classId()));
        requireClassDepartment(student.classId(),student.departmentId());
        try {
            db.update("INSERT INTO student_profile(student_no,full_name,gender,department_id,class_id,major) VALUES(?,?,?,?,?,?)",student.studentNo(),student.fullName(),student.gender(),student.departmentId(),student.classId(),student.major());
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","学号已存在");
        }
        events.publishEvent(new StudentCensusChanged(List.of(student.classId())));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="STUDENT_BATCH_CREATE",targetType="student_batch")
    public Map<String,Object> addStudentsBatch(CurrentUser actor,List<Map<String,Object>> items) {
        requireAdmin(actor);
        if(items==null||items.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"EMPTY_BATCH","学生批次不能为空");
        if(items.size()>1000)throw new ApiException(HttpStatus.BAD_REQUEST,"BATCH_TOO_LARGE","单批最多新增1000名学生");
        var validated=new java.util.ArrayList<StudentInput>(items.size());
        var numbers=new java.util.HashSet<String>();
        for(int i=0;i<items.size();i++){
            try {
                StudentInput student=validateStudent(items.get(i));
                if(!numbers.add(student.studentNo())||studentNumberExists(student.studentNo(),null))throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","学号重复");
                requireClassDepartment(student.classId(),student.departmentId());
                validated.add(student);
            } catch(RuntimeException e) {
                if(e instanceof ApiException api)throw new ApiException(api.status(),api.code(),"第"+(i+1)+"条："+api.getMessage());
                throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STUDENT_ROW","第"+(i+1)+"条学生档案无效");
            }
        }
        lockClasses(validated.stream().map(StudentInput::classId).toList());
        var affectedClasses=new java.util.LinkedHashSet<Long>();
        for(int i=0;i<validated.size();i++){
            StudentInput student=validated.get(i);
            try {
                db.update("INSERT INTO student_profile(student_no,full_name,gender,department_id,class_id,major) VALUES(?,?,?,?,?,?)",student.studentNo(),student.fullName(),student.gender(),student.departmentId(),student.classId(),student.major());
            } catch(org.springframework.dao.DuplicateKeyException e) {
                throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","第"+(i+1)+"条：学号已存在");
            }
            affectedClasses.add(student.classId());
        }
        events.publishEvent(new StudentCensusChanged(List.copyOf(affectedClasses)));
        return Map.of("created",validated.size());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="STUDENT_UPDATE",targetType="student",targetIdArgument=1)
    public Map<String,Object> updateStudent(CurrentUser actor,long studentId,Map<String,Object> body) {
        requireAdmin(actor);
        lockStudentProfiles(List.of(studentId));
        Map<String,Object> old=db.queryForMap("SELECT student_no,full_name,gender,department_id,class_id,major,account_id FROM student_profile WHERE id=?",studentId);
        long oldClass=((Number)old.get("class_id")).longValue();
        String studentNo=body.containsKey("studentNo")?required(body,"studentNo"):(String)old.get("student_no");
        String fullName=body.containsKey("fullName")?required(body,"fullName"):(String)old.get("full_name");
        Object gender=body.containsKey("gender")?body.get("gender"):old.get("gender");
        long departmentId=body.containsKey("departmentId")?parseId(body,"departmentId"):((Number)old.get("department_id")).longValue();
        long classId=body.containsKey("classId")?parseId(body,"classId"):oldClass;
        String major=body.containsKey("major")?required(body,"major"):(String)old.get("major");
        if(studentNumberExists(studentNo,studentId))throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","学号已存在");
        requireClassDepartment(classId,departmentId);
        lockClasses(List.of(oldClass,classId));
        try {
            db.update("UPDATE student_profile SET student_no=?,full_name=?,gender=?,department_id=?,class_id=?,major=? WHERE id=?",studentNo,fullName,gender,departmentId,classId,major,studentId);
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","学号已存在");
        }
        Object accountId=old.get("account_id");
        if(accountId!=null&&(!fullName.equals(old.get("full_name"))||!studentNo.equals(old.get("student_no")))){
            try { db.update("UPDATE iam_account SET business_no=?,real_name=?,auth_version=auth_version+1 WHERE id=? AND role='STUDENT'",studentNo,fullName,accountId); }
            catch(org.springframework.dao.DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT,"STUDENT_NUMBER_EXISTS","学号已被其他账户使用"); }
        }
        var affectedClasses=List.of(oldClass,classId).stream().distinct().toList();
        if(oldClass!=classId){
            events.publishEvent(new StudentCensusChanged(affectedClasses));
            var terms=db.queryForList("SELECT DISTINCT term_id FROM grade_record WHERE student_id=? ORDER BY term_id",Long.class,studentId);
            var classTerms=new java.util.ArrayList<ClassTerm>();
            for(long termId:terms){classTerms.add(new ClassTerm(oldClass,termId));classTerms.add(new ClassTerm(classId,termId));}
            if(!classTerms.isEmpty())events.publishEvent(new GradeChanged(List.of(),classTerms.stream().distinct().toList()));
        }
        return Map.of("updated",true,"classChanged",oldClass!=classId);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="STUDENT_DELETE",targetType="student",targetIdArgument=1)
    public Map<String,Object> deleteStudent(CurrentUser actor,long studentId) {
        requireAdmin(actor);
        lockStudentProfiles(List.of(studentId));
        Map<String,Object> student=db.queryForMap("SELECT class_id,account_id FROM student_profile WHERE id=?",studentId);
        Integer gradeCount=db.queryForObject("SELECT COUNT(*) FROM grade_record WHERE student_id=?",Integer.class,studentId);
        if(gradeCount!=null&&gradeCount>0)throw new ApiException(HttpStatus.CONFLICT,"STUDENT_HAS_GRADES","已有成绩的学生档案不能删除");
        if(student.get("account_id")!=null)throw new ApiException(HttpStatus.CONFLICT,"STUDENT_ACCOUNT_BOUND","请先显式解除学生账户绑定");
        long classId=((Number)student.get("class_id")).longValue();
        lockClasses(List.of(classId));
        db.update("DELETE FROM student_term_summary WHERE student_id=?",studentId);
        db.update("DELETE FROM class_term_ranking WHERE student_id=?",studentId);
        if(db.update("DELETE FROM student_profile WHERE id=?",studentId)!=1)throw new ApiException(HttpStatus.NOT_FOUND,"STUDENT_NOT_FOUND","学生档案不存在");
        events.publishEvent(new StudentCensusChanged(List.of(classId)));
        return Map.of("deleted",true);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="STUDENT_ACCOUNT_BINDING",targetType="student",targetIdArgument=1)
    public Map<String,Object> bindStudentAccount(CurrentUser actor,long studentId,Map<String,Object> body) {
        requireAdmin(actor);
        if(!body.containsKey("accountId"))throw new ApiException(HttpStatus.BAD_REQUEST,"ACCOUNT_ID_REQUIRED","必须明确提交 accountId；使用 null 解除绑定");
        lockStudentProfiles(List.of(studentId));
        Map<String,Object> profile=db.queryForMap("SELECT account_id FROM student_profile WHERE id=?",studentId);
        Long oldAccount=profile.get("account_id")==null?null:((Number)profile.get("account_id")).longValue();
        Object requested=body.get("accountId");
        Long newAccount=requested==null?null:parseId(Map.of("accountId",requested),"accountId");
        if(java.util.Objects.equals(oldAccount,newAccount))return Map.of("updated",true,"changed",false);
        var accountIds=java.util.stream.Stream.of(oldAccount,newAccount).filter(java.util.Objects::nonNull).toList();
        for(long accountId:accountIds.stream().distinct().sorted().toList())db.queryForObject("SELECT id FROM iam_account WHERE id=? FOR UPDATE",Long.class,accountId);
        if(newAccount!=null){
            Map<String,Object> account=db.queryForMap("SELECT role,student_id FROM iam_account WHERE id=?",newAccount);
            if(!"STUDENT".equals(account.get("role")))throw new ApiException(HttpStatus.CONFLICT,"ACCOUNT_ROLE_MISMATCH","学生档案只能绑定 STUDENT 账户");
            Object linkedStudent=account.get("student_id");
            if(linkedStudent!=null&&((Number)linkedStudent).longValue()!=studentId)throw new ApiException(HttpStatus.CONFLICT,"ACCOUNT_ALREADY_BOUND","账户已绑定其他学生档案");
        }
        if(oldAccount!=null)db.update("UPDATE iam_account SET student_id=NULL,auth_version=auth_version+1 WHERE id=? AND student_id=?",oldAccount,studentId);
        db.update("UPDATE student_profile SET account_id=? WHERE id=?",newAccount,studentId);
        if(newAccount!=null)db.update("UPDATE iam_account SET student_id=?,auth_version=auth_version+1 WHERE id=?",studentId,newAccount);
        return Map.of("updated",true,"changed",true);
    }
    private StudentInput validateStudent(Map<String,Object> body) {
        String gender=body.get("gender")==null?null:body.get("gender").toString();
        if(gender!=null&&!List.of("MALE","FEMALE","OTHER").contains(gender))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_GENDER","性别值无效");
        return new StudentInput(required(body,"studentNo"),required(body,"fullName"),gender,parseId(body,"departmentId"),parseId(body,"classId"),required(body,"major"));
    }
    private boolean studentNumberExists(String studentNo,Long exceptId) {
        Integer count=exceptId==null?db.queryForObject("SELECT COUNT(*) FROM student_profile WHERE student_no=?",Integer.class,studentNo):db.queryForObject("SELECT COUNT(*) FROM student_profile WHERE student_no=? AND id<>?",Integer.class,studentNo,exceptId);
        return count!=null&&count>0;
    }
    private void requireClassDepartment(long classId,long departmentId) {
        Long classDepartment=db.queryForObject("SELECT department_id FROM academic_class WHERE id=?",Long.class,classId);
        if(classDepartment==null||classDepartment!=departmentId)throw new ApiException(HttpStatus.BAD_REQUEST,"CLASS_DEPARTMENT_MISMATCH","学生学院必须与班级所属学院一致");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="DERIVED_DATA_REBUILD",targetType="system")
    public Map<String,Object> rebuildDerivedData(CurrentUser actor){
        requireAdmin(actor);List<Long> students=db.queryForList("SELECT id FROM student_profile ORDER BY id",Long.class);lockStudentProfiles(students);
        List<Long> classes=db.queryForList("SELECT id FROM academic_class ORDER BY id",Long.class);lockClasses(classes);events.publishEvent(new StudentCensusChanged(classes));
        List<Long> terms=db.queryForList("SELECT id FROM academic_term ORDER BY academic_year,term_order",Long.class);
        for(long student:students)for(long term:terms)recalculate(student,term);
        db.update("DELETE FROM class_term_ranking");for(long classId:classes)for(long term:terms)recalculateRankings(classId,term);
        return Map.of("studentsRebuilt",students.size(),"classesRebuilt",classes.size(),"termsRebuilt",terms.size());
    }
    public Map<String,Object> students(CurrentUser user,int page,int pageSize,String keyword,Long classId) {
        validatePage(page,pageSize);var conditions=new java.util.ArrayList<String>();var args=new java.util.ArrayList<Object>();
        switch(user.role()){
            case "ADMIN" -> conditions.add("1=1");
            case "TEACHER" -> {conditions.add("EXISTS (SELECT 1 FROM staff_class_scope sc WHERE sc.class_id=s.class_id AND sc.staff_account_id=?)");args.add(user.id());conditions.add("EXISTS (SELECT 1 FROM grade_record g JOIN teacher_course_scope tc ON tc.course_id=g.course_id WHERE g.student_id=s.id AND tc.teacher_account_id=?)");args.add(user.id());}
            case "COUNSELOR" -> {conditions.add("EXISTS (SELECT 1 FROM staff_class_scope sc WHERE sc.class_id=s.class_id AND sc.staff_account_id=?)");args.add(user.id());}
            default -> conditions.add("1=0");
        }
        if(classId!=null){conditions.add("s.class_id=?");args.add(classId);}
        if(keyword!=null&&!keyword.isBlank()){conditions.add("(LOWER(s.student_no) LIKE ? OR LOWER(s.full_name) LIKE ?)");String pattern="%"+keyword.trim().toLowerCase()+"%";args.add(pattern);args.add(pattern);}
        String where=String.join(" AND ",conditions);Long total=db.queryForObject("SELECT COUNT(DISTINCT s.id) FROM student_profile s WHERE "+where,Long.class,args.toArray());
        var queryArgs=new java.util.ArrayList<>(args);queryArgs.add(pageSize);queryArgs.add((long)(page-1)*pageSize);
        var items=db.queryForList("SELECT CAST(s.id AS CHAR) id,s.student_no,s.full_name,CAST(s.class_id AS CHAR) class_id,c.name class_name FROM student_profile s JOIN academic_class c ON c.id=s.class_id WHERE "+where+" ORDER BY s.student_no LIMIT ? OFFSET ?",queryArgs.toArray());
        return pageResult(items,total==null?0:total,page,pageSize);
    }
    public Map<String,Object> studentAdminList(int page,int pageSize,String keyword,Long classId){
        validatePage(page,pageSize);var conditions=new java.util.ArrayList<String>();var args=new java.util.ArrayList<Object>();
        if(classId!=null){conditions.add("s.class_id=?");args.add(classId);}if(keyword!=null&&!keyword.isBlank()){conditions.add("(LOWER(s.student_no) LIKE ? OR LOWER(s.full_name) LIKE ?)");String p="%"+keyword.trim().toLowerCase()+"%";args.add(p);args.add(p);}
        String where=conditions.isEmpty()?"1=1":String.join(" AND ",conditions);Long total=db.queryForObject("SELECT COUNT(*) FROM student_profile s WHERE "+where,Long.class,args.toArray());
        var queryArgs=new java.util.ArrayList<>(args);queryArgs.add(pageSize);queryArgs.add((long)(page-1)*pageSize);
        var items=db.queryForList("SELECT CAST(s.id AS CHAR) id,s.student_no,s.full_name,s.gender,CAST(s.department_id AS CHAR) department_id,CAST(s.class_id AS CHAR) class_id,s.major,CAST(s.account_id AS CHAR) account_id,c.name class_name FROM student_profile s JOIN academic_class c ON c.id=s.class_id WHERE "+where+" ORDER BY s.student_no LIMIT ? OFFSET ?",queryArgs.toArray());
        return pageResult(items,total==null?0:total,page,pageSize);
    }
    public List<Map<String,Object>> grades(CurrentUser user, Long requestedStudent, Long termId) {
        return grades(user,requestedStudent,termId,null,null,null,null,null);
    }
    public List<Map<String,Object>> grades(CurrentUser user,Long requestedStudent,Long termId,Long classId,Long courseId,BigDecimal scoreMin,BigDecimal scoreMax,String keyword) {
        GradeQuery query=gradeQuery(user,requestedStudent,termId,classId,courseId,scoreMin,scoreMax,keyword);
        return query.studentMissing()?List.of():executeGradeQuery(query,null,null);
    }
    public Map<String,Object> gradePageForStudent(CurrentUser user,Long termId,int page,int pageSize) {
        validatePage(page,pageSize);
        GradeQuery query=gradeQuery(user,user.studentId(),termId,null,null,null,null,null);
        if(query.studentMissing())return pageResult(List.of(),0,page,pageSize);
        Long total=db.queryForObject("SELECT COUNT(*) FROM grade_record g JOIN student_profile s ON s.id=g.student_id JOIN course_catalog c ON c.id=g.course_id JOIN academic_term t ON t.id=g.term_id WHERE "+query.where(),Long.class,query.args().toArray());
        return pageResult(executeGradeQuery(query,pageSize,(long)(page-1)*pageSize),total==null?0:total,page,pageSize);
    }
    private GradeQuery gradeQuery(CurrentUser user,Long requestedStudent,Long termId,Long classId,Long courseId,BigDecimal scoreMin,BigDecimal scoreMax,String keyword){
        if((scoreMin!=null&&(scoreMin.signum()<0||scoreMin.compareTo(BigDecimal.valueOf(100))>0))||(scoreMax!=null&&(scoreMax.signum()<0||scoreMax.compareTo(BigDecimal.valueOf(100))>0))||(scoreMin!=null&&scoreMax!=null&&scoreMin.compareTo(scoreMax)>0))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCORE_RANGE","成绩范围必须位于0到100且最小值不大于最大值");
        if(user.role().equals("STUDENT"))requestedStudent=user.studentId();
        if(user.role().equals("STUDENT")&&requestedStudent==null)return new GradeQuery("1=0",List.of(),true);
        var conditions=new java.util.ArrayList<String>();var args=new java.util.ArrayList<Object>();
        switch(user.role()){
            case "ADMIN" -> conditions.add("1=1");
            case "STUDENT" -> {conditions.add("g.student_id=?");args.add(requestedStudent);}
            case "COUNSELOR" -> {conditions.add("EXISTS (SELECT 1 FROM student_profile s JOIN staff_class_scope x ON x.class_id=s.class_id WHERE s.id=g.student_id AND x.staff_account_id=?)");args.add(user.id());}
            case "TEACHER" -> {conditions.add("EXISTS (SELECT 1 FROM student_profile s JOIN staff_class_scope x ON x.class_id=s.class_id JOIN teacher_course_scope y ON y.course_id=g.course_id WHERE s.id=g.student_id AND x.staff_account_id=? AND y.teacher_account_id=?)");args.add(user.id());args.add(user.id());}
            default -> conditions.add("1=0");
        }
        if(requestedStudent!=null&&!user.role().equals("STUDENT")){conditions.add("g.student_id=?");args.add(requestedStudent);}
        if(termId!=null){conditions.add("g.term_id=?");args.add(termId);}
        if(classId!=null&&!user.role().equals("STUDENT")){conditions.add("s.class_id=?");args.add(classId);}
        if(courseId!=null){conditions.add("g.course_id=?");args.add(courseId);}
        if(scoreMin!=null){conditions.add("g.score_value>=?");args.add(scoreMin);}
        if(scoreMax!=null){conditions.add("g.score_value<=?");args.add(scoreMax);}
        if(keyword!=null&&!keyword.isBlank()){conditions.add("(LOWER(s.student_no) LIKE ? OR LOWER(s.full_name) LIKE ? OR LOWER(c.name) LIKE ?)");String pattern="%"+keyword.trim().toLowerCase()+"%";args.add(pattern);args.add(pattern);args.add(pattern);}
        return new GradeQuery(String.join(" AND ",conditions),List.copyOf(args),false);
    }
    private List<Map<String,Object>> executeGradeQuery(GradeQuery query,Integer limit,Long offset){
        var args=new java.util.ArrayList<>(query.args());String sql="SELECT g.id,g.version,CAST(g.student_id AS CHAR) student_id,s.student_no,s.full_name,CAST(g.course_id AS CHAR) course_id,c.name course_name,CAST(g.term_id AS CHAR) term_id,t.name term_name,g.credits_snapshot,g.assessment_type,g.score_value,g.grade_point FROM grade_record g JOIN student_profile s ON s.id=g.student_id JOIN course_catalog c ON c.id=g.course_id JOIN academic_term t ON t.id=g.term_id WHERE "+query.where()+" ORDER BY s.student_no,t.academic_year,t.term_order,c.name,g.id";
        if(limit!=null){sql+=" LIMIT ? OFFSET ?";args.add(limit);args.add(offset);}
        return db.query(sql,args.toArray(),(rs,n)->{
            var row=new java.util.LinkedHashMap<String,Object>();
            row.put("id",Long.toString(rs.getLong("id")));row.put("version",rs.getLong("version"));
            row.put("student_id",rs.getString("student_id"));row.put("student_no",rs.getString("student_no"));row.put("full_name",rs.getString("full_name"));
            row.put("course_id",rs.getString("course_id"));row.put("course_name",rs.getString("course_name"));row.put("term_id",rs.getString("term_id"));row.put("term_name",rs.getString("term_name"));
            row.put("credits_snapshot",rs.getBigDecimal("credits_snapshot"));row.put("assessment_type",rs.getString("assessment_type"));row.put("score_value",rs.getBigDecimal("score_value"));row.put("grade_point",rs.getBigDecimal("grade_point"));return row;
        });
    }
    public Map<String,Object> gradePage(CurrentUser user,Long studentId,Long termId,Long classId,Long courseId,BigDecimal scoreMin,BigDecimal scoreMax,String keyword,int page,int pageSize){
        validatePage(page,pageSize);GradeQuery query=gradeQuery(user,studentId,termId,classId,courseId,scoreMin,scoreMax,keyword);
        if(query.studentMissing())return pageResult(List.of(),0,page,pageSize);
        String countSql="SELECT COUNT(*) FROM grade_record g JOIN student_profile s ON s.id=g.student_id JOIN course_catalog c ON c.id=g.course_id JOIN academic_term t ON t.id=g.term_id WHERE "+query.where();
        Long total=db.queryForObject(countSql,Long.class,query.args().toArray());
        return pageResult(executeGradeQuery(query,pageSize,(long)(page-1)*pageSize),total==null?0:total,page,pageSize);
    }
    public List<Map<String,Object>> gradeExportRows(CurrentUser user,Long studentId,Long termId,Long classId,Long courseId,BigDecimal scoreMin,BigDecimal scoreMax,String keyword){
        if(user==null||!List.of("ADMIN","TEACHER").contains(user.role()))throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED","仅管理员或教师可导出成绩");
        GradeQuery query=gradeQuery(user,studentId,termId,classId,courseId,scoreMin,scoreMax,keyword);
        if(query.studentMissing())return List.of();
        String countSql="SELECT COUNT(*) FROM grade_record g JOIN student_profile s ON s.id=g.student_id JOIN course_catalog c ON c.id=g.course_id JOIN academic_term t ON t.id=g.term_id WHERE "+query.where();
        Long total=db.queryForObject(countSql,Long.class,query.args().toArray());
        if(total!=null&&total>10_000)throw new ApiException(HttpStatus.BAD_REQUEST,"EXPORT_TOO_MANY_ROWS","筛选结果超过单个 XLSX 文件的行数限制，请缩小筛选范围");
        return executeGradeQuery(query,10_001,0L);
    }
    private static void validatePage(int page,int pageSize){if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","page 必须大于0，pageSize 必须在1到100之间");}
    private static Map<String,Object> pageResult(List<?> items,long total,int page,int pageSize){return Map.of("items",items,"total",total,"page",page,"pageSize",pageSize);}
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Map<String,Object>> summary(CurrentUser user,Long studentId,Long termId){return summary(user,studentId,termId,null);}
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Map<String,Object>> summary(CurrentUser user,Long studentId,Long termId,Long courseId){
        if(user.role().equals("STUDENT"))studentId=user.studentId();
        if(studentId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"STUDENT_REQUIRED","缺少学生身份");
        long scopedStudent=studentId;
        Long classId=db.queryForObject("SELECT class_id FROM student_profile WHERE id=? FOR UPDATE",Long.class,scopedStudent);
        lockClasses(List.of(classId));
        if(!user.role().equals("ADMIN")&&!user.role().equals("STUDENT")){
            Integer allowed=db.queryForObject("SELECT COUNT(*) FROM staff_class_scope WHERE staff_account_id=? AND class_id=?",Integer.class,user.id(),classId);
            if(allowed==null||allowed==0)throw new ApiException(HttpStatus.FORBIDDEN,"SCOPE_DENIED","无权查看该学生统计");
        }
        var rows=grades(user,scopedStudent,termId,null,courseId,null,null,null);
        var grouped=new java.util.TreeMap<String,List<Map<String,Object>>>();
        for(var row:rows)grouped.computeIfAbsent((String)row.get("term_id"),ignored->new java.util.ArrayList<>()).add(row);
        if(termId!=null&&grouped.isEmpty())grouped.put(Long.toString(termId),List.of());
        var result=new java.util.ArrayList<Map<String,Object>>();
        grouped.forEach((term,items)->{
            var facts=items.stream().map(DomainService::fact).toList();
            GradeMetrics.Summary metrics=GradeMetrics.summarize(facts);
            var summary=new java.util.LinkedHashMap<String,Object>();
            summary.put("studentId",Long.toString(scopedStudent));summary.put("termId",term);summary.put("recordCount",metrics.recordCount());summary.put("distinctCourseCount",metrics.distinctCourseCount());
            summary.put("totalScore",metrics.totalScore());summary.put("arithmeticAverage",metrics.arithmeticAverage());summary.put("weightedAverage",metrics.weightedAverage());summary.put("gpa",metrics.gpa());summary.put("totalCredits",metrics.totalCredits());summary.put("passedCredits",metrics.passedCredits());
            Object classRank=null;
            if(!"TEACHER".equals(user.role())&&courseId==null)classRank=db.query("SELECT competition_rank FROM class_term_ranking WHERE student_id=? AND class_id=? AND term_id=?",rs->rs.next()?rs.getInt(1):null,scopedStudent,classId,Long.parseLong(term));
            summary.put("classRank",classRank);result.add(summary);
        });
        return result;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String,Object> studentGradeDetail(CurrentUser user,Long studentId,Long termId,Long courseId){
        if(user.role().equals("STUDENT"))studentId=user.studentId();
        if(studentId==null)throw new ApiException(HttpStatus.BAD_REQUEST,"STUDENT_REQUIRED","缺少学生身份");
        return Map.of("records",grades(user,studentId,termId,null,courseId,null,null,null),"summaries",summary(user,studentId,termId,courseId));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String,Object> analyticsOverview(CurrentUser user,Long termId,Long classId,Long courseId){
        lockAnalyticsClasses(user,classId);
        var rows=grades(user,null,termId,classId,courseId,null,null,null);
        var facts=rows.stream().map(DomainService::fact).toList();
        var metrics=GradeMetrics.summarize(facts);
        BigDecimal weightedTotal=BigDecimal.ZERO,gpaTotal=BigDecimal.ZERO,weightSum=BigDecimal.ZERO;
        var studentIds=new java.util.HashSet<Long>();
        var bands=new int[5];
        for(var fact:facts){
            BigDecimal weight="EXAM".equals(fact.assessmentType())?fact.credits():fact.credits().divide(BigDecimal.valueOf(2));
            weightedTotal=weightedTotal.add(fact.score().multiply(weight));gpaTotal=gpaTotal.add(fact.gradePoint().multiply(weight));weightSum=weightSum.add(weight);studentIds.add(fact.studentId());
            int band=fact.score().compareTo(BigDecimal.valueOf(90))>=0?0:fact.score().compareTo(BigDecimal.valueOf(80))>=0?1:fact.score().compareTo(BigDecimal.valueOf(70))>=0?2:fact.score().compareTo(BigDecimal.valueOf(60))>=0?3:4;
            bands[band]++;
        }
        int recordCount=facts.size(),passed=(int)facts.stream().filter(f->f.score().compareTo(BigDecimal.valueOf(60))>=0).count();
        var distribution=new java.util.ArrayList<Map<String,Object>>();String[] labels={"90-100","80-89.99","70-79.99","60-69.99","0-59.99"};
        for(int i=0;i<bands.length;i++)distribution.add(Map.of("range",labels[i],"count",bands[i]));
        var courseGroups=new java.util.TreeMap<String,List<Map<String,Object>>>();
        var termGroups=new java.util.TreeMap<String,List<Map<String,Object>>>();
        for(var row:rows){courseGroups.computeIfAbsent((String)row.get("course_id"),ignored->new java.util.ArrayList<>()).add(row);termGroups.computeIfAbsent((String)row.get("term_id"),ignored->new java.util.ArrayList<>()).add(row);}
        var courses=new java.util.ArrayList<Map<String,Object>>();
        courseGroups.values().stream().sorted(java.util.Comparator.comparing(items->(String)items.getFirst().get("course_name"))).forEach(items->{
            var courseMetrics=GradeMetrics.summarize(items.stream().map(DomainService::fact).toList());
            var item=new java.util.LinkedHashMap<String,Object>();item.put("courseId",items.getFirst().get("course_id"));item.put("courseName",items.getFirst().get("course_name"));item.put("recordCount",courseMetrics.recordCount());item.put("averageScore",courseMetrics.arithmeticAverage()==null?null:courseMetrics.arithmeticAverage().setScale(1,RoundingMode.HALF_UP));courses.add(item);
        });
        var trend=new java.util.ArrayList<Map<String,Object>>();
        var termOrder=db.queryForList("SELECT CAST(id AS CHAR) term_id,name term_name FROM academic_term ORDER BY academic_year,term_order");
        for(var term:termOrder){List<Map<String,Object>> items=termGroups.get((String)term.get("term_id"));if(items==null)continue;
            var termMetrics=GradeMetrics.summarize(items.stream().map(DomainService::fact).toList());
            var item=new java.util.LinkedHashMap<String,Object>();item.put("termId",term.get("term_id"));item.put("termName",term.get("term_name"));item.put("averageScore",termMetrics.arithmeticAverage()==null?null:termMetrics.arithmeticAverage().setScale(1,RoundingMode.HALF_UP));item.put("gpa",termMetrics.gpa());trend.add(item);
        }
        var result=new java.util.LinkedHashMap<String,Object>();
        result.put("studentCount",studentIds.size());result.put("recordCount",recordCount);result.put("totalScore",metrics.totalScore());
        result.put("arithmeticAverage",divide(metrics.totalScore(),recordCount,1));result.put("passRate",recordCount==0?null:BigDecimal.valueOf(passed).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(recordCount),1,RoundingMode.HALF_UP));
        result.put("weightedAverage",weightSum.signum()==0?null:weightedTotal.divide(weightSum,1,RoundingMode.HALF_UP));result.put("gpa",weightSum.signum()==0?null:gpaTotal.divide(weightSum,2,RoundingMode.HALF_UP));
        result.put("scoreDistribution",distribution);result.put("courses",courses);result.put("terms",trend);return result;
    }
    private void lockAnalyticsClasses(CurrentUser user,Long requestedClass){
        if("STUDENT".equals(user.role()))return;
        List<Long> classes;
        if("ADMIN".equals(user.role()))classes=requestedClass==null?db.queryForList("SELECT id FROM academic_class ORDER BY id",Long.class):List.of(requestedClass);
        else if("COUNSELOR".equals(user.role()))classes=requestedClass==null?db.queryForList("SELECT class_id FROM staff_class_scope WHERE staff_account_id=? ORDER BY class_id",Long.class,user.id()):db.queryForList("SELECT class_id FROM staff_class_scope WHERE staff_account_id=? AND class_id=?",Long.class,user.id(),requestedClass);
        else if("TEACHER".equals(user.role()))classes=requestedClass==null?db.queryForList("SELECT class_id FROM staff_class_scope WHERE staff_account_id=? ORDER BY class_id",Long.class,user.id()):db.queryForList("SELECT class_id FROM staff_class_scope WHERE staff_account_id=? AND class_id=?",Long.class,user.id(),requestedClass);
        else classes=List.of();
        lockClasses(classes);
    }
    private static BigDecimal divide(BigDecimal numerator,int denominator,int scale){return denominator==0?null:numerator.divide(BigDecimal.valueOf(denominator),scale,RoundingMode.HALF_UP);}
    private static GradeMetrics.Fact fact(Map<String,Object> row){
        return new GradeMetrics.Fact(Long.parseLong((String)row.get("student_id")),Long.parseLong((String)row.get("course_id")),Long.parseLong((String)row.get("term_id")),(BigDecimal)row.get("score_value"),(BigDecimal)row.get("grade_point"),(BigDecimal)row.get("credits_snapshot"),(String)row.get("assessment_type"));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="GRADE_CREATE",targetType="grade")
    public Map<String,Object> addGrade(CurrentUser actor, Map<String,Object> body) {
        long studentId=Long.parseLong(required(body,"studentId")), courseId=Long.parseLong(required(body,"courseId")), termId=Long.parseLong(required(body,"termId"));
        BigDecimal score=new BigDecimal(required(body,"score"));
        validateScore(score);
        lockStudentProfiles(List.of(studentId));
        Map<String,Object> student=db.queryForMap("SELECT class_id FROM student_profile WHERE id=?",studentId);
        long classId=((Number)student.get("class_id")).longValue();
        lockClasses(List.of(classId));
        db.queryForObject("SELECT id FROM course_catalog WHERE id=?",Long.class,courseId);
        db.queryForObject("SELECT id FROM academic_term WHERE id=?",Long.class,termId);
        requireGradeWriteAccess(actor, studentId, courseId);
        Map<String,Object> course=db.queryForMap("SELECT default_credits FROM course_catalog WHERE id=?",courseId);
        BigDecimal credits=body.containsKey("credits")?new BigDecimal(required(body,"credits")):(BigDecimal)course.get("default_credits");
        validateCredits(credits);
        String assessment=required(body,"assessmentType");
        if(!List.of("EXAM","ASSESSMENT").contains(assessment)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ASSESSMENT","考核性质无效");
        BigDecimal gpa=gpaCalculator.calculate(score).gradePoint();
        try { db.update("INSERT INTO grade_record(student_id,course_id,term_id,credits_snapshot,assessment_type,score_value,grade_point,created_by) VALUES(?,?,?,?,?,?,?,?)",studentId,courseId,termId,credits,assessment,score,gpa,actor.id()); }
        catch(org.springframework.dao.DuplicateKeyException e){throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_GRADE","该学生此课程学期已有成绩");}
        events.publishEvent(new GradeChanged(List.of(new StudentTerm(studentId,termId)),List.of(new ClassTerm(classId,termId))));
        return Map.of("created",true,"gradePoint",gpa);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="GRADE_BATCH_CREATE",targetType="grade_batch")
    public Map<String,Object> addGradesBatch(CurrentUser actor, List<Map<String,Object>> items) {
        if(items==null||items.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"EMPTY_BATCH","成绩批次不能为空");
        if(items.size()>1000)throw new ApiException(HttpStatus.BAD_REQUEST,"BATCH_TOO_LARGE","单批最多录入1000条成绩");
        var validated=new java.util.ArrayList<ValidatedGrade>(items.size());
        var studentIds=new java.util.ArrayList<Long>();
        var gradeKeys=new java.util.HashSet<String>();
        for(int i=0;i<items.size();i++){
            try {
                Map<String,Object> body=items.get(i);
                long studentId=Long.parseLong(required(body,"studentId")),courseId=Long.parseLong(required(body,"courseId")),termId=Long.parseLong(required(body,"termId"));
                if(!gradeKeys.add(studentId+":"+courseId+":"+termId))throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_GRADE","批次内成绩记录重复");
                Integer existing=db.queryForObject("SELECT COUNT(*) FROM grade_record WHERE student_id=? AND course_id=? AND term_id=?",Integer.class,studentId,courseId,termId);
                if(existing!=null&&existing>0)throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_GRADE","该学生此课程学期已有成绩");
                BigDecimal score=new BigDecimal(required(body,"score"));validateScore(score);
                Map<String,Object> student=db.queryForMap("SELECT class_id FROM student_profile WHERE id=?",studentId);
                long classId=((Number)student.get("class_id")).longValue();
                requireGradeWriteAccess(actor,studentId,courseId);
                Map<String,Object> course=db.queryForMap("SELECT default_credits FROM course_catalog WHERE id=?",courseId);
                db.queryForObject("SELECT id FROM academic_term WHERE id=?",Long.class,termId);
                BigDecimal credits=body.containsKey("credits")?new BigDecimal(required(body,"credits")):(BigDecimal)course.get("default_credits");
                validateCredits(credits);
                String assessment=required(body,"assessmentType");
                if(!List.of("EXAM","ASSESSMENT").contains(assessment))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ASSESSMENT","考核性质无效");
                validated.add(new ValidatedGrade(studentId,courseId,termId,classId,credits,assessment,score,gpaCalculator.calculate(score).gradePoint()));
                studentIds.add(studentId);
            } catch(RuntimeException e) {
                if(e instanceof ApiException api)throw new ApiException(api.status(),api.code(),"第"+(i+1)+"条："+api.getMessage());
                throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_GRADE_ROW","第"+(i+1)+"条成绩数据无效");
            }
        }
        lockStudentProfiles(studentIds);
        var currentClasses=new java.util.HashMap<Long,Long>();
        for(long studentId:studentIds.stream().distinct().sorted().toList()){
            long classId=((Number)db.queryForMap("SELECT class_id FROM student_profile WHERE id=?",studentId).get("class_id")).longValue();
            currentClasses.put(studentId,classId);
        }
        var lockedGrades=new java.util.ArrayList<ValidatedGrade>(validated.size());
        var recheckedKeys=new java.util.HashSet<String>();
        for(int i=0;i<validated.size();i++){
            ValidatedGrade grade=validated.get(i);
            long classId=currentClasses.get(grade.studentId());
            try { requireGradeWriteAccess(actor,grade.studentId(),grade.courseId()); }
            catch(ApiException e){throw new ApiException(e.status(),e.code(),"第"+(i+1)+"条："+e.getMessage());}
            String key=grade.studentId()+":"+grade.courseId()+":"+grade.termId();
            if(!recheckedKeys.add(key))throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_GRADE","第"+(i+1)+"条：批次内成绩记录重复");
            Integer existing=db.queryForObject("SELECT COUNT(*) FROM grade_record WHERE student_id=? AND course_id=? AND term_id=?",Integer.class,grade.studentId(),grade.courseId(),grade.termId());
            if(existing!=null&&existing>0)throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_GRADE","第"+(i+1)+"条：该学生此课程学期已有成绩");
            lockedGrades.add(new ValidatedGrade(grade.studentId(),grade.courseId(),grade.termId(),classId,grade.credits(),grade.assessment(),grade.score(),grade.gpa()));
        }
        validated=lockedGrades;
        lockClasses(validated.stream().map(ValidatedGrade::classId).toList());
        var studentTerms=new java.util.LinkedHashSet<StudentTerm>();
        var classTerms=new java.util.LinkedHashSet<ClassTerm>();
        for(int i=0;i<validated.size();i++){
            var grade=validated.get(i);
            try {
                db.update("INSERT INTO grade_record(student_id,course_id,term_id,credits_snapshot,assessment_type,score_value,grade_point,created_by) VALUES(?,?,?,?,?,?,?,?)",grade.studentId(),grade.courseId(),grade.termId(),grade.credits(),grade.assessment(),grade.score(),grade.gpa(),actor.id());
            } catch(org.springframework.dao.DuplicateKeyException e) {
                throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_GRADE","第"+(i+1)+"条：该学生此课程学期已有成绩");
            }
            studentTerms.add(new StudentTerm(grade.studentId(),grade.termId()));
            classTerms.add(new ClassTerm(grade.classId(),grade.termId()));
        }
        events.publishEvent(new GradeChanged(List.copyOf(studentTerms),List.copyOf(classTerms)));
        return Map.of("created",validated.size());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="GRADE_UPDATE",targetType="grade",targetIdArgument=1)
    public Map<String,Object> updateGrade(CurrentUser actor,long gradeId,Map<String,Object> body) {
        Map<String,Object> old=db.queryForMap("SELECT student_id,course_id,term_id,credits_snapshot,assessment_type,score_value,version FROM grade_record WHERE id=? FOR UPDATE",gradeId);
        long oldStudent=((Number)old.get("student_id")).longValue(),oldCourse=((Number)old.get("course_id")).longValue(),oldTerm=((Number)old.get("term_id")).longValue();
        long studentId=body.containsKey("studentId")?Long.parseLong(required(body,"studentId")):oldStudent;
        lockStudentProfiles(List.of(oldStudent,studentId));
        long oldClass=((Number)db.queryForMap("SELECT class_id FROM student_profile WHERE id=?",oldStudent).get("class_id")).longValue();
        requireGradeWriteAccess(actor,oldStudent,oldCourse);
        long courseId=body.containsKey("courseId")?Long.parseLong(required(body,"courseId")):oldCourse;
        long termId=body.containsKey("termId")?Long.parseLong(required(body,"termId")):oldTerm;
        requireGradeWriteAccess(actor,studentId,courseId);
        Map<String,Object> student=db.queryForMap("SELECT class_id FROM student_profile WHERE id=?",studentId);
        long newClass=((Number)student.get("class_id")).longValue();
        lockClasses(List.of(oldClass,newClass));
        db.queryForObject("SELECT id FROM academic_term WHERE id=?",Long.class,termId);
        BigDecimal score=body.containsKey("score")?new BigDecimal(required(body,"score")):(BigDecimal)old.get("score_value");
        validateScore(score);
        BigDecimal credits;
        if(body.containsKey("credits")) credits=new BigDecimal(required(body,"credits"));
        else if(courseId!=oldCourse) credits=db.queryForObject("SELECT default_credits FROM course_catalog WHERE id=?",BigDecimal.class,courseId);
        else credits=(BigDecimal)old.get("credits_snapshot");
        validateCredits(credits);
        String assessment=body.containsKey("assessmentType")?required(body,"assessmentType"):(String)old.get("assessment_type");
        if(!List.of("EXAM","ASSESSMENT").contains(assessment))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ASSESSMENT","考核性质无效");
        long version=Long.parseLong(required(body,"version"));
        if(version!=((Number)old.get("version")).longValue())throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","成绩已被其他操作修改");
        int changed=db.update("UPDATE grade_record SET student_id=?,course_id=?,term_id=?,credits_snapshot=?,assessment_type=?,score_value=?,grade_point=?,version=version+1 WHERE id=? AND version=?",studentId,courseId,termId,credits,assessment,score,gpaCalculator.calculate(score).gradePoint(),gradeId,version);
        if(changed!=1)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","成绩已被其他操作修改");
        var scopes=List.of(new StudentTerm(oldStudent,oldTerm),new StudentTerm(studentId,termId)).stream().distinct().toList();
        var classTerms=List.of(new ClassTerm(oldClass,oldTerm),new ClassTerm(newClass,termId)).stream().distinct().toList();
        events.publishEvent(new GradeChanged(scopes,classTerms));
        return Map.of("updated",true,"version",version+1);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action="GRADE_DELETE",targetType="grade",targetIdArgument=1)
    public Map<String,Object> deleteGrade(CurrentUser actor,long gradeId,long version) {
        Map<String,Object> old=db.queryForMap("SELECT student_id,course_id,term_id,version FROM grade_record WHERE id=? FOR UPDATE",gradeId);
        long studentId=((Number)old.get("student_id")).longValue(),courseId=((Number)old.get("course_id")).longValue(),termId=((Number)old.get("term_id")).longValue();
        lockStudentProfiles(List.of(studentId));
        long classId=((Number)db.queryForMap("SELECT class_id FROM student_profile WHERE id=?",studentId).get("class_id")).longValue();
        lockClasses(List.of(classId));
        requireGradeWriteAccess(actor,studentId,courseId);
        if(version!=((Number)old.get("version")).longValue())throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","成绩已被其他操作修改");
        if(db.update("DELETE FROM grade_record WHERE id=? AND version=?",gradeId,version)!=1)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","成绩已被其他操作修改");
        events.publishEvent(new GradeChanged(List.of(new StudentTerm(studentId,termId)),List.of(new ClassTerm(classId,termId))));
        return Map.of("deleted",true);
    }
    public List<Map<String,Object>> rankings(CurrentUser user,long classId,long termId) {
        return rankings(user, classId, java.util.List.of(termId));
    }
    public List<Map<String,Object>> allTermRankings(CurrentUser user,long classId) {
        return rankings(user, classId, null);
    }
    private List<Map<String,Object>> rankings(CurrentUser user,long classId,List<Long> termIds) {
        if("TEACHER".equals(user.role()))throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED","教师不可查看班级完整排名");
        if("COUNSELOR".equals(user.role())) {
            Integer allowed=db.queryForObject("SELECT COUNT(*) FROM staff_class_scope WHERE staff_account_id=? AND class_id=?",Integer.class,user.id(),classId);
            if(allowed==null||allowed==0)throw new ApiException(HttpStatus.FORBIDDEN,"SCOPE_DENIED","无权查看该班级排名");
        } else if("STUDENT".equals(user.role())) {
            Integer allowed=db.queryForObject("SELECT COUNT(*) FROM student_profile WHERE id=? AND class_id=?",Integer.class,user.studentId(),classId);
            if(allowed==null||allowed==0)throw new ApiException(HttpStatus.FORBIDDEN,"SCOPE_DENIED","无权查看该班级排名");
        } else if(!"ADMIN".equals(user.role()))throw new ApiException(HttpStatus.FORBIDDEN,"SCOPE_DENIED","无权查看班级排名");
        if (termIds != null) {
            return db.queryForList("SELECT CAST(s.id AS CHAR) student_id,s.student_no,s.full_name,CAST(t.id AS CHAR) term_id,t.name term_name,ROUND(r.weighted_total/r.weight_sum,2) weighted_average,r.competition_rank FROM student_profile s LEFT JOIN class_term_ranking r ON r.student_id=s.id AND r.class_id=s.class_id AND r.term_id=? LEFT JOIN academic_term t ON t.id=? WHERE s.class_id=? ORDER BY r.competition_rank IS NULL,r.competition_rank,s.student_no", termIds.getFirst(), termIds.getFirst(), classId);
        }
        return db.queryForList("SELECT CAST(s.id AS CHAR) student_id,s.student_no,s.full_name,CAST(t.id AS CHAR) term_id,t.name term_name,ROUND(r.weighted_total/r.weight_sum,2) weighted_average,r.competition_rank FROM academic_term t JOIN student_profile s ON s.class_id=? LEFT JOIN class_term_ranking r ON r.student_id=s.id AND r.class_id=s.class_id AND r.term_id=t.id ORDER BY t.academic_year,t.term_order,r.competition_rank IS NULL,r.competition_rank,s.student_no", classId);
    }
    @Transactional
    @Audited(action="SCOPE_ASSIGN",targetType="account",targetIdArgument=1)
    public void assign(CurrentUser actor,long accountId,String scopeType,long targetId){
        if(!"ADMIN".equals(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED","仅管理员可维护授权");
        String role=db.queryForObject("SELECT role FROM iam_account WHERE id=?",String.class,accountId);
        if("course".equals(scopeType)){if(!"TEACHER".equals(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","课程授权对象必须是教师");db.update("INSERT IGNORE INTO teacher_course_scope(teacher_account_id,course_id) VALUES(?,?)",accountId,targetId);}
        else if("class".equals(scopeType)){if(!List.of("TEACHER","COUNSELOR").contains(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","班级授权对象角色无效");db.update("INSERT IGNORE INTO staff_class_scope(staff_account_id,class_id) VALUES(?,?)",accountId,targetId);}
        else throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","授权类型无效");
    }
    public List<Map<String,Object>> accounts(){return db.queryForList("SELECT id,business_no,username,real_name,email,phone,role,status FROM iam_account ORDER BY id DESC");}
    public List<Map<String,Object>> accountExportRows(CurrentUser actor,String keyword,String role,String status){
        requireAdmin(actor);
        var conditions=new java.util.ArrayList<String>();var args=new java.util.ArrayList<Object>();
        if(keyword!=null&&!keyword.isBlank()){conditions.add("(business_no LIKE ? OR username LIKE ? OR real_name LIKE ? OR email LIKE ?)");String match="%"+keyword.trim()+"%";args.add(match);args.add(match);args.add(match);args.add(match);}
        if(role!=null&&!role.isBlank()){if(!List.of("ADMIN","TEACHER","COUNSELOR","STUDENT").contains(role))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROLE","账户角色筛选无效");conditions.add("role=?");args.add(role);}
        if(status!=null&&!status.isBlank()){if(!List.of("ACTIVE","DISABLED").contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATUS","账户状态筛选无效");conditions.add("status=?");args.add(status);}
        String where=conditions.isEmpty()?"1=1":String.join(" AND ",conditions);
        var rows=db.queryForList("SELECT business_no,username,real_name,email,phone,role,status FROM iam_account WHERE "+where+" ORDER BY id DESC LIMIT 10001",args.toArray());
        if(rows.size()>10_000)throw new ApiException(HttpStatus.BAD_REQUEST,"EXPORT_TOO_MANY_ROWS","筛选结果超过单个 XLSX 文件的行数限制，请缩小筛选范围");
        return rows;
    }
    public List<Map<String,Object>> auditEvents(int limit){return db.queryForList("SELECT id,actor_id,actor_name,role,action,target_type,target_id,outcome,error_code,trace_id,created_at FROM audit_event ORDER BY id DESC LIMIT ?",Math.max(1,Math.min(limit,500)));}
    public List<Map<String,Object>> classStatistics(Long departmentId,Integer entryYear){
        var conditions=new java.util.ArrayList<String>();var args=new java.util.ArrayList<Object>();
        if(departmentId!=null){conditions.add("c.department_id=?");args.add(departmentId);}if(entryYear!=null){conditions.add("c.entry_year=?");args.add(entryYear);}
        String where=conditions.isEmpty()?"1=1":String.join(" AND ",conditions);
        return db.queryForList("SELECT CAST(c.id AS CHAR) class_id,c.name class_name,c.entry_year,CAST(c.department_id AS CHAR) department_id,COUNT(s.id) student_count FROM academic_class c LEFT JOIN student_profile s ON s.class_id=c.id WHERE "+where+" GROUP BY c.id,c.name,c.entry_year,c.department_id ORDER BY c.entry_year DESC,c.name",args.toArray());
    }
    public Map<String,Object> options(CurrentUser user){
        var result=new java.util.LinkedHashMap<String,Object>();
        if(user.role().equals("ADMIN")){result.put("departments",list("departments"));result.put("classes",list("classes"));result.put("courses",list("courses"));result.put("terms",list("terms"));return result;}
        if(user.role().equals("TEACHER")){
            result.put("courses",db.queryForList("SELECT c.id,c.name,c.default_credits,c.department_id FROM course_catalog c JOIN teacher_course_scope x ON x.course_id=c.id WHERE x.teacher_account_id=? ORDER BY c.name",user.id()));
            result.put("classes",db.queryForList("SELECT c.id,c.name,c.entry_year,c.department_id,c.student_count FROM academic_class c JOIN staff_class_scope x ON x.class_id=c.id WHERE x.staff_account_id=? ORDER BY c.entry_year DESC,c.name",user.id()));
        }else if(user.role().equals("COUNSELOR")){
            result.put("courses",List.of());result.put("classes",db.queryForList("SELECT c.id,c.name,c.entry_year,c.department_id,c.student_count FROM academic_class c JOIN staff_class_scope x ON x.class_id=c.id WHERE x.staff_account_id=? ORDER BY c.entry_year DESC,c.name",user.id()));
        }else{result.put("courses",List.of());result.put("classes",List.of());}
        result.put("terms",list("terms"));
        return result;
    }
    void derive(GradeChanged event) {
        for(var scope:event.studentTerms())recalculate(scope.studentId(),scope.termId());
        for(var scope:event.classTerms())recalculateRankings(scope.classId(),scope.termId());
    }
    void recalculate(long studentId,long termId) {
        var rows=db.queryForList("SELECT score_value,grade_point,credits_snapshot,assessment_type FROM grade_record WHERE student_id=? AND term_id=?",studentId,termId);
        if(rows.isEmpty()){db.update("DELETE FROM student_term_summary WHERE student_id=? AND term_id=?",studentId,termId);return;}
        BigDecimal total=BigDecimal.ZERO, weights=BigDecimal.ZERO, weighted=BigDecimal.ZERO, gp=BigDecimal.ZERO, credits=BigDecimal.ZERO, passed=BigDecimal.ZERO;
        for(var row:rows){BigDecimal score=(BigDecimal)row.get("score_value"), c=(BigDecimal)row.get("credits_snapshot"), weight="EXAM".equals(row.get("assessment_type"))?c:c.divide(BigDecimal.valueOf(2));BigDecimal point=(BigDecimal)row.get("grade_point");total=total.add(score);weights=weights.add(weight);weighted=weighted.add(score.multiply(weight));gp=gp.add(point.multiply(weight));credits=credits.add(c);if(score.compareTo(BigDecimal.valueOf(60))>=0)passed=passed.add(c);}
        BigDecimal avg=weights.signum()==0?null:weighted.divide(weights,2,RoundingMode.HALF_UP), gpa=weights.signum()==0?null:gp.divide(weights,2,RoundingMode.HALF_UP);
        db.update("INSERT INTO student_term_summary(student_id,term_id,record_count,total_score,weight_sum,weighted_average,gpa,total_credits,passed_credits) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE record_count=VALUES(record_count),total_score=VALUES(total_score),weight_sum=VALUES(weight_sum),weighted_average=VALUES(weighted_average),gpa=VALUES(gpa),total_credits=VALUES(total_credits),passed_credits=VALUES(passed_credits),updated_at=CURRENT_TIMESTAMP(6)",studentId,termId,rows.size(),total,weights,avg,gpa,credits,passed);
    }
    private void recalculateRankings(long classId,long termId) {
        db.queryForObject("SELECT id FROM academic_class WHERE id=? FOR UPDATE",Long.class,classId);
        var rows=db.query("SELECT s.id,COALESCE(SUM(g.score_value*CASE WHEN g.assessment_type='EXAM' THEN g.credits_snapshot ELSE g.credits_snapshot/2 END),0) weighted_total,COALESCE(SUM(CASE WHEN g.assessment_type='EXAM' THEN g.credits_snapshot ELSE g.credits_snapshot/2 END),0) weight_sum FROM student_profile s LEFT JOIN grade_record g ON g.student_id=s.id AND g.term_id=? WHERE s.class_id=? GROUP BY s.id",(rs,n)->new GradeRanking.Candidate(rs.getLong("id"),rs.getBigDecimal("weighted_total"),rs.getBigDecimal("weight_sum")),termId,classId);
        var eligible=rows.stream().filter(row->row.weightSum().signum()>0).toList();
        db.update("DELETE FROM class_term_ranking WHERE class_id=? AND term_id=?",classId,termId);
        for(var ranked:GradeRanking.rank(eligible))db.update("INSERT INTO class_term_ranking(student_id,class_id,term_id,weighted_total,weight_sum,competition_rank) VALUES(?,?,?,?,?,?)",ranked.studentId(),classId,termId,ranked.weightedTotal(),ranked.weightSum(),ranked.rank());
    }
    private void requireGradeWriteAccess(CurrentUser actor,long studentId,long courseId) {
        if("ADMIN".equals(actor.role()))return;
        if(!"TEACHER".equals(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"SCOPE_DENIED","无权维护成绩");
        Integer allowed=db.queryForObject("SELECT COUNT(*) FROM teacher_course_scope tc JOIN staff_class_scope sc ON sc.staff_account_id=tc.teacher_account_id JOIN student_profile s ON s.class_id=sc.class_id WHERE tc.teacher_account_id=? AND tc.course_id=? AND s.id=?",Integer.class,actor.id(),courseId,studentId);
        if(allowed==null||allowed==0)throw new ApiException(HttpStatus.FORBIDDEN,"SCOPE_DENIED","不在授权教学范围内");
    }
    private static void requireAdmin(CurrentUser actor){if(actor==null||!"ADMIN".equals(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED","仅管理员可执行该操作");}
    private void lockClasses(List<Long> classIds) {
        for(long classId:classIds.stream().distinct().sorted().toList())db.queryForObject("SELECT id FROM academic_class WHERE id=? FOR UPDATE",Long.class,classId);
    }
    private void lockStudentProfiles(List<Long> studentIds) {
        for(long studentId:studentIds.stream().distinct().sorted().toList())db.queryForObject("SELECT id FROM student_profile WHERE id=? FOR UPDATE",Long.class,studentId);
    }
    private static long parseId(Map<String,Object> body,String key) {
        try { return Long.parseLong(required(body,key)); }
        catch(NumberFormatException e) { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ID","字段 "+key+" 必须是有效ID"); }
    }
    private static void validateScore(BigDecimal score) {
        if(score.compareTo(BigDecimal.ZERO)<0||score.compareTo(BigDecimal.valueOf(100))>0||score.scale()>2)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCORE","成绩必须在0到100之间且最多两位小数");
    }
    private static void validateCredits(BigDecimal credits){if(credits.signum()<0||credits.scale()>2||credits.compareTo(new BigDecimal("999.99"))>0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CREDITS","学分必须在0到999.99之间且最多两位小数");}
    public record StudentTerm(long studentId,long termId) {}
    public record ClassTerm(long classId,long termId) {}
    public record GradeChanged(List<StudentTerm> studentTerms,List<ClassTerm> classTerms) {}
    private record GradeQuery(String where,List<Object> args,boolean studentMissing){}
    private record StudentInput(String studentNo,String fullName,String gender,long departmentId,long classId,String major) {}
    private record ValidatedGrade(long studentId,long courseId,long termId,long classId,BigDecimal credits,String assessment,BigDecimal score,BigDecimal gpa) {}
    public record StudentCensusChanged(List<Long> classIds) {}
    private static String required(Map<String,Object> body,String key){Object value=body.get(key);if(value==null||value.toString().isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","缺少必要字段: "+key);return value.toString().trim();}
    private static String rawRequired(Map<String,Object> body,String key){Object value=body.get(key);if(!(value instanceof String text)||text.isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","缺少必要字段: "+key);return text;}
    private static String optional(Map<String,Object> body,String key){Object value=body.get(key);if(value==null||value.toString().isBlank())return null;return value.toString().trim();}
}
