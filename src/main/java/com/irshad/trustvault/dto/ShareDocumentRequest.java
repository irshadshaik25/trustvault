
package com.irshad.trustvault.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ShareDocumentRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Enter a valid recipient email")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @NotBlank(message = "Permission is required")
    @Pattern(
        regexp = "VIEWER|EDITOR",
        message = "Permission must be VIEWER or EDITOR"
    )
    private String permission;

    public ShareDocumentRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }
}
