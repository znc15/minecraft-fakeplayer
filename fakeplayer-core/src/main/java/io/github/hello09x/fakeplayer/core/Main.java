package io.github.hello09x.fakeplayer.core;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.github.hello09x.bedrock.i18n.I18n;
import io.github.hello09x.bedrock.i18n.I18nSupported;
import io.github.hello09x.bedrock.util.RegistrablePlugin;
import io.github.hello09x.fakeplayer.api.spi.NMSBridge;
import io.github.hello09x.fakeplayer.core.command.CommandRegistry;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.listener.FakeplayerListener;
import io.github.hello09x.fakeplayer.core.listener.PlayerListeners;
import io.github.hello09x.fakeplayer.core.listener.ReplenishListener;
import io.github.hello09x.fakeplayer.core.manager.WildFakeplayerManager;
import io.github.hello09x.fakeplayer.core.util.update.UpdateChecker;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class Main extends RegistrablePlugin implements I18nSupported {

    @Getter
    private static Main instance;

    private I18n i18n;

    private Injector injector;

    @Inject
    FakeplayerConfig config;

    @Inject
    WildFakeplayerManager wildFakeplayerManager;

    @Inject
    PlayerListeners playerListeners;

    @Inject
    FakeplayerListener fakeplayerListener;

    @Inject
    ReplenishListener replenishListener;

    @Override
    public void onLoad() {
        instance = this;
        injector = Guice.createInjector(new FakeplayerGuiceModule());
        this.i18n = new I18n(this, "message/message");
    }

    @Override
    public void onEnable() {
        injector.injectMembers(this);
        injector.getInstance(CommandRegistry.class).register();
        {
            var messenger = getServer().getMessenger();
            messenger.registerIncomingPluginChannel(this, "BungeeCord", wildFakeplayerManager);
            messenger.registerOutgoingPluginChannel(this, "BungeeCord");
        }

        {
            var manager = getServer().getPluginManager();
            manager.registerEvents(playerListeners, this);
            manager.registerEvents(fakeplayerListener, this);
            manager.registerEvents(replenishListener, this);
        }

        if (config.isCheckForUpdates()) {
            checkForUpdatesAsync();
        }
    }

    public void checkForUpdatesAsync() {
        CompletableFuture.runAsync(() -> {
            var meta = getPluginMeta();
            var checker = new UpdateChecker("tanyaofei", "minecraft-fakeplayer");
            try {
                var release = checker.getLastRelease();

                var current = meta.getVersion();
                var other = release.getTagName();
                if (other.charAt(0) == 'v') {
                    other = other.substring(1);
                }

                if (UpdateChecker.isNew(current, other)) {
                    var log = getLogger();
                    log.info("检测到新的版本: " + release.getTagName());
                    log.info("前往此处下载 " + meta.getWebsite());
                    log.info("更新日志");
                    for (var line : release.getBody().split("\n")) {
                        log.info("\t" + line);
                    }
                }

            } catch (Throwable e) {
                getLogger().warning("检测新版本发生异常: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        super.onDisable();
        {
            var messenger = getServer().getMessenger();
            messenger.unregisterIncomingPluginChannel(this);
            messenger.unregisterOutgoingPluginChannel(this);
        }
    }

    @Override
    public @NotNull ClassLoader classLoader() {
        return getClassLoader();
    }

    public static @NotNull I18n getI18n() {
        return instance.i18n;
    }

    public static @NotNull Injector getInjector() {
        return instance.injector;
    }

}
