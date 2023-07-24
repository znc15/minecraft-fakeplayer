package io.github.hello09x.fakeplayer;

import io.github.hello09x.fakeplayer.command.RootCommand;
import io.github.hello09x.fakeplayer.listener.*;
import io.github.hello09x.fakeplayer.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.repository.UsedIdRepository;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    @Getter
    private static Main instance;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;

        {
            getServer().getPluginCommand("fakeplayer").setExecutor(RootCommand.instance);
        }

        registerListeners();
    }

    @Override
    public void onDisable() {
        FakeplayerManager.instance.removeAll();
        UsedIdRepository.instance.save();
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(PlayerListeners.instance, this);
    }

}