package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeGpaAspectTest {
    @Test
    void proxyAspectCalculatesEveryDocumentedBoundary() {
        try (var context = new AnnotationConfigApplicationContext(GpaTestConfig.class)) {
            var calculator = context.getBean(GradeGpaCalculator.class);
            assertTrue(AopUtils.isAopProxy(calculator));
            Map<String, String> boundaries = Map.ofEntries(
                    Map.entry("100", "4.0"), Map.entry("90", "4.0"), Map.entry("89.99", "3.7"),
                    Map.entry("85", "3.7"), Map.entry("84.99", "3.3"), Map.entry("82", "3.3"),
                    Map.entry("81.99", "3.0"), Map.entry("78", "3.0"), Map.entry("77.99", "2.7"),
                    Map.entry("75", "2.7"), Map.entry("74.99", "2.3"), Map.entry("72", "2.3"),
                    Map.entry("71.99", "2.0"), Map.entry("68", "2.0"), Map.entry("67.99", "1.5"),
                    Map.entry("64", "1.5"), Map.entry("63.99", "1.0"), Map.entry("60", "1.0"), Map.entry("59.99", "0.0"), Map.entry("0", "0.0"));
            boundaries.forEach((score, expected) -> assertEquals(new BigDecimal(expected), calculator.calculate(new BigDecimal(score)).gradePoint()));
        }
    }

    @Test
    void aspectRejectsOutOfRangeAndOverPrecisionScores() {
        try (var context = new AnnotationConfigApplicationContext(GpaTestConfig.class)) {
            var calculator = context.getBean(GradeGpaCalculator.class);
            assertThrows(ApiException.class, () -> calculator.calculate(new BigDecimal("-0.01")));
            assertThrows(ApiException.class, () -> calculator.calculate(new BigDecimal("100.01")));
            assertThrows(ApiException.class, () -> calculator.calculate(new BigDecimal("89.999")));
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class GpaTestConfig {
        @Bean GradeGpaCalculator calculator() { return new GradeGpaCalculator(); }
        @Bean GradeGpaAspect aspect() { return new GradeGpaAspect(); }
    }
}
