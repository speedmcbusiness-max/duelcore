package net.duelcore.plugin;

import net.duelcore.plugin.config.ConfigManager;
import net.duelcore.plugin.database.DatabaseManager;
import net.duelcore.plugin.discord.DiscordManager;
import net.duelcore.plugin.hotbar.HotbarManager;
import net.duelcore.plugin.listeners.HotbarListener;
import net.duelcore.plugin.listeners.PlayerConnectionListener;
import net.duelcore.plugin.player.PlayerDataManager;
import net.duelcore.plugin.tiers.TierService;
import net.duelcore.plugin.leaderboard.LeaderboardService;
import net.duelcore.plugin.placeholders.DuelCorePlaceholders;
import net.duelcore.plugin.commands.VerifyCommand;
import net.duelcore.plugin.commands.DuelCoreAdminCommand;
import net.duelcore.plugin.commands.ResultCommand;
import net.duelcore.plugin.commands.ProfileCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DuelCorePlugin extends JavaPlugin {

    private static DuelCorePlugin instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private HotbarManager hotbarManager;
    private DiscordManager discordManager;
    private TierService tierService;
    private LeaderboardService leaderboardService;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Could not connect to the database. Disabling DuelCore.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        databaseManager.runSchema();

        this.playerDataManager = new PlayerDataManager(this);
        this.hotbarManager = new HotbarManager(this);
        this.tierService = new TierService(this);
        this.leaderboardService = new LeaderboardService(this);

        registerListeners();
        registerCommands();

        if (configManager.getConfig().getBoolean("discord.enabled")) {
            this.discordManager = new DiscordManager(this);
            this.discordManager.start();
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new DuelCorePlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        getLogger().info("DuelCore has been enabled.");
    }

    @Override
    public void onDisable() {
        if (discordManager != null) {
            discordManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("DuelCore has been disabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new HotbarListener(this), this);
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("verify")).setExecutor(new VerifyCommand(this));
        Objects.requireNonNull(getCommand("duelcoreadmin")).setExecutor(new DuelCoreAdminCommand(this));
        ResultCommand resultCommand = new ResultCommand(this);
        Objects.requireNonNull(getCommand("result")).setExecutor(resultCommand);
        Objects.requireNonNull(getCommand("result")).setTabCompleter(resultCommand);
        ProfileCommand profileCommand = new ProfileCommand(this);
        Objects.requireNonNull(getCommand("profile")).setExecutor(profileCommand);
        Objects.requireNonNull(getCommand("stats")).setExecutor(profileCommand);
        // /queue, /duel, /party, /leaderboard, /kiteditor, /duelsettings executors
        // are wired up in the follow-up GUI batch — the commands already exist
        // in plugin.yml so nothing else needs to change when they're added.
    }

    public static DuelCorePlugin get() {
        return instance;
    }

    public ConfigManager config() {
        return configManager;
    }

    public DatabaseManager database() {
        return databaseManager;
    }

    public PlayerDataManager players() {
        return playerDataManager;
    }

    public HotbarManager hotbar() {
        return hotbarManager;
    }

    public DiscordManager discord() {
        return discordManager;
    }

    public TierService tiers() {
        return tierService;
    }

    public LeaderboardService leaderboard() {
        return leaderboardService;
    }
}
