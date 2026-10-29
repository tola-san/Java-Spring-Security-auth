package javasecurity.authservice.config;

import java.util.List;
import javasecurity.authservice.auth.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .map(user -> new org.springframework.security.core.userdetails.User(
                        user.getEmail(), user.getPasswordHash(), List.of()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            HttpSessionSecurityContextRepository repository) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/csrf", "/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(repository))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeMessage(response, 401, "Authentication required. Please log in."))
                        .accessDeniedHandler((request, response, ex) -> writeMessage(response, 403,
                                ex instanceof org.springframework.security.web.csrf.CsrfException
                                        ? "Missing or invalid CSRF token. Fetch /auth/csrf and send the token with your session cookie."
                                        : "Access denied")))
                .logout(logout -> logout.logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, auth) ->
                                writeMessage(response, 200, "Logout successful")))
                .build();
    }

    // Messages here are fixed server strings, never request input.
    private static void writeMessage(jakarta.servlet.http.HttpServletResponse response,
            int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
