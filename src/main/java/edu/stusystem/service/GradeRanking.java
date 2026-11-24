package edu.stusystem.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class GradeRanking {
    private GradeRanking() {}

    static List<Ranked> rank(List<Candidate> candidates) {
        var sorted = new ArrayList<>(candidates);
        sorted.sort((left, right) -> {
            int comparison = right.weightedTotal().multiply(left.weightSum())
                    .compareTo(left.weightedTotal().multiply(right.weightSum()));
            return comparison == 0 ? Long.compare(left.studentId(), right.studentId()) : comparison;
        });
        var result = new ArrayList<Ranked>(sorted.size());
        Candidate previous = null;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            var candidate = sorted.get(i);
            if (previous == null || candidate.weightedTotal().multiply(previous.weightSum())
                    .compareTo(previous.weightedTotal().multiply(candidate.weightSum())) != 0) rank = i + 1;
            result.add(new Ranked(candidate.studentId(), candidate.weightedTotal(), candidate.weightSum(), rank));
            previous = candidate;
        }
        return List.copyOf(result);
    }

    record Candidate(long studentId, BigDecimal weightedTotal, BigDecimal weightSum) {}
    record Ranked(long studentId, BigDecimal weightedTotal, BigDecimal weightSum, int rank) {}
}
