package edu.stusystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradeMetricsTest {
    @Test
    void appliesAssessmentHalfWeightAndCountsDistinctCourses() {
        var summary=GradeMetrics.summarize(List.of(
                new GradeMetrics.Fact(1,10,100,new BigDecimal("90"),new BigDecimal("4.0"),new BigDecimal("3.00"),"EXAM"),
                new GradeMetrics.Fact(1,11,100,new BigDecimal("60"),new BigDecimal("1.0"),new BigDecimal("2.00"),"ASSESSMENT")));

        assertEquals(2,summary.recordCount());
        assertEquals(2,summary.distinctCourseCount());
        assertEquals(new BigDecimal("150"),summary.totalScore());
        assertEquals(new BigDecimal("75.00"),summary.arithmeticAverage());
        assertEquals(new BigDecimal("82.50"),summary.weightedAverage());
        assertEquals(new BigDecimal("3.25"),summary.gpa());
        assertEquals(new BigDecimal("5.00"),summary.totalCredits());
        assertEquals(new BigDecimal("5.00"),summary.passedCredits());
    }

    @Test
    void zeroEffectiveWeightKeepsAveragesNull() {
        var summary=GradeMetrics.summarize(List.of(
                new GradeMetrics.Fact(1,10,100,new BigDecimal("0"),new BigDecimal("0"),new BigDecimal("0.00"),"EXAM")));

        assertNull(summary.weightedAverage());
        assertNull(summary.gpa());
        assertEquals(new BigDecimal("0.00"),summary.totalCredits());
    }
}
