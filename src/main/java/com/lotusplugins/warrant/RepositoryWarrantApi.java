package com.lotusplugins.warrant;

import com.lotusplugins.warrant.api.WarrantApi;
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

final class RepositoryWarrantApi implements WarrantApi {

    private final PunishmentRepository repository;
    private final Executor async;

    RepositoryWarrantApi(PunishmentRepository repository, Executor async) {
        this.repository = repository;
        this.async = async;
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActive(UUID player, PunishmentType type) {
        return CompletableFuture.supplyAsync(() -> repository.findActive(player, type), async);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActiveByAddress(InetAddress address) {
        return CompletableFuture.supplyAsync(() -> repository.findActiveByAddress(address), async);
    }

    @Override
    public CompletableFuture<HistoryPage> history(UUID player, Optional<PunishmentType> type, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> repository.findHistory(player, type, offset, limit), async);
    }

    @Override
    public CompletableFuture<Optional<InetAddress>> lastAddress(UUID player) {
        return CompletableFuture.supplyAsync(() -> repository.findLastAddress(player), async);
    }

    @Override
    public CompletableFuture<List<UUID>> playersByAddress(InetAddress address) {
        return CompletableFuture.supplyAsync(() -> repository.findPlayersByAddress(address), async);
    }
}
