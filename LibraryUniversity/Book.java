package LibraryUniversity;

import java.time.LocalDate;

public class Book implements Library {
    private String id;
    private String title;
    private String author;
    private int year;
    private boolean borrowed;
    private LocalDate startDate;
    private LocalDate endDate;

    public Book(String id, String title, String author, int year) {
        this.id = id != null ? id.trim() : "";
        this.title = title != null ? title.trim() : "";
        this.author = author != null ? author.trim() : "";
        this.year = year;
        this.borrowed = false;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public int getYear() { return year; }
    public boolean isBorrowed() { return borrowed; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    public void setTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }
    }

    public void setAuthor(String author) {
        if (author != null && !author.trim().isEmpty()) {
            this.author = author.trim();
        }
    }

    public void setYear(int year) { this.year = year; }
    public void setBorrowed(boolean borrowed) { this.borrowed = borrowed; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    @Override
    public String getDisplayInfo() {
        return "ID: " + id + " | Title: " + title + " | Author: " + author +
                " | Year: " + year + " | Status: " + (borrowed ? "Borrowed" : "Available");
    }

    @Override
    public boolean isAvailable() {
        return !borrowed;
    }

    @Override
    public String toString() {
        return getDisplayInfo();
    }
}