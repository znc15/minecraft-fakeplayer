package io.github.hello09x.fakeplayer.core.manager;

import io.github.hello09x.bedrock.command.MessageException;
import io.github.hello09x.bedrock.task.Tasks;
import io.github.hello09x.fakeplayer.api.action.ActionSetting;
import io.github.hello09x.fakeplayer.api.action.ActionType;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.entity.FakePlayer;
import io.github.hello09x.fakeplayer.core.entity.SpawnOption;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;
import io.github.hello09x.fakeplayer.core.manager.naming.NameManager;
import io.github.hello09x.fakeplayer.core.manager.naming.SequenceName;
import io.github.hello09x.fakeplayer.core.manager.naming.exception.IllegalCustomNameException;
import io.github.hello09x.fakeplayer.core.repository.UsedIdRepository;
import io.github.hello09x.fakeplayer.core.repository.UserConfigRepository;
import io.github.hello09x.fakeplayer.core.repository.model.Configs;
import io.github.hello09x.fakeplayer.core.util.AddressUtils;
import io.github.hello09x.fakeplayer.core.util.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

public class FakeplayerManager {

    public final static FakeplayerManager instance = new FakeplayerManager();

    private final static Logger log = Main.getInstance().getLogger();

    private final FakeplayerConfig config = FakeplayerConfig.instance;

    private final UsedIdRepository usedIdRepository = UsedIdRepository.instance;

    private final NameManager nameManager = NameManager.instance;

    private final FakeplayerList playerList = FakeplayerList.instance;

    private final UserConfigRepository userConfigRepository = UserConfigRepository.instance;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    private FakeplayerManager() {
        timer.scheduleAtFixedRate(() -> {
                    if (Bukkit.getServer().getTPS()[1] < config.getKaleTps()) {
                        Tasks.run(() -> {
                            if (removeAll("low tps") > 0) {
                                Bukkit.broadcast(text("[服务器过于卡顿, 已移除所有假人]", RED, ITALIC));
                            }
                        }, Main.getInstance());
                    }
                }, 0, 60, TimeUnit.SECONDS
        );
    }

    /**
     * 创建一个假人
     *
     * @param creator 创建者
     * @param spawnAt 生成地点
     */
    public @NotNull CompletableFuture<Player> spawnAsync(
            @NotNull CommandSender creator,
            @NotNull String name,
            @NotNull Location spawnAt,
            @Nullable LocalDateTime removeAt
    ) {
        this.checkLimit(creator);

        SequenceName sn;
        try {
            sn = name.isBlank() ? nameManager.register(creator) : nameManager.custom(creator, name);
        } catch (IllegalCustomNameException e) {
            throw new MessageException(e.getText());
        }

        var fp = new FakePlayer(
                creator,
                AddressUtils.getAddress(creator),
                sn,
                removeAt
        );

        var player = fp.getPlayer();
        player.playerListName(text(player.getName(), GRAY, ITALIC));

        return CompletableFuture
                .supplyAsync(() -> {
                    boolean invulnerable = Configs.invulnerable.defaultValue(),
                            lookAtEntity = Configs.look_at_entity.defaultValue(),
                            collidable = Configs.collidable.defaultValue(),
                            pickupItems = Configs.pickup_items.defaultValue(),
                            skin = Configs.skin.defaultValue();

                    if (creator instanceof Player p) {
                        var creatorId = p.getUniqueId();
                        invulnerable = userConfigRepository.selectOrDefault(creatorId, Configs.invulnerable);
                        lookAtEntity = userConfigRepository.selectOrDefault(creatorId, Configs.look_at_entity);
                        collidable = userConfigRepository.selectOrDefault(creatorId, Configs.collidable);
                        pickupItems = userConfigRepository.selectOrDefault(creatorId, Configs.pickup_items);
                        skin = userConfigRepository.selectOrDefault(creatorId, Configs.skin);
                    }

                    return new SpawnOption(
                            spawnAt,
                            invulnerable,
                            collidable,
                            lookAtEntity,
                            pickupItems,
                            skin
                    );
                })
                .thenApply(option -> fp.spawnAsync(option).join())
                .thenRunAsync(() -> {
                    Tasks.run(() -> {
                        playerList.add(fp);
                        usedIdRepository.add(player.getUniqueId());
                    }, Main.getInstance());

                    Tasks.run(() -> {
                        dispatchCommands(player, config.getPreparingCommands());
                        performCommands(player, config.getSelfCommands());
                    }, Main.getInstance(), 20);
                }).thenApply(ignored -> player);
    }

    /**
     * 获取一个假人
     *
     * @param creator 创建者
     * @param name    假人名称
     * @return 假人
     */
    public @Nullable Player get(@NotNull CommandSender creator, @NotNull String name) {
        return Optional
                .ofNullable(playerList.getByName(name))
                .filter(p -> p.getCreator().equals(creator))
                .map(FakePlayer::getPlayer)
                .orElse(null);
    }

