package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class SensitivePasswordVerificationService {
    private static final long FAILURE_LIMIT=3;
    private static final Duration FAILURE_WINDOW=Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION=Duration.ofMinutes(5);
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final AtomicRateLimitService rateLimits;

    public SensitivePasswordVerificationService(JdbcTemplate db,PasswordEncoder passwords,AtomicRateLimitService rateLimits){this.db=db;this.passwords=passwords;this.rateLimits=rateLimits;}

    public Map<String,Object> verify(CurrentUser actor,String currentPassword){
        if(actor==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请重新登录");
        String prefix="auth:sensitive:account:"+actor.id();
        String failureKey=prefix+":failures",lockKey=prefix+":locked";
        if(rateLimits.isLimited(lockKey))throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"SENSITIVE_VERIFICATION_LOCKED","密码验证次数过多，请5分钟后重试");
        String hash;
        try{hash=db.queryForObject("SELECT password_hash FROM iam_account WHERE id=? AND status='ACTIVE'",String.class,actor.id());}
        catch(EmptyResultDataAccessException missing){throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","账户当前不可用");}
        catch(DataAccessException unavailable){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AUTH_STATE_UNAVAILABLE","账户验证暂不可用");}
        if(currentPassword==null||!passwords.matches(currentPassword,hash)){
            rateLimits.recordFailure(failureKey,lockKey,FAILURE_LIMIT,FAILURE_WINDOW,LOCK_DURATION);
            throw new ApiException(HttpStatus.UNAUTHORIZED,"CURRENT_PASSWORD_INVALID","当前密码验证失败");
        }
        rateLimits.clear(failureKey,lockKey);
        return Map.of("verified",true);
    }
}
