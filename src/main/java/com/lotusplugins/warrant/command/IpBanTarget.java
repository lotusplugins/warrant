package com.lotusplugins.warrant.command;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.OfflinePlayer;

public record IpBanTarget(UUID id, String name, InetAddress address, Optional<OfflinePlayer> player) {

    public static IpBanTarget of(InetAddress address) {
        UUID id = UUID.nameUUIDFromBytes(("ip:" + address.getHostAddress()).getBytes(StandardCharsets.UTF_8));
        return new IpBanTarget(id, address.getHostAddress(), address, Optional.empty());
    }

    public static IpBanTarget of(OfflinePlayer player, InetAddress address) {
        return new IpBanTarget(player.getUniqueId(), CommandSupport.nameOf(player), address, Optional.of(player));
    }
}
