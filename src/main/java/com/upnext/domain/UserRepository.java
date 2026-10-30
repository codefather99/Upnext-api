package com.upnext.domain;

import com.upnext.domain.Views.UserRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    /** Thrown when the (case-insensitive) email is already registered. Two simultaneous signups: one wins. */
    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException() { super("An account with that email already exists. Try signing in."); }
    }

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID insert(String email, String passwordHash, String displayName, Role role) {
        try {
            return jdbc.queryForObject(Sql.INSERT_USER, UUID.class, email, passwordHash, displayName, role.name());
        } catch (DuplicateKeyException e) {
            throw new EmailTakenException();
        }
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.query(Sql.USER_BY_EMAIL, (rs, i) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("display_name"), Role.valueOf(rs.getString("role"))), email)
                .stream().findFirst();
    }
}
