package com.first.dto;

public class CallRequestDto {
    private String callId;
    private String callerName;
    private String callerUid;
    private String calleeName;
    private String calleeUid;
    private String status; // "ringing", "accepted", "rejected", "ended"
    private String channelName;
    private long timestamp;

    public CallRequestDto() {}

    public CallRequestDto(String callId, String callerName, String callerUid, String calleeName, String calleeUid, String status, String channelName, long timestamp) {
        this.callId = callId;
        this.callerName = callerName;
        this.callerUid = callerUid;
        this.calleeName = calleeName;
        this.calleeUid = calleeUid;
        this.status = status;
        this.channelName = channelName;
        this.timestamp = timestamp;
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }

    public String getCallerUid() { return callerUid; }
    public void setCallerUid(String callerUid) { this.callerUid = callerUid; }

    public String getCalleeName() { return calleeName; }
    public void setCalleeName(String calleeName) { this.calleeName = calleeName; }

    public String getCalleeUid() { return calleeUid; }
    public void setCalleeUid(String calleeUid) { this.calleeUid = calleeUid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
