package javasecurity.authservice.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;




@RestController
public class AuthController {
    private final AuthService service;
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository repository;

    public AuthController(AuthService service, AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository repository) {
        this.service = service;
        this.authenticationManager = authenticationManager;
        this.repository = repository;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }

    @GetMapping("/auth/csrf")
    public Map<String, Object> csrf(CsrfToken token) {
        return Map.of("status", 200, "message", "CSRF token retrieved",
                "token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        service.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", 201, "message", "Registration successful"));
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
        }
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            AuthService.normalizeEmail(body.email()), body.password()));
            new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            repository.saveContext(context, request, response);
            // Discard the pre-login CSRF token. Fetch /auth/csrf again after login.
            new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
            return Map.of("status", 200, "message", "Login successful", "email", authentication.getName());
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(Principal principal) {
        return Map.of("status", 200, "message", "Authenticated user retrieved", "email", principal.getName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validationError(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, second) -> first));
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400, "message", "Validation failed", "errors", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedJson() {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400, "message", "Invalid JSON body. Provide email and password as strings."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> requestError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "message", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }
}
