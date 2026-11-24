ALTER TABLE student_term_summary
  MODIFY weight_sum DECIMAL(12,3) NOT NULL;

ALTER TABLE class_term_ranking
  MODIFY weight_sum DECIMAL(18,3) NOT NULL;
