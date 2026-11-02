package com.lotusplugins.warrant.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentIssuer;
import com.lotusplugins.warrant.api.model.PunishmentType;
import java.io.File;
import java.net.InetAddress;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class H2IpBanTest {

    private InetAddress address;
    private H2PunishmentRepository repository;

    @BeforeEach
    void setUp(@TempDir File dataFolder) throws Exception {
        address = InetAddress.getByName("203.0.113.7");
        repository = new H2PunishmentRepository(dataFolder);
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.connectionSource.close();
    }

    private Punishment ipBan(UUID target, InetAddress on, Instant expiry) {
        return new Punishment(
                UUID.randomUUID(),
                target,
                PunishmentType.IP_BAN,
                new PunishmentIssuer(
                        UUID.randomUUID(), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),
                "reason",
                expiry,
                null,
                on);
    }

    @Test
    void activeBanIsFoundByAddress() {
        Punishment ban = ipBan(UUID.randomUUID(), address, null);
        repository.create(ban);

        assertEquals(Optional.of(ban), repository.findActiveByAddress(address));
    }

    @Test
    void otherAddressIsNotBanned() throws Exception {
        repository.create(ipBan(UUID.randomUUID(), address, null));

        assertTrue(repository
                .findActiveByAddress(InetAddress.getByName("203.0.113.8"))
                .isEmpty());
    }

    @Test
    void expiredBanIsNotActive() {
        repository.create(ipBan(UUID.randomUUID(), address, Instant.now().minus(Duration.ofMinutes(1))));

        assertTrue(repository.findActiveByAddress(address).isEmpty());
    }

    @Test
    void secondBanOnTheSameAddressIsRejectedWhateverTheTarget() {
        assertTrue(repository.create(ipBan(UUID.randomUUID(), address, null)));

        assertFalse(repository.create(ipBan(UUID.randomUUID(), address, null)));
    }

    @Test
    void revokingThroughAnyTargetLiftsTheAddressBan() {
        Punishment ban = ipBan(UUID.randomUUID(), address, null);
        repository.create(ban);

        repository.revoke(ban.id(), UUID.randomUUID(), "appeal", Instant.now());

        assertTrue(repository.findActiveByAddress(address).isEmpty());
    }

    @Test
    void revokingOneAddressLeavesAnotherBanned() throws Exception {
        var other = InetAddress.getByName("2001:db8::1");
        Punishment ban = ipBan(UUID.randomUUID(), address, null);
        Punishment otherBan = ipBan(ban.target(), other, null);
        repository.create(ban);
        repository.create(otherBan);

        repository.revoke(ban.id(), UUID.randomUUID(), "appeal", Instant.now());

        assertTrue(repository.findActiveByAddress(address).isEmpty());
        assertEquals(Optional.of(otherBan), repository.findActiveByAddress(other));
    }

    @Test
    void databaseWithoutAddressColumnMigratesInPlace(@TempDir File dataFolder) throws Exception {
        new H2PunishmentRepository(dataFolder).connectionSource.close();
        try (var connection = DriverManager.getConnection("jdbc:h2:" + new File(dataFolder, "warrant"));
                var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `punishments` DROP COLUMN `address`");
        }

        var migrated = new H2PunishmentRepository(dataFolder);
        try {
            Punishment ban = ipBan(UUID.randomUUID(), address, null);
            migrated.create(ban);
            assertEquals(Optional.of(ban), migrated.findActiveByAddress(address));
        } finally {
            migrated.connectionSource.close();
        }
    }
}
