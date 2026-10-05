package edu.institution.ims.auth;
public record CurrentUserResponse(Long id, String email, String role, String roleLabel, boolean mustChangePassword) {}
