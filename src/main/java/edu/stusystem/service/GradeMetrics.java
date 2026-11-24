package edu.stusystem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class GradeMetrics {
    private GradeMetrics() {}

    static Summary summarize(List<Fact> facts) {
        BigDecimal total=BigDecimal.ZERO,weightSum=BigDecimal.ZERO,weighted=BigDecimal.ZERO,gpaWeighted=BigDecimal.ZERO,totalCredits=BigDecimal.ZERO,passedCredits=BigDecimal.ZERO;
        var courses=new java.util.HashSet<Long>();
        for(Fact fact:facts){
            BigDecimal weight="EXAM".equals(fact.assessmentType())?fact.credits():fact.credits().divide(BigDecimal.valueOf(2));
            total=total.add(fact.score());weightSum=weightSum.add(weight);weighted=weighted.add(fact.score().multiply(weight));
            gpaWeighted=gpaWeighted.add(fact.gradePoint().multiply(weight));totalCredits=totalCredits.add(fact.credits());
            if(fact.score().compareTo(BigDecimal.valueOf(60))>=0)passedCredits=passedCredits.add(fact.credits());
            courses.add(fact.courseId());
        }
        BigDecimal arithmeticAverage=average(total,BigDecimal.valueOf(facts.size()));
        BigDecimal weightedAverage=average(weighted,weightSum);
        BigDecimal gpa=average(gpaWeighted,weightSum);
        return new Summary(facts.size(),courses.size(),total,arithmeticAverage,weightSum,weightedAverage,gpa,totalCredits,passedCredits);
    }

    private static BigDecimal average(BigDecimal numerator,BigDecimal denominator) {
        return denominator.signum()==0?null:numerator.divide(denominator,2,RoundingMode.HALF_UP);
    }

    record Fact(long studentId,long courseId,long termId,BigDecimal score,BigDecimal gradePoint,BigDecimal credits,String assessmentType) {}
    record Summary(int recordCount,int distinctCourseCount,BigDecimal totalScore,BigDecimal arithmeticAverage,BigDecimal weightSum,BigDecimal weightedAverage,BigDecimal gpa,BigDecimal totalCredits,BigDecimal passedCredits) {}
}
