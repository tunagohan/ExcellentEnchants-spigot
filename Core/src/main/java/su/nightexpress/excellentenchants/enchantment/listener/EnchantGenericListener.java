package su.nightexpress.excellentenchants.enchantment.listener;

import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.manager.AbstractListener;
import su.nexmedia.engine.utils.EngineUtils;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.config.Config;
import su.nightexpress.excellentenchants.enchantment.EnchantManager;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;
import su.nightexpress.excellentenchants.enchantment.type.ObtainType;
import su.nightexpress.excellentenchants.enchantment.util.EnchantUtils;
import su.nightexpress.excellentenchants.hook.HookId;
import su.nightexpress.excellentenchants.hook.impl.MythicMobsHook;

import java.util.HashMap;
import java.util.Map;

public class EnchantGenericListener extends AbstractListener<ExcellentEnchants> {

    public EnchantGenericListener(@NotNull EnchantManager enchantManager) {
        super(enchantManager.plugin());
    }

    // ---------------------------------------------------------------
    // Update enchantment lore after grindstone
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantUpdateGrindstoneClick(InventoryClickEvent e) {
        Inventory inventory = e.getInventory();
        if (inventory.getType() != InventoryType.GRINDSTONE) return;
        if (e.getRawSlot() == 2) return;

        this.updateGrindstone(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantUpdateGrindstoneDrag(InventoryDragEvent e) {
        Inventory inventory = e.getInventory();
        if (inventory.getType() != InventoryType.GRINDSTONE) return;

        this.updateGrindstone(inventory);
    }

    private void updateGrindstone(@NotNull Inventory inventory) {
        this.plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack result = inventory.getItem(2);
            if (result == null || result.getType().isAir()) return;

            Map<ExcellentEnchant, Integer> curses = new HashMap<>();
            for (int slot = 0; slot < 2; slot++) {
                ItemStack source = inventory.getItem(slot);
                if (source == null || source.getType().isAir()) continue;

                curses.putAll(EnchantUtils.getExcellents(source));
            }
            curses.entrySet().removeIf(entry -> !entry.getKey().isCursed());
            curses.forEach((excellentEnchant, level) -> {
                EnchantUtils.add(result, excellentEnchant, level, true);
            });
            EnchantUtils.updateDisplay(result);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantUpdatePickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        Item item = e.getItem();
        ItemStack itemStack = item.getItemStack();
        if (EnchantUtils.updateDisplay(itemStack)) {
            item.setItemStack(itemStack);
        }
    }

    // ---------------------------------------------------------------
    // Handle Enchanting Table
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEnchantPopulateEnchantingTable(final EnchantItemEvent event) {
        ItemStack target = event.getItem();
        World world = event.getEnchanter().getWorld();

        Map<Enchantment, Integer> enchantsPrepared = event.getEnchantsToAdd();
        Map<Enchantment, Integer> enchantsToPopulate = EnchantUtils.getPopulationCandidates(
            target, ObtainType.ENCHANTING, enchantsPrepared,
            enchant -> enchant.getLevelByEnchantCost(event.getExpLevelCost()),
            world);

        enchantsPrepared.putAll(enchantsToPopulate);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack result = event.getInventory().getItem(0);
            if (result == null) return;

            // Fix enchantments for Enchant Books.
            // Enchants are not added on book because they do not exists in NMS.
            // Server gets enchants from NMS to apply it on Book NBT tags.
            ItemMeta meta = result.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                event.getEnchantsToAdd().forEach((enchantment, level) -> {
                    if (!storageMeta.hasStoredEnchant(enchantment)) {
                        storageMeta.addStoredEnchant(enchantment, level, true);
                    }
                });
                result.setItemMeta(storageMeta);
            }

            event.getEnchantsToAdd().forEach((enchantment, level) -> {
                if (enchantment instanceof ExcellentEnchant enchant && enchant.isChargesEnabled()) {
                    EnchantUtils.restoreCharges(result, enchant, level);
                }
            });
            EnchantUtils.updateDisplay(result);

            event.getInventory().setItem(0, result);
        });
    }

    // ---------------------------------------------------------------
    // Adding Enchants to Villagers
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantPopulateVillagerAcquire(VillagerAcquireTradeEvent event) {
        MerchantRecipe recipe = event.getRecipe();
        ItemStack result = recipe.getResult();
        World world = event.getEntity().getWorld();

        if (!EnchantUtils.isEnchantable(result)) return;
        if (!EnchantUtils.populate(result, ObtainType.VILLAGER, world)) return;

        int uses = recipe.getUses();
        int maxUses = recipe.getMaxUses();
        boolean expReward = recipe.hasExperienceReward();
        int villagerExperience = recipe.getVillagerExperience();
        float priceMultiplier = recipe.getPriceMultiplier();
        int demand = recipe.getDemand();
        int specialPrice = recipe.getSpecialPrice();

        MerchantRecipe recipe2 = new MerchantRecipe(result, uses, maxUses, expReward, villagerExperience,
            priceMultiplier, demand, specialPrice);
        recipe2.setIngredients(recipe.getIngredients());
        event.setRecipe(recipe2);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantPopulateLoot(LootGenerateEvent event) {
        if (Config.getObtainSettings(ObtainType.LOOT_GENERATION).isEmpty()) return;

        Entity entity = event.getEntity();
        InventoryHolder holder = event.getInventoryHolder();
        World world = event.getWorld();

        if (entity instanceof Minecart || holder instanceof Chest) {
            event.getLoot().forEach(item -> {
                if (item != null && EnchantUtils.isEnchantable(item)) {
                    EnchantUtils.populate(item, ObtainType.LOOT_GENERATION, world);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantPopulateFishing(PlayerFishEvent event) {
        if (Config.getObtainSettings(ObtainType.FISHING).isEmpty()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item item)) return;

        ItemStack itemStack = item.getItemStack();
        World world = item.getWorld();
        if (EnchantUtils.isEnchantable(itemStack)) {
            EnchantUtils.populate(itemStack, ObtainType.FISHING, world);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantPopulateSpawn(CreatureSpawnEvent e) {
        //if (Config.getObtainSettings(ObtainType.MOB_SPAWNING).isEmpty()) return;
        LivingEntity entity = e.getEntity();
        if (entity.getType() == EntityType.ARMOR_STAND) return;

        this.plugin.runTaskLater(task -> {
            EntityEquipment equipment = entity.getEquipment();
            if (equipment == null) return;

            World world = entity.getWorld();
            boolean isMythic = EngineUtils.hasPlugin(HookId.MYTHIC_MOBS) && MythicMobsHook.isMythicMob(entity);
            boolean doPopulation = Config.getObtainSettings(ObtainType.MOB_SPAWNING).isPresent() && !isMythic;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = equipment.getItem(slot);
                if (EnchantUtils.isEnchantable(item)) {
                    if (doPopulation) EnchantUtils.populate(item, ObtainType.MOB_SPAWNING, world);
                    EnchantUtils.getExcellents(item).forEach((enchant, level) -> EnchantUtils.restoreCharges(item, enchant, level));
                    equipment.setItem(slot, item);
                }
            }
        }, 40L);
    }
}
