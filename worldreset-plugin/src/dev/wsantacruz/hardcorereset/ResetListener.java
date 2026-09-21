package dev.wsantacruz.hardcorereset;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ResetListener implements Listener {

    private final ResetManager resetManager;

    public ResetListener(ResetManager resetManager) {
        this.resetManager = resetManager;
    }

    // El paquete que dispara la pantalla de "Has muerto" en el cliente se manda
    // como parte de la muerte real, antes de que un PlayerDeathEvent pueda hacer
    // algo al respecto (por eso cambiar el gamemode ahí siempre llegaba tarde).
    // La única forma de evitarla al 100% es cancelar el golpe letal ANTES de que
    // la muerte ocurra y simularla nosotros mismos.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        if (!victim.getWorld().getName().equals(resetManager.getArenaWorldName())) {
            return;
        }
        if (victim.getGameMode() == GameMode.SPECTATOR || victim.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (victim.getHealth() - event.getFinalDamage() > 0) {
            return;
        }

        // Si un totem de inmortalidad va a activarse, dejamos que vanilla lo maneje
        // normalmente (no cancelamos el evento) en vez de forzar el reset. El totem
        // no salva de caer al vacio, igual que en el juego normal.
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID && hasTotem(victim)) {
            return;
        }

        event.setCancelled(true);

        for (ItemStack item : victim.getInventory().getContents()) {
            if (item != null) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), item);
            }
        }
        victim.getInventory().clear();
        victim.setExp(0);
        victim.setLevel(0);
        victim.setHealth(1.0);
        victim.setGameMode(GameMode.SPECTATOR);

        resetManager.beginReset(victim);
    }

    private boolean hasTotem(Player victim) {
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();
        return mainHand.getType() == Material.TOTEM_OF_UNDYING || offHand.getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        resetManager.sendResourcePack(player);

        if (resetManager.isResetting()) {
            player.teleport(resetManager.hubSpawn());
            return;
        }

        // Si ya está en la arena actual, respetamos dónde se desconectó (como
        // Minecraft normalmente). Solo lo mandamos al spawn si su mundo guardado
        // ya no existe (se borró en un reinicio mientras estaba desconectado).
        if (!player.getWorld().getName().equals(resetManager.getArenaWorldName())) {
            player.teleport(resetManager.arenaSpawn());
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }.runTaskLater(resetManager.getPlugin(), 1L);
        }
    }
}
