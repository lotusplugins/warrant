package com.lotusplugins.warrant.api;

import com.lotusplugins.warrant.api.model.HistoryPage;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WarrantApi {

    CompletableFuture<Optional<Punishment>> findActive(UUID player, PunishmentType type);

    CompletableFuture<Optional<Punishment>> findActiveByAddress(InetAddress address);

    CompletableFuture<HistoryPage> history(UUID player, Optional<PunishmentType> type, int offset, int limit);

    CompletableFuture<Optional<InetAddress>> lastAddress(UUID player);

    CompletableFuture<List<UUID>> playersByAddress(InetAddress address);
}
