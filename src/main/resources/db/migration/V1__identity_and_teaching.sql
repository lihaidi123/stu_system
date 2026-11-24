CREATE TABLE iam_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_no VARCHAR(32) NOT NULL,
  username VARCHAR(30) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  real_name VARCHAR(80) NOT NULL,
  role ENUM('ADMIN','TEACHER','COUNSELOR','STUDENT') NOT NULL,
  status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
  auth_version BIGINT NOT NULL DEFAULT 0,
  student_id BIGINT NULL,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_account_business_no (business_no),
  UNIQUE KEY uk_account_username (username)
);
CREATE TABLE academic_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE academic_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  entry_year SMALLINT NOT NULL,
  department_id BIGINT NOT NULL,
  student_count INT NOT NULL DEFAULT 0,
  FOREIGN KEY (department_id) REFERENCES academic_department(id)
);
CREATE TABLE academic_term (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(24) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  academic_year VARCHAR(9) NOT NULL,
  term_order TINYINT NOT NULL,
  UNIQUE KEY uk_term_year_order (academic_year, term_order)
);
CREATE TABLE course_catalog (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL UNIQUE,
  default_credits DECIMAL(5,2) NOT NULL,
  department_id BIGINT NOT NULL,
  FOREIGN KEY (department_id) REFERENCES academic_department(id),
  CHECK (default_credits >= 0)
);
CREATE TABLE student_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_no VARCHAR(32) NOT NULL UNIQUE,
  full_name VARCHAR(80) NOT NULL,
  gender ENUM('MALE','FEMALE','OTHER') NULL,
  department_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  major VARCHAR(100) NOT NULL,
  account_id BIGINT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (department_id) REFERENCES academic_department(id),
  FOREIGN KEY (class_id) REFERENCES academic_class(id),
  FOREIGN KEY (account_id) REFERENCES iam_account(id)
);
ALTER TABLE iam_account ADD CONSTRAINT fk_account_student FOREIGN KEY (student_id) REFERENCES student_profile(id);
CREATE TABLE teacher_course_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_account_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  UNIQUE KEY uk_teacher_course (teacher_account_id, course_id),
  FOREIGN KEY (teacher_account_id) REFERENCES iam_account(id),
  FOREIGN KEY (course_id) REFERENCES course_catalog(id)
);
CREATE TABLE staff_class_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_account_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  UNIQUE KEY uk_staff_class (staff_account_id, class_id),
  FOREIGN KEY (staff_account_id) REFERENCES iam_account(id),
  FOREIGN KEY (class_id) REFERENCES academic_class(id)
);
CREATE TABLE grade_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  term_id BIGINT NOT NULL,
  credits_snapshot DECIMAL(5,2) NOT NULL,
  assessment_type ENUM('EXAM','ASSESSMENT') NOT NULL,
  score_value DECIMAL(5,2) NOT NULL,
  grade_point DECIMAL(3,2) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_grade_student_course_term (student_id, course_id, term_id),
  KEY ix_grade_term_student (term_id, student_id),
  FOREIGN KEY (student_id) REFERENCES student_profile(id),
  FOREIGN KEY (course_id) REFERENCES course_catalog(id),
  FOREIGN KEY (term_id) REFERENCES academic_term(id),
  FOREIGN KEY (created_by) REFERENCES iam_account(id),
  CHECK (credits_snapshot >= 0),
  CHECK (score_value >= 0 AND score_value <= 100)
);
CREATE TABLE student_term_summary (
  student_id BIGINT NOT NULL,
  term_id BIGINT NOT NULL,
  record_count INT NOT NULL,
  total_score DECIMAL(10,2) NOT NULL,
  weight_sum DECIMAL(10,2) NOT NULL,
  weighted_average DECIMAL(7,2) NULL,
  gpa DECIMAL(5,2) NULL,
  total_credits DECIMAL(8,2) NOT NULL,
  passed_credits DECIMAL(8,2) NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (student_id, term_id),
  FOREIGN KEY (student_id) REFERENCES student_profile(id),
  FOREIGN KEY (term_id) REFERENCES academic_term(id)
);
CREATE TABLE token_revocation (
  jti CHAR(36) PRIMARY KEY,
  account_id BIGINT NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (account_id) REFERENCES iam_account(id),
  KEY ix_revocation_expiry (expires_at)
);
CREATE TABLE audit_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NULL,
  actor_name VARCHAR(80) NULL,
  role VARCHAR(20) NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(40) NULL,
  target_id VARCHAR(64) NULL,
  outcome VARCHAR(20) NOT NULL,
  error_code VARCHAR(64) NULL,
  trace_id VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY ix_audit_created (created_at),
  KEY ix_audit_actor (actor_id, created_at)
);
