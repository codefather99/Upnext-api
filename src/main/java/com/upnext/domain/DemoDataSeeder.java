package com.upnext.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DEMONSTRATION DATA ONLY. Runs at startup when the database has no requests: 44 requests across all four statuses,
 * the awkward cases the rules exist for (a 160-character title, a request with no tags, a request with no votes),
 * one admin and eight users who vote. Everything is fictional; no claim of a real product or customers.
 */
@Component
@ConditionalOnProperty(name = "upnext.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {
    private static final String[] TITLES = {
            // 0-17 OPEN
            "Dark mode for night time use", "Export requests to CSV", "Email me when my request changes status", "Request with nobody voting for it yet",
            "Keyboard shortcuts for the admin board", "PLACEHOLDER-LONG-TITLE", "Idea that nobody tagged", "Search that also looks inside comments",
            "Mobile app for browsing the roadmap", "Slack notifications for new requests", "Let users follow a request without voting",
            "Show how many people voted this week", "Embeddable roadmap widget for our docs site", "Single sign-on for company accounts",
            "Bulk move cards between columns", "Print-friendly roadmap page", "Weekly digest email of top requests", "Dark theme for the admin board",
            // 18-27 PLANNED
            "Public API for reading the roadmap", "Tag suggestions while typing a request", "Merge duplicate requests", "Comments with a Team badge on admin replies",
            "Saved filters for the browse page", "Roadmap view grouped by quarter", "Two-factor sign in for admins", "Accessible colour palette review",
            "Webhook when a request is completed", "Import requests from a spreadsheet",
            // 28-35 IN_PROGRESS
            "Faster loading of the browse page", "Screen reader labels on the board", "Sort by recently updated", "Show who moved a card and when",
            "Better empty states on every screen", "Clear error messages when a vote fails", "Keep my filters after I sign in", "Roadmap page that works on small phones",
            // 36-43 COMPLETED
            "Sign up and sign in", "Submit a feature request", "Vote once per request", "Filter requests by tag", "Filter requests by status",
            "Public roadmap page", "Admin board with drag and drop", "Search requests by keyword"};
    private static final String[] TAGS = {"ui", "integrations", "mobile", "performance", "accessibility", "reporting", "security", "api", "notifications", "data"};
    private static final String[] NAMES = {"Ada Obi", "Tunde Bello", "Ngozi Eze", "Chidi Okafor", "Fatima Sani", "Emeka Nwosu", "Bisi Adeyemi", "Yusuf Danjuma"};
    private static final String[] EMAILS = {"ada", "tunde", "ngozi", "chidi", "fatima", "emeka", "bisi", "yusuf"};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PlatformTransactionManager tm;
    private final String demoPassword;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder encoder, PlatformTransactionManager tm,
                          @Value("${upnext.demo.password}") String demoPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.tm = tm;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long existing = jdbc.queryForObject(Sql.COUNT_REQUESTS, Long.class);
        if (existing != null && existing > 0) return;
        new TransactionTemplate(tm).executeWithoutResult(status -> seed());
    }

    private void seed() {
        String hash = encoder.encode(demoPassword);
        jdbc.update(Sql.INSERT_USER_IF_MISSING, "admin@upnext.demo", hash, "Demo Admin", Role.ADMIN.name());
        List<UUID> voters = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            jdbc.update(Sql.INSERT_USER_IF_MISSING, EMAILS[i] + "@upnext.demo", hash, NAMES[i], Role.USER.name());
            voters.add(userId(EMAILS[i] + "@upnext.demo"));
        }
        UUID admin = userId("admin@upnext.demo");

        for (int i = 0; i < TITLES.length; i++) {
            Status status = i < 18 ? Status.OPEN : i < 28 ? Status.PLANNED : i < 36 ? Status.IN_PROGRESS : Status.COMPLETED;
            int moves = status.ordinal();
            String title = i == 5 ? longTitle() : TITLES[i];
            List<String> tags = i == 6 ? List.of() : tagsFor(i);
            int createdAgo = 480 - i * 8;
            UUID author = voters.get(i % voters.size());
            String description = "We would like this: " + title.toLowerCase().substring(0, Math.min(60, title.length()))
                    + ". It would save our team time and make the product easier to use. (Demonstration data.)";
            UUID id = jdbc.queryForObject(Sql.INSERT_SEED_REQUEST, UUID.class, title, description,
                    "{" + String.join(",", tags) + "}", status.name(), author, NAMES[i % NAMES.length], moves,
                    createdAgo, Math.max(1, createdAgo - 12 * moves));
            int votes = Math.min((i * 5 + 3) % 9, voters.size());       // request 3 gets 0; others 1 to 8
            for (int v = 0; v < votes; v++) jdbc.update(Sql.CAST_VOTE, id, voters.get(v));
            Status previous = Status.OPEN;
            for (int step = 1; step <= moves; step++) {
                Status next = Status.values()[step];
                jdbc.update(Sql.SEED_HISTORY, id, previous.name(), next.name(), admin, "Demo Admin (admin)", Math.max(1, createdAgo - 6 * step));
                previous = next;
            }
        }
    }

    private UUID userId(String email) {
        return jdbc.query(Sql.USER_BY_EMAIL, (rs, i) -> rs.getObject("id", UUID.class), email).get(0);
    }

    private static List<String> tagsFor(int i) {
        String first = TAGS[i % TAGS.length];
        String second = TAGS[(i * 3 + 1) % TAGS.length];
        return i % 2 == 0 && !first.equals(second) ? List.of(first, second) : List.of(first);
    }

    /** Exactly 160 characters: the longest title the product accepts, to break any layout that assumes short text. */
    private static String longTitle() {
        String base = "Let each team configure notification preferences per project, per channel and per time zone, with quiet hours, digests and an escape hatch for urgent items ";
        String s = (base + "and more detail than any sensible title needs ").substring(0, 160);
        return s.endsWith(" ") ? s.substring(0, 159) + "." : s;
    }
}
