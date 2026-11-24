CREATE TABLE class_term_ranking (
  student_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  term_id BIGINT NOT NULL,
  weighted_total DECIMAL(24,4) NOT NULL,
  weight_sum DECIMAL(16,2) NOT NULL,
  competition_rank INT NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (student_id, term_id),
  KEY ix_class_term_rank (class_id, term_id, competition_rank),
  FOREIGN KEY (student_id) REFERENCES student_profile(id),
  FOREIGN KEY (class_id) REFERENCES academic_class(id),
  FOREIGN KEY (term_id) REFERENCES academic_term(id)
);
