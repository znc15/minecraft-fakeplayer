package io.github.hello09x.fakeplayer.entity;

import com.mojang.authlib.GameProfile;
import io.github.hello09x.fakeplayer.Main;
import io.github.hello09x.fakeplayer.core.EmptyConnection;
import io.github.hello09x.fakeplayer.core.EmptyNetworkManager;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FakePlayer extends ServerPlayer {

    private final UUID uniqueId;

    private final String name;

    private final Location spawnLocation;

    public FakePlayer(
            MinecraftServer server,
            ServerLevel world,
            UUID uniqueId,
            String name,
            Location at
    ) {
        super(server, world, new GameProfile(uniqueId, name));
        this.uniqueId = uniqueId;
        this.name = name;
        this.spawnLocation = at;

        try {
            var networkManager = new EmptyNetworkManager(PacketFlow.CLIENTBOUND);
            this.connection = new EmptyConnection(server, networkManager, this);
            networkManager.setListener(this.connection);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isChunkLoaded(Location at) {
        if (at.getWorld() == null) {
            return false;
        }
        int x = at.getBlockX() >> 4;
        int z = at.getBlockZ() >> 4;
        return at.getWorld().isChunkLoaded(x, z);
    }

    public Player prepare() {
        addToPlayerList();
        spawn();

        var p = Objects.requireNonNull(Bukkit.getPlayer(this.uuid));
        p.setSleepingIgnored(true);
        p.setPersistent(false);

        new BukkitRunnable() {
            @Override
            public void run() {
                doTick();
                tickCount++;
            }
        }.runTaskTimer(Main.getInstance(), 0, 1);
        return p;
    }

    @SuppressWarnings("all")
    public void addToPlayerList() {
        var packet = new ServerboundClientInformationPacket(
                "EN",
                10,
                ChatVisiblity.FULL,
                false,
                0,
                HumanoidArm.LEFT,
                false,
                true
        );
        this.updateOptions(packet);

        var entity = this.getBukkitEntity();
        var handle = (ServerPlayer) ((CraftEntity) entity).getHandle();
        if (handle.level() == null) {
            return;
        }
        var playerList = handle.level().players();
        if (!playerList.contains(handle)) {
            ((List) playerList).add(handle);
        }

        try {
            var updatePlayerStatus = ChunkMap.class.getDeclaredMethod(
                    "a",
                    ServerPlayer.class,
                    boolean.class
            );
            updatePlayerStatus.setAccessible(true);
            updatePlayerStatus.invoke(
                    ((ServerLevel) handle.level()).getChunkSource().chunkMap,
                    handle,
                    true
            );
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        for(var online: Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) online).getHandle().connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, handle));
            ((CraftPlayer) online).getHandle().connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, handle));
        }

    }

    public void spawn() {
        var entity = this.getBukkitEntity();
        var handle = (ServerPlayer) ((CraftEntity) entity).getHandle();

        if (!isChunkLoaded(spawnLocation)) {
            spawnLocation.getChunk().load();
        }
        handle.level().addFreshEntity(handle, CreatureSpawnEvent.SpawnReason.CUSTOM);

        ((CraftServer) Bukkit.getServer()).getHandle().respawn(
                this,
                true,
                PlayerRespawnEvent.RespawnReason.PLUGIN
        );

        // move directly
        getBukkitEntity().teleport(spawnLocation);
    }


}
