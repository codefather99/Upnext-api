package com.upnext.domain;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Read models. Field names match openapi/upnext-openapi.yaml, so they serialise straight onto the wire. */
public final class Views {
    private Views() {}

    public static final UUID VISITOR = new UUID(0L, 0L);   // viewerHasVoted is always false for this id

    public record RequestView(UUID id, String title, String description, List<String> tags, Status status, int voteCount,
                              boolean viewerHasVoted, String authorName, int version, Instant createdAt, Instant updatedAt) {}
    public record VoteState(UUID requestId, int voteCount, boolean viewerHasVoted) {}
    public record HistoryEntry(long id, Status fromStatus, Status toStatus, String changedBy, Instant changedAt) {}
    public record PageView<T>(List<T> items, int page, int size, long totalItems) {}
    public record TagCount(String tag, int count) {}
    public record Board(List<RequestView> open, List<RequestView> planned, List<RequestView> inProgress,
                        List<RequestView> completed, Instant generatedAt) {}
    public record Roadmap(List<RequestView> planned, List<RequestView> inProgress, List<RequestView> completed, Instant generatedAt) {}
    public record UserRow(UUID id, String email, String passwordHash, String displayName, Role role) {}

    static RequestView request(ResultSet rs) throws SQLException {
        Array tags = rs.getArray("tags");
        List<String> tagList = tags == null ? List.of() : Arrays.asList((String[]) tags.getArray());
        return new RequestView(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"), tagList,
                Status.valueOf(rs.getString("status")), rs.getInt("vote_count"), rs.getBoolean("viewer_voted"),
                rs.getString("author_name"), rs.getInt("version"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    static Instant instant(ResultSet rs, String col) throws SQLException {
        OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }
}
