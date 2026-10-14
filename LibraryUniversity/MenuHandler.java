package LibraryUniversity;

import java.util.Scanner;

public class MenuHandler {
    private Scanner scanner;
    private LibrarySystem librarySystem;
    private Student currentUser;

    public MenuHandler(LibrarySystem librarySystem) {
        this.scanner = new Scanner(System.in);
        this.librarySystem = librarySystem;
        this.currentUser = null;
    }

    public void displayMainMenu() {
        while (true) {
            System.out.println("\n=== University Library Management System ===");
            System.out.println("1. Guest Access");
            System.out.println("2. Student Registration");
            System.out.println("3. Student Login");
            System.out.println("4. Staff Login");
            System.out.println("5. Manager Login");
            System.out.println("6. View Registered Student Count");
            System.out.println("7. Exit");
            System.out.print("Please enter your choice: ");

            int choice = getIntInput(1, 7);
            switch (choice) {
                case 1:
                    displayGuestMenu();
                    break;
                case 2:
                    handleStudentRegistration();
                    break;
                case 3:
                    handleStudentLogin();

                    break;
                case 4:
                    displayStaffLoginMenu();
                    break;
                case 5:
                    displayManagerLoginMenu();
                    break;
                case 6:
                    displayStudentCount();
                    break;
                case 7:
                    System.out.println("Exiting system. Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option! Please try again.");
            }
            System.out.println("___________________________");
        }
    }

    private void displayStudentCount() {
        int studentCount = librarySystem.getStudentCount();
        System.out.println("\nTotal registered students: " + studentCount);
    }

    public void displayGuestMenu() {
        while (true) {
            System.out.println("\n=== Guest Menu ===");
            System.out.println("1. View Registered Student Count");
            System.out.println("2. Search Book by Title");
            System.out.println("3. View Simple Library Statistics");
            System.out.println("4. Exit");
            System.out.print("Please enter your choice: ");
            int choice = getIntInput(1, 4);
            switch (choice) {
                case 1:
                    System.out.println("\nTotal registered students: " + librarySystem.getStudentCount());
                    break;
                case 2:
                    librarySystem.searchBookByTitleForGuest();
                    break;
                case 3:
                    librarySystem.displayLibraryStats();
                    break;
                case 4:
                    System.out.println("Exiting guest menu.");
                    return;
                default:
                    System.out.println("Invalid option! Please try again.");
            }
        }
    }

    private void handleStudentRegistration() {
        System.out.println("\n--- New Student Registration ---");
        System.out.print("Student name: ");
        String name = scanner.nextLine();
        System.out.print("Student ID: ");
        String studentId = scanner.nextLine();
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        librarySystem.registerStudent(name, studentId, username, password);
    }

    private void handleStudentLogin() {
        System.out.println("\n--- Student Login ---");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        currentUser = librarySystem.authenticateStudent(username, password);
        if (currentUser != null) {
            System.out.println("Login successful! Welcome, " + currentUser.getName());
            displayLoggedInStudentMenu();
        } else {
            System.out.println("Invalid username or password. Please try again.");
        }
    }

    private void displayLoggedInStudentMenu() {
        while (currentUser != null) {
            System.out.println("\n=== Student Dashboard ===");
            System.out.println("1. View My Information");
            System.out.println("2. Edit My Information");
            System.out.println("3. Search a Book");
            System.out.println("4. Borrow a Book");
            System.out.println("5. Return a Book");
            System.out.println("6. View Available Books");
            System.out.println("7. Logout");
            System.out.print("Please enter your choice: ");
            int choice = getIntInput(1, 7);
            switch (choice) {
                case 1:
                    System.out.println("\n--- My Information ---");
                    System.out.println(currentUser);
                    break;
                case 2:
                    librarySystem.editStudentInformation(currentUser);
                    break;
                case 3:
                    librarySystem.searchBooks();
                    break;
                case 4:
                    librarySystem.borrowBook(currentUser);
                    break;
                case 5:
                    librarySystem.returnBook(currentUser);
                    break;
                case 6:
                    librarySystem.displayAvailableBooks();
                    break;
                case 7:
                    currentUser = null;
                    System.out.println("Logged out successfully.");
                    return;
                default:
                    System.out.println("Invalid option! Please try again.");
            }
        }
    }

