package edu.stusystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradeRankingTest {
    @Test
    void competitionRankingUsesUnroundedWeightedAverageAndSkipsTies() {
        var ranked = GradeRanking.rank(List.of(
                new GradeRanking.Candidate(1L, new BigDecimal("90004"), new BigDecimal("1000")),
                new GradeRanking.Candidate(2L, new BigDecimal("90003"), new BigDecimal("1000")),
                new GradeRanking.Candidate(3L, new BigDecimal("8000"), new BigDecimal("100"))));

        assertEquals(List.of(1, 2, 3), ranked.stream().map(GradeRanking.Ranked::rank).toList());
    }

    @Test
    void equalUnroundedAveragesReceiveCompetitionRanks() {
        var ranked = GradeRanking.rank(List.of(
                new GradeRanking.Candidate(1L, new BigDecimal("900"), new BigDecimal("10")),
                new GradeRanking.Candidate(2L, new BigDecimal("9000"), new BigDecimal("100")),
                new GradeRanking.Candidate(3L, new BigDecimal("800"), new BigDecimal("10"))));

        assertEquals(List.of(1, 1, 3), ranked.stream().map(GradeRanking.Ranked::rank).toList());
    }

    @Test
    void allTermRankingsCanKeepTermPartitionsIndependent() {
        var firstTerm = GradeRanking.rank(List.of(
                new GradeRanking.Candidate(1L, new BigDecimal("800"), new BigDecimal("10")),
                new GradeRanking.Candidate(2L, new BigDecimal("900"), new BigDecimal("10"))));
        var secondTerm = GradeRanking.rank(List.of(
                new GradeRanking.Candidate(1L, new BigDecimal("950"), new BigDecimal("10")),
                new GradeRanking.Candidate(2L, new BigDecimal("700"), new BigDecimal("10"))));

        assertEquals(java.util.Map.of(1L, 2, 2L, 1), firstTerm.stream().collect(java.util.stream.Collectors.toMap(GradeRanking.Ranked::studentId, GradeRanking.Ranked::rank)));
        assertEquals(java.util.Map.of(1L, 1, 2L, 2), secondTerm.stream().collect(java.util.stream.Collectors.toMap(GradeRanking.Ranked::studentId, GradeRanking.Ranked::rank)));
    }
}
