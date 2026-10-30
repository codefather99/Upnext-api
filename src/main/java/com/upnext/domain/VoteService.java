package com.upnext.domain;

import com.upnext.domain.Views.VoteState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * One vote per user per request. Both calls are idempotent: voting twice (or from two tabs at once) leaves one vote,
 * and withdrawing a vote that is not there is a success. The database primary key is what holds the rule.
 */
@Service
@Transactional
public class VoteService {
    private final JdbcTemplate jdbc;

    public VoteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public VoteState cast(UUID requestId, UUID userId) {
        requireVotingOpen(requestId);
        jdbc.update(Sql.CAST_VOTE, requestId, userId);          // plain insert; the key refuses a second vote
        return state(requestId, userId);
    }

    public VoteState withdraw(UUID requestId, UUID userId) {
        requireVotingOpen(requestId);
        jdbc.update(Sql.WITHDRAW_VOTE, requestId, userId);
        return state(requestId, userId);
    }

    private void requireVotingOpen(UUID requestId) {
        List<Status> rows = jdbc.query(Sql.REQUEST_STATUS, (rs, i) -> Status.valueOf(rs.getString("status")), requestId);
        if (rows.isEmpty()) throw new NotFoundException("Request");
        if (rows.get(0) == Status.COMPLETED) throw new VotingClosedException();
    }

    private VoteState state(UUID requestId, UUID userId) {
        return jdbc.query(Sql.VOTE_STATE, (rs, i) -> new VoteState(requestId, rs.getInt("c"), rs.getBoolean("mine")),
                requestId, requestId, userId).get(0);
    }
}
