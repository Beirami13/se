package LibraryUniversity;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class BookManager {
    private List<Book> books = new ArrayList<>();
    private List<BorrowRequest> borrowRequests = new ArrayList<>();

    private static final String BOOK_FILE = "books.txt";
    private static final String BORROW_LOG = "borrows.txt";
    private static final String REQUESTS_FILE = "borrow_requests.txt";

    private StaffManager staffManager;
    private StudentManager studentManager;

    public BookManager(StaffManager staffManager, StudentManager studentManager) {
        this.staffManager = staffManager;
        this.studentManager = studentManager;
        loadBooksFromFile();
        loadBorrowRequests();
    }

    public int getBookCount() {
        return books.size();
    }

    public int getTotalBorrows() {
        File file = new File(BORROW_LOG);
        if (!file.exists()) return 0;

        int count = 0;
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                scanner.nextLine();
                count++;
            }
        } catch (IOException e) {
            System.out.println("Error reading borrow log: " + e.getMessage());
        }
        return count;
    }

    public int getCurrentlyBorrowedCount() {
        return (int) books.stream()
                .filter(Book::isBorrowed)
                .count();
    }

    public List<Book> getAvailableBooks() {
        return books.stream()
                .filter(book -> !book.isBorrowed())
                .collect(Collectors.toList());
    }

    public void searchBooks() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- Search Book ---");
        System.out.println("1. By Title");
        System.out.println("2. By Author");
        System.out.println("3. By Year");
        System.out.print("Choose search type: ");

        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }

        List<Book> results = new ArrayList<>();
        switch (choice) {
            case 1:
                System.out.print("Enter book title: ");
                String title = scanner.nextLine().toLowerCase();
                results = books.stream()
                        .filter(book -> book.getTitle().toLowerCase().contains(title))
                        .collect(Collectors.toList());
                break;
            case 2:
                System.out.print("Enter author name: ");
                String author = scanner.nextLine().toLowerCase();
                results = books.stream()
                        .filter(book -> book.getAuthor().toLowerCase().contains(author))
                        .collect(Collectors.toList());
                break;
            case 3:
                System.out.print("Enter publication year: ");
                try {
                    int year = Integer.parseInt(scanner.nextLine());
                    results = books.stream()
                            .filter(book -> book.getYear() == year)
                            .collect(Collectors.toList());
                } catch (NumberFormatException e) {
                    System.out.println("Year must be a number.");
                    return;
                }
                break;
            default:
                System.out.println("Invalid choice.");
                return;
        }

        if (results.isEmpty()) {
            System.out.println("No books found.");
        } else {
            System.out.println("\nSearch Results:");
            for (Book book : results) {
                System.out.println(book);
            }
        }
    }

    public void borrowBook(Student student) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter book ID to borrow: ");
        String bookId = scanner.nextLine().trim();

        if (!student.isActive()) {
            System.out.println("Sorry, your account is deactivated. You cannot borrow books.");
            return;
        }

        Book book = findBookById(bookId);
        if (book == null) {
            System.out.println("Book not found.");
            return;
        }

        if (book.isBorrowed()) {
            System.out.println("Sorry, this book is already borrowed.");
            return;
        }

        try {
            System.out.print("Enter start date (yyyy-MM-dd): ");
            LocalDate startDate = LocalDate.parse(scanner.nextLine());
            System.out.print("Enter end date (yyyy-MM-dd): ");
            LocalDate endDate = LocalDate.parse(scanner.nextLine());

            if (endDate.isBefore(startDate)) {
                System.out.println("End date cannot be before start date.");
                return;
            }

            submitBorrowRequest(student, bookId, startDate, endDate);

        } catch (Exception e) {
            System.out.println("Invalid date format. Please use yyyy-MM-dd.");
        }
    }

    public void returnBook(Student student, String bookId) {
        Book book = books.stream()
                .filter(b -> b.getId().equals(bookId) && b.isBorrowed())
                .findFirst()
                .orElse(null);

        if (book == null) {
            System.out.println("Book not found or not borrowed by you.");
            return;
        }

        BorrowRequest request = borrowRequests.stream()
                .filter(r -> r.getBookId().equals(bookId) &&
                        r.getStudentId().equals(student.getStudentId()) &&
                        r.getStatus().equals("BORROWED"))
                .findFirst()
                .orElse(null);

        if (request != null) {
            request.setStatus("RETURNED");

            LocalDate returnDate = LocalDate.now();
            long daysLate = 0;
            if (returnDate.isAfter(request.getEndDate())) {
                daysLate = java.time.temporal.ChronoUnit.DAYS.between(
                        request.getEndDate(), returnDate);
            }

            appendReturnLog(student, book, returnDate, daysLate);
        }

        book.setBorrowed(false);
        book.setStartDate(null);
        book.setEndDate(null);

        saveAllBooks();
        saveAllBorrowRequests();
        System.out.println("Book returned successfully!");
    }

    private void appendBorrowLog(Student student, Book book, LocalDate start, LocalDate end) {
        try (FileWriter writer = new FileWriter(BORROW_LOG, true)) {
            writer.write("BORROW," + student.getStudentId() + "," +
                    student.getUsername() + "," +
                    book.getId() + "," +
                    book.getTitle() + "," +
                    start + "," + end + "\n");
        } catch (IOException e) {
            System.out.println("Error logging borrow: " + e.getMessage());
        }
    }

    private void saveAllBooks() {
        try (FileWriter writer = new FileWriter(BOOK_FILE)) {
            for (Book book : books) {
                writer.write(book.getId() + "," +
                        escapeCsv(book.getTitle()) + "," +
                        escapeCsv(book.getAuthor()) + "," +
                        book.getYear() + "," +
                        book.isBorrowed() + "," +
                        (book.getStartDate() != null ? book.getStartDate() : "") + "," +
                        (book.getEndDate() != null ? book.getEndDate() : "") + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving books: " + e.getMessage());
        }
    }

    private void loadBooksFromFile() {
        books.clear();
        File file = new File(BOOK_FILE);
        if (!file.exists()) return;

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String[] data = scanner.nextLine().split(",", -1);
                if (data.length >= 4) {
                    Book book = new Book(data[0].trim(),
                            data[1].trim(),
                            data[2].trim(),
                            Integer.parseInt(data[3].trim()));

                    if (data.length >= 5) {
                        book.setBorrowed(Boolean.parseBoolean(data[4].trim()));
                    }

                    if (data.length >= 7) {
                        if (!data[5].isEmpty()) book.setStartDate(LocalDate.parse(data[5].trim()));
                        if (!data[6].isEmpty()) book.setEndDate(LocalDate.parse(data[6].trim()));
                    }

                    books.add(book);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading books file: " + e.getMessage());
        }
    }

    public void searchBookByTitleForGuest() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter book title to search: ");
        String title = scanner.nextLine().toLowerCase();

        List<Book> results = books.stream()
                .filter(book -> book.getTitle().toLowerCase().contains(title))
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            System.out.println("No books found.");
        } else {
            System.out.println("\nSearch Results:");
            for (Book book : results) {
                System.out.println("Title: " + book.getTitle() + ", Author: " + book.getAuthor() +
                        ", Year: " + book.getYear() + ", Status: " +
                        (book.isBorrowed() ? "Borrowed" : "Available"));
            }
        }
    }

    public void registerBook() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter book ID: ");
        String id = scanner.nextLine();
        System.out.print("Enter book title: ");
        String title = scanner.nextLine();
        System.out.print("Enter author name: ");
        String author = scanner.nextLine();
        System.out.print("Enter publication year: ");

        int year;
        try {
            year = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Year must be a number.");
            return;
        }

        Book book = new Book(id, title, author, year);
        books.add(book);
        saveAllBooks();
        System.out.println("Book registered successfully!");
    }

    public void editBook() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter book ID to edit: ");
        String id = scanner.nextLine().trim();

        Book book = findBookById(id);
        if (book == null) {
            System.out.println("Book not found.");
            return;
        }

        System.out.println("Editing: " + book);
        System.out.print("New title (leave blank to keep current): ");
        String newTitle = scanner.nextLine();
        if (newTitle != null && !newTitle.trim().isEmpty()) book.setTitle(newTitle);

        System.out.print("New author (leave blank to keep current): ");
        String newAuthor = scanner.nextLine();
        if (newAuthor != null && !newAuthor.trim().isEmpty()) book.setAuthor(newAuthor);

        System.out.print("New year (leave blank to keep current): ");
        String yearInput = scanner.nextLine();
        if (yearInput != null && !yearInput.trim().isEmpty()) {
            try {
                book.setYear(Integer.parseInt(yearInput));
            } catch (NumberFormatException e) {
                System.out.println("Invalid year. Keeping current.");
            }
        }

        saveAllBooks();
        System.out.println("Book updated successfully!");
    }

    public void displayAvailableBooks() {
        List<Book> availableBooks = getAvailableBooks();
        if (availableBooks.isEmpty()) {
            System.out.println("No books available at the moment.");
        } else {
            System.out.println("\nAvailable Books:");
            for (Book book : availableBooks) {
                System.out.println(book);
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace(",", " ");
    }

    public void submitBorrowRequest(Student student, String bookId, LocalDate startDate, LocalDate endDate) {
        String requestId = "REQ_" + System.currentTimeMillis();
        BorrowRequest request = new BorrowRequest(requestId, student.getStudentId(), bookId, startDate, endDate);
        borrowRequests.add(request);
        saveBorrowRequest(request);
        System.out.println("Borrow request submitted successfully! Request ID: " + requestId);
    }

    private void saveBorrowRequest(BorrowRequest request) {
        try (FileWriter writer = new FileWriter(REQUESTS_FILE, true)) {
            writer.write(request.getRequestId() + "," +
                    request.getStudentId() + "," +
                    request.getBookId() + "," +
                    request.getStartDate() + "," +
                    request.getEndDate() + "," +
                    request.getStatus() + "\n");
        } catch (IOException e) {
            System.out.println("Error saving borrow request: " + e.getMessage());
        }
    }

    public void loadBorrowRequests() {
        borrowRequests.clear();
        File file = new File(REQUESTS_FILE);
        if (!file.exists()) return;

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String[] data = scanner.nextLine().split(",", -1);
                if (data.length >= 6) {
                    BorrowRequest request = new BorrowRequest(
                            data[0].trim(), data[1].trim(), data[2].trim(),
                            LocalDate.parse(data[3].trim()), LocalDate.parse(data[4].trim())
                    );
                    request.setStatus(data[5].trim());
                    borrowRequests.add(request);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading borrow requests: " + e.getMessage());
        }
    }

    public void approveBorrowRequests() {
        LocalDate today = LocalDate.now();
        List<BorrowRequest> requestsToApprove = borrowRequests.stream()
                .filter(request -> request.getStatus().equals("PENDING"))
                .filter(request -> request.getStartDate().isEqual(today) || request.getStartDate().isEqual(today.minusDays(1)))
                .collect(Collectors.toList());

        if (requestsToApprove.isEmpty()) {
            System.out.println("No pending requests for today or yesterday.");
            return;
        }

        System.out.println("\n--- Pending Borrow Requests for Approval ---");
        for (BorrowRequest request : requestsToApprove) {
            System.out.println(request);

            Scanner scanner = new Scanner(System.in);
            System.out.print("Approve this request? (Y/N): ");
            String decision = scanner.nextLine().trim().toUpperCase();

            if (decision.equals("Y")) {
                request.setStatus("APPROVED");
                Book book = findBookById(request.getBookId());
                if (book != null) {
                    book.setBorrowed(true);
                    book.setStartDate(LocalDate.now());
                    book.setEndDate(request.getEndDate());
                    request.setStatus("BORROWED");

                    Student student = findStudentById(request.getStudentId());
                    if (student != null) {
                        appendBorrowLog(student, book, LocalDate.now(), request.getEndDate());
                    }
                }
                System.out.println("Request approved successfully!");
            } else {
                request.setStatus("REJECTED");
                System.out.println("Request rejected.");
            }
        }

        saveAllBorrowRequests();
        saveAllBooks();
    }

    private Book findBookById(String bookId) {
        return books.stream()
                .filter(book -> book.getId().equals(bookId))
                .findFirst()
                .orElse(null);
    }

    private Student findStudentById(String studentId) {
        if (studentManager != null) {
            return studentManager.findStudentById(studentId);
        }
        return null;
    }

    private void saveAllBorrowRequests() {
        try (FileWriter writer = new FileWriter(REQUESTS_FILE)) {
            for (BorrowRequest request : borrowRequests) {
                writer.write(request.getRequestId() + "," +
                        request.getStudentId() + "," +
                        request.getBookId() + "," +
                        request.getStartDate() + "," +
                        request.getEndDate() + "," +
                        request.getStatus() + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving borrow requests: " + e.getMessage());
        }
    }

    public void viewStudentStatistics() {
        System.out.println("\n=== Student Statistics Report ===");
        System.out.println("========================================");
        viewAllStudentsSummary();
        displayTopStudentsWithDelays(10);
        System.out.println("========================================");
    }

    private void viewAllStudentsSummary() {
        Map<String, StudentStats> studentStatsMap = new HashMap<>();

        for (BorrowRequest request : borrowRequests) {
            String studentId = request.getStudentId();
            StudentStats stats = studentStatsMap.getOrDefault(studentId, new StudentStats(studentId));

            if (request.getStatus().equals("BORROWED")) {
                stats.notReturnedCount++;
            } else if (request.getStatus().equals("RETURNED")) {
                stats.returnedCount++;

                long daysLate = calculateDaysLate(request);
                if (daysLate > 0) {
                    stats.delayedReturnsCount++;
                    stats.totalDelayDays += daysLate;
                }
            }
            stats.totalBorrows++;

            studentStatsMap.put(studentId, stats);
        }

        System.out.println("\n--- Overall Student Statistics ---");
        System.out.printf("%-15s %-20s %-12s %-15s %-15s %-18s %-15s%n",
                "Student ID", "Name", "Total", "Returned", "Not Returned", "Delayed Returns", "Total Delay Days");
        System.out.println("------------------------------------------------------------------------------------------------------");

        for (StudentStats stats : studentStatsMap.values()) {
            Student student = findStudentById(stats.studentId);
            String studentName = (student != null) ? student.getName() : "Unknown";

            System.out.printf("%-15s %-20s %-12d %-15d %-15d %-18d %-15d%n",
                    stats.studentId, studentName, stats.totalBorrows,
                    stats.returnedCount, stats.notReturnedCount,
                    stats.delayedReturnsCount, stats.totalDelayDays);
        }
    }

    private void displayTopStudentsWithDelays(int limit) {
        Map<String, Long> delayDaysByStudent = new HashMap<>();

        for (BorrowRequest request : borrowRequests) {
            if (request.getStatus().equals("RETURNED")) {
                long daysLate = calculateDaysLate(request);
                if (daysLate > 0) {
                    String studentId = request.getStudentId();
                    delayDaysByStudent.put(studentId, delayDaysByStudent.getOrDefault(studentId, 0L) + daysLate);
                }
            }
        }

        List<Map.Entry<String, Long>> topDelayedStudents = delayDaysByStudent.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .collect(Collectors.toList());

        System.out.println("\n--- Top " + limit + " Students with Most Delay Days ---");
        if (topDelayedStudents.isEmpty()) {
            System.out.println("No students with delayed returns found.");
        } else {
            System.out.printf("%-15s %-20s %-15s%n", "Student ID", "Name", "Total Delay Days");
            System.out.println("-----------------------------------------------");

            for (Map.Entry<String, Long> entry : topDelayedStudents) {
                String studentId = entry.getKey();
                Student student = findStudentById(studentId);
                String studentName = (student != null) ? student.getName() : "Unknown";

                System.out.printf("%-15s %-20s %-15d%n", studentId, studentName, entry.getValue());
            }
        }
    }

    private long calculateDaysLate(BorrowRequest request) {
        if (!request.getStatus().equals("RETURNED") || request.getEndDate() == null) {
            return 0;
        }

        LocalDate returnDate = LocalDate.now();
        if (returnDate.isAfter(request.getEndDate())) {
            return java.time.temporal.ChronoUnit.DAYS.between(
                    request.getEndDate(), returnDate);
        }
        return 0;
    }

    private class StudentStats {
        String studentId;
        int totalBorrows;
        int notReturnedCount;
        int returnedCount;
        int delayedReturnsCount;
        long totalDelayDays;

        public StudentStats(String studentId) {
            this.studentId = studentId;
            this.totalBorrows = 0;
            this.notReturnedCount = 0;
            this.returnedCount = 0;
            this.delayedReturnsCount = 0;
            this.totalDelayDays = 0;
        }
    }

    public void viewIndividualStudentHistory(String studentId) {
        List<BorrowRequest> studentRequests = borrowRequests.stream()
                .filter(request -> request.getStudentId().equals(studentId))
                .collect(Collectors.toList());

        if (studentRequests.isEmpty()) {
            System.out.println("No borrow history found for student ID: " + studentId);
            return;
        }

        Student student = findStudentById(studentId);
        String studentName = (student != null) ? student.getName() : "Unknown";

        long totalBorrows = studentRequests.size();
        long notReturnedCount = studentRequests.stream()
                .filter(request -> request.getStatus().equals("BORROWED"))
                .count();

        long delayedReturnsCount = studentRequests.stream()
                .filter(request -> request.getStatus().equals("RETURNED"))
                .filter(request -> calculateDaysLate(request) > 0)
                .count();

        long totalDelayDays = studentRequests.stream()
                .filter(request -> request.getStatus().equals("RETURNED"))
                .mapToLong(this::calculateDaysLate)
                .sum();

        System.out.println("\n=== Student Borrow History ===");
        System.out.println("Student: " + studentName + " (ID: " + studentId + ")");
        System.out.println("----------------------------------------");
        System.out.println("Total Borrows: " + totalBorrows);
        System.out.println("Not Returned: " + notReturnedCount);
        System.out.println("Delayed Returns: " + delayedReturnsCount);
        System.out.println("Total Delay Days: " + totalDelayDays);
        System.out.println("----------------------------------------");

        System.out.println("\nDetailed Borrow History:");
        for (BorrowRequest request : studentRequests) {
            Book book = findBookById(request.getBookId());
            String bookTitle = (book != null) ? book.getTitle() : "Unknown Book";
            long daysLate = calculateDaysLate(request);

            System.out.println("Book: " + bookTitle +
                    ", Period: " + request.getStartDate() + " to " + request.getEndDate() +
                    ", Status: " + request.getStatus() +
                    (daysLate > 0 ? ", Delay: " + daysLate + " days" : ""));
        }
    }

    public void toggleStudentStatus(Student student) {
        student.setActive(!student.isActive());
        System.out.println("Student " + (student.isActive() ? "activated" : "deactivated") + " successfully.");
    }

    public void receiveReturnedBook(String bookId) {
        Book book = findBookById(bookId);
        if (book == null) {
            System.out.println("Book not found.");
            return;
        }

        if (!book.isBorrowed()) {
            System.out.println("This book is not currently borrowed.");
            return;
        }

        BorrowRequest borrowRequest = borrowRequests.stream()
                .filter(request -> request.getBookId().equals(bookId) &&
                        request.getStatus().equals("BORROWED"))
                .findFirst()
                .orElse(null);

        if (borrowRequest == null) {
            System.out.println("No active borrow request found for this book.");
            return;
        }

        LocalDate returnDate = LocalDate.now();
        long daysLate = 0;

        if (returnDate.isAfter(borrowRequest.getEndDate())) {
            daysLate = java.time.temporal.ChronoUnit.DAYS.between(
                    borrowRequest.getEndDate(), returnDate);
        }

        book.setBorrowed(false);
        book.setStartDate(null);
        book.setEndDate(null);
        borrowRequest.setStatus("RETURNED");

        Student student = findStudentById(borrowRequest.getStudentId());
        if (student != null) {
            appendReturnLog(student, book, returnDate, daysLate);
        }

        saveAllBooks();
        saveAllBorrowRequests();

        System.out.println("Book returned successfully on: " + returnDate);
        if (daysLate > 0) {
            System.out.println("This book was " + daysLate + " days late.");
        }
    }

    private void appendReturnLog(Student student, Book book, LocalDate returnDate, long daysLate) {
        try (FileWriter writer = new FileWriter(BORROW_LOG, true)) {
            writer.write("RETURN," + student.getStudentId() + "," +
                    student.getUsername() + "," +
                    book.getId() + "," +
                    book.getTitle() + "," +
                    returnDate + "," +
                    daysLate + "\n");
        } catch (IOException e) {
            System.out.println("Error logging return: " + e.getMessage());
        }
    }

    public void viewBorrowStatistics() {
        long totalRequests = borrowRequests.size();
        long approvedRequests = borrowRequests.stream()
                .filter(request -> request.getStatus().equals("APPROVED") ||
                        request.getStatus().equals("BORROWED") ||
                        request.getStatus().equals("RETURNED"))
                .count();

        long returnedRequests = borrowRequests.stream()
                .filter(request -> request.getStatus().equals("RETURNED"))
                .count();

        double averageDays = calculateAverageBorrowDays();

        System.out.println("\n=== Borrow Statistics Report ===");
        System.out.println("----------------------------------------");
        System.out.println("Total Borrow Requests: " + totalRequests);
        System.out.println("Total Approved Requests: " + approvedRequests);
        System.out.println("Total Returned Books: " + returnedRequests);
        System.out.printf("Average Borrow Days: %.2f days%n", averageDays);
        System.out.println("----------------------------------------");
    }

    private double calculateAverageBorrowDays() {
        List<BorrowRequest> returnedRequests = borrowRequests.stream()
                .filter(request -> request.getStatus().equals("RETURNED"))
                .collect(Collectors.toList());

        if (returnedRequests.isEmpty()) {
            return 0.0;
        }

        long totalDays = 0;
        for (BorrowRequest request : returnedRequests) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    request.getStartDate(), request.getEndDate());
            totalDays += days;
        }

        return (double) totalDays / returnedRequests.size();
    }
}
