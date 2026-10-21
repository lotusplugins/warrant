package com.lotusplugins.warrant.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class IpBanCommandsTest extends CommandTestBase {

    private final List<Punishment> enforced = new ArrayList<>();
    private InetAddress address;
    private PlayerMock staff;
    private PlayerMock target;
    private PlayerMock alt;

    @BeforeEach
    void registerCommands() throws Exception {
        new IpBanCommands(support, repository, config).register(manager, enforced::add);
        address = InetAddress.getByName("203.0.113.7");
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
        alt = server.addPlayer("Alt");
        repository.recordAddress(target.getUniqueId(), address, Instant.now());
        repository.recordAddress(alt.getUniqueId(), address, Instant.now());
    }

    @Test
    void banByPlayerStoresTheirLastAddress() {
        run(staff, "ipban Target griefing");

        Punishment stored = history(target.getUniqueId(), PunishmentType.IP_BAN).getFirst();
        assertEquals(address, stored.address());
        assertEquals("griefing", stored.reason());
        assertNull(stored.expiry());
        assertEquals(List.of(stored.id()), enforced.stream().map(Punishment::id).toList());
    }

    @Test
    void banByRawAddressNeedsNoKnownPlayer() {
        run(staff, "ipban 198.51.100.4 proxy abuse");

        Punishment stored = enforced.getFirst();
        assertEquals(PunishmentType.IP_BAN, stored.type());
        assertEquals("198.51.100.4", stored.address().getHostAddress());
        assertEquals("proxy abuse", stored.reason());
        assertTrue(repository.findActiveByAddress(stored.address()).isPresent());
    }

    @Test
    void banByIpv6Address() {
        run(staff, "ipban 2001:db8::1");

        assertEquals("2001:db8:0:0:0:0:0:1", enforced.getFirst().address().getHostAddress());
    }

    @Test
    void playerWithoutKnownAddressIsNotBanned() {
        server.addPlayer("Stranger");

        assertThrows(CompletionException.class, () -> run(staff, "ipban Stranger"));

        assertTrue(enforced.isEmpty());
    }

    @Test
    void secondBanOnTheSameAddressIsRefusedWhoeverIsNamed() {
        run(staff, "ipban Target first");
        run(staff, "ipban Alt second");
        run(staff, "ipban 203.0.113.7 third");

        assertEquals(1, enforced.size());
    }

    @Test
    void temporaryBanExpiresAfterDuration() {
        Instant before = Instant.now();

        run(staff, "tempipban Target 1h spamming");

        Instant expiry = enforced.getFirst().expiry();
        assertNotNull(expiry);
        assertFalse(expiry.isBefore(before.plus(Duration.ofHours(1))));
    }

    @Test
    void exemptPlayerIsNotBanned() {
        target.addAttachment(plugin, "warrant.exempt", true);

        run(staff, "ipban Target");

        assertTrue(enforced.isEmpty());
    }

    @Test
    void exemptPlayerSharingTheAddressBlocksTheBan() {
        alt.addAttachment(plugin, "warrant.exempt", true);

        run(staff, "ipban Target");
        run(staff, "ipban 203.0.113.7");

        assertTrue(enforced.isEmpty());
    }

    @Test
    void unbanByAnotherPlayerOnTheAddressLiftsTheBan() {
        run(staff, "ipban Target");

        run(staff, "unipban Alt appeal");

        assertTrue(repository.findActiveByAddress(address).isEmpty());
        assertTrue(
                history(target.getUniqueId(), PunishmentType.IP_BAN).getFirst().revoked());
    }

    @Test
    void unbanByRawAddressLiftsTheBan() {
        run(staff, "ipban 198.51.100.4");

        run(staff, "unipban 198.51.100.4");

        assertTrue(repository.findActiveByAddress(enforced.getFirst().address()).isEmpty());
    }

    @Test
    void unbanWithoutActiveBanChangesNothing() {
        run(staff, "unipban Target");

        assertTrue(history(target.getUniqueId(), PunishmentType.IP_BAN).isEmpty());
    }
}
