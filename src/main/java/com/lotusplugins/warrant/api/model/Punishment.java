package com.lotusplugins.warrant.api.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Punishment(
        @NotNull UUID id,
        @NotNull UUID target,
        @NotNull PunishmentType type,
        @NotNull PunishmentIssuer issuer,
        @Nullable String reason,
        @Nullable Instant expiry,
        @Nullable Revocation revocation,
        @Nullable InetAddress address) {

    public Punishment(
            UUID id,
            UUID target,
            PunishmentType type,
            PunishmentIssuer issuer,
            String reason,
            Instant expiry,
            Revocation revocation) {
        this(id, target, type, issuer, reason, expiry, revocation, null);
    }

    public boolean expired() {
        return expiry != null && expiry.isBefore(Instant.now());
    }

    public boolean revoked() {
        return revocation != null;
    }
}
