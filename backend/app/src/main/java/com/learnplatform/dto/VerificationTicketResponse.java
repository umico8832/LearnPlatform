package com.learnplatform.dto;

public class VerificationTicketResponse {
    private String verificationTicket;
    private long expiresIn;

    public VerificationTicketResponse(String verificationTicket, long expiresIn) {
        this.verificationTicket = verificationTicket;
        this.expiresIn = expiresIn;
    }

    public String getVerificationTicket() { return verificationTicket; }
    public long getExpiresIn() { return expiresIn; }
}
