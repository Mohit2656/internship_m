package edu.institution.ims.internship;

public enum InternshipSource {
    DEPARTMENT("Department"), CDC("CDC"), SELF("Self");

    private final String label;
    InternshipSource(String label) { this.label = label; }
    public String getLabel() { return label; }
}
