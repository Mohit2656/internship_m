package edu.institution.ims.studentprofile.dto;

import java.util.List;

public record ProfileOptionsResponse(List<Option> branches, List<String> bloodGroups, int minimumSemester, int maximumSemester, List<String> sections) {
    public record Option(String value, String label) {}
}

