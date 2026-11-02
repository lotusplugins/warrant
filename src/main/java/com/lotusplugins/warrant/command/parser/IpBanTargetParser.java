package com.lotusplugins.warrant.command.parser;

import com.google.common.net.InetAddresses;
import com.lotusplugins.warrant.command.IpBanTarget;
import com.lotusplugins.warrant.storage.PunishmentRepository;
import com.lotusplugins.warrant.storage.StorageException;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.bukkit.parser.OfflinePlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class IpBanTargetParser implements ArgumentParser<CommandSender, IpBanTarget> {

    private final PunishmentRepository repository;
    private final ArgumentParser<CommandSender, OfflinePlayer> players =
            OfflinePlayerParser.<CommandSender>offlinePlayerParser().parser();

    private IpBanTargetParser(PunishmentRepository repository) {
        this.repository = repository;
    }

    public static ParserDescriptor<CommandSender, IpBanTarget> ipBanTargetParser(PunishmentRepository repository) {
        return ParserDescriptor.of(new IpBanTargetParser(repository), IpBanTarget.class);
    }

    @Override
    public ArgumentParseResult<IpBanTarget> parse(CommandContext<CommandSender> context, CommandInput input) {
        String token = input.peekString();
        if (InetAddresses.isInetAddress(token)) {
            input.readString();
            return ArgumentParseResult.success(IpBanTarget.of(InetAddresses.forString(token)));
        }
        var parsed = players.parse(context, input);
        if (parsed.failure().isPresent()) {
            return ArgumentParseResult.failure(parsed.failure().get());
        }
        OfflinePlayer player = parsed.parsedValue().orElseThrow();
        try {
            return repository
                    .findLastAddress(player.getUniqueId())
                    .map(address -> ArgumentParseResult.success(IpBanTarget.of(player, address)))
                    .orElseGet(() -> ArgumentParseResult.failure(
                            new IllegalArgumentException("No known IP address for " + token)));
        } catch (StorageException e) {
            return ArgumentParseResult.failure(e);
        }
    }

    @Override
    public SuggestionProvider<CommandSender> suggestionProvider() {
        return players.suggestionProvider();
    }
}
