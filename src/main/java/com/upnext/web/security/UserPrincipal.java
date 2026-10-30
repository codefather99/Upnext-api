package com.upnext.web.security;

import com.upnext.domain.Role;

import java.util.UUID;

public record UserPrincipal(UUID id, String displayName, Role role) {}
