package edu.institution.ims.internship;

public enum DocumentType {
    OFFER_LETTER("Offer Letter"),
    UNDERTAKING("Undertaking Form"),
    DEPARTMENT_NOC("NOC by Department"),
    STUDENT_NOC("NOC by Student");

    private final String label;
    DocumentType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
