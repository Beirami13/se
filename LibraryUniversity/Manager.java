package LibraryUniversity;

public class Manager extends Person {
    private String managerId;

    public Manager(String name, String managerId, String username, String password) {
        super(name, username, password);
        this.managerId = managerId != null ? managerId.trim() : "";
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        if (managerId != null && !managerId.trim().isEmpty()) {
            this.managerId = managerId.trim();
        }
    }

    @Override
    public String toString() {
        return "Name: " + getName() + " | Manager ID: " + managerId + " | Username: " + getUsername();
    }
}
