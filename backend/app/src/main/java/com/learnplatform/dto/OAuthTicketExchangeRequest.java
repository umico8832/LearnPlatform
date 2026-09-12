package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class OAuthTicketExchangeRequest {
    @NotBlank(message = "登录票据不能为空")
    private String ticket;

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }
}
