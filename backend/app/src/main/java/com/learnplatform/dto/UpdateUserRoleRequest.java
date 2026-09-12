package com.learnplatform.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserRoleRequest {

    @NotBlank(message = "角色不能为空")
    private String role;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
