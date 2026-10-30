package com.upnext.domain;

/**
 * Every SQL statement up-next runs, in one file, so it can be reviewed and run against a real Postgres on its own
 * (see db/verify_upnext_sql.py). `?` are JDBC placeholders. Never edit an applied migration: add a new V file.
 */
final class Sql {
    private Sql() {}

    // ------------------------------------------------------------ reading requests (vote_count is COMPUTED, never stored)
    /** First placeholder is the viewer's user id (the all-zero id for visitors). */
    static final String VIEW = """
            SELECT r.id, r.title, r.description, r.tags, r.status, r.author_name, r.version, r.created_at, r.updated_at,
                   COALESCE(v.c, 0) AS vote_count,
                   EXISTS (SELECT 1 FROM vote mv WHERE mv.request_id = r.id AND mv.user_id = ?) AS viewer_voted
            FROM request r
            LEFT JOIN (SELECT request_id, count(*) AS c FROM vote GROUP BY request_id) v ON v.request_id = r.id
            WHERE r.duplicate_of IS NULL
            """;
    static final String AND_STATUS = " AND r.status = ?";
    static final String AND_TAG = " AND r.tags @> ARRAY[?]::text[]";
    static final String AND_Q = " AND (r.title ILIKE ? ESCAPE '\\' OR r.description ILIKE ? ESCAPE '\\')";
    static final String AND_ID = " AND r.id = ?";
    static final String ORDER_VOTES = " ORDER BY COALESCE(v.c, 0) DESC, r.created_at DESC, r.id LIMIT ? OFFSET ?";
    static final String ORDER_NEWEST = " ORDER BY r.created_at DESC, r.id LIMIT ? OFFSET ?";
    static final String COUNT_BASE = "SELECT count(*) FROM request r WHERE r.duplicate_of IS NULL";
    static final String TAGS = """
            SELECT t AS tag, count(*) AS c FROM request r CROSS JOIN LATERAL unnest(r.tags) AS t
            WHERE r.duplicate_of IS NULL GROUP BY t ORDER BY c DESC, t
            """;

    // ------------------------------------------------------------ writing
    static final String INSERT_REQUEST = """
            INSERT INTO request (title, description, tags, author_id, author_name) VALUES (?, ?, ?::text[], ?, ?) RETURNING id
            """;
    static final String REQUEST_STATUS = "SELECT status, version FROM request WHERE id = ? AND duplicate_of IS NULL";

    /** THE RULE: one vote per user per request. The primary key (request_id, user_id) decides, even for two taps at once. */
    static final String CAST_VOTE = "INSERT INTO vote (request_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
    static final String WITHDRAW_VOTE = "DELETE FROM vote WHERE request_id = ? AND user_id = ?";
    static final String VOTE_STATE = """
            SELECT (SELECT count(*) FROM vote WHERE request_id = ?) AS c,
                   EXISTS (SELECT 1 FROM vote WHERE request_id = ? AND user_id = ?) AS mine
            """;

    /** THE OTHER RULE: compare and set. Two admins, one card: only the one holding the true version updates a row. */
    static final String MOVE = "UPDATE request SET status = ?, version = version + 1, updated_at = now() WHERE id = ? AND version = ?";
    static final String HISTORY_INSERT = """
            INSERT INTO request_status_history (request_id, from_status, to_status, changed_by_id, changed_by_name, request_id_hdr)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    static final String HISTORY_FOR = """
            SELECT id, from_status, to_status, changed_by_name, changed_at FROM request_status_history
            WHERE request_id = ? ORDER BY changed_at, id
            """;

    // ------------------------------------------------------------ users (V100)
    static final String INSERT_USER = "INSERT INTO app_user (email, password_hash, display_name, role) VALUES (?, ?, ?, ?) RETURNING id";
    static final String INSERT_USER_IF_MISSING = "INSERT INTO app_user (email, password_hash, display_name, role) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
    static final String USER_BY_EMAIL = "SELECT id, email, password_hash, display_name, role FROM app_user WHERE lower(email) = lower(?)";

    // ------------------------------------------------------------ demo seed
    static final String COUNT_REQUESTS = "SELECT count(*) FROM request";
    static final String INSERT_SEED_REQUEST = """
            INSERT INTO request (title, description, tags, status, author_id, author_name, version, created_at, updated_at)
            VALUES (?, ?, ?::text[], ?, ?, ?, ?, now() - make_interval(hours => ?), now() - make_interval(hours => ?))
            RETURNING id
            """;
    static final String SEED_HISTORY = """
            INSERT INTO request_status_history (request_id, from_status, to_status, changed_by_id, changed_by_name, changed_at)
            VALUES (?, ?, ?, ?, ?, now() - make_interval(hours => ?))
            """;
}