    /**
     * 根据名称获取假人
     *
     * @param name 名称
     * @return 假人
     */
    public @Nullable Player get(@NotNull String name) {
        return Optional
                .ofNullable(playerList.getByName(name))
                .map(FakePlayer::getPlayer)
                .orElse(null);
    }

    /**
     * 获取一个假人的创建者, 如果这个玩家不是假人, 则为 {@code null}
     *
     * @param player 假人
     * @return 假人的创建者
     */
    public @Nullable String getCreatorName(@NotNull Player player) {
        return Optional
                .ofNullable(playerList.getByUUID(player.getUniqueId()))
                .map(FakePlayer::getCreator)
                .map(CommandSender::getName)
                .orElse(null);
    }

    /**
     * 根据名称删除假人
     *
     * @param name   名称
     * @param reason 原因
     * @return 是否删除成功
     */
    public boolean remove(@NotNull String name, @Nullable String reason) {
        return this.remove(name, reason == null ? null : text(reason));
    }

    /**
     * 根据名称删除假人
     *
     * @param name   名称
     * @param reason 原因
     * @return 是否移除成功
     */
    public boolean remove(@NotNull String name, @Nullable Component reason) {
        var player = this.get(name);
        if (player == null) {
            return false;
        }

        player.kick(textOfChildren(
                text("[fakeplayer] "),
                reason == null ? text("removed") : reason
        ));
        return true;
    }

    /**
     * 移除所有假人
     *
     * @return 移除的假人数量
     */
    public int removeAll(@Nullable String reason) {
        var fakers = getAll();
        for (var f : fakers) {
            f.kick(text("[fakeplayer] " + (reason == null ? "removed" : reason)));
        }
        return fakers.size();
    }

    /**
     * @return 获取所有假人
     */
    public @NotNull List<Player> getAll() {
        return playerList.getAll().stream().map(FakePlayer::getPlayer).toList();
    }

    /**
     * 清理假人
     *
     * @param player 假人
     */
    public void cleanup(@NotNull Player player) {
        var fakeplayer = playerList.removeByUUID(player.getUniqueId());
        if (fakeplayer == null) {
            return;
        }
        nameManager.unregister(fakeplayer.getSequenceName());
        if (config.isDropInventoryOnQuiting()) {
            ActionManager.instance.setAction(fakeplayer.getPlayer(), ActionType.DROP_INVENTORY, ActionSetting.once());
        }
    }

    /**
     * 获取创建者创建的所有假人
     *
     * @param creator 创建者
     * @return 创建者创建的假人
     */
    public @NotNull List<Player> getAll(@NotNull CommandSender creator) {
        return playerList.getByCreator(creator.getName()).stream().map(FakePlayer::getPlayer).toList();
    }

    /**
     * 判断一名玩家是否是假人
     *
     * @param player 玩家
     * @return 是否是假人
     */
    public boolean isFake(@NotNull Player player) {
        return playerList.getByUUID(player.getUniqueId()) != null;
    }

    /**
     * 获取 IP 地址创建着多少个假人
     *
     * @param address IP 地址
     * @return 该 IP 地址创建着多少个假人
     */
    public long countByAddress(@NotNull String address) {
        return playerList
                .stream()
                .filter(p -> p.getCreatorIp().equals(address))
                .count();
    }

    /**
     * 以假人身份执行命令
     *
     * @param player   假人
     * @param commands 命令
     */
    public void performCommands(@NotNull Player player, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        if (!isFake(player)) {
            return;
        }

        for (var cmd : Commands.formatCommands(commands)) {
            if (!player.performCommand(cmd)) {
                log.warning("执行命令失败: " + cmd);
            }
        }
    }

    /**
     * 以控制台身份对玩家执行命令
     *
     * @param player   假人
     * @param commands 命令
     */
    public void dispatchCommands(@NotNull Player player, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }

        if (!isFake(player)) {
            return;
        }

        var server = Bukkit.getServer();
        var sender = Bukkit.getConsoleSender();
        for (var cmd : Commands.formatCommands(
                commands,
                "%p", player.getName(),
                "%u", player.getUniqueId().toString(),
                "%c", Objects.requireNonNull(getCreatorName(player)))
        ) {
            if (!server.dispatchCommand(sender, cmd)) {
                log.warning("执行命令失败: " + cmd);
            }
        }
    }

    public void onDisable() {
        this.timer.shutdown();
    }

    private void checkLimit(@NotNull CommandSender creator) throws MessageException {
        if (creator.isOp()) {
            return;
        }

        if (this.playerList.count() >= config.getServerLimit()) {
            throw new MessageException("服务器假人数量已达上限");
        }

        if (this.playerList.getByCreator(creator.getName()).size() >= config.getPlayerLimit()) {
            throw new MessageException("你创建的假人数量已达上限");
        }

        if (config.isDetectIp() && this.countByAddress(AddressUtils.getAddress(creator)) >= config.getPlayerLimit()) {
            throw new MessageException("你所在 IP 创建的假人已达上限");
        }
    }

}
