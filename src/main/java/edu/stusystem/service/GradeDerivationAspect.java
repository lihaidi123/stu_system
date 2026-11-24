package edu.stusystem.service;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.jdbc.core.JdbcTemplate;

@Aspect
@Component
public class GradeDerivationAspect {
    private final DomainService domain;
    private final JdbcTemplate db;
    public GradeDerivationAspect(DomainService domain, JdbcTemplate db) { this.domain = domain; this.db = db; }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void derive(DomainService.GradeChanged event) { domain.derive(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void synchronizeCensus(DomainService.StudentCensusChanged event) {
        for (long classId : event.classIds().stream().distinct().sorted().toList()) {
            db.update("UPDATE academic_class SET student_count=(SELECT COUNT(*) FROM student_profile WHERE class_id=?) WHERE id=?", classId, classId);
        }
    }
}
