package edu.stusystem.security;

public record CurrentUser(long id, String username, String realName, String role, long authVersion, Long studentId) {}
