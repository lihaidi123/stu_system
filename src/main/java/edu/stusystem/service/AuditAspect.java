package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {
    private static final Logger log=LoggerFactory.getLogger(AuditAspect.class);
    private final AuditService audit;
    public AuditAspect(AuditService audit){this.audit=audit;}

    @Around("@annotation(edu.stusystem.service.Audited)")
    public Object record(ProceedingJoinPoint invocation) throws Throwable {
        Audited audited=((MethodSignature)invocation.getSignature()).getMethod().getAnnotation(Audited.class);
        Object[] args=invocation.getArgs();
        CurrentUser actor=null;
        for(Object arg:args)if(arg instanceof CurrentUser user){actor=user;break;}
        String targetId=audited.targetIdArgument()<0||audited.targetIdArgument()>=args.length?null:String.valueOf(args[audited.targetIdArgument()]);
        String traceId=UUID.randomUUID().toString();
        try {
            Object result=invocation.proceed();
            if(actor==null&&result instanceof CurrentUser user)actor=user;
            writeSafely(actor,audited.action(),audited.targetType(),targetId,"SUCCESS",null,traceId);
            return result;
        } catch(Throwable failure) {
            String code=failure instanceof ApiException api?api.code():"OPERATION_FAILED";
            writeSafely(actor,audited.action(),audited.targetType(),targetId,"FAILURE",code,traceId);
            throw failure;
        }
    }

    private void writeSafely(CurrentUser actor,String action,String targetType,String targetId,String outcome,String errorCode,String traceId){
        try { audit.record(actor,action,targetType,targetId,outcome,errorCode,traceId); }
        catch(RuntimeException failure){log.error("Audit persistence failed for action {} outcome {} ({})",action,outcome,failure.getClass().getSimpleName());}
    }
}
