package com.first.dto;

public class CallHistoryDto {
    private String callId;
    private String userId; // Owner UID for per-user history filtering
    private String callerName;
    private String calleeName;
    private String duration;
    private long timestamp;

    public CallHistoryDto() {}

    public CallHistoryDto(String callId, String userId, String callerName, String calleeName, String duration, long timestamp) {
        this.callId = callId;
        this.userId = userId;
        this.callerName = callerName;
        this.calleeName = calleeName;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    public CallHistoryDto(String callId, String callerName, String calleeName, String duration, long timestamp) {
        this(callId, null, callerName, calleeName, duration, timestamp);
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }

    public String getCalleeName() { return calleeName; }
    public void setCalleeName(String calleeName) { this.calleeName = calleeName; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