    public void displayStaffLoginMenu() {
        System.out.println("\n--- Staff Login ---");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        Staff staff = librarySystem.authenticateStaff(username, password);
        if (staff != null) {
            System.out.println("Login successful! Welcome, " + staff.getName());
            displayStaffDashboard(staff);
        } else {
            System.out.println("Invalid username or password.");
        }
    }

    private void displayStaffDashboard(Staff staff) {
        while (true) {
            System.out.println("\n=== Staff Dashboard ===");
            System.out.println("1. View Statistics");
            System.out.println("2. Change Password");
            System.out.println("3. Register New Book");
            System.out.println("4. Edit Existing Book");
            System.out.println("5. Approve Borrow Requests");
            System.out.println("6. View Student Borrow History");
            System.out.println("7. Toggle Student Status");
            System.out.println("8. Receive Returned Book");
            System.out.println("9. Logout");
            System.out.print("Choose an option: ");
            int choice = getIntInput(1, 9);
            switch (choice) {
                case 1:
                    librarySystem.displayLibraryStats();
                    break;
                case 2:
                    System.out.print("Enter new password: ");
                    String newPassword = scanner.nextLine();
                    librarySystem.changeStaffPassword(staff, newPassword);
                    break;
                case 3:
                    librarySystem.registerBook();
                    break;
                case 4:
                    librarySystem.editBook();
                    break;
                case 5:
                    librarySystem.approveBorrowRequests();
                    break;
                case 6:
                    System.out.print("Enter student ID to view history: ");
                    String studentId = scanner.nextLine();
                    librarySystem.viewStudentBorrowHistory(studentId);
                    break;
                case 7:
                    System.out.print("Enter student ID to toggle status: ");
                    String toggleStudentId = scanner.nextLine();
                    Student studentToToggle = librarySystem.findStudentById(toggleStudentId);
                    if (studentToToggle != null) {
                        librarySystem.toggleStudentStatus(studentToToggle);
                    } else {
                        System.out.println("Student not found.");
                    }
                    break;
                case 8:
                    System.out.print("Enter book ID to receive: ");
                    String receiveBookId = scanner.nextLine();
                    librarySystem.receiveReturnedBook(receiveBookId);
                    break;
                case 9:
                    System.out.println("Logged out successfully.");
                    return;
            }
        }
    }

    public void displayManagerLoginMenu() {
        System.out.println("\n--- Manager Login ---");
        System.out.print("Enter Manager Password: ");
        String password = scanner.nextLine();

        Staff manager = librarySystem.authenticateManager(password);
        if (manager != null) {
            System.out.println("Login successful! Welcome, Manager.");
            displayManagerDashboard();
        } else {
            System.out.println("Invalid manager password.");
        }
    }

    private void displayManagerDashboard() {
        while (true) {
            System.out.println("\n=== Manager Dashboard ===");
            System.out.println("1. Register New Staff");
            System.out.println("2. View Staff Performance");
            System.out.println("3. View Borrow Statistics");
            System.out.println("4. View Student Statistics");
            System.out.println("5. View Individual Student History");
            System.out.println("6. View Top Delayed Students");
            System.out.println("7. Logout");
            System.out.print("Choose an option: ");

            int choice = getIntInput(1, 7);
            switch (choice) {
                case 1:
                    registerNewStaff();
                    break;
                case 2:
                    librarySystem.viewStaffPerformance();
                    break;
                case 3:
                    librarySystem.viewBorrowStatistics();
                    break;
                case 4:
                    librarySystem.viewStudentStatistics();
                    break;
                case 5:
                    System.out.print("Enter student ID: ");
                    String studentId = scanner.nextLine();
                    librarySystem.viewIndividualStudentHistory(studentId);
                    break;
                case 6:
                    librarySystem.displayTopDelayedStudents();
                    break;
                case 7:
                    System.out.println("Logged out successfully.");
                    return;
            }
        }
    }

    private void registerNewStaff() {
        System.out.println("\n--- Register New Staff ---");
        System.out.print("Staff name: ");
        String name = scanner.nextLine();
        System.out.print("Staff ID: ");
        String staffId = scanner.nextLine();
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        librarySystem.registerStaff(name, staffId, username, password);
    }

    private int getIntInput(int min, int max) {
        while (true) {
            try {
                int input = Integer.parseInt(scanner.nextLine());
                if (input >= min && input <= max) return input;
                System.out.printf("Please enter a number between %d and %d: ", min, max);
            } catch (NumberFormatException e) {
                System.out.print("Invalid input. Please enter a number: ");
            }
        }
    }
}
