package io.github.hello09x.fakeplayer.manager;

import com.google.common.base.Throwables;
import io.github.hello09x.fakeplayer.Main;
import io.github.hello09x.fakeplayer.entity.FakePlayer;
import io.github.hello09x.fakeplayer.properties.FakeplayerProperties;
import io.github.hello09x.fakeplayer.util.AddressUtils;
import io.github.hello09x.fakeplayer.util.SeedUUID;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

public class FakePlayerManager {

    public final static FakePlayerManager instance = new FakePlayerManager();

    private final static Logger log = Main.getInstance().getLogger();

    private final static String META_KEY_CREATOR = "fakeplayer:creator";

    private final static String META_KEY_CREATOR_IP = "fakeplayer:creator-ip";

    private final FakeplayerProperties properties = FakeplayerProperties.instance;

    /**
     * 命名计数器
     * key: 创建者名称
     * value: 创建数
     */
    private final Map<String, Integer> nameCounter = new HashMap<>();

    private final SeedUUID idGenerator = new SeedUUID(String.valueOf(Bukkit.getServer().getWorlds().get(0).getSeed()));

    public FakePlayerManager() {
        // 服务器 tps 过低删除所有假人
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (Bukkit.getServer().getTPS()[1] < properties.getKaleTps()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (removeFakePlayers() > 0) {
                                Bukkit.getServer().broadcast(text("[服务器过于卡顿, 已删除所有假人]").style(Style.style(RED, ITALIC)));
                            }
                        }
                    }.runTask(Main.getInstance());
                }
            }
        }, 60_000, 60_000);
    }

    /**
     * 创建一个假人
     *
     * @param creator 创建者
     * @param spawnAt 生成地点
     */
    public synchronized void spawnFakePlayer(
            @NotNull CommandSender creator,
            @NotNull Location spawnAt
    ) {
        var playerLimit = properties.getPlayerLimit();
        if (!creator.isOp() && playerLimit != Integer.MAX_VALUE && getFakePlayers(creator).size() >= playerLimit) {
            creator.sendMessage(text("你创建的假人数量已达到上限...", RED));
            return;
        }

        var serverLimit = properties.getServerLimit();
        if (!creator.isOp() && serverLimit != Integer.MAX_VALUE && getFakePlayers().size() >= serverLimit) {
            creator.sendMessage(text("服务器假人数量已达到上限...", RED));
            return;
        }

        if (!creator.isOp() && properties.isDetectIp() && countByAddress(AddressUtils.getAddress(creator)) >= 1) {
            creator.sendMessage(text("你所在 IP 创建的假人数量已达到上限...", RED));
            return;
        }

        var faker = new FakePlayer(
                creator.getName(),
                ((CraftServer) Bukkit.getServer()).getServer(),
                ((CraftWorld) spawnAt.getWorld()).getHandle(),
                generateId(),
                generateName(creator),
                spawnAt
        ).spawn(properties.getTickPeriod());

        faker.setMetadata(META_KEY_CREATOR, new FixedMetadataValue(Main.getInstance(), creator.getName()));
        faker.setMetadata(META_KEY_CREATOR_IP, new FixedMetadataValue(Main.getInstance(), AddressUtils.getAddress(creator)));
        faker.playerListName(text(creator.getName() + "的假人").style(Style.style(GRAY, ITALIC)));

        dispatchCommands(faker, properties.getPreparingCommands());
    }

    public @Nullable Player getFakePlayer(@NotNull CommandSender creator, @NotNull String name) {
        return Optional
                .ofNullable(getFakePlayer(name))
                .filter(faker -> Objects.equals(this.getCreator(faker), creator.getName()))
                .orElse(null);
    }

    /**
     * 根据名称获取假人
     *
     * @param name 名称
     * @return 假人
     */
    public @Nullable Player getFakePlayer(@NotNull String name) {
        return Optional
                .ofNullable(Bukkit.getServer().getPlayer(name))
                .filter(this::isFakePlayer)
                .orElse(null);
    }

    /**
     * 移除指定创建者创建的假人
     *
     * @param creator 创建者
     * @return 移除假人的数量
     */
    public int removeFakePlayers(@NotNull CommandSender creator) {
        var fakers = getFakePlayers(creator);
        fakers.forEach(Player::kick);
        synchronized (nameCounter) {
            nameCounter.remove(creator.getName());
        }
        return fakers.size();
    }

    /**
     * 根据名称删除假人
     *
     * @param name 名称
     * @return 名称对应的玩家不在线或者不是假人
     */
    public boolean removeFakePlayer(@NotNull String name) {
        var faker = Optional.ofNullable(getFakePlayer(name)).filter(this::isFakePlayer);
        if (faker.isPresent()) {
            faker.get().kick();
            return true;
        }
        return false;
    }

    /**
     * 获取一个假人的创建者, 如果这个玩家不是假人, 则为 {@code null}
     *
     * @param fakePlayer 假人
     * @return 假人的创建者
     */
    public @Nullable String getCreator(@NotNull Player fakePlayer) {
        return fakePlayer
                .getMetadata(META_KEY_CREATOR)
                .stream()
                .findFirst()
                .map(MetadataValue::asString)
                .orElse(null);
    }

    /**
     * 移除所有假人
     *
     * @return 移除的假人数量
     */
    public int removeFakePlayers() {
        var fakers = getFakePlayers();
        fakers.forEach(Player::kick);
        synchronized (nameCounter) {
            nameCounter.clear();
        }
        return fakers.size();
    }

    /**
     * @return 获取所有假人
     */
    public @NotNull List<Player> getFakePlayers() {
        return Bukkit
                .getServer()
                .getOnlinePlayers()
                .stream()
                .filter(p -> !p.getMetadata(META_KEY_CREATOR).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 获取创建者创建的所有假人
     *
     * @param creator 创建者
     * @return 创建者创建的假人
     */
    public @NotNull List<Player> getFakePlayers(@NotNull CommandSender creator) {
        var name = creator.getName();
        return Bukkit
                .getServer()
                .getOnlinePlayers()
                .stream()
                .filter(p -> p.getMetadata(META_KEY_CREATOR)
                        .stream()
                        .anyMatch(meta -> meta.asString().equals(name)))
                .collect(Collectors.toList());
    }

    /**
     * 判断一名玩家是否是假人
     *
     * @param player 玩家
     * @return 是否是假人
     */
    public boolean isFakePlayer(@NotNull Player player) {
        return !player.getMetadata(META_KEY_CREATOR).isEmpty();
    }

    public long countByAddress(@NotNull String address) {
        return Bukkit.getServer()
                .getOnlinePlayers()
                .stream()
                .filter(p -> p.getMetadata(META_KEY_CREATOR_IP).stream().anyMatch(meta -> meta.asString().equals(address)))
                .count();
    }

    private @NotNull UUID generateId() {
        int maxTries = 5;
        while (maxTries > 0) {
            var id = idGenerator.uuid();
            if (Bukkit.getServer().getPlayer(id) == null) {
                return id;
            }
            maxTries--;
        }
        return UUID.randomUUID();
    }

    private @NotNull
    String generateName(CommandSender creator) {
        var base = properties.getNameTemplate();
        if (base.isBlank()) {
            base = creator.getName();
        }

        int count;
        synchronized (nameCounter) {
            count = nameCounter.getOrDefault(creator.getName(), 0);
        }

        var suffix = "_" + (++count);
        nameCounter.put(creator.getName(), count);

        String name;
        if (base.length() + suffix.length() > 16) {
            name = base.substring(0, (16 - suffix.length()));
        } else {
            name = base;
        }
        name = name + suffix;
        return name;
    }

    public boolean dispatchCommands(@NotNull Player player, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return true;
        }

        if (!isFakePlayer(player)) {
            return false;
        }

        var server = Bukkit.getServer();
        var sender = Bukkit.getConsoleSender();
        for (var cmd : properties.getPreparingCommands()) {
            cmd = cmd.trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (cmd.length() > 1) {
                cmd = cmd
                        .replaceAll("%p", player.getName())
                        .replaceAll("%u", player.getUniqueId().toString());
            }

            if (cmd.isBlank()) {
                continue;
            }

            try {
                server.dispatchCommand(sender, cmd);
            } catch (Throwable e) {
                log.warning(Throwables.getStackTraceAsString(e));
            }
        }

        return true;
    }


}
