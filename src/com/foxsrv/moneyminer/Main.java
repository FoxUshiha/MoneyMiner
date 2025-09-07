package com.foxsrv.moneyminer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends JavaPlugin implements Listener {

    private Storage storage;
    private FileConfiguration config;
    private Economy econ;
    private AtomicBoolean running = new AtomicBoolean(true);
    private int miningTaskId = -1; // ✅ guarda o ID da task

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = getConfig();

        if (!setupEconomy()) {
            getLogger().severe("Failed to find Vault dependency! Disabling the plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.storage = new Storage(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        startMiningTask();

        getLogger().info("MoneyMiner enabled!");
    }

    @Override
    public void onDisable() {
        running.set(false);
        if (miningTaskId != -1) {
            Bukkit.getScheduler().cancelTask(miningTaskId);
        }
        getLogger().info("MoneyMiner disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private void startMiningTask() {
        if (miningTaskId != -1) {
            Bukkit.getScheduler().cancelTask(miningTaskId);
        }

        long intervalTicks = Math.max(1L, Math.round(getConfig().getDouble("interval-seconds", 1.0) * 20));

        miningTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.config = getConfig();

            double amountPerTick = config.getDouble("amount-per-tick", 1.0);
            double spent = config.getDouble("spent", 0.1);
            String serverUUID = config.getString("server-uuid");

            if (serverUUID == null) return;
            double serverBalance = econ.getBalance(Bukkit.getOfflinePlayer(java.util.UUID.fromString(serverUUID)));

            storage.getAllPlayers().forEach(owner -> {
                int minersCount = storage.getOwnerMiners(owner).size();
                if (minersCount <= 0) return;

                double totalSpent = spent * minersCount;
                double playerFuel = storage.getOwnerFuel(owner);
                if (playerFuel <= 0) return;

                double actualSpent = Math.min(playerFuel, totalSpent);
                double totalAmount = (actualSpent / totalSpent) * (amountPerTick * minersCount);

                if (totalAmount > serverBalance) {
                    totalAmount = serverBalance;
                    actualSpent = (serverBalance / (amountPerTick * minersCount)) * totalSpent;
                }

                storage.addAmountToOwner(owner, totalAmount);
                storage.consumeFuel(owner, actualSpent);
            });

        }, 0L, intervalTicks).getTaskId();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        Material minerBlock = Material.valueOf(config.getString("miner-block", "VAULT"));

        if (b.getType() == minerBlock) {
            storage.createMiner(p.getName(), b.getX(), b.getY(), b.getZ());
            p.sendMessage("Miner created!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material minerBlock = Material.valueOf(config.getString("miner-block", "VAULT"));

        if (b.getType() == minerBlock) {
            String owner = storage.removeMinerAt(b.getX(), b.getY(), b.getZ());
            if (owner != null) {
                e.getPlayer().sendMessage("Miner removed!");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null) return;
        Material minerBlock = Material.valueOf(config.getString("miner-block", "VAULT"));

        if (b.getType() == minerBlock) {
            Material inHand = p.getInventory().getItemInMainHand().getType();
            int itemAmount = p.getInventory().getItemInMainHand().getAmount();
            double fuelAmount = 0;

            if (config.isConfigurationSection("fuels")) {
                for (Map.Entry<String, Object> entry : config.getConfigurationSection("fuels").getValues(false).entrySet()) {
                    try {
                        Material mat = Material.valueOf(entry.getKey().toUpperCase());
                        if (inHand == mat) {
                            fuelAmount = Double.parseDouble(entry.getValue().toString());
                            break;
                        }
                    } catch (Exception ex) {
                        getLogger().warning("Invalid fuel material in config: " + entry.getKey());
                    }
                }
            }

            if (fuelAmount > 0 && itemAmount > 0) {
                storage.addFuelToOwner(p.getName(), fuelAmount * itemAmount);
                p.getInventory().getItemInMainHand().setAmount(0);
                p.sendMessage("You put " + (fuelAmount * itemAmount) + " of fuel in the miner ring!");
                return;
            }

            double amt = storage.collectAmountForPlayer(p.getName());
            if (amt > 0) {
                econ.depositPlayer(p, amt);
                p.sendMessage("You collected " + amt + " $ from the miner ring!");
            } else {
                p.sendMessage("No founds left!");
            }

            double fuelLeft = storage.getOwnerFuel(p.getName());
            p.sendMessage("Fuel: " + fuelLeft);
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("miner")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                this.config = getConfig();
                startMiningTask(); // ✅ recria a task com o novo intervalo
                sender.sendMessage("Plugin reloaded!");
                return true;
            }
        }
        return false;
    }
}
