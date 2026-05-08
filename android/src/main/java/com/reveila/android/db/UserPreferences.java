package com.reveila.android.db;

/**
 * Java implementation of user preference data.
 * Models the legal and configuration state of the Sovereign Engine.
 */
public class UserPreferences {
    private final int id;
    private final boolean userAgreementAccepted;
    private final Long acceptanceTimestamp;
    private final String acceptanceIpOrMachineId;

    public UserPreferences(int id, boolean userAgreementAccepted, Long acceptanceTimestamp, String acceptanceIpOrMachineId) {
        this.id = id;
        this.userAgreementAccepted = userAgreementAccepted;
        this.acceptanceTimestamp = acceptanceTimestamp;
        this.acceptanceIpOrMachineId = acceptanceIpOrMachineId;
    }

    // Standard constructor for new preferences
    public UserPreferences(boolean userAgreementAccepted, Long acceptanceTimestamp, String acceptanceIpOrMachineId) {
        this(1, userAgreementAccepted, acceptanceTimestamp, acceptanceIpOrMachineId);
    }

    public int getId() { return id; }
    public boolean isUserAgreementAccepted() { return userAgreementAccepted; }
    public Long getAcceptanceTimestamp() { return acceptanceTimestamp; }
    public String getAcceptanceIpOrMachineId() { return acceptanceIpOrMachineId; }
}