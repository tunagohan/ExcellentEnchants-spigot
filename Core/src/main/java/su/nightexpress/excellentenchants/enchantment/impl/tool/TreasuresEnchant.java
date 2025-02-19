package su.nightexpress.excellentenchants.enchantment.impl.tool;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.utils.blocktracker.PlayerBlockTracker;
import su.nexmedia.engine.utils.random.Rnd;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.Placeholders;
import su.nightexpress.excellentenchants.api.enchantment.Cleanable;
import su.nightexpress.excellentenchants.api.enchantment.meta.Chanced;
import su.nightexpress.excellentenchants.api.enchantment.type.BlockBreakEnchant;
import su.nightexpress.excellentenchants.api.enchantment.type.BlockDropEnchant;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;
import su.nightexpress.excellentenchants.enchantment.impl.meta.ChanceImplementation;
import su.nightexpress.excellentenchants.api.enchantment.ItemCategory;
import su.nightexpress.excellentenchants.enchantment.util.EnchantUtils;

import java.util.*;
import java.util.function.Predicate;

public class TreasuresEnchant extends ExcellentEnchant implements Chanced, BlockBreakEnchant, BlockDropEnchant, Cleanable {

    public static final String ID = "treasures";

    private final Predicate<Block> blockTracker;

    private Map<Material, Map<Material, Double>> treasures;
    private ChanceImplementation chanceImplementation;

    private Block handleDrop;

    public TreasuresEnchant(@NotNull ExcellentEnchants plugin) {
        super(plugin, ID);
        this.getDefaults().setDescription(Placeholders.ENCHANTMENT_CHANCE + "% chance to attempt to find a treasure in mined block.");
        this.getDefaults().setLevelMax(5);
        this.getDefaults().setTier(0.1);

        PlayerBlockTracker.initialize();
        PlayerBlockTracker.BLOCK_FILTERS.add(this.blockTracker = (block) -> {
           return this.treasures.containsKey(block.getType());
        });
    }

    @Override
    public void loadSettings() {
        super.loadSettings();
        this.chanceImplementation = ChanceImplementation.create(this,
            "10.0 + " + Placeholders.ENCHANTMENT_LEVEL + " * 4.0");

        this.treasures = new HashMap<>();

        if (cfg.getSection("Settings.Treasures").isEmpty()) {
            Tag.BASE_STONE_OVERWORLD.getValues().forEach(material -> {
                cfg.addMissing("Settings.Treasures." + material.name() + ".BONE_MEAL", 2.0);
            });
            Tag.DIRT.getValues().forEach(material -> {
                cfg.addMissing("Settings.Treasures." + material.name() + ".CLAY_BALL", 0.5);
                cfg.addMissing("Settings.Treasures." + material.name() + ".BOWL", 1.0);
                cfg.addMissing("Settings.Treasures." + material.name() + ".STICK", 2.0);
            });
            Tag.SAND.getValues().forEach(material -> {
                cfg.addMissing("Settings.Treasures." + material.name() + ".GLOWSTONE_DUST", 1.0);
                cfg.addMissing("Settings.Treasures." + material.name() + ".GOLD_NUGGET", 0.3);
            });
            Tag.LEAVES.getValues().forEach(material -> {
                cfg.addMissing("Settings.Treasures." + material.name() + ".APPLE", 12.0);
            });
        }

        for (String sFromArray : cfg.getSection("Settings.Treasures")) {
            for (String sFrom : sFromArray.split(",")) {
                Material mFrom = Material.getMaterial(sFrom.toUpperCase());
                if (mFrom == null) {
                    plugin.error("[Treasures] Invalid source material '" + sFrom + "' !");
                    continue;
                }
                Map<Material, Double> treasuresList = new HashMap<>();

                for (String sTo : cfg.getSection("Settings.Treasures." + sFromArray)) {
                    Material mTo = Material.getMaterial(sTo.toUpperCase());
                    if (mTo == null) {
                        plugin.error("[Treasures] Invalid result material '" + sTo + "' for '" + sFromArray + "' !");
                        continue;
                    }

                    double tChance = cfg.getDouble("Settings.Treasures." + sFromArray + "." + sTo);
                    treasuresList.put(mTo, tChance);
                }
                this.treasures.put(mFrom, treasuresList);
            }
        }

        this.cfg.setComments("Settings.Treasures",
            "List of source materials (blocks that will drop additional loot). Separated by a comma.",
            "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html");
    }

    @Override
    public void clear() {
        PlayerBlockTracker.BLOCK_FILTERS.remove(this.blockTracker);
    }

    @NotNull
    @Override
    public ChanceImplementation getChanceImplementation() {
        return chanceImplementation;
    }

    @Override
    @NotNull
    public ItemCategory[] getFitItemTypes() {
        return new ItemCategory[]{ItemCategory.PICKAXE, ItemCategory.AXE, ItemCategory.SHOVEL};
    }

    @Override
    @NotNull
    public EnchantmentTarget getCategory() {
        return EnchantmentTarget.TOOL;
    }

    @NotNull
    @Override
    public EventPriority getDropPriority() {
        return EventPriority.NORMAL;
    }

    @Override
    public boolean onBreak(@NotNull BlockBreakEvent event, @NotNull LivingEntity player, @NotNull ItemStack item, int level) {
        if (!event.isDropItems()) return false;
        if (PlayerBlockTracker.isTracked(event.getBlock())) return false;

        this.handleDrop = event.getBlock();
        return false;
    }

    @Override
    public boolean onDrop(@NotNull BlockDropItemEvent event, @NotNull LivingEntity player, @NotNull ItemStack item, int level) {
        if (this.handleDrop != event.getBlock()) return false;
        this.handleDrop = null;

        if (!this.checkTriggerChance(level)) return false;

        this.getTreasures(event.getBlockState().getType()).forEach(treasure -> {
            EnchantUtils.popResource(event, treasure);
        });
        return true;
    }

    @NotNull
    public final List<ItemStack> getTreasures(@NotNull Material type) {
        List<ItemStack> list = new ArrayList<>();
        Map<Material, Double> treasures = this.treasures.getOrDefault(type, Collections.emptyMap());
        treasures.forEach((mat, chance) -> {
            if (mat.isAir() || !mat.isItem() || !Rnd.chance(chance)) return;
            list.add(new ItemStack(mat));
        });
        return list;
    }
}
