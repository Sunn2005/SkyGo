package com.skygo.backend.auth.contract;

import java.time.Instant;

public record LoginResponse(
    String accessToken, String tokenType, Instant expiresAt, AuthUserResponse user) {}
