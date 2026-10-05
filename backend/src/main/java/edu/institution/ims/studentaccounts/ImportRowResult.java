package edu.institution.ims.studentaccounts;

public record ImportRowResult(int row, String name, String uid, String email, String status, String reason) {}
