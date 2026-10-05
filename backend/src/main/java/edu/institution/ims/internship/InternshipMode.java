package edu.institution.ims.internship;

public enum InternshipMode {
    OFFICE_REPORTING("Office Reporting"), HYBRID("Hybrid"), ONLINE("Online"), COLLEGE_REPORTING("College Reporting");
    private final String label;
    InternshipMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}
