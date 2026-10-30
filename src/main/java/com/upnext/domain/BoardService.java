package com.upnext.domain;

import com.upnext.domain.Views.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BoardService {
    private static final int COLUMN_LIMIT = 100;
    private final JdbcTemplate jdbc;

    public BoardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Move a card. Compare and set on the version the screen last saw: if another admin got there first the UPDATE
     * matches no row and we answer STALE_VERSION with the card as it truly is. Moving to the status it already has
     * (with a matching version) is a no-op. Every real move is recorded with who and when.
     */
    public RequestView move(UUID id, Status target, int expectedVersion, Actor admin) {
        List<Object[]> rows = jdbc.query(Sql.REQUEST_STATUS,
                (rs, i) -> new Object[]{Status.valueOf(rs.getString("status")), rs.getInt("version")}, id);
        if (rows.isEmpty()) throw new NotFoundException("Request");
        Status from = (Status) rows.get(0)[0];
        int version = (Integer) rows.get(0)[1];
        if (from == target && version == expectedVersion) return RequestService.one(jdbc, id, admin.id());
        int updated = jdbc.update(Sql.MOVE, target.name(), id, expectedVersion);
        if (updated == 0) throw new StaleVersionException(RequestService.one(jdbc, id, admin.id()));
        jdbc.update(Sql.HISTORY_INSERT, id, from.name(), target.name(), admin.id(), admin.label(), admin.requestId());
        return RequestService.one(jdbc, id, admin.id());
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID id) {
        RequestService.one(jdbc, id, null);          // 404 if the request does not exist
        return jdbc.query(Sql.HISTORY_FOR, (rs, i) -> new HistoryEntry(rs.getLong("id"),
                rs.getString("from_status") == null ? null : Status.valueOf(rs.getString("from_status")),
                Status.valueOf(rs.getString("to_status")), rs.getString("changed_by_name"),
                Views.instant(rs, "changed_at")), id);
    }

    @Transactional(readOnly = true)
    public Board board(UUID viewer) {
        return new Board(col(Status.OPEN, viewer), col(Status.PLANNED, viewer), col(Status.IN_PROGRESS, viewer),
                col(Status.COMPLETED, viewer), Instant.now());
    }

    /** A query over request status, never a separate copy, so it always agrees with the board. OPEN never appears. */
    @Transactional(readOnly = true)
    public Roadmap roadmap(UUID viewer) {
        return new Roadmap(col(Status.PLANNED, viewer), col(Status.IN_PROGRESS, viewer), col(Status.COMPLETED, viewer), Instant.now());
    }

    private List<RequestView> col(Status s, UUID viewer) { return RequestService.column(jdbc, s, viewer, COLUMN_LIMIT); }
}
