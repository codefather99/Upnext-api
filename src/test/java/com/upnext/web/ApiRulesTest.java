package com.upnext.web;

import com.upnext.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talks to the running service over real HTTP, as a hostile or buggy client would. Every test must FAIL if the rule it
 * checks is removed. Runs against a real Postgres (Testcontainers) with the demo seed loaded.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiRulesTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ------------------------------------------------------------------ helpers
    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), "expected " + regex + " in " + text);
        return m.group(1);
    }

    private String login(String email, String password) throws Exception {
        HttpResponse<String> r = send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        assertEquals(200, r.statusCode(), r.body());
        return find(r.body(), "\"accessToken\":\"([^\"]+)\"");
    }

    private String admin() throws Exception { return login("admin@upnext.demo", "Demo-Pass-1"); }

    private String newUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.example";
        HttpResponse<String> r = send("POST", "/api/v1/auth/signup",
                "{\"email\":\"" + email + "\",\"password\":\"a-long-password\",\"displayName\":\"Test User\"}", null);
        assertEquals(201, r.statusCode(), r.body());
        return find(r.body(), "\"accessToken\":\"([^\"]+)\"");
    }

    private String newRequest(String token) throws Exception {
        HttpResponse<String> r = send("POST", "/api/v1/requests",
                "{\"title\":\"Test request " + UUID.randomUUID() + "\",\"description\":\"A description that is long enough to pass.\",\"tags\":[\"Testing\",\"ui\"]}", token);
        assertEquals(201, r.statusCode(), r.body());
        return find(r.body(), "\"id\":\"([0-9a-f-]{36})\"");
    }

    private <T> List<Future<T>> inParallel(int n, Callable<T> task) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) fs.add(pool.submit(() -> { go.await(); return task.call(); }));
        go.countDown();
        pool.shutdown();
        return fs;
    }

    // ------------------------------------------------------------------ visitors and access (the wrong-role test)
    @Test
    void visitors_can_browse_and_read_the_roadmap_but_not_participate() throws Exception {
        HttpResponse<String> list = send("GET", "/api/v1/requests?size=50", null, null);
        assertEquals(200, list.statusCode());
        assertTrue(Long.parseLong(find(list.body(), "\"totalItems\":(\\d+)")) >= 40, "the demo seed must provide at least 40 requests");
        assertEquals(200, send("GET", "/api/v1/roadmap", null, null).statusCode());
        assertEquals(401, send("POST", "/api/v1/requests", "{\"title\":\"x\",\"description\":\"y\"}", null).statusCode());
        assertEquals(401, send("PUT", "/api/v1/requests/" + UUID.randomUUID() + "/vote", null, null).statusCode());
        assertEquals(401, send("GET", "/api/v1/board", null, null).statusCode());
    }

    @Test
    void a_plain_user_cannot_move_cards_or_open_the_board_but_an_admin_can() throws Exception {
        String user = newUser();
        String id = newRequest(user);
        HttpResponse<String> denied = send("PUT", "/api/v1/requests/" + id + "/status", "{\"status\":\"PLANNED\",\"expectedVersion\":0}", user);
        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("\"code\":\"FORBIDDEN\""), denied.body());
        assertEquals(403, send("GET", "/api/v1/board", null, user).statusCode());
        assertEquals(403, send("GET", "/api/v1/requests/" + id + "/status-history", null, user).statusCode());
        assertEquals(200, send("GET", "/api/v1/board", null, admin()).statusCode());
    }

    // ------------------------------------------------------------------ accounts and validation
    @Test
    void duplicate_email_in_another_letter_case_is_refused_and_login_works() throws Exception {
        String email = "Case-" + UUID.randomUUID() + "@Test.Example";
        String body = "{\"email\":\"%s\",\"password\":\"a-long-password\",\"displayName\":\"Case Test\"}";
        assertEquals(201, send("POST", "/api/v1/auth/signup", String.format(body, email), null).statusCode());
        HttpResponse<String> dup = send("POST", "/api/v1/auth/signup", String.format(body, email.toLowerCase()), null);
        assertEquals(409, dup.statusCode());
        assertTrue(dup.body().contains("EMAIL_TAKEN"), dup.body());
        assertNotNull(login(email.toLowerCase(), "a-long-password"));
        assertEquals(401, send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"wrong\"}", null).statusCode());
    }

    @Test
    void invalid_input_is_a_validation_error_with_field_messages() throws Exception {
        HttpResponse<String> r = send("POST", "/api/v1/requests", "{\"title\":\"ab\",\"description\":\"short\"}", newUser());
        assertEquals(422, r.statusCode());
        assertTrue(r.body().contains("\"code\":\"VALIDATION_FAILED\"") && r.body().contains("fieldErrors") && r.body().contains("title"), r.body());
    }

    // ------------------------------------------------------------------ RULE 1: one vote per user per request
    @Test
    void voting_twice_and_withdrawing_twice_are_both_harmless() throws Exception {
        String user = newUser();
        String id = newRequest(user);
        String path = "/api/v1/requests/" + id + "/vote";
        send("PUT", path, null, user);
        HttpResponse<String> second = send("PUT", path, null, user);
        assertEquals(200, second.statusCode());
        assertEquals("1", find(second.body(), "\"voteCount\":(\\d+)"));
        send("DELETE", path, null, user);
        HttpResponse<String> gone = send("DELETE", path, null, user);
        assertEquals(200, gone.statusCode());
        assertEquals("0", find(gone.body(), "\"voteCount\":(\\d+)"));
        assertTrue(gone.body().contains("\"viewerHasVoted\":false"));
    }

    @Test
    void eight_simultaneous_votes_from_one_user_record_exactly_one_vote() throws Exception {
        String user = newUser();
        String id = newRequest(user);
        List<Future<HttpResponse<String>>> fs = inParallel(8, () -> send("PUT", "/api/v1/requests/" + id + "/vote", null, user));
        for (Future<HttpResponse<String>> f : fs) assertEquals(200, f.get(30, TimeUnit.SECONDS).statusCode());
        HttpResponse<String> one = send("GET", "/api/v1/requests/" + id, null, user);
        assertEquals("1", find(one.body(), "\"voteCount\":(\\d+)"), "one user, one vote, however many taps: " + one.body());
    }

    @Test
    void voting_is_refused_once_a_request_is_completed() throws Exception {
        String user = newUser();
        String id = newRequest(user);
        assertEquals(200, send("PUT", "/api/v1/requests/" + id + "/status", "{\"status\":\"COMPLETED\",\"expectedVersion\":0}", admin()).statusCode());
        HttpResponse<String> r = send("PUT", "/api/v1/requests/" + id + "/vote", null, user);
        assertEquals(409, r.statusCode());
        assertTrue(r.body().contains("\"code\":\"VOTING_CLOSED\""), r.body());
    }

    // ------------------------------------------------------------------ RULE 2: two admins, one card
    @Test
    void two_admins_moving_one_card_at_once_one_wins_and_the_other_sees_the_true_card() throws Exception {
        String id = newRequest(newUser());
        String admin = admin();
        String[] targets = {"PLANNED", "IN_PROGRESS"};
        List<Future<HttpResponse<String>>> fs = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        for (String t : targets)
            fs.add(pool.submit(() -> { go.await(); return send("PUT", "/api/v1/requests/" + id + "/status",
                    "{\"status\":\"" + t + "\",\"expectedVersion\":0}", admin); }));
        go.countDown();
        pool.shutdown();
        int won = 0, stale = 0;
        for (Future<HttpResponse<String>> f : fs) {
            HttpResponse<String> r = f.get(30, TimeUnit.SECONDS);
            if (r.statusCode() == 200) won++;
            else if (r.statusCode() == 409 && r.body().contains("\"code\":\"STALE_VERSION\"")) {
                stale++;
                assertTrue(r.body().contains("\"current\""), "the refusal must carry the card as it truly is: " + r.body());
            } else fail("unexpected " + r.statusCode() + ": " + r.body());
        }
        assertEquals(1, won, "exactly one admin may win");
        assertEquals(1, stale);
        assertEquals("1", find(send("GET", "/api/v1/requests/" + id, null, null).body(), "\"version\":(\\d+)"), "the card moved exactly once");
    }

    @Test
    void roadmap_board_and_history_agree_after_a_move() throws Exception {
        String user = newUser();
        String id = newRequest(user);
        String admin = admin();
        assertEquals(200, send("PUT", "/api/v1/requests/" + id + "/status", "{\"status\":\"PLANNED\",\"expectedVersion\":0}", admin).statusCode());
        assertEquals(200, send("PUT", "/api/v1/requests/" + id + "/status", "{\"status\":\"PLANNED\",\"expectedVersion\":1}", admin).statusCode(),
                "moving to the status it already has is a no-op");
        assertTrue(send("GET", "/api/v1/roadmap", null, null).body().contains(id), "a PLANNED request is on the public roadmap");
        String history = send("GET", "/api/v1/requests/" + id + "/status-history", null, admin).body();
        assertTrue(history.contains("\"toStatus\":\"PLANNED\"") && history.contains("(admin)"), "history names who moved it: " + history);
        assertEquals(1, history.split("\"toStatus\"", -1).length - 1, "the no-op must not add a history row");
    }

    // ------------------------------------------------------------------ browse
    @Test
    void filters_and_the_seed_shapes_work() throws Exception {
        String completed = send("GET", "/api/v1/requests?status=COMPLETED&size=50", null, null).body();
        assertFalse(completed.contains("\"status\":\"OPEN\""), "status filter must only return COMPLETED");
        assertEquals(200, send("GET", "/api/v1/requests?tag=ui&sort=newest", null, null).statusCode());
        assertTrue(send("GET", "/api/v1/requests?q=quiet%20hours", null, null).body().contains("quiet hours"), "the seeded 160-character title is findable");
        assertTrue(send("GET", "/api/v1/requests?q=zzz-no-such-thing", null, null).body().contains("\"items\":[]"), "an empty result is an empty list");
        assertEquals(422, send("GET", "/api/v1/requests?sort=banana", null, null).statusCode());
        assertTrue(send("GET", "/api/v1/tags", null, null).body().contains("\"tag\":\"ui\""));
    }
}
