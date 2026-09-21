package dev.wsantacruz.hardcorereset;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class HardcoreReset extends JavaPlugin {

    private ResetManager resetManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();
        String arenaPrefix = cfg.getString("arena-world", "arena");

        resetManager = new ResetManager(
                this,
                arenaPrefix,
                cfg.getString("current-arena", arenaPrefix),
                cfg.getString("hub-world", getServer().getWorlds().get(0).getName()),
                cfg.getInt("reset-delay-seconds", 5),
                cfg.getLong("seed", 0L),
                cfg.getBoolean("broadcast", true),
                cfg.getString("resource-pack-url", ""),
                cfg.getString("resource-pack-sha1", ""),
                cfg.getString("death-sound", ""),
                cfg.getDouble("death-sound-volume", 1.0),
                cfg.getDouble("death-sound-pitch", 1.0)
        );

        World arena = resetManager.ensureArenaWorld();
        arena.setHardcore(false);

        World hub = getServer().getWorld(resetManager.getHubWorldName());
        if (hub != null) {
            hub.setHardcore(false);
        }

        getServer().getPluginManager().registerEvents(new ResetListener(resetManager), this);

        if (resetManager.hasResourcePack()) {
            for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                resetManager.sendResourcePack(p);
            }
        }

        getCommand("worldreset").setExecutor((sender, command, label, args) -> {
            if (resetManager.isResetting()) {
                sender.sendMessage("§cYa hay un reinicio en curso.");
            } else {
                resetManager.beginReset(null);
                sender.sendMessage("§aReinicio del mundo iniciado.");
            }
            return true;
        });

        getLogger().info("HardcoreReset activo. Mundo de juego: " + resetManager.getArenaWorldName());
    }
}
