package com.first.dto;

public class AnnouncementDto {
    private String id;
    private String message;
    private String type;
    private boolean active;
    private long timestamp;

    public AnnouncementDto() {}

    public AnnouncementDto(String id, String message, String type, boolean active, long timestamp) {
        this.id = id;
        this.message = message;
        this.type = type;
        this.active = active;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
