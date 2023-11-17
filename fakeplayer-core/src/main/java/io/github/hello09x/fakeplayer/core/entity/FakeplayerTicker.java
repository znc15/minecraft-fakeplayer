package io.github.hello09x.fakeplayer.core.entity;

import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

public class FakeplayerTicker extends BukkitRunnable {

    public final static long NO_REMOVE_AT = -1;

    @NotNull
    private final FakePlayer player;

    /**
     * 移除时间
     * <p>如果不需要定时移除则为 0</p>
     */
    private final long removeAt;

    private final FakeplayerManager manager = FakeplayerManager.instance;

    public FakeplayerTicker(
            @NotNull FakePlayer player,
            long lifespan
    ) {
        this.player = player;
        this.removeAt = lifespan > 0 ? System.currentTimeMillis() + lifespan : NO_REMOVE_AT;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            super.cancel();
            return;
        }

        if (removeAt != NO_REMOVE_AT && player.getTickCount() % 20 == 0 && System.currentTimeMillis() > removeAt) {
            manager.remove(player.getName(), "lifespan ends");
            player.getCreator().sendMessage(
                    textOfChildren(
                            text("假人 ", GRAY),
                            text(this.player.getName()),
                            text(" 存活时间到期, 已移除", GRAY)
                    )
            );
            super.cancel();
            return;
        }

        var handle = this.player.getHandle();
        var player = this.player.getPlayer();

        if (this.player.getTickCount() == 0) {
            // region 处理第一次生成时被别的插件干预然后随机传送
            var x = handle.getX();
            var y = handle.getY();
            var z = handle.getZ();

            // 将本 tick 的移动取消
            handle.setXo(x);
            handle.setYo(y);
            handle.setZo(z);

            handle.doTick();

            // clearFog 插件会在第一次传送的时候改变了玩家的位置, 因此必须进行一次传送
            player.teleport(new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch()));
            handle.absMoveTo(x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
            // endregion
        } else {
            handle.doTick();
        }
    }

    private @NotNull Player getBukkitPlayer() {
        return this.player.getPlayer();
    }

}
