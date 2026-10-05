package edu.institution.ims.internship;

public enum CompanyType {
    IT("IT"), MANUFACTURING("Manufacturing"), CORE("Core"), OTHER("Other");
    private final String label;
    CompanyType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
