package LibraryUniversity;

import java.util.Scanner;

public class LibrarySystem {
    private StudentManager studentManager;
    private BookManager bookManager;
    private MenuHandler menuHandler;
    private StaffManager staffManager;

    public LibrarySystem() {
        this.studentManager = new StudentManager();
        this.staffManager = new StaffManager();
        this.bookManager = new BookManager(staffManager, studentManager);
        this.menuHandler = new MenuHandler(this);
        this.bookManager.loadBorrowRequests();
    }

    public int getStudentCount() {
        return studentManager.getStudentCount();
    }

    public int getBookCount() {
        return bookManager.getBookCount();
    }

    public int getTotalBorrows() {
        return bookManager.getTotalBorrows();
    }

    public int getCurrentlyBorrowedCount() {
        return bookManager.getCurrentlyBorrowedCount();
    }

    public void registerStudent(String name, String studentId, String username, String password) {
        studentManager.registerStudent(name, studentId, username, password);
    }

    public Student authenticateStudent(String username, String password) {
        return studentManager.authenticateStudent(username, password);
    }

    public void searchBooks() {
        bookManager.searchBooks();
    }

    public void searchBookByTitleForGuest() {
        bookManager.searchBookByTitleForGuest();
    }

    public void registerBook() {
        bookManager.registerBook();
    }

    public void editBook() {
        bookManager.editBook();
    }

    public void borrowBook(Student student) {
        bookManager.borrowBook(student);
    }

    public void returnBook(Student student) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter book ID to return: ");
        String bookId = scanner.nextLine();
        bookManager.returnBook(student, bookId);
    }

    public void displayAvailableBooks() {
        bookManager.displayAvailableBooks();
    }

    public void displayLibraryStats() {
        System.out.println("\n--- Library Simple Statistics ---");
        System.out.println("Total registered students: " + getStudentCount());
        System.out.println("Total books: " + getBookCount());
        System.out.println("Total borrow requests (historical): " + getTotalBorrows());
        System.out.println("Currently borrowed books: " + getCurrentlyBorrowedCount());
    }

    public Staff authenticateStaff(String username, String password) {
        return staffManager.authenticateStaff(username, password);
    }

    public void changeStaffPassword(Staff staff, String newPassword) {
        staffManager.changePassword(staff, newPassword);
    }

    public void editStudentInformation(Student student) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- Edit Student Information ---");
        System.out.println("Current information: " + student);

        System.out.print("New name (leave blank to keep current): ");
        String newName = scanner.nextLine();

        System.out.print("New password (leave blank to keep current): ");
        String newPassword = scanner.nextLine();

        studentManager.updateStudent(student, newName, newPassword);
        System.out.println("Information updated successfully!");
    }

    public void approveBorrowRequests() {
        bookManager.approveBorrowRequests();
    }

    public void start() {
        menuHandler.displayMainMenu();
    }

    public void viewStudentBorrowHistory(String studentId) {
        System.out.println("This feature is not available. Use viewIndividualStudentHistory instead.");
    }

    public void toggleStudentStatus(Student student) {
        bookManager.toggleStudentStatus(student);
    }

    public void receiveReturnedBook(String bookId) {
        bookManager.receiveReturnedBook(bookId);
    }

    public Student findStudentById(String studentId) {
        return studentManager.findStudentById(studentId);
    }

    public void registerStaff(String name, String staffId, String username, String password) {
        staffManager.registerStaff(name, staffId, username, password);
    }

    public Staff authenticateManager(String password) {
        return staffManager.authenticateManager("manager", password);
    }

    public void viewStaffPerformance() {
        System.out.println("Staff performance report is not implemented yet.");
    }

    public void viewBorrowStatistics() {
        bookManager.viewBorrowStatistics();
    }

    public void viewStudentStatistics() {
        bookManager.viewStudentStatistics();
    }

    public void viewIndividualStudentHistory(String studentId) {
        bookManager.viewIndividualStudentHistory(studentId);
    }

    public void displayTopDelayedStudents() {
        System.out.println("Displaying top delayed students from statistics:");
        bookManager.viewStudentStatistics();
    }

    public static void main(String[] args) {
        LibrarySystem system = new LibrarySystem();
        system.start();
    }
}
