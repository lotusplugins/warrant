package com.lotusplugins.warrant.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentIssuer;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.storage.H2PunishmentRepository;
import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WarrantApiTest {

    private final UUID player = UUID.randomUUID();
    private H2PunishmentRepository repository;
    private WarrantApi api;

    @BeforeEach
    void setUp(@TempDir File dataFolder) throws Exception {
        repository = new H2PunishmentRepository(dataFolder);
        api = new WarrantApi(repository, Runnable::run);
    }

    private Punishment ban(Instant expiry) {
        var punishment = new Punishment(
                UUID.randomUUID(),
                player,
                PunishmentType.BAN,
                new PunishmentIssuer(UUID.randomUUID(), Instant.now()),
                "reason",
                expiry,
                null);
        repository.create(punishment);
        return punishment;
    }

    @Test
    void findsActivePunishment() {
        var punishment = ban(null);
        assertEquals(
                punishment.id(),
                api.findActive(player, PunishmentType.BAN).join().orElseThrow().id());
    }

    @Test
    void ignoresExpiredPunishment() {
        ban(Instant.now().minusSeconds(60));
        assertTrue(api.findActive(player, PunishmentType.BAN).join().isEmpty());
    }

    @Test
    void pagesHistory() {
        ban(null);
        var page = api.history(player, Optional.empty(), 0, 10).join();
        assertEquals(1, page.total());
    }
}
