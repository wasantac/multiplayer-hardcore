package dev.wsantacruz.hardcorereset;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ResetManager {

    private final HardcoreReset plugin;
    private final String arenaPrefix;
    private final String hubWorldName;
    private final int delaySeconds;
    private final long fixedSeed;
    private final boolean broadcast;
    private final String resourcePackUrl;
    private final byte[] resourcePackHash;
    private final String deathSound;
    private final float deathSoundVolume;
    private final float deathSoundPitch;

    private volatile String currentArenaName;
    private volatile boolean resetting = false;

    public ResetManager(HardcoreReset plugin, String arenaPrefix, String initialArenaName, String hubWorldName,
                         int delaySeconds, long fixedSeed, boolean broadcast,
                         String resourcePackUrl, String resourcePackSha1,
                         String deathSound, double deathSoundVolume, double deathSoundPitch) {
        this.plugin = plugin;
        this.arenaPrefix = arenaPrefix;
        this.currentArenaName = initialArenaName;
        this.hubWorldName = hubWorldName;
        this.delaySeconds = Math.max(0, delaySeconds);
        this.fixedSeed = fixedSeed;
        this.broadcast = broadcast;
        this.resourcePackUrl = resourcePackUrl == null ? "" : resourcePackUrl.trim();
        this.resourcePackHash = parseSha1(resourcePackSha1);
        this.deathSound = deathSound;
        this.deathSoundVolume = (float) deathSoundVolume;
        this.deathSoundPitch = (float) deathSoundPitch;
    }

    private static byte[] parseSha1(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return null;
        }
        String clean = hex.trim();
        if (clean.length() != 40) {
            return null;
        }
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public boolean hasResourcePack() {
        return !resourcePackUrl.isEmpty();
    }

    public void sendResourcePack(Player player) {
        if (!hasResourcePack()) {
            return;
        }
        if (resourcePackHash != null) {
            player.setResourcePack(resourcePackUrl, resourcePackHash);
        } else {
            player.setResourcePack(resourcePackUrl);
        }
    }

    public HardcoreReset getPlugin() {
        return plugin;
    }

    public String getArenaWorldName() {
        return currentArenaName;
    }

    public String getHubWorldName() {
        return hubWorldName;
    }

    public boolean isResetting() {
        return resetting;
    }

    public World ensureArenaWorld() {
        World arena = Bukkit.getWorld(currentArenaName);
        return arena != null ? arena : createArenaWorld(currentArenaName);
    }

    private World createArenaWorld(String name) {
        WorldCreator creator = new WorldCreator(name).environment(World.Environment.NORMAL);
        if (fixedSeed != 0L) {
            creator.seed(fixedSeed);
        }
        World world = creator.createWorld();
        world.setHardcore(false);
        world.setGameRule(GameRule.BLOCK_DROPS, true);
        world.setGameRule(GameRule.MOB_DROPS, true);
        // Aseguramos un punto de aparición sobre suelo sólido; el spawn calculado
        // por la generación puede quedar en aire justo después de crear el mundo.
        Location spawn = world.getSpawnLocation();
        Location safeSpawn = world.getHighestBlockAt(spawn).getLocation().add(0.5, 1.0, 0.5);
        world.setSpawnLocation(safeSpawn);
        return world;
    }

    private String nextArenaName() {
        return arenaPrefix + "_" + System.currentTimeMillis();
    }

    private void persistCurrentArena(String name) {
        plugin.getConfig().set("current-arena", name);
        plugin.saveConfig();
    }

    public Location arenaSpawn() {
        World w = Bukkit.getWorld(currentArenaName);
        return w != null ? w.getSpawnLocation() : hubSpawn();
    }

    public Location hubSpawn() {
        World w = Bukkit.getWorld(hubWorldName);
        return w != null ? w.getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void beginReset(Player victim) {
        if (resetting) {
            return;
        }
        resetting = true;

        if (broadcast) {
            String who = victim != null ? victim.getName() : "Alguien";
            Bukkit.broadcastMessage("§c" + who + " ha muerto. El mundo se reiniciará en " + delaySeconds + " segundos...");

            String title = "§4☠ " + who + " ha muerto";
            String subtitle = "§eEl mundo se reiniciará en " + delaySeconds + "s...";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(title, subtitle, 10, 60, 20);
                if (deathSound != null && !deathSound.trim().isEmpty()) {
                    p.playSound(p.getLocation(), deathSound, deathSoundVolume, deathSoundPitch);
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                performReset();
            }
        }.runTaskLater(plugin, delaySeconds * 20L);
    }

    private void performReset() {
        // La gente se queda tranquila en la arena vieja (que sigue cargada) mientras
        // generamos la nueva en segundo plano. Así solo hay UN salto de mundo al
        // final (vieja -> nueva) en vez de dos (vieja -> hub -> nueva), que era la
        // causa de la mayoría de los cuelgues de cliente que veíamos.
        String oldArenaName = currentArenaName;
        World oldArena = Bukkit.getWorld(oldArenaName);

        String newName = nextArenaName();
        World newArena = createArenaWorld(newName);
        currentArenaName = newName;
        persistCurrentArena(newName);

        // Pequeño margen para que el seguimiento de entidades/chunks del mundo
        // recién creado termine de inicializarse antes de meter jugadores dentro.
        new BukkitRunnable() {
            @Override
            public void run() {
                Location spawn = newArena.getSpawnLocation();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.teleport(spawn);
                }

                // Ahora que ya no queda nadie dentro, se puede descargar sin problema.
                if (oldArena != null) {
                    Bukkit.unloadWorld(oldArena, false);
                }

                if (broadcast) {
                    Bukkit.broadcastMessage("§a¡El mundo se ha reiniciado! Buena suerte.");
                }

                resetting = false;

                // Un tick después del teletransporte (no en el mismo), para no dejar
                // al jugador a medio inicializar en el mundo nuevo sin interacción.
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.setHealth(20.0);
                            p.setFoodLevel(20);
                            p.setGameMode(GameMode.SURVIVAL);
                        }
                    }
                }.runTaskLater(plugin, 1L);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        deleteDirectory(new File(Bukkit.getWorldContainer(), oldArenaName).toPath());
                    }
                }.runTaskLater(plugin, 40L);
            }
        }.runTaskLater(plugin, 5L);
    }

    private void deleteDirectory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best effort; leftover files no bloquean el nombre nuevo
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo borrar la carpeta del mundo anterior: " + e.getMessage());
        }
    }
}
