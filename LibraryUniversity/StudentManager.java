package LibraryUniversity;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StudentManager {
    private List<Student> students = new ArrayList<>();

    public StudentManager() {
        loadStudentsFromDatabase();
    }

    public void registerStudent(String name, String studentId, String username, String password) {
        if (isUsernameTaken(username)) {
            System.out.println("This username already exists. Please choose a different username.");
            return;
        }
        Student student = new Student(name, studentId, username, password);
        students.add(student);
        saveStudent(student);
        System.out.println("Student registered successfully!");
    }

    private boolean isUsernameTaken(String username) {
        if (username == null) return false;
        for (Student student : students) {
            if (student.getUsername().equalsIgnoreCase(username.trim())) {
                return true;
            }
        }
        return false;
    }

    public int getStudentCount() {
        return students.size();
    }

    private void saveStudent(Student student) {
        String sql = "INSERT INTO students(student_id, name, username, password, active) VALUES(?,?,?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, student.getStudentId());
            pstmt.setString(2, student.getName());
            pstmt.setString(3, student.getUsername());
            pstmt.setString(4, student.getPassword());
            pstmt.setInt(5, student.isActive() ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving student: " + e.getMessage());
        }
    }

    private void loadStudentsFromDatabase() {
        students.clear();
        String sql = "SELECT * FROM students";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Student student = new Student(
                        rs.getString("name"),
                        rs.getString("student_id"),
                        rs.getString("username"),
                        rs.getString("password"));
                student.setActive(rs.getInt("active") == 1);
                students.add(student);
            }
        } catch (SQLException e) {
            System.out.println("Error reading students: " + e.getMessage());
        }
    }

    public Student authenticateStudent(String username, String password) {
        if (username == null) return null;
        for (Student student : students) {
            if (student.getUsername().equals(username.trim()) && student.getPassword().equals(password)) {
                return student;
            }
        }
        return null;
    }

    private void saveAllStudents() {
        String sql = "INSERT INTO students(student_id, name, username, password, active) VALUES(?,?,?,?,?) " +
                "ON CONFLICT(student_id) DO UPDATE SET name = excluded.name, " +
                "username = excluded.username, password = excluded.password, active = excluded.active";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Student student : students) {
                pstmt.setString(1, student.getStudentId());
                pstmt.setString(2, student.getName());
                pstmt.setString(3, student.getUsername());
                pstmt.setString(4, student.getPassword());
                pstmt.setInt(5, student.isActive() ? 1 : 0);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Error saving students: " + e.getMessage());
        }
    }

    public void updateStudent(Student student, String newName, String newPassword) {
        for (Student s : students) {
            if (s.getUsername().equals(student.getUsername())) {
                if (newName != null && !newName.trim().isEmpty()) {
                    s.setName(newName);
                }
                if (newPassword != null && !newPassword.trim().isEmpty()) {
                    s.setPassword(newPassword);
                }
                saveAllStudents();
                break;
            }
        }
    }

    public Student findStudentById(String studentId) {
        for (Student student : students) {
            if (student.getStudentId().equals(studentId)) {
                return student;
            }
        }
        return null;
    }

}
