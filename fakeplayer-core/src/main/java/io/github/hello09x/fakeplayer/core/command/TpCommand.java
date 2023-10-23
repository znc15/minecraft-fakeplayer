package io.github.hello09x.fakeplayer.core.command;

import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.util.Teleportor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

public class TpCommand extends AbstractCommand {

    public final static TpCommand instance = new TpCommand();

    public void tp(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var target = getTarget(sender, args);
        this.teleport(sender, sender, target);
    }

    public void tphere(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var target = getTarget(sender, args);
        this.teleport(sender, target, sender);
    }

    public void tps(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var target = getTarget(sender, args);

        var l1 = sender.getLocation().clone();
        var l2 = target.getLocation().clone();

        Teleportor.teleportAndSound(target, l1);
        Teleportor.teleportAndSound(sender, l2);
    }

    private void teleport(@NotNull CommandSender sender, @NotNull Player from, @NotNull Player to) {
        if (!Teleportor.teleportAndSound(from, to.getLocation())) {
            sender.sendMessage(text("传送失败: 可能由于第三方插件影响", GRAY));
        }
    }

}
