package io.github.hello09x.fakeplayer.core;

import com.google.common.base.Throwables;
import com.google.inject.AbstractModule;
import io.github.hello09x.fakeplayer.api.spi.NMSBridge;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.manager.invsee.DefaultInvseeImpl;
import io.github.hello09x.fakeplayer.core.manager.invsee.Invsee;
import io.github.hello09x.fakeplayer.core.manager.invsee.OpenInvInvseeImpl;
import org.bukkit.Bukkit;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class FakeplayerGuiceModule extends AbstractModule {

    private final Logger log = Main.getInstance().getLogger();

    @Override
    protected void configure() {
        super.bind(FakeplayerConfig.class).toProvider(this::fakeplayerConfig);
        super.bind(Invsee.class).toProvider(this::invsee);
        super.bind(NMSBridge.class).toInstance(nmsBridge());
    }

    private FakeplayerConfig fakeplayerConfig() {
        return new FakeplayerConfig(Main.getInstance(), "13");
    }

    private Invsee invsee() {
        if (Bukkit.getPluginManager().getPlugin("OpenInv") != null) {
            try {
                return new OpenInvInvseeImpl();
            } catch(Throwable e) {
                log.warning(Throwables.getStackTraceAsString(e));
            }
        }

        return new DefaultInvseeImpl();
    }

    private NMSBridge nmsBridge() {
        var bridge = ServiceLoader
                .load(NMSBridge.class, NMSBridge.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(NMSBridge::isSupported)
                .findAny()
                .orElse(null);

        if (bridge == null) {
            throw new ExceptionInInitializerError("Unsupported Minecraft version: " + Bukkit.getMinecraftVersion());
        }
        return bridge;
    }

}
