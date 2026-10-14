package LibraryUniversity;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class StudentManager {
    private List<Student> students = new ArrayList<>();
    private static final String STUDENT_FILE = "students.txt";

    public StudentManager() {
        loadStudentsFromFile();
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
        try (FileWriter writer = new FileWriter(STUDENT_FILE, true)) {
            writer.write(escapeCsv(student.getName()) + "," +
                    escapeCsv(student.getStudentId()) + "," +
                    escapeCsv(student.getUsername()) + "," +
                    escapeCsv(student.getPassword()) + "," +
                    student.isActive() + "\n");
        } catch (IOException e) {
            System.out.println("Error saving student: " + e.getMessage());
        }
    }

    private void loadStudentsFromFile() {
        students.clear();
        File file = new File(STUDENT_FILE);
        if (!file.exists()) return;

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(",", -1);
                if (parts.length >= 4) {
                    Student student = new Student(
                            parts[0].trim(),
                            parts[1].trim(),
                            parts[2].trim(),
                            parts[3].trim()
                    );

                    if (parts.length >= 5) {
                        student.setActive(Boolean.parseBoolean(parts[4].trim()));
                    }

                    students.add(student);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading students file: " + e.getMessage());
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

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace(",", " ");
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

    private void saveAllStudents() {
        try (FileWriter writer = new FileWriter(STUDENT_FILE)) {
            for (Student student : students) {
                writer.write(escapeCsv(student.getName()) + "," +
                        escapeCsv(student.getStudentId()) + "," +
                        escapeCsv(student.getUsername()) + "," +
                        escapeCsv(student.getPassword()) + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving students: " + e.getMessage());
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
