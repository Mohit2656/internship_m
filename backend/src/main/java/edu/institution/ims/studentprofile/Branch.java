package edu.institution.ims.studentprofile;

public enum Branch {
    COMPUTER_ENGINEERING("Computer Engineering"),
    INFORMATION_TECHNOLOGY("Information Technology"),
    ELECTRONICS("Electronics"),
    ELECTRICAL("Electrical"),
    MECHANICAL("Mechanical"),
    CIVIL("Civil");

    private final String label;
    Branch(String label) { this.label = label; }
    public String getLabel() { return label; }
}

