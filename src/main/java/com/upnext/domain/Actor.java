package com.upnext.domain;

import java.util.UUID;

/** Who is acting. The web layer builds it from the signed-in user; services never see a token. */
public record Actor(UUID id, String label, String requestId) {}
