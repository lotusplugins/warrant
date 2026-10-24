package com.lotusplugins.warrant.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lotusplugins.warrant.ServerTestBase;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class AddressListenerTest extends ServerTestBase {

    @Test
    void loginRecordsTheConnectingAddress() throws Exception {
        var uuid = UUID.randomUUID();
        var address = InetAddress.getByName("198.51.100.4");

        new AddressListener(plugin, repository)
                .onPreLogin(new AsyncPlayerPreLoginEvent("Target", address, uuid, false));

        assertEquals(Optional.of(address), repository.findLastAddress(uuid));
    }
}
