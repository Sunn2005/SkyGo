package com.skygo.backend.auth.model;

import java.time.Instant;
import java.util.List;

public record LoginGuard(List<Instant> failures, Instant lockedUntil) {}
