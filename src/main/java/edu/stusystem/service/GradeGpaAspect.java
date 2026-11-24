package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Aspect
@Component
public class GradeGpaAspect {
    @Around("execution(* edu.stusystem.service.GradeGpaCalculator.calculate(..)) && args(score)")
    public GradeGpaCalculation calculate(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0 || score.scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCORE", "成绩必须在0到100之间且最多两位小数");
        }
        BigDecimal[][] bands = {{new BigDecimal("90"),new BigDecimal("4.0")},{new BigDecimal("85"),new BigDecimal("3.7")},{new BigDecimal("82"),new BigDecimal("3.3")},{new BigDecimal("78"),new BigDecimal("3.0")},{new BigDecimal("75"),new BigDecimal("2.7")},{new BigDecimal("72"),new BigDecimal("2.3")},{new BigDecimal("68"),new BigDecimal("2.0")},{new BigDecimal("64"),new BigDecimal("1.5")},{new BigDecimal("60"),new BigDecimal("1.0")}};
        for (var band : bands) if (score.compareTo(band[0]) >= 0) return new GradeGpaCalculation(score, band[1]);
        return new GradeGpaCalculation(score, BigDecimal.ZERO.setScale(1));
    }
}
