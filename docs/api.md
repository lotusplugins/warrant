---
title: Developer API
description: Events, lookups and model types for plugins that integrate with Warrant.
sidebar:
  order: 7
---

## Add Warrant to your project

Warrant is on Maven Central as `com.lotusplugins:warrant`. Use `compileOnly`, because the server loads Warrant itself.

```kotlin
dependencies {
    compileOnly("com.lotusplugins:warrant:<version>")
}
```

Add Warrant to your `plugin.yml` so it loads first:

```yaml
depend: [Warrant]
```

Use `softdepend` if your plugin works without it.

## Look up punishments

Get `WarrantApi` from the Bukkit services manager.

```java
var api = getServer().getServicesManager().load(WarrantApi.class);
```

Every method returns a `CompletableFuture` and runs off the main thread, because it reads the database. Move back to the main thread before you touch the Bukkit API.

| Method                                                       | Returns                          |
|--------------------------------------------------------------|----------------------------------|
| `findActive(UUID player, PunishmentType type)`               | The active punishment, if any    |
| `findActiveByAddress(InetAddress address)`                   | The active IP ban, if any        |
| `history(UUID player, Optional<PunishmentType> type, int offset, int limit)` | A `HistoryPage` with `entries` and `total` |
| `lastAddress(UUID player)`                                   | The last address the player joined from, if known |
| `playersByAddress(InetAddress address)`                      | UUIDs seen on that address       |

```java
api.findActive(player.getUniqueId(), PunishmentType.MUTE)
        .thenAccept(mute -> getServer().getScheduler().runTask(this, () -> {
            if (mute.isPresent()) {
                player.sendMessage("You are muted.");
            }
        }));
```

Expired and revoked punishments are not returned by `findActive`. If the database fails, the future completes with an exception, so add `.exceptionally(...)` if you need to react to it.

## Events

Warrant fires two cancellable events.

| Event                          | Fired when                    |
|--------------------------------|-------------------------------|
| `PlayerPunishedEvent`          | A punishment is about to be saved  |
| `PlayerPunishmentRevokedEvent` | A punishment is about to be revoked |

Both live in `com.lotusplugins.warrant.api.events` and expose `getPunishment()`. Cancelling the event stops the action.

:::caution
Commands run asynchronously, so these events usually fire off the main thread. Don't touch the Bukkit world directly from your listener.
:::

## Example

```java
import com.lotusplugins.warrant.api.events.PlayerPunishedEvent;
import com.lotusplugins.warrant.api.model.PunishmentType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PunishmentLogger implements Listener {

    @EventHandler
    public void onPunish(PlayerPunishedEvent event) {
        var punishment = event.getPunishment();
        if (punishment.type() == PunishmentType.BAN && punishment.reason() == null) {
            event.setCancelled(true);
        }
    }
}
```

## Model

`Punishment` is a record in `com.lotusplugins.warrant.api.model`.

| Field        | Type               | Notes                               |
|--------------|--------------------|-------------------------------------|
| `id`         | `UUID`             | Unique punishment ID                |
| `target`     | `UUID`             | The punished player                 |
| `type`       | `PunishmentType`   | `WARN`, `KICK`, `MUTE`, `BAN` or `IP_BAN` |
| `issuer`     | `PunishmentIssuer` | Issuer UUID and `issuedAt` instant  |
| `reason`     | `String`           | May be `null`                       |
| `expiry`     | `Instant`          | `null` means permanent              |
| `revocation` | `Revocation`       | `null` unless revoked               |
| `address`    | `InetAddress`      | Set for IP bans, otherwise `null`   |

`expired()` and `revoked()` are provided for convenience.
