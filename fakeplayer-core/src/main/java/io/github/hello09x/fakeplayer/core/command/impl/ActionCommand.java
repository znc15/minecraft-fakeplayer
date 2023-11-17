package io.github.hello09x.fakeplayer.core.command.impl;

import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.CommandExecutor;
import io.github.hello09x.fakeplayer.api.action.ActionSetting;
import io.github.hello09x.fakeplayer.api.action.ActionType;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ActionCommand extends AbstractCommand {

    public final static ActionCommand instance = new ActionCommand();

    private final ActionManager actionManager = ActionManager.instance;

    public @NotNull CommandExecutor action(@NotNull ActionType action, @NotNull ActionSetting setting) {
        return (sender, args) -> action(sender, args, action, setting.clone());
    }

    /**
     * 执行动作
     */
    public void action(
            @NotNull CommandSender sender,
            @NotNull CommandArguments args,
            @NotNull ActionType action,
            @NotNull ActionSetting setting
    ) throws WrapperCommandSyntaxException {
        var target = super.getTarget(sender, args);
        actionManager.setAction(target, action, setting);

        String translationKey;
        if (setting.equals(ActionSetting.stop())) {
            translationKey = "fakeplayer.command.action.stop";
        } else if (setting.equals(ActionSetting.once())) {
            translationKey = "fakeplayer.command.action.once";
        } else {
            translationKey = "fakeplayer.command.action.continuous";
        }

        sender.sendMessage(i18n.translate(
                translationKey,
                GRAY,
                Placeholder.component("name", text(target.getName(), WHITE)),
                Placeholder.component("action", i18n.translate(action, WHITE))
        ));
    }

}
