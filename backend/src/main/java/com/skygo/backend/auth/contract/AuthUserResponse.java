package com.skygo.backend.auth.contract;

import java.time.Instant;

public record AuthUserResponse(
    String userId, String fullName, String phoneNumber, String email, Instant createdAt) {}
