package com.lotusplugins.warrant.storage.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

@DatabaseTable(tableName = "player_addresses")
public class PlayerAddressModel {
    @DatabaseField(id = true)
    private UUID player;

    @DatabaseField
    private String address;

    @DatabaseField(persisterClass = InstantPersister.class)
    private Instant lastSeen;

    public PlayerAddressModel() {}

    public PlayerAddressModel(UUID player, InetAddress address, Instant lastSeen) {
        this.player = player;
        this.address = address.getHostAddress();
        this.lastSeen = lastSeen;
    }

    public UUID getPlayer() {
        return player;
    }

    public InetAddress toAddress() throws UnknownHostException {
        return InetAddress.getByName(address);
    }
}
