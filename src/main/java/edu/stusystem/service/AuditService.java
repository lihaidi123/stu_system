package edu.stusystem.service;

import edu.stusystem.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate db;
    public AuditService(JdbcTemplate db){this.db=db;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(CurrentUser actor,String action,String targetType,String targetId,String outcome,String errorCode,String traceId){
        db.update("INSERT INTO audit_event(actor_id,actor_name,role,action,target_type,target_id,outcome,error_code,trace_id) VALUES(?,?,?,?,?,?,?,?,?)",
                actor==null?null:actor.id(),actor==null?null:actor.realName(),actor==null?null:actor.role(),action,targetType,targetId,outcome,errorCode,traceId);
    }
}
