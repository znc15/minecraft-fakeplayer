package io.github.hello09x.fakeplayer.v1_20_R2.spi;

import io.github.hello09x.fakeplayer.api.spi.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Set;

public class NMSBridgeImpl implements NMSBridge {

    // 删除 SUPPORTS 集合
    // private final static Set<String> SUPPORTS = Set.of("1.20.2");

    @Override
    public @NotNull NMSEntity fromEntity(@NotNull Entity entity) {
        return new NMSEntityImpl(entity);
    }

    @Override
    public @NotNull NMSServer fromServer(@NotNull Server server) {
        return new NMSServerImpl(server);
    }

    @Override
    public @NotNull NMSServerLevel fromWorld(@NotNull World world) {
        return new NMSServerLevelImpl(world);
    }

    @Override
    public @NotNull NMSServerPlayer fromPlayer(@NotNull Player player) {
        return new NMSServerPlayerImpl(player);
    }

    @Override
    public @NotNull NMSNetwork createNetwork(@NotNull InetAddress address) {
        return new NMSNetworkImpl(address);
    }

    @Override
    public boolean isSupported() {
        // 移除版本检测
        // return SUPPORTS.contains(Bukkit.getMinecraftVersion());
        return true;
    }

    @Override
    public @NotNull ActionTicker createAction(@NotNull Player player, @NotNull Action.ActionType action, @NotNull Action.ActionSetting setting) {
        return new ActionTickerImpl(player, action, setting);
    }
}

