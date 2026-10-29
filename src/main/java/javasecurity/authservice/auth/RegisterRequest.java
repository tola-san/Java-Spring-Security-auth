package javasecurity.authservice.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {}
