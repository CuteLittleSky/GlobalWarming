package me.poma123.globalwarming;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemConsumptionHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

import me.poma123.globalwarming.api.TemperatureType;
import me.poma123.globalwarming.commands.GlobalWarmingCommand;
import me.poma123.globalwarming.items.CinnabariteResource;
import me.poma123.globalwarming.items.machines.AirCompressor;
import me.poma123.globalwarming.items.machines.TemperatureMeter;
import me.poma123.globalwarming.listeners.PollutionListener;
import me.poma123.globalwarming.listeners.WorldListener;
import me.poma123.globalwarming.tasks.BurnTask;
import me.poma123.globalwarming.tasks.FireTask;
import me.poma123.globalwarming.tasks.MeltTask;
import me.poma123.globalwarming.tasks.SlownessTask;

import net.guizhanss.guizhanlib.updater.GuizhanBuildsUpdater;

public class GlobalWarmingPlugin extends JavaPlugin implements SlimefunAddon {

    private static GlobalWarmingPlugin instance;
    private static Registry registry = new Registry();
    private final TemperatureManager temperatureManager = new TemperatureManager();
    private final GlobalWarmingCommand command = new GlobalWarmingCommand(this);
    private final Config cfg = new Config(this);
    private Config messages;

    @Override
    public void onEnable() {
        instance = this;

        if (getConfig().getBoolean("options.auto-update") &&
            getDescription().getVersion().startsWith("Build")) {
            new GuizhanBuildsUpdater(this, getFile(), "ybw0014", "GlobalWarming-CN", "master", false).start();
        }

        new Metrics(this, 9132);

        final File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/messages.yml"), messagesFile.toPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法创建默认配置 messages.yml", e);
            }
        }
        messages = new Config(this, "messages.yml");

        File biomeMapDirectory = new File(getDataFolder(), "biome-maps");
        if (!biomeMapDirectory.exists()) {
            biomeMapDirectory.mkdirs();
        }

        // Create biome map files
        final File pre118BiomeMap = new File(biomeMapDirectory, "pre-1.18.json");
        if (!pre118BiomeMap.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/biome-maps/pre-1.18.json"), pre118BiomeMap.toPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法创建默认配置 biome-maps/pre-1.18.json", e);
            }
        }

        final File post118BiomeMap = new File(biomeMapDirectory, "post-1.18.json");
        if (!post118BiomeMap.exists()) {
            try {
                Files.copy(this.getClass().getResourceAsStream("/biome-maps/post-1.18.json"), post118BiomeMap.toPath());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "无法创建默认配置 biome-maps/post-1.18.json", e);
            }
        }

        registerItems();
        registerResearches();
        registry.load(cfg, messages);
        scheduleTasks();

        command.register();
        Bukkit.getPluginManager().registerEvents(new PollutionListener(), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(), this);
    }

    private void registerItems() {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(this, "global_warming"), new CustomItemStack(Items.THERMOMETER, "&2全球变暖"));

        new TemperatureMeter(itemGroup, Items.THERMOMETER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.NICKEL_INGOT, new ItemStack(Material.GLASS), SlimefunItems.NICKEL_INGOT,
                SlimefunItems.NICKEL_INGOT, Items.MERCURY, SlimefunItems.NICKEL_INGOT,
                SlimefunItems.NICKEL_INGOT, new ItemStack(Material.GLASS), SlimefunItems.NICKEL_INGOT
        }) {
            @Override
            public void tick(Block b) {
                Location loc = b.getLocation();
                updateHologram(b, GlobalWarmingPlugin.getTemperatureManager().getTemperatureString(loc, TemperatureType.valueOf(BlockStorage.getLocationInfo(loc, "type"))));
            }
        }.register(this);

        new TemperatureMeter(itemGroup, Items.AIR_QUALITY_METER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.BILLON_INGOT, SlimefunItems.BILLON_INGOT, SlimefunItems.BILLON_INGOT,
                SlimefunItems.SOLDER_INGOT, Items.THERMOMETER, SlimefunItems.SOLDER_INGOT,
                SlimefunItems.SOLDER_INGOT, SlimefunItems.MAGNET, SlimefunItems.SOLDER_INGOT
        }) {
            @Override
            public void tick(Block b) {
                Location loc = b.getLocation();
                updateHologram(b, "&7环境变化: " + GlobalWarmingPlugin.getTemperatureManager().getAirQualityString(loc.getWorld(), TemperatureType.valueOf(BlockStorage.getLocationInfo(loc, "type"))));
            }
        }.register(this);

        new AirCompressor(itemGroup, Items.AIR_COMPRESSOR, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                SlimefunItems.SOLDER_INGOT, Items.FILTER, SlimefunItems.SOLDER_INGOT,
                SlimefunItems.ALUMINUM_BRASS_INGOT, SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.ALUMINUM_BRASS_INGOT,
                SlimefunItems.SOLDER_INGOT, SlimefunItems.BATTERY, SlimefunItems.SOLDER_INGOT
        }) {
            @Override
            public int getEnergyConsumption() {
                return 16;
            }

            @Override
            public int getCapacity() {
                return 512;
            }

            @Override
            public int getSpeed() {
                return 1;
            }
        }.register(this);
        new AirCompressor.AirCompressor2(itemGroup, Items.AIR_COMPRESSOR_2, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
            Items.AIR_COMPRESSOR, Items.AIR_COMPRESSOR, Items.AIR_COMPRESSOR,
            Items.AIR_COMPRESSOR, Items.SKYCRAFT_COMPRESS_CORE, Items.AIR_COMPRESSOR,
            Items.AIR_COMPRESSOR, Items.AIR_COMPRESSOR, Items.AIR_COMPRESSOR
    }) {
        @Override
        public int getEnergyConsumption() {
            return 128;
        }
    
        @Override
        public int getCapacity() {
            return 4096;
        }
    
        @Override
        public int getSpeed() {
            return 8;
        }
    }.register(this);

        new SlimefunItem(itemGroup, Items.EMPTY_CANISTER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                null, SlimefunItems.SOLDER_INGOT, null,
                SlimefunItems.SOLDER_INGOT, new ItemStack(Material.GLASS_BOTTLE), SlimefunItems.SOLDER_INGOT,
                SlimefunItems.SOLDER_INGOT, SlimefunItems.SOLDER_INGOT, SlimefunItems.SOLDER_INGOT
        }).register(this);

        new SimpleSlimefunItem<ItemConsumptionHandler>(itemGroup, Items.CO2_CANISTER, AirCompressor.RECIPE_TYPE, new ItemStack[] {
                null, null, null,
                null, Items.EMPTY_CANISTER, null,
                null, null, null
        }) {
            @Override
            public ItemConsumptionHandler getItemHandler() {
                return (e, p, item) -> e.setCancelled(true);
            }
        }.register(this);

        new SlimefunItem(itemGroup, Items.CINNABARITE, RecipeType.GEO_MINER, new ItemStack[]{}).register(this);
        new CinnabariteResource().register();

        new SlimefunItem(itemGroup, Items.MERCURY, RecipeType.SMELTERY, new ItemStack[]{
                Items.CINNABARITE, null, null,
                null, null, null,
                null, null, null
        }).register(this);

        new SlimefunItem(itemGroup, Items.FILTER, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[]{
                null, new ItemStack(Material.GLASS), null,
                new ItemStack(Material.GLASS), Items.SKYCRAFT_ACTIVE_CHARCOAL, new ItemStack(Material.GLASS),
                null, new ItemStack(Material.GLASS), null
        }).register(this);

        new SlimefunItem(itemGroup, Items.SKYCRAFT_COMPRESS_CORE, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
            SlimefunItems.CARBON_CHUNK, SlimefunItems.SYNTHETIC_DIAMOND, SlimefunItems.CARBON_CHUNK,
            SlimefunItems.SYNTHETIC_DIAMOND, SlimefunItems.CARBONADO, SlimefunItems.SYNTHETIC_DIAMOND,
            SlimefunItems.CARBON_CHUNK, SlimefunItems.SYNTHETIC_DIAMOND, SlimefunItems.CARBON_CHUNK
        }).register(this);

        new SlimefunItem(itemGroup, Items.SKYCRAFT_ACTIVE_CHARCOAL, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
            new ItemStack(Material.CHARCOAL), new ItemStack(Material.PAPER), new ItemStack(Material.CHARCOAL),
            new ItemStack(Material.PAPER), new ItemStack(Material.ICE), new ItemStack(Material.PAPER),
            new ItemStack(Material.CHARCOAL), new ItemStack(Material.PAPER), new ItemStack(Material.CHARCOAL)
        }).register(this);
    }

    private void registerResearches() {
        registerResearch("thermometer", 69696969, "温度计", 10, Items.THERMOMETER);
        registerResearch("air_quality_meter", 69696970, "空气质量监测仪", 30, Items.AIR_QUALITY_METER);
        registerResearch("air_compressor", 69696971, "空气压缩机", 30, Items.AIR_COMPRESSOR);
        registerResearch("air_compressor", 69696971, "高级空气压缩机", 50, Items.SKYCRAFT_COMPRESS_CORE, Items.AIR_COMPRESSOR_2);
        registerResearch("canisters", 69696972, "污染存储", 6, Items.EMPTY_CANISTER, Items.CO2_CANISTER);
        registerResearch("filter", 69696973, "过滤", 8, Items.FILTER);
        registerResearch("mercury", 69696973, "水银", 12, Items.CINNABARITE, Items.MERCURY);
        registerResearch("activecoal", 69696973, "活性炭", 3, Items.SKYCRAFT_ACTIVE_CHARCOAL);
    }

    private void scheduleTasks() {
        if (cfg.getBoolean("mechanics.FOREST_FIRES.enabled")) {
            new FireTask(cfg.getOrSetDefault("mechanics.FOREST_FIRES.min-temperature-in-celsius", 40.0),
                    cfg.getOrSetDefault("mechanics.FOREST_FIRES.chance", 0.3),
                    cfg.getOrSetDefault("mechanics.FOREST_FIRES.fire-per-second", 10)
            ).scheduleRepeating(0, 20);
        }

        if (cfg.getBoolean("mechanics.ICE_MELTING.enabled")) {
            new MeltTask(cfg.getOrSetDefault("mechanics.ICE_MELTING.min-temperature-in-celsius", 2.0),
                    cfg.getOrSetDefault("mechanics.ICE_MELTING.chance", 0.5),
                    cfg.getOrSetDefault("mechanics.ICE_MELTING.melt-per-second", 10)
            ).scheduleRepeating(0, 20);
        }

        if (cfg.getBoolean("mechanics.SLOWNESS.enabled")) {
            new SlownessTask(cfg.getOrSetDefault("mechanics.SLOWNESS.chance", 0.8)).scheduleRepeating(0, 200);
        }

        if (cfg.getBoolean("mechanics.BURN.enabled")) {
            new BurnTask(cfg.getOrSetDefault("mechanics.BURN.chance", 0.8)).scheduleRepeating(0, 200);
        }

        temperatureManager.runCalculationTask(0, 100);
    }

    private void registerResearch(String key, int id, String name, int defaultCost, ItemStack... items) {
        Research research = new Research(new NamespacedKey(this, key), id, name, defaultCost);

        for (ItemStack item : items) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);

            if (sfItem != null) {
                research.addItems(sfItem);
            }
        }

        research.register();
    }

    public static Registry getRegistry() {
        return registry;
    }

    public static TemperatureManager getTemperatureManager() {
        return instance.temperatureManager;
    }

    public static GlobalWarmingPlugin getInstance() {
        return instance;
    }

    public static GlobalWarmingCommand getCommand() {
        return instance.command;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/GuizhanCraft/GlobalWarming-CN/issues";
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    public static Config getCfg() {
        return instance.cfg;
    }

    public static Config getMessagesConfig() {
        return instance.messages;
    }

}
