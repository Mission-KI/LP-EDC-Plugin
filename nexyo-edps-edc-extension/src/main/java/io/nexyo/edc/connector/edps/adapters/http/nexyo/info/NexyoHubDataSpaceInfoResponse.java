package io.nexyo.edc.connector.edps.adapters.http.nexyo.info;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public class NexyoHubDataSpaceInfoResponse {
    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("participantCount")
    private int participantCount;

    @JsonProperty("isRestricted")
    private boolean isRestricted;

    @JsonProperty("participantDIDs")
    private String[] participantDIDs;

    public NexyoHubDataSpaceInfoResponse() {
    }

    public NexyoHubDataSpaceInfoResponse(String title, String description, int participantCount, boolean isRestricted, String[] participantDIDs) {
        this.title = title;
        this.description = description;
        this.participantCount = participantCount;
        this.isRestricted = isRestricted;
        this.participantDIDs = participantDIDs;
    }

    // Getters and setters for each property

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public boolean isRestricted() {
        return isRestricted;
    }

    public void setRestricted(boolean restricted) {
        isRestricted = restricted;
    }

    public String[] getParticipantDIDs() {
        return participantDIDs;
    }

    public void setParticipantDIDs(String[] participantDIDs) {
        this.participantDIDs = participantDIDs;
    }

    // Override toString() for debugging purposes
    @Override
    public String toString() {
        return "NexyoHubDataSpaceInfo{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", participantCount=" + participantCount +
                ", isRestricted=" + isRestricted +
                ", participantDIDs=" + Arrays.toString(participantDIDs) +
                '}';
    }


}
