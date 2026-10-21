package com.lotusplugins.warrant.command;

import static com.lotusplugins.warrant.command.CommandSupport.issuerId;
import static com.lotusplugins.warrant.command.CommandSupport.reasonOf;
import static com.lotusplugins.warrant.command.parser.DurationParser.durationParser;
import static com.lotusplugins.warrant.command.parser.IpBanTargetParser.ipBanTargetParser;
import static com.lotusplugins.warrant.command.parser.ReasonParser.reasonParser;

import com.lotusplugins.warrant.PluginConfig;
import com.lotusplugins.warrant.api.model.Punishment;
import com.lotusplugins.warrant.api.model.PunishmentIssuer;
import com.lotusplugins.warrant.api.model.PunishmentType;
import com.lotusplugins.warrant.command.parser.DurationParser;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

public final class IpBanCommands {

    private final CommandSupport support;
    private final PunishmentRepository repository;
    private final PluginConfig config;

    public IpBanCommands(CommandSupport support, PunishmentRepository repository, PluginConfig config) {
        this.support = support;
        this.repository = repository;
        this.config = config;
    }

    public void register(CommandManager<CommandSender> manager, Consumer<Punishment> enforce) {
        var silent = manager.flagBuilder("silent").withAliases("s");
        manager.command(manager.commandBuilder("ipban")
                .required("target", ipBanTargetParser(repository))
                .optional("reason", reasonParser())
                .flag(silent)
                .permission("warrant.ipban")
                .handler(ctx -> support.execute(ctx, () -> issue(ctx, null, enforce))));
        manager.command(manager.commandBuilder("tempipban")
                .required("target", ipBanTargetParser(repository))
                .required("duration", durationParser())
                .optional("reason", reasonParser())
                .flag(silent)
                .permission("warrant.tempipban")
                .handler(ctx -> support.execute(ctx, () -> issue(ctx, ctx.get("duration"), enforce))));
        manager.command(manager.commandBuilder("unipban")
                .required("target", ipBanTargetParser(repository))
                .optional("reason", reasonParser())
                .flag(silent)
                .permission("warrant.unipban")
                .handler(ctx -> support.execute(ctx, () -> revoke(ctx))));
    }

    private void issue(CommandContext<CommandSender> ctx, @Nullable Duration duration, Consumer<Punishment> enforce) {
        IpBanTarget target = ctx.get("target");
        var exempt = repository.findPlayersByAddress(target.address()).stream()
                .map(Bukkit::getOfflinePlayer)
                .filter(support::isExempt)
                .findFirst();
        if (exempt.isPresent()) {
            support.reply(ctx, "exempt", exempt.get());
            return;
        }
        if (repository.findActiveByAddress(target.address()).isPresent()) {
            support.reply(ctx, "ipban.already-active", target.name());
            return;
        }
        Instant now = Instant.now();
        var punishment = new Punishment(
                UUID.randomUUID(),
                target.id(),
                PunishmentType.IP_BAN,
                new PunishmentIssuer(issuerId(ctx.sender()), now),
                reasonOf(ctx),
                duration == null ? null : now.plus(duration),
                null,
                target.address());
        if (!support.tryPunish(ctx, target.name(), punishment)) {
            return;
        }
        support.announce(
                ctx,
                "ipban.announce",
                target.name(),
                Placeholder.unparsed(
                        "duration",
                        duration == null ? config.raw("permanent-duration") : DurationParser.format(duration)));
        enforce.accept(punishment);
    }

    private void revoke(CommandContext<CommandSender> ctx) {
        IpBanTarget target = ctx.get("target");
        repository
                .findActiveByAddress(target.address())
                .ifPresentOrElse(
                        active -> {
                            if (support.tryRevoke(ctx, target.name(), active)) {
                                support.announce(ctx, "unipban.announce", target.name());
                            }
                        },
                        () -> support.reply(ctx, "unipban.not-active", target.name()));
    }
}
