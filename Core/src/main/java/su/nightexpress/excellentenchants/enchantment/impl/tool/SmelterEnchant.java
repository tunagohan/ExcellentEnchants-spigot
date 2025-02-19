package su.nightexpress.excellentenchants.enchantment.impl.tool;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.config.JOption;
import su.nexmedia.engine.utils.LocationUtil;
import su.nexmedia.engine.utils.values.UniParticle;
import su.nexmedia.engine.utils.values.UniSound;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.Placeholders;
import su.nightexpress.excellentenchants.api.enchantment.meta.Chanced;
import su.nightexpress.excellentenchants.api.enchantment.type.BlockDropEnchant;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;
import su.nightexpress.excellentenchants.enchantment.impl.meta.ChanceImplementation;
import su.nightexpress.excellentenchants.api.enchantment.ItemCategory;

import java.util.Map;

public class SmelterEnchant extends ExcellentEnchant implements Chanced, BlockDropEnchant {

    public static final String ID = "smelter";

    private UniSound                sound;
    private Map<Material, Material> smeltingTable;
    private ChanceImplementation chanceImplementation;

    public SmelterEnchant(@NotNull ExcellentEnchants plugin) {
        super(plugin, ID);
        this.getDefaults().setDescription(Placeholders.ENCHANTMENT_CHANCE + "% chance to smelt a block/ore.");
        this.getDefaults().setLevelMax(5);
        this.getDefaults().setTier(0.3);
        this.getDefaults().setConflicts(
            DivineTouchEnchant.ID,
            Enchantment.SILK_TOUCH.getKey().getKey()
        );
    }

    @Override
    public void loadSettings() {
        super.loadSettings();
        this.chanceImplementation = ChanceImplementation.create(this,
            "25.0 + " + Placeholders.ENCHANTMENT_LEVEL + " * 10");

        this.sound = JOption.create("Settings.Sound",UniSound.of(Sound.BLOCK_LAVA_EXTINGUISH),
            "Sound to play on smelting.",
            "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html").read(cfg);

        this.smeltingTable = JOption.forMap("Settings.Smelting_Table",
            key -> Material.getMaterial(key.toUpperCase()),
            (cfg, path, key) -> Material.getMaterial(cfg.getString(path + "." + key, "").toUpperCase()),
            Map.of(
                Material.RAW_IRON, Material.IRON_INGOT,
                Material.RAW_GOLD, Material.GOLD_INGOT
            ),
            "Table of Original -> Smelted items.",
            "Syntax: 'Material Source : Material Result'.",
            "Note: Material source is material name of the dropped item, not the broken block!",
            "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html"
        ).setWriter((cfg, path, map) -> map.forEach((src, to) -> cfg.set(path + "." + src.name(), to.name()))).read(cfg);
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
    public boolean onDrop(@NotNull BlockDropItemEvent event, @NotNull LivingEntity player, @NotNull ItemStack item, int level) {
        // TODO Use furnace recipes & Re-add smelted items instead of setType

        if (event.getBlockState() instanceof Container) return false;
        if (!this.checkTriggerChance(level)) return false;
        if (event.getItems().stream().noneMatch(drop -> this.isSmeltable(drop.getItemStack().getType()))) return false;

        event.getItems().forEach(drop -> {
            Material material = this.smeltingTable.get(drop.getItemStack().getType());
            if (material != null) {
                ItemStack stack = drop.getItemStack();
                stack.setType(material);
                drop.setItemStack(stack);
            }
        });

        Block block = event.getBlockState().getBlock();
        if (this.hasVisualEffects()) {
            Location location = LocationUtil.getCenter(block.getLocation(), true);
            UniParticle.of(Particle.FLAME).play(location, 0.25, 0.05, 20);
            this.sound.play(location);
        }
        return true;
    }

    public boolean isSmeltable(@NotNull Material material) {
        return this.smeltingTable.containsKey(material);
    }
}
