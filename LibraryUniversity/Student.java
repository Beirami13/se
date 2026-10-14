package LibraryUniversity;

public class Student extends Person {
    private String studentId;
    private boolean active;

    public Student(String name, String studentId, String username, String password) {
        super(name, username, password);
        this.studentId = studentId != null ? studentId.trim() : "";
        this.active = true;
    }

    public String getStudentId() {
        return studentId;
    }
    public boolean isActive() {
        return active;
    }

    public void setStudentId(String studentId) {
        if (studentId != null && !studentId.trim().isEmpty()) {
            this.studentId = studentId.trim();
        }
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "Name: " + getName() + " | Student ID: " + studentId +
                " | Username: " + getUsername() + " | Status: " + (isActive() ? "Active" : "Inactive");
    }
}