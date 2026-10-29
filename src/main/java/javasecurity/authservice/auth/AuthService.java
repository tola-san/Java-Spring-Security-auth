package javasecurity.authservice.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    /*
     * Constructor injection:
     * Spring automatically provides these dependencies.
     * Using final fields makes dependencies immutable after construction.
     */
    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    /*
     * Normalize emails consistently so that email comparisons
     * are not affected by differences in letter casing.
     *
     * Locale.ROOT avoids locale-specific case conversion issues.
     */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void register(RegisterRequest request) {

        /*
         * BCrypt only processes up to 72 UTF-8 bytes.
         * A Java String's character count is not the same as its
         * UTF-8 byte length, especially for non-ASCII characters.
         *
         * Reject oversized passwords instead of silently truncating them.
         */
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password exceeds 72 UTF-8 bytes"
            );
        }

        // Normalize before checking or saving to ensure consistent email storage.
        String email = normalizeEmail(request.email());

        /*
         * Early check improves usability by detecting existing accounts.
         * However, it cannot prevent concurrent registration requests
         * from attempting to insert the same email.
         */
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        try {
            /*
             * NEVER store plaintext passwords.
             * PasswordEncoder generates a secure password hash.
             *
             * saveAndFlush() sends pending SQL changes to the database
             * now, helping surface unique-constraint violations here.
             */
            users.saveAndFlush(
                    new User(email, passwords.encode(request.password()))
            );

        } catch (DataIntegrityViolationException ex) {

            /*
             * The database unique constraint is the final protection
             * against duplicate emails, including concurrent requests.
             *
             * Return HTTP 409 Conflict when the email already exists.
             * In production, inspect the database constraint violation
             * before translating every integrity error into this response.
             */
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }
    }
}
