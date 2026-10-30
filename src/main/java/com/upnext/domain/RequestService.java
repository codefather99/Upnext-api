package com.upnext.domain;

import com.upnext.domain.Views.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class RequestService {
    public enum Sort { VOTES, NEWEST }

    public record Browse(String tag, Status status, String q, Sort sort, int page, int size, UUID viewer) {}

    private final JdbcTemplate jdbc;

    public RequestService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** New requests always start OPEN. Tags are lower-cased and de-duplicated (the web layer has already validated them). */
    public RequestView create(String title, String description, List<String> tags, Actor author) {
        List<String> clean = tags == null ? List.of()
                : tags.stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty()).distinct().limit(5).toList();
        UUID id = jdbc.queryForObject(Sql.INSERT_REQUEST, UUID.class, title.trim(), description.trim(),
                "{" + String.join(",", clean) + "}", author.id(), author.label());
        return get(id, author.id());
    }

    @Transactional(readOnly = true)
    public PageView<RequestView> search(Browse b) {
        StringBuilder where = new StringBuilder();
        List<Object> filters = new ArrayList<>();
        if (b.status() != null) { where.append(Sql.AND_STATUS); filters.add(b.status().name()); }
        if (b.tag() != null && !b.tag().isBlank()) { where.append(Sql.AND_TAG); filters.add(b.tag().trim().toLowerCase(Locale.ROOT)); }
        if (b.q() != null && !b.q().isBlank()) {
            String like = "%" + escapeLike(b.q().trim()) + "%";
            where.append(Sql.AND_Q);
            filters.add(like);
            filters.add(like);
        }
        Long total = jdbc.queryForObject(Sql.COUNT_BASE + where, Long.class, filters.toArray());
        List<Object> args = new ArrayList<>();
        args.add(b.viewer() == null ? Views.VISITOR : b.viewer());
        args.addAll(filters);
        args.add(b.size());
        args.add(b.page() * b.size());
        List<RequestView> items = jdbc.query(Sql.VIEW + where + (b.sort() == Sort.NEWEST ? Sql.ORDER_NEWEST : Sql.ORDER_VOTES),
                (rs, i) -> Views.request(rs), args.toArray());
        return new PageView<>(items, b.page(), b.size(), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public RequestView get(UUID id, UUID viewer) { return one(jdbc, id, viewer); }

    @Transactional(readOnly = true)
    public List<TagCount> tags() { return jdbc.query(Sql.TAGS, (rs, i) -> new TagCount(rs.getString("tag"), rs.getInt("c"))); }

    /** Shared by the board and roadmap: the visible requests in one status, most upvoted first. */
    static List<RequestView> column(JdbcTemplate jdbc, Status status, UUID viewer, int limit) {
        return jdbc.query(Sql.VIEW + Sql.AND_STATUS + Sql.ORDER_VOTES, (rs, i) -> Views.request(rs),
                viewer == null ? Views.VISITOR : viewer, status.name(), limit, 0);
    }

    static RequestView one(JdbcTemplate jdbc, UUID id, UUID viewer) {
        List<RequestView> rows = jdbc.query(Sql.VIEW + Sql.AND_ID, (rs, i) -> Views.request(rs),
                viewer == null ? Views.VISITOR : viewer, id);
        if (rows.isEmpty()) throw new NotFoundException("Request");
        return rows.get(0);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
