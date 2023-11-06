package io.github.hello09x.fakeplayer.core.command;

import com.google.common.base.Throwables;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.bedrock.command.MessageException;
import io.github.hello09x.bedrock.page.Page;
import io.github.hello09x.bedrock.task.Tasks;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.util.Mth;
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
import org.joml.Math;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;

public class SpawnCommand extends AbstractCommand {

    public final static SpawnCommand instance = new SpawnCommand();
    private final static Logger log = Main.getInstance().getLogger();

    private final static MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final static DateTimeFormatter REMOVE_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static String toLocationString(@NotNull Location location) {
        return location.getWorld().getName()
                + ": "
                + StringUtils.joinWith(", ",
                Mth.floor(location.getX(), 0.5),
                Mth.floor(location.getY(), 0.5),
                Mth.floor(location.getZ(), 0.5));
    }

    public void spawn(@NotNull CommandSender sender, @NotNull CommandArguments args) {
        var name = (String) args.get("name");
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
            fakeplayerManager.spawnAsync(
                            sender,
                            Optional.ofNullable(name).map(String::trim).orElse(""),
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
                                message = MINI_MESSAGE.deserialize(
                                        "<gray>" + i18n.asString("fakeplayer.command.spawn.success.without-lifespan") + "</gray>",
                                        Placeholder.component("name", text(player.getName(), WHITE)),
                                        Placeholder.component("location", text(toLocationString(spawnpoint), WHITE))
                                );
                            } else {
                                message = MINI_MESSAGE.deserialize(
                                        "<gray>" + i18n.asString("fakeplayer.command.spawn.success.with-lifespan") + "</gray>",
                                        Placeholder.component("name", text(player.getName(), WHITE)),
                                        Placeholder.component("location", text(toLocationString(spawnpoint), WHITE)),
                                        Placeholder.component("remove-at", text(REMOVE_AT_FORMATTER.format(removedAt)))
                                );
                            }
                            sender.sendMessage(textOfChildren(message));
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

    public void kill(@NotNull CommandSender sender, @NotNull CommandArguments args) {
        @SuppressWarnings("unchecked")
        var targets = (List<Player>) args.get("names");
        if (targets == null) {
            var reserved = fakeplayerManager.getAll(sender);
            if (reserved.size() == 1) {
                targets = Collections.singletonList(reserved.get(0));
            } else {
                targets = Collections.emptyList();
            }
        }

        if (targets.isEmpty()) {
            sender.sendMessage(text(i18n.asString("fakeplayer.command.kill.error.non-removed"), GRAY));
            return;
        }

        var names = new StringJoiner(", ");
        for (var target : targets) {
            if (fakeplayerManager.remove(target.getName(), "command kill")) {
                names.add(target.getName());
            }
        }
        sender.sendMessage(textOfChildren(
                i18n.translate("fakeplayer.command.kill.success.removed", GRAY),
                space(),
                text(names.toString())
        ));
    }

    public void list(@NotNull CommandSender sender, @NotNull CommandArguments args) {
        var page = (int) args.getOptional("page").orElse(1);
        var size = (int) args.getOptional("size").orElse(10);

        var fakers = sender.isOp()
                ? fakeplayerManager.getAll()
                : fakeplayerManager.getAll(sender);

        var p = Page.of(fakers, page, size);

        var allowsTp = sender instanceof Player && sender.hasPermission(Permission.tp);
        sender.sendMessage(p.asComponent(
                text(i18n.asString("fakeplayer.command.list.title"), AQUA, BOLD),
                fakeplayer -> textOfChildren(
                        text(fakeplayer.getName() + " (" + fakeplayerManager.getCreatorName(fakeplayer) + ")", GOLD),
                        text(" - ", GRAY),
                        text(toLocationString(fakeplayer.getLocation()), WHITE),
                        allowsTp ? textOfChildren(space(), i18n.translate("fakeplayer.command.list.button.teleport", AQUA).clickEvent(runCommand("/fp tp " + fakeplayer.getName()))) : empty(),
                        textOfChildren(space(), i18n.translate("fakeplayer.command.list.button.kill", RED)).clickEvent(runCommand("/fp kill " + fakeplayer.getName()))
                ),
                i -> "/fp list " + i + " " + size
        ));
    }

    public void distance(
            @NotNull Player sender,
            @NotNull CommandArguments args
    ) throws WrapperCommandSyntaxException {
        var target = getTarget(sender, args);
        var from = target.getLocation().toBlockLocation();
        var to = sender.getLocation().toBlockLocation();

        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            sender.sendMessage(miniMessage.deserialize(
                    "<gray>" + i18n.asString("fakeplayer.command.distance.error.too-far") + "</gray>",
                    Placeholder.component("name", text(target.getName(), WHITE))
            ));
            return;
        }

        var euclidean = Mth.floor(from.distance(to), 0.5);
        var x = Math.abs(from.getBlockX() - to.getBlockX());
        var y = Math.abs(from.getBlockY() - to.getBlockY());
        var z = Math.abs(from.getBlockZ() - to.getBlockZ());

        sender.sendMessage(textOfChildren(
                miniMessage.deserialize(
                        "<gray>" + i18n.asString("fakeplayer.command.distance.baseline") + "</gray>",
                        Placeholder.component("name", text(target.getName(), WHITE))
                ),
                newline(),
                i18n.translate("fakeplayer.command.distance.euclidean", GRAY), space(), text(euclidean, WHITE), newline(),
                i18n.translate("fakeplayer.command.distance.x", GRAY), space(), text(x, WHITE), newline(),
                i18n.translate("fakeplayer.command.distance.y", GRAY), space(), text(y, WHITE), newline(),
                i18n.translate("fakeplayer.command.distance.z", GRAY), space(), text(z, WHITE)
        ));
    }

}
