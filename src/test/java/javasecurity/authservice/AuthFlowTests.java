package javasecurity.authservice;

import java.net.*;
import java.net.http.*;
import java.util.regex.Pattern;
import javasecurity.authservice.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    @Test
    void registrationLoginSessionAndLogout() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String body = "{\"email\":\"Learner@example.com\",\"password\":\"learning-password\"}";
        assertEquals(401, send(client, "GET", "/auth/me", null, null).statusCode());
        assertEquals(403, send(client, "POST", "/auth/register", body, null).statusCode());
        String token = token(client);
        var malformed = send(client, "POST", "/auth/login", "{", token);
        assertEquals(400, malformed.statusCode());
        assertTrue(malformed.body().contains("Invalid JSON body"));
        var missingPassword = send(client, "POST", "/auth/login",
                "{\"email\":\"test@example.com\"}", token);
        assertEquals(400, missingPassword.statusCode());
        assertTrue(missingPassword.body().contains("Validation failed"));
        assertTrue(missingPassword.body().contains("password"));
        assertEquals(400, send(client, "POST", "/auth/register",
                "{\"email\":\"invalid\",\"password\":\"short\"}", token).statusCode());
        var registered = send(client, "POST", "/auth/register", body, token);
        assertEquals(201, registered.statusCode());
        assertTrue(registered.body().contains("Registration successful"));
        var user = users.findByEmail("learner@example.com").orElseThrow();
        assertNotEquals("learning-password", user.getPasswordHash());
        assertTrue(passwords.matches("learning-password", user.getPasswordHash()));
        assertEquals(409, send(client, "POST", "/auth/register", body, token).statusCode());
        assertEquals(401, send(client, "POST", "/auth/login",
                body.replace("learning-password", "wrong-password"), token).statusCode());
        String oldSession = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var loggedIn = send(client, "POST", "/auth/login", body, token);
        assertEquals(200, loggedIn.statusCode());
        assertTrue(loggedIn.body().contains("Login successful"));
        String newSession = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertNotEquals(oldSession, newSession);
        var me = send(client, "GET", "/auth/me", null, null);
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("learner@example.com"));
        assertFalse(me.body().contains("password"));
        assertEquals(403, send(client, "POST", "/auth/logout", "", token).statusCode());
        var loggedOut = send(client, "POST", "/auth/logout", "", token(client));
        assertEquals(200, loggedOut.statusCode());
        assertTrue(loggedOut.body().contains("Logout successful"));
        assertEquals(401, send(client, "GET", "/auth/me", null, null).statusCode());
    }

    private String token(HttpClient client) throws Exception {
        var result = send(client, "GET", "/auth/csrf", null, null);
        assertEquals(200, result.statusCode());
        var matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(result.body());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private HttpResponse<String> send(HttpClient client, String method, String path,
            String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("X-CSRF-TOKEN", token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
