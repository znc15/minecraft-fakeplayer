package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.common.base.Throwables;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.bedrock.command.MessageException;
import io.github.hello09x.bedrock.task.Tasks;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.util.Mth;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SpawnCommand extends AbstractCommand {

    public final static SpawnCommand instance = new SpawnCommand();

    private final static DateTimeFormatter REMOVE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static String toLocationString(@NotNull Location location) {
        return location.getWorld().getName()
                + ": "
                + StringUtils.joinWith(", ",
                Mth.floor(location.getX(), 0.5),
                Mth.floor(location.getY(), 0.5),
                Mth.floor(location.getZ(), 0.5));
    }

    /**
     * 交换主副手物品
     */
    public void spawn(@NotNull CommandSender sender, @NotNull CommandArguments args) {
        var name = (String) args.get("name");
        if (name != null && name.isEmpty()) {
            name = null;
        }
        var world = (World) args.get("world");
        var location = (Location) args.get("location");

        Location spawnpoint;
        if (world == null || location == null) {
            spawnpoint = sender instanceof Player p
                    ? p.getLocation().clone()
                    : Bukkit.getWorlds().get(0).getSpawnLocation().clone();
        } else {
            spawnpoint = new Location(
                    world,
                    location.getX(),
                    location.getY(),
                    location.getZ()
            );
        }

        var removedAt = Optional.ofNullable(config.getLifespan()).map(lifespan -> LocalDateTime.now().plus(lifespan)).orElse(null);
        try {
            manager.spawnAsync(
                            sender,
                            name,
                            spawnpoint,
                            removedAt
                    )
                    .thenAccept(player -> {
                        if (player == null) {
                            return;
                        }
                        Tasks.run(() -> {
                            Component message;
                            if (removedAt == null) {
                                message = miniMessage.deserialize(
                                        "<gray>" + i18n.asString("fakeplayer.command.spawn.success.without-lifespan") + "</gray>",
                                        Placeholder.component("name", text(player.getName(), WHITE)),
                                        Placeholder.component("location", text(toLocationString(spawnpoint), WHITE))
                                );
                            } else {
                                message = miniMessage.deserialize(
                                        "<gray>" + i18n.asString("fakeplayer.command.spawn.success.with-lifespan") + "</gray>",
                                        Placeholder.component("name", text(player.getName(), WHITE)),
                                        Placeholder.component("location", text(toLocationString(spawnpoint), WHITE)),
                                        Placeholder.component("remove-at", text(REMOVE_AT_FORMATTER.format(removedAt)))
                                );
                            }
                            sender.sendMessage(textOfChildren(message));

                            if (sender instanceof Player p && manager.countByCreator(sender) == 1) {
                                // 有些命令在有假人的时候才会显示, 因此需要强制刷新一下
                                p.updateCommands();
                            }

                        }, Main.getInstance());
                    }).exceptionally(e -> {
                        if (e instanceof MessageException me) {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> sender.sendMessage(me.asComponent()));
                        } else if (e.getCause() != null && e.getCause() instanceof MessageException me) {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> sender.sendMessage(me.asComponent()));
                        } else {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> sender.sendMessage(i18n.translate("fakeplayer.command.spawn.error.unknown", RED)));
                            log.severe(Throwables.getStackTraceAsString(e));
                        }
                        return null;
                    });
        } catch (MessageException e) {
            sender.sendMessage(e.asComponent());
        }
    }


}