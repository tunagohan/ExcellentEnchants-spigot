package su.nightexpress.excellentenchants.enchantment.impl.armor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.Version;
import su.nexmedia.engine.api.manager.EventListener;
import su.nexmedia.engine.api.server.AbstractTask;
import su.nexmedia.engine.utils.Pair;
import su.nexmedia.engine.utils.random.Rnd;
import su.nexmedia.engine.utils.values.UniParticle;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.api.enchantment.Cleanable;
import su.nightexpress.excellentenchants.api.enchantment.type.GenericEnchant;
import su.nightexpress.excellentenchants.enchantment.config.EnchantScaler;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;
import su.nightexpress.excellentenchants.enchantment.util.EnchantUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class FlameWalkerEnchant extends ExcellentEnchant implements GenericEnchant, EventListener, Cleanable {

    public static final String ID = "flame_walker";

    private static final BlockFace[] FACES = {BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST};
    private static final Map<Block, Pair<Long, Integer>> BLOCKS_TO_DESTROY = new ConcurrentHashMap<>();

    private EnchantScaler blockDecayTime;
    private BlockTickTask blockTickTask;

    public FlameWalkerEnchant(@NotNull ExcellentEnchants plugin) {
        super(plugin, ID);
        this.getDefaults().setDescription("Ability to walk on lava and magma blocks without getting damage.");
        this.getDefaults().setLevelMax(3);
        this.getDefaults().setTier(0.7);
        this.getDefaults().getConflicts().add(Enchantment.FROST_WALKER.getKey().getKey());

        this.blockTickTask = new BlockTickTask(plugin);
        this.blockTickTask.start();
    }

    @Override
    public void loadSettings() {
        super.loadSettings();
        this.blockDecayTime = EnchantScaler.read(this, "Settings.Block_Decay", "12.0",
            "Sets up to how long (in seconds) blocks will stay before turn back to lava.");
    }

    @Override
    public void clear() {
        if (this.blockTickTask != null) {
            this.blockTickTask.stop();
            this.blockTickTask = null;
        }
        BLOCKS_TO_DESTROY.keySet().forEach(block -> block.setType(Material.LAVA));
        BLOCKS_TO_DESTROY.clear();
    }

    public static void addBlock(@NotNull Block block, double seconds) {
        BLOCKS_TO_DESTROY.put(block, Pair.of(System.currentTimeMillis() + (long) seconds * 1000L, Rnd.get(100)));
    }

    public static boolean isBlock(@NotNull Block block) {
        return BLOCKS_TO_DESTROY.containsKey(block);
    }

    @Override
    @NotNull
    public EnchantmentTarget getCategory() {
        return EnchantmentTarget.ARMOR_FEET;
    }

    public double getBlockDecayTime(int level) {
        return this.blockDecayTime.getValue(level);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isFlying() || !this.isAvailableToUse(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType().isAir()) return;

        int level = EnchantUtils.getLevel(boots, this.getBackend());
        if (level <= 0) return;

        Block bTo = to.getBlock().getRelative(BlockFace.DOWN);
        boolean hasLava = Stream.of(FACES).anyMatch(face -> bTo.getRelative(face).getType() == Material.LAVA);
        if (!hasLava) return;

        plugin.getEnchantNMS().handleFlameWalker(player, player.getLocation(), level).forEach(block -> {
            addBlock(block, Rnd.getDouble(this.getBlockDecayTime(level)) + 1);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlameWalkerBlock(BlockBreakEvent event) {
        if (isBlock(event.getBlock())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            event.getBlock().setType(Material.LAVA);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(EntityExplodeEvent event) {
        this.processExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode2(BlockExplodeEvent event) {
        this.processExplosion(event.blockList());
    }

    private void processExplosion(@NotNull List<Block> blocks) {
        blocks.removeIf(block -> {
            if (isBlock(block)) {
                block.setType(Material.AIR);
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMagmaDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.HOT_FLOOR) return;
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
        if (!this.isAvailableToUse(livingEntity)) return;

        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment == null) return;

        ItemStack boots = equipment.getBoots();
        if (boots == null || boots.getType().isAir()) return;

        int level = EnchantUtils.getLevel(boots, this.getBackend());
        if (level <= 0) return;

        event.setCancelled(true);
    }

    static class BlockTickTask extends AbstractTask<ExcellentEnchants> {

        public BlockTickTask(@NotNull ExcellentEnchants plugin) {
            super(plugin, 1, false);
        }

        @Override
        public void action() {
            long now = System.currentTimeMillis();

            BLOCKS_TO_DESTROY.keySet().removeIf(block -> {
                if (block.isEmpty() || block.getType() != Material.MAGMA_BLOCK) return true;

                Pair<Long, Integer> pair = BLOCKS_TO_DESTROY.get(block);
                long time = pair.getFirst();
                if (now >= time) {
                    if (Version.isAtLeast(Version.V1_19_R3)) {
                        block.getWorld().getPlayers().forEach(player -> {
                            player.sendBlockDamage(block.getLocation(), 0F, pair.getSecond());
                        });
                    }

                    block.setType(Material.LAVA);
                    UniParticle.blockCrack(Material.MAGMA_BLOCK).play(block.getLocation(), 0.5, 0.7, 0.5, 0.03, 30);
                    return true;
                }
                else if (Version.isAtLeast(Version.V1_19_R3)) {
                    long diff = TimeUnit.MILLISECONDS.toSeconds(time - now);

                    float progress = (float) (1D - Math.min(1D, diff / 5D));
                    if (progress > 1F) progress = 1F;
                    if (progress < 0F) progress = 0F;

                    float finalProgress = progress;
                    block.getWorld().getPlayers().forEach(player -> {
                        player.sendBlockDamage(block.getLocation(), finalProgress, pair.getSecond());
                    });
                }
                return false;
            });
        }
    }
}
