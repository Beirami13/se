package LibraryUniversity;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class StaffManager {
    private List<Staff> staffList = new ArrayList<>();
    private static final String STAFF_FILE = "staff.txt";

    public StaffManager() {
        loadStaffFromFile();
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

    private void loadStaffFromFile() {
        staffList.clear();
        File file = new File(STAFF_FILE);
        if (!file.exists()) return;

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String[] data = scanner.nextLine().split(",");
                if (data.length == 4) {
                    Staff staff = new Staff(data[0], data[1], data[2], data[3]);
                    staffList.add(staff);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading staff file: " + e.getMessage());
        }
    }

    private void saveAllStaff() {
        try (FileWriter writer = new FileWriter(STAFF_FILE)) {
            for (Staff staff : staffList) {
                writer.write(staff.getName() + "," +
                        staff.getStaffId() + "," +
                        staff.getUsername() + "," +
                        staff.getPassword() + "\n");
            }
        } catch (IOException e) {
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
