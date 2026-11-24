package edu.stusystem.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class GradeGpaCalculator {
    public GradeGpaCalculation calculate(BigDecimal score) {
        throw new IllegalStateException("Grade GPA calculation aspect is not active");
    }
}
