package com.lotusplugins.warrant.listener;

import com.lotusplugins.warrant.storage.PunishmentRepository;
import com.lotusplugins.warrant.storage.StorageException;
import java.time.Instant;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

public final class AddressListener implements Listener {

    private final Plugin plugin;
    private final PunishmentRepository repository;

    public AddressListener(Plugin plugin, PunishmentRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            repository.recordAddress(event.getUniqueId(), event.getAddress(), Instant.now());
        } catch (StorageException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not record login address", e);
        }
    }
}
