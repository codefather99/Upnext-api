package com.upnext.web.api;

import com.upnext.domain.Role;
import com.upnext.domain.UserRepository;
import com.upnext.domain.Views.UserRow;
import com.upnext.web.dto.Dto;
import com.upnext.web.error.BadCredentialsApiException;
import com.upnext.web.security.CurrentUser;
import com.upnext.web.security.JwtService;
import com.upnext.web.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Unsafe, not idempotent. New accounts are always USER. The email is unique ignoring case, held by the database. */
    @PostMapping("/signup")
    public ResponseEntity<Dto.AuthResponse> signup(@Valid @RequestBody Dto.SignupRequest req) {
        String name = req.displayName().trim();
        UUID id = users.insert(req.email().trim(), encoder.encode(req.password()), name, Role.USER);
        return ResponseEntity.status(201).body(session(new UserPrincipal(id, name, Role.USER)));
    }

    /** Unsafe, not idempotent. One message for "no such user" and "wrong password" on purpose. */
    @PostMapping("/login")
    public Dto.AuthResponse login(@Valid @RequestBody Dto.LoginRequest req) {
        UserRow u = users.findByEmail(req.email().trim())
                .filter(r -> encoder.matches(req.password(), r.passwordHash()))
                .orElseThrow(BadCredentialsApiException::new);
        return session(new UserPrincipal(u.id(), u.displayName(), u.role()));
    }

    @GetMapping("/me")
    public Dto.UserView me() {
        UserPrincipal p = CurrentUser.required();
        return new Dto.UserView(p.id(), p.displayName(), p.role());
    }

    private Dto.AuthResponse session(UserPrincipal p) {
        return new Dto.AuthResponse(jwt.issue(p), "Bearer", jwt.ttlSeconds(), new Dto.UserView(p.id(), p.displayName(), p.role()));
    }
}
