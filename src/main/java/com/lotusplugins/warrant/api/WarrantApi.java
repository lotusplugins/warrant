package com.lotusplugins.warrant.api;

import com.lotusplugins.warrant.api.model.HistoryPage;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class WarrantApi {

    private final PunishmentRepository repository;
    private final Executor async;

    public WarrantApi(PunishmentRepository repository, Executor async) {
        this.repository = repository;
        this.async = async;
    }

    public CompletableFuture<Optional<Punishment>> findActive(UUID player, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> repository.findActive(player, type), async);
    }

    public CompletableFuture<Optional<Punishment>> findActiveByAddress(InetAddress address) {
        return CompletableFuture.supplyAsync(() -> repository.findActiveByAddress(address), async);
    }

    public CompletableFuture<HistoryPage> history(UUID player, Optional<PunishmentType> type, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> repository.findHistory(player, type, offset, limit), async);
    }

    public CompletableFuture<Optional<InetAddress>> lastAddress(UUID player) {
        return CompletableFuture.supplyAsync(() -> repository.findLastAddress(player), async);
    }

    public CompletableFuture<List<UUID>> playersByAddress(InetAddress address) {
        return CompletableFuture.supplyAsync(() -> repository.findPlayersByAddress(address), async);
    }
}
