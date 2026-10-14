package LibraryUniversity;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StaffManager {
    private List<Staff> staffList = new ArrayList<>();

    public StaffManager() {
        loadStaffFromDatabase();
        if (staffList.isEmpty()) {
            loadDefaultStaff();
            saveAllStaff();
        }
        addDefaultManager();
    }

    private void addDefaultManager() {
        boolean managerExists = staffList.stream()
                .anyMatch(staff -> staff.getUsername().equals("manager"));

        if (!managerExists) {
            Staff manager = new Staff("System Manager", "M001", "manager", "admin123");
            staffList.add(manager);
            saveAllStaff();
        }
    }

    private void loadDefaultStaff() {
        staffList.add(new Staff("Abdi", "1", "staff1", "1234"));
        staffList.add(new Staff("Rad", "2", "staff2", "1383"));
    }

    public Staff authenticateStaff(String username, String password) {
        return staffList.stream()
                .filter(staff -> staff.getUsername().equals(username) && staff.getPassword().equals(password))
                .findFirst()
                .orElse(null);
    }

    public void changePassword(Staff staff, String newPassword) {
        staffList.stream()
                .filter(s -> s.getUsername().equals(staff.getUsername()))
                .findFirst()
                .ifPresent(s -> s.setPassword(newPassword));

        saveAllStaff();
        System.out.println("Password changed successfully!");
    }

    private void loadStaffFromDatabase() {
        staffList.clear();
        String sql = "SELECT * FROM staff";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Staff staff = new Staff(
                        rs.getString("name"),
                        rs.getString("staff_id"),
                        rs.getString("username"),
                        rs.getString("password"));
                staffList.add(staff);
            }
        } catch (SQLException e) {
            System.out.println("Error reading staff: " + e.getMessage());
        }
    }

    private void saveAllStaff() {
        String sql = "INSERT INTO staff(staff_id, name, username, password) VALUES(?,?,?,?) " +
                "ON CONFLICT(staff_id) DO UPDATE SET name = excluded.name, " +
                "username = excluded.username, password = excluded.password";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Staff staff : staffList) {
                pstmt.setString(1, staff.getStaffId());
                pstmt.setString(2, staff.getName());
                pstmt.setString(3, staff.getUsername());
                pstmt.setString(4, staff.getPassword());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Error saving staff: " + e.getMessage());
        }
    }

    public void registerStaff(String name, String staffId, String username, String password) {
        if (isUsernameTaken(username)) {
            System.out.println("Username already exists! Choose a different one.");
            return;
        }

        if (password == null || password.length() < 4) {
            System.out.println("Password must be at least 4 characters long.");
            return;
        }

        Staff staff = new Staff(name, staffId, username, password);
        staffList.add(staff);
        saveAllStaff();
        System.out.println("Staff registered successfully!");
    }

    private boolean isUsernameTaken(String username) {
        return staffList.stream()
                .anyMatch(staff -> staff.getUsername().equalsIgnoreCase(username));
    }

    public Staff authenticateManager(String username, String password) {
        return staffList.stream()
                .filter(staff -> staff.getUsername().equals("manager") &&
                        staff.getPassword().equals(password))
                .findFirst()
                .orElse(null);
    }
}
