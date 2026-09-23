package com.spc.antiesp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpcAntiESP extends JavaPlugin {

    private NoclipMonitor noclipMonitor;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Engine-level anti-xray (block/container hiding) is Paper's own
        // feature — it runs inside chunk generation/sending on the netty
        // thread and costs effectively nothing extra. We don't reimplement
        // it here; we just make sure the server operator knows to enable it.
        checkPaperAntiXray();

        if (getConfig().getBoolean("noclip-detection.enabled", true)) {
            noclipMonitor = new NoclipMonitor(this);
            noclipMonitor.start();
        }

        getLogger().info("spcAntiESP enabled. TPS-safe checks running async/interval-based.");
    }

    @Override
    public void onDisable() {
        if (noclipMonitor != null) {
            noclipMonitor.stop();
        }
    }

    private void checkPaperAntiXray() {
        // We can't edit paper-world-defaults.yml from here reliably across
        // versions, so we just warn loudly with guidance.
        getLogger().warning("Reminder: block/container ESP-hiding is handled by Paper's");
        getLogger().warning("built-in anti-xray engine, NOT by this plugin's code.");
        getLogger().warning("If 'chunk' engine-mode is hurting TPS, switch to 'lighting' mode");
        getLogger().warning("in paper-world-defaults.yml — much cheaper, slightly less strict.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spcantiesp.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§7Usage: /spcantiesp <reload|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("§aspcAntiESP config reloaded.");
            }
            case "status" -> {
                sender.sendMessage("§7spcAntiESP: §aRunning");
                sender.sendMessage("§7Noclip detection: " + (noclipMonitor != null ? "§aON" : "§cOFF"));
                sender.sendMessage("§7Anti-xray hiding: §7delegated to Paper engine (check paper-world-defaults.yml)");
            }
            default -> sender.sendMessage("§7Usage: /spcantiesp <reload|status>");
        }
        return true;
    }
}
