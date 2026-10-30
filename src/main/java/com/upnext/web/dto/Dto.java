package com.upnext.web.dto;

import com.upnext.domain.Role;
import com.upnext.domain.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire shapes that are not read models. Names and limits match openapi/upnext-openapi.yaml. */
public final class Dto {
    private Dto() {}

    public record Meta(String productName, boolean demoMode, Instant serverTime, String version) {}
    public record UserView(UUID id, String displayName, Role role) {}
    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserView user) {}
    public record FieldError(String field, String message) {}

    public record SignupRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 160) String email,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 100, message = "Password must be 8 to 100 characters") String password,
            @NotBlank(message = "Name is required") @Size(min = 2, max = 80, message = "Name must be 2 to 80 characters") String displayName) {
        @Override public String toString() { return "SignupRequest[email=" + email + "]"; }
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Size(max = 160) String email,
            @NotBlank(message = "Password is required") @Size(max = 100) String password) {
        @Override public String toString() { return "LoginRequest[email=" + email + "]"; }
    }

    public record CreateRequest(
            @NotBlank(message = "Title is required") @Size(min = 3, max = 160, message = "Title must be 3 to 160 characters") String title,
            @NotBlank(message = "Description is required") @Size(min = 10, max = 4000, message = "Description must be 10 to 4000 characters") String description,
            @Size(max = 5, message = "At most 5 tags") List<@Pattern(regexp = "^[A-Za-z0-9-]{1,24}$", message = "Tags use letters, numbers and hyphens, up to 24 characters") String> tags) {}

    public record MoveRequest(
            @NotNull(message = "status is required") Status status,
            @NotNull(message = "expectedVersion is required") @Min(value = 0, message = "expectedVersion must be 0 or more") Integer expectedVersion) {}
}
