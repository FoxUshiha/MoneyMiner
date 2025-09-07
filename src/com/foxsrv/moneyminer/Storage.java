package com.foxsrv.moneyminer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Storage {

    private final JavaPlugin plugin;
    private final File minersFile;
    private final YamlConfiguration minersCfg;

    // Mapas em memória para performance
    private final Map<String, Double> ownerAmount = new ConcurrentHashMap<>();
    private final Map<String, Double> ownerFuel = new ConcurrentHashMap<>();
    private final Map<String, List<Miner>> ownerMiners = new ConcurrentHashMap<>();

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;

        minersFile = new File(plugin.getDataFolder(), "miners.yml");
        if (!minersFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                minersFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        minersCfg = YamlConfiguration.loadConfiguration(minersFile);
        loadAll();
    }

    private void loadAll() {
        ownerAmount.clear();
        ownerFuel.clear();
        ownerMiners.clear();

        for (String owner : minersCfg.getKeys(false)) {
            double amount = minersCfg.getDouble(owner + ".Amount", 0.0);
            double fuel = minersCfg.getDouble(owner + ".Fuel", 0.0);

            ownerAmount.put(owner, amount);
            ownerFuel.put(owner, fuel);

            List<Miner> miners = new ArrayList<>();
            if (minersCfg.contains(owner + ".Miners")) {
                for (String key : minersCfg.getConfigurationSection(owner + ".Miners").getKeys(false)) {
                    int x = minersCfg.getInt(owner + ".Miners." + key + ".x");
                    int y = minersCfg.getInt(owner + ".Miners." + key + ".y");
                    int z = minersCfg.getInt(owner + ".Miners." + key + ".z");
                    miners.add(new Miner(owner, x, y, z));
                }
            }
            ownerMiners.put(owner, miners);
        }
    }

    public synchronized void save() {
        try {
            for (String owner : ownerAmount.keySet()) {
                minersCfg.set(owner + ".Amount", ownerAmount.getOrDefault(owner, 0.0));
                minersCfg.set(owner + ".Fuel", ownerFuel.getOrDefault(owner, 0.0));

                List<Miner> miners = ownerMiners.getOrDefault(owner, new ArrayList<>());
                if (!miners.isEmpty()) {
                    for (int i = 0; i < miners.size(); i++) {
                        Miner m = miners.get(i);
                        minersCfg.set(owner + ".Miners." + i + ".x", m.getX());
                        minersCfg.set(owner + ".Miners." + i + ".y", m.getY());
                        minersCfg.set(owner + ".Miners." + i + ".z", m.getZ());
                    }
                } else {
                    minersCfg.set(owner + ".Miners", null);
                }
            }
            minersCfg.save(minersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Criar miner
    public synchronized void createMiner(String owner, int x, int y, int z) {
        Miner miner = new Miner(owner, x, y, z);
        ownerMiners.computeIfAbsent(owner, k -> new ArrayList<>()).add(miner);
        ownerAmount.putIfAbsent(owner, 0.0);
        ownerFuel.putIfAbsent(owner, 0.0);
        save();
    }

    // Remover miner por coordenadas
    public synchronized String removeMinerAt(int x, int y, int z) {
        for (String owner : ownerMiners.keySet()) {
            List<Miner> miners = ownerMiners.get(owner);
            Iterator<Miner> it = miners.iterator();
            while (it.hasNext()) {
                Miner m = it.next();
                if (m.getX() == x && m.getY() == y && m.getZ() == z) {
                    it.remove();
                    save();
                    return owner;
                }
            }
        }
        return null;
    }

    // Retorna lista de todos os jogadores com miners
    public List<String> getAllPlayers() {
        return new ArrayList<>(ownerMiners.keySet());
    }

    // Retorna todos os miners de um jogador
    public List<Miner> getOwnerMiners(String owner) {
        return ownerMiners.getOrDefault(owner, new ArrayList<>());
    }

    // Adiciona amount ao jogador
    public synchronized void addAmountToOwner(String owner, double amount) {
        ownerAmount.put(owner, ownerAmount.getOrDefault(owner, 0.0) + amount);
        save();
    }

    // Coleta amount do jogador (reseta para 0)
    public synchronized double collectAmountForPlayer(String owner) {
        double amt = ownerAmount.getOrDefault(owner, 0.0);
        ownerAmount.put(owner, 0.0);
        save();
        return amt;
    }

    // Combustível
    public synchronized void addFuelToOwner(String owner, double fuel) {
        ownerFuel.put(owner, ownerFuel.getOrDefault(owner, 0.0) + fuel);
        save();
    }

    public synchronized void consumeFuel(String owner, double fuel) {
        double current = ownerFuel.getOrDefault(owner, 0.0);
        ownerFuel.put(owner, Math.max(0, current - fuel));
        save();
    }

    public double getOwnerFuel(String owner) {
        return ownerFuel.getOrDefault(owner, 0.0);
    }
}
