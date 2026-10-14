package LibraryUniversity;

public class Staff extends Person {
    private String staffId;
    private int booksRegistered;
    private int booksBorrowed;
    private int booksReturned;

    public Staff(String name, String staffId, String username, String password) {
        super(name, username, password);
        this.staffId = staffId != null ? staffId.trim() : "";
        this.booksRegistered = 0;
        this.booksBorrowed = 0;
        this.booksReturned = 0;
    }
    public int getBooksRegistered() {
        return booksRegistered;
    }
    public int getBooksBorrowed() {
        return booksBorrowed;
    }
    public int getBooksReturned() {
        return booksReturned;
    }
    public void incrementBooksRegistered() {
        booksRegistered++;
    }
    public void incrementBooksBorrowed() {
        booksBorrowed++;
    }
    public void incrementBooksReturned() {
        booksReturned++;
    }
    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        if (staffId != null && !staffId.trim().isEmpty()) {
            this.staffId = staffId.trim();
        }
    }

    @Override
    public String toString() {
        return "Name: " + getName() + " | Staff ID: " + staffId + " | Username: " + getUsername();
    }
}
