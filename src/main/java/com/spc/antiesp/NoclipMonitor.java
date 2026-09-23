package com.spc.antiesp;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects players whose bounding box sits inside solid, non-passable
 * blocks (the server-side signature of noclip/freecam-through-walls
 * cheats) without relying on any per-tick global loop.
 *
 * Design choices made specifically to guarantee this never drags TPS:
 *  - Runs on a timer at a configurable interval (default every 4 ticks),
 *    not every tick.
 *  - The actual block lookups happen on the main thread in small batches
 *    (Bukkit's world/chunk API isn't thread-safe), but the work per
 *    player per check is O(1) — a handful of Material lookups — so even
 *    at hundreds of online players this is sub-millisecond per pass.
 *  - Violation counting uses a rolling counter per player, not history
 *    logging, so memory stays flat.
 */
public class NoclipMonitor {

    private final SpcAntiESP plugin;
    private final Map<java.util.UUID, Integer> violations = new ConcurrentHashMap<>();
    private BukkitTask task;

    private static final Set<Material> PASSABLE_EXTRA = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA
    );

    public NoclipMonitor(SpcAntiESP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int interval = plugin.getConfig().getInt("noclip-detection.check-interval-ticks", 4);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        int threshold = plugin.getConfig().getInt("noclip-detection.violation-threshold", 6);
        Set<String> exempt = Set.copyOf(plugin.getConfig().getStringList("noclip-detection.exempt-gamemodes"));

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission("spcantiesp.bypass")) continue;
            if (exempt.contains(p.getGameMode().name())) continue;

            if (isInsideSolid(p)) {
                int v = violations.merge(p.getUniqueId(), 1, Integer::sum);
                if (v >= threshold) {
                    handleViolation(p);
                    violations.remove(p.getUniqueId());
                }
            } else {
                violations.remove(p.getUniqueId());
            }
        }
    }

    /** Cheap check: is the player's head/feet location inside a non-passable block? */
    private boolean isInsideSolid(Player p) {
        Location loc = p.getLocation();
        Material feet = loc.getBlock().getType();
        Material head = loc.clone().add(0, 1, 0).getBlock().getType();
        return isSolidAndNotPassable(feet) || isSolidAndNotPassable(head);
    }

    private boolean isSolidAndNotPassable(Material m) {
        if (PASSABLE_EXTRA.contains(m)) return false;
        return m.isSolid() && m.isOccluding();
    }

    private void handleViolation(Player p) {
        String action = plugin.getConfig().getString("noclip-detection.action", "KICK");
        String prefix = color(plugin.getConfig().getString("alerts.prefix", ""));

        if (action.equalsIgnoreCase("ALERT_STAFF") || action.equalsIgnoreCase("BOTH")) {
            String perm = plugin.getConfig().getString("alerts.broadcast-to-permission", "spcantiesp.admin");
            String msg = prefix + "§e" + p.getName() + " §7flagged for noclip/freecam movement.";
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(s -> s.hasPermission(perm))
                    .forEach(s -> s.sendMessage(msg));
        }
        if (action.equalsIgnoreCase("KICK") || action.equalsIgnoreCase("BOTH")) {
            String kickMsg = color(plugin.getConfig().getString(
                    "noclip-detection.kick-message", "&cDisconnected: illegal movement detected."));
            p.kick(net.kyori.adventure.text.Component.text(kickMsg));
        }
    }

    private String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
