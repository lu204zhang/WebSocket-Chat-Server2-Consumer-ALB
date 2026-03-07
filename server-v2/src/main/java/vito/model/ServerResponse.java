package vito.model;

import java.time.Instant;

public class ServerResponse {
    private String status;
    private Instant serverTimestamp;
    private String message;

    public ServerResponse(String status, Instant serverTimestamp, String message) {
        this.status = status;
        this.serverTimestamp = serverTimestamp;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(Instant serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
