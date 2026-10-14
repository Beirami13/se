package LibraryUniversity;

public abstract class Person implements Library {
    protected String name;
    protected String username;
    protected String password;

    public Person(String name, String username, String password) {
        this.name = name != null ? name.trim() : "";
        this.username = username != null ? username.trim() : "";
        this.password = password != null ? password : "";
    }

    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public void setName(String name) {
        this.name = name != null ? name.trim() : this.name;
    }

    public void setPassword(String password) {
        this.password = password != null ? password : this.password;
    }

    @Override
    public String getId() {
        if (this instanceof Student) {
            return ((Student) this).getStudentId();
        } else if (this instanceof Staff) {
            return ((Staff) this).getStaffId();
        }
        return "";
    }

    @Override
    public String getTitle() {
        return name;
    }

    @Override
    public String getDisplayInfo() {
        return toString();
    }

    @Override
    public boolean isAvailable() {
        if (this instanceof Student) {
            return ((Student) this).isActive();
        }
        return true;
    }

    @Override
    public String toString() {
        return "Name: " + name + " | Username: " + username;
    }
}