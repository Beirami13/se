package LibraryUniversity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:library.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initialize() {
        String[] tableStatements = {
            "CREATE TABLE IF NOT EXISTS books (" +
                "id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "author TEXT, " +
                "year INTEGER, " +
                "borrowed INTEGER, " +
                "start_date TEXT, " +
                "end_date TEXT)",

            "CREATE TABLE IF NOT EXISTS students (" +
                "student_id TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "username TEXT UNIQUE, " +
                "password TEXT, " +
                "active INTEGER)",

            "CREATE TABLE IF NOT EXISTS staff (" +
                "staff_id TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "username TEXT UNIQUE, " +
                "password TEXT)",

            "CREATE TABLE IF NOT EXISTS borrow_requests (" +
                "request_id TEXT PRIMARY KEY, " +
                "student_id TEXT, " +
                "book_id TEXT, " +
                "start_date TEXT, " +
                "end_date TEXT, " +
                "status TEXT, " +
                "FOREIGN KEY (student_id) REFERENCES students(student_id), " +
                "FOREIGN KEY (book_id) REFERENCES books(id))",

            "CREATE TABLE IF NOT EXISTS borrow_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT, " +
                "student_id TEXT, " +
                "username TEXT, " +
                "book_id TEXT, " +
                "book_title TEXT, " +
                "date1 TEXT, " +
                "date2 TEXT)"
        };

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : tableStatements) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }
}
