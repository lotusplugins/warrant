package com.lotusplugins.warrant.storage;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.lotusplugins.warrant.api.model.HistoryPage;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.storage.model.PlayerAddressModel;
import com.lotusplugins.warrant.storage.model.PunishmentModel;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class H2PunishmentRepository implements PunishmentRepository {
    public final ConnectionSource connectionSource;
    private final Dao<PunishmentModel, UUID> punishmentDao;
    private final Dao<PlayerAddressModel, UUID> addressDao;

    public H2PunishmentRepository(File dataFolder) throws SQLException {
        String dbPath = new File(dataFolder, "warrant").getAbsolutePath();
        String url = "jdbc:h2:" + dbPath;

        this.connectionSource = new JdbcConnectionSource(url);
        this.punishmentDao = DaoManager.createDao(connectionSource, PunishmentModel.class);

        this.addressDao = DaoManager.createDao(connectionSource, PlayerAddressModel.class);

        TableUtils.createTableIfNotExists(connectionSource, PunishmentModel.class);
        TableUtils.createTableIfNotExists(connectionSource, PlayerAddressModel.class);
        addImportOrigin();
        addAddress();
    }

    private void addAddress() throws SQLException {
        punishmentDao.executeRaw("ALTER TABLE `punishments` ADD COLUMN IF NOT EXISTS `address` VARCHAR(255)");
    }

    private void addImportOrigin() throws SQLException {
        punishmentDao.executeRaw("ALTER TABLE `punishments` ADD COLUMN IF NOT EXISTS `importSource` VARCHAR(255)");
        punishmentDao.executeRaw("ALTER TABLE `punishments` ADD COLUMN IF NOT EXISTS `importId` VARCHAR(255)");
        punishmentDao.executeRaw("CREATE UNIQUE INDEX IF NOT EXISTS `punishments_import`"
                + " ON `punishments` (`importSource`, `importId`)");
    }

    @Override
    public synchronized boolean create(Punishment punishment) {
        if (isExclusive(punishment.type()) && findConflicting(punishment).isPresent()) return false;
        try {
            requireOpen();
            punishmentDao.create(new PunishmentModel(punishment));
            return true;
        } catch (SQLException e) {
            throw new StorageException("create punishment", e);
        }
    }

    @Override
    public synchronized boolean createImported(Punishment punishment, String source, String sourceId) {
        if (hasImported(source, sourceId)) return false;
        try {
            requireOpen();
            PunishmentModel model = new PunishmentModel(punishment);
            model.markImported(source, sourceId);
            punishmentDao.create(model);
            return true;
        } catch (SQLException e) {
            throw new StorageException("create imported punishment", e);
        }
    }

    @Override
    public boolean hasImported(String source, String sourceId) {
        try {
            requireOpen();
            return punishmentDao
                            .queryBuilder()
                            .where()
                            .eq("importSource", source)
                            .and()
                            .eq("importId", sourceId)
                            .countOf()
                    > 0;
        } catch (SQLException e) {
            throw new StorageException("check imported punishment", e);
        }
    }

    private void requireOpen() throws SQLException {
        if (!connectionSource.isOpen("punishments")) {
            throw new SQLException("Database connection is closed");
        }
    }

    private static boolean isExclusive(PunishmentType type) {
        return switch (type) {
            case BAN, MUTE, IP_BAN -> true;
            case WARN, KICK -> false;
        };
    }

    @Override
    public synchronized void revoke(UUID punishmentId, UUID revokedBy, String reason, Instant atTime) {
        try {
            requireOpen();
            PunishmentModel model = punishmentDao.queryForId(punishmentId);
            if (model == null) return;
            TransactionManager.callInTransaction(connectionSource, () -> {
                List<PunishmentModel> active =
                        isExclusive(model.getType()) ? activeInScope(model).query() : List.of(model);
                for (PunishmentModel candidate : active) {
                    Punishment punishment = candidate.toPunishment();
                    if (isExclusive(punishment.type())
                            && punishment.expiry() != null
                            && !punishment.expiry().isAfter(atTime)) {
                        continue;
                    }
                    candidate.setIsRevoked(true);
                    candidate.setRevokedBy(revokedBy);
                    candidate.setRevokedReason(reason);
                    candidate.setRevokedAt(atTime);
                    punishmentDao.update(candidate);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageException("revoke punishment", e);
        }
    }

    private Where<PunishmentModel, UUID> activeInScope(PunishmentModel model) throws SQLException {
        Where<PunishmentModel, UUID> where = punishmentDao
                .queryBuilder()
                .where()
                .eq("type", model.getType())
                .and()
                .eq("revoked", false)
                .and();
        return model.getType() == PunishmentType.IP_BAN
                ? where.eq("address", model.getAddress())
                : where.eq("target", model.getTarget());
    }

    private Optional<Punishment> findConflicting(Punishment punishment) {
        return punishment.type() == PunishmentType.IP_BAN
                ? findActiveByAddress(Objects.requireNonNull(punishment.address()))
                : findActive(punishment.target(), punishment.type());
    }

    @Override
    public Optional<Punishment> findActiveByAddress(InetAddress address) {
        try {
            requireOpen();
            Instant now = Instant.now();
            return punishmentDao
                    .queryBuilder()
                    .where()
                    .eq("type", PunishmentType.IP_BAN)
                    .and()
                    .eq("address", address.getHostAddress())
                    .and()
                    .eq("revoked", false)
                    .query()
                    .stream()
                    .map(PunishmentModel::toPunishment)
                    .filter(p -> p.expiry() == null || p.expiry().isAfter(now))
                    .findFirst();
        } catch (SQLException e) {
            throw new StorageException("read active address punishments", e);
        }
    }

    @Override
    public Optional<Punishment> findActive(UUID player, PunishmentType type) {
        try {
            requireOpen();
            Instant now = Instant.now();
            return punishmentDao
                    .queryBuilder()
                    .where()
                    .eq("target", player)
                    .and()
                    .eq("type", type)
                    .and()
                    .eq("revoked", false)
                    .query()
                    .stream()
                    .map(PunishmentModel::toPunishment)
                    .filter(p -> p.expiry() == null || p.expiry().isAfter(now))
                    .findFirst();
        } catch (SQLException e) {
            throw new StorageException("read active punishments", e);
        }
    }

    @Override
    public List<Punishment> findUnacknowledgedWarnings(UUID player) {
        try {
            requireOpen();
            return punishmentDao
                    .queryBuilder()
                    .where()
                    .eq("target", player)
                    .and()
                    .eq("type", PunishmentType.WARN)
                    .and()
                    .eq("revoked", false)
                    .and()
                    .eq("acknowledged", false)
                    .query()
                    .stream()
                    .map(PunishmentModel::toPunishment)
                    .toList();
        } catch (SQLException e) {
            throw new StorageException("read pending warnings", e);
        }
    }

    @Override
    public void acknowledge(UUID punishmentId) {
        try {
            requireOpen();
            var update = punishmentDao.updateBuilder();
            update.updateColumnValue("acknowledged", true).where().idEq(punishmentId);
            update.update();
        } catch (SQLException e) {
            throw new StorageException("acknowledge warning", e);
        }
    }

    @Override
    public void recordAddress(UUID player, InetAddress address, Instant seenAt) {
        try {
            requireOpen();
            addressDao.createOrUpdate(new PlayerAddressModel(player, address, seenAt));
        } catch (SQLException e) {
            throw new StorageException("record player address", e);
        }
    }

    @Override
    public List<UUID> findPlayersByAddress(InetAddress address) {
        try {
            requireOpen();
            return addressDao.queryForEq("address", address.getHostAddress()).stream()
                    .map(PlayerAddressModel::getPlayer)
                    .toList();
        } catch (SQLException e) {
            throw new StorageException("read players by address", e);
        }
    }

    @Override
    public Optional<InetAddress> findLastAddress(UUID player) {
        try {
            requireOpen();
            PlayerAddressModel model = addressDao.queryForId(player);
            return model == null ? Optional.empty() : Optional.of(model.toAddress());
        } catch (SQLException | UnknownHostException e) {
            throw new StorageException("read player address", e);
        }
    }

    @Override
    public List<Punishment> findHistory(UUID player) {
        try {
            requireOpen();
            List<PunishmentModel> models =
                    punishmentDao.queryBuilder().where().eq("target", player).query();
            if (models == null || models.isEmpty()) return new ArrayList<>();
            return models.stream().map(PunishmentModel::toPunishment).toList();
        } catch (SQLException e) {
            throw new StorageException("read punishment history", e);
        }
    }

    @Override
    public HistoryPage findHistory(UUID player, Optional<PunishmentType> type, int offset, int limit) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("offset must be >= 0 and limit >= 1");
        try {
            requireOpen();
            QueryBuilder<PunishmentModel, UUID> count =
                    punishmentDao.queryBuilder().setCountOf(true);
            filterHistory(count, player, type);
            QueryBuilder<PunishmentModel, UUID> page = punishmentDao.queryBuilder();
            filterHistory(page, player, type);
            page.orderBy("issuedAt", false)
                    .orderBy("id", false)
                    .offset((long) offset)
                    .limit((long) limit);
            return new HistoryPage(
                    page.query().stream().map(PunishmentModel::toPunishment).toList(),
                    Math.toIntExact(punishmentDao.countOf(count.prepare())));
        } catch (SQLException e) {
            throw new StorageException("read punishment history", e);
        }
    }

    private static void filterHistory(
            QueryBuilder<PunishmentModel, UUID> query, UUID player, Optional<PunishmentType> type) throws SQLException {
        var where = query.where().eq("target", player);
        if (type.isPresent()) where.and().eq("type", type.get());
    }
}
