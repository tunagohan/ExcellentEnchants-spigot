package su.nightexpress.excellentenchants.enchantment.impl.weapon;

import org.bukkit.Particle;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.config.JOption;
import su.nexmedia.engine.utils.NumberUtil;
import su.nexmedia.engine.utils.values.UniParticle;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.Placeholders;
import su.nightexpress.excellentenchants.api.enchantment.type.CombatEnchant;
import su.nightexpress.excellentenchants.enchantment.config.EnchantScaler;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;

import java.util.Set;

public class EnchantBaneOfNetherspawn extends ExcellentEnchant implements CombatEnchant {

    public static final String ID = "bane_of_netherspawn";

    private static final String PLACEHOLDER_DAMAGE = "%enchantment_damage%";
    private static final Set<EntityType> ENTITY_TYPES = Set.of(
        EntityType.BLAZE, EntityType.MAGMA_CUBE,
        EntityType.WITHER_SKELETON, EntityType.GHAST, EntityType.WITHER,
        EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.ZOGLIN, EntityType.HOGLIN,
        EntityType.STRIDER, EntityType.ZOMBIFIED_PIGLIN
    );

    private boolean       damageModifier;
    private EnchantScaler damageFormula;

    public EnchantBaneOfNetherspawn(@NotNull ExcellentEnchants plugin) {
        super(plugin, ID);
        this.getDefaults().setDescription("Inflicts " + PLACEHOLDER_DAMAGE + " more damage to nether mobs.");
        this.getDefaults().setLevelMax(5);
        this.getDefaults().setTier(0.1);
    }

    @Override
    public void loadSettings() {
        super.loadSettings();
        this.damageModifier = JOption.create("Settings.Damage.As_Modifier", false,
            "When 'true' multiplies the damage. When 'false' sums plain values.").read(cfg);
        this.damageFormula = EnchantScaler.read(this, "Settings.Damage.Amount",
            "0.5 * " + Placeholders.ENCHANTMENT_LEVEL,
            "Amount of additional damage.");

        this.addPlaceholder(PLACEHOLDER_DAMAGE, level -> NumberUtil.format(this.getDamageModifier(level)));
    }

    public double getDamageModifier(int level) {
        return this.damageFormula.getValue(level);
    }

    @Override
    @NotNull
    public EnchantmentTarget getCategory() {
        return EnchantmentTarget.WEAPON;
    }

    @Override
    public boolean onAttack(@NotNull EntityDamageByEntityEvent event, @NotNull LivingEntity damager, @NotNull LivingEntity victim, @NotNull ItemStack weapon, int level) {
        if (!ENTITY_TYPES.contains(victim.getType())) return false;

        double damageEvent = event.getDamage();
        double damageAdd = this.getDamageModifier(level);
        event.setDamage(this.damageModifier ? damageEvent * damageAdd : damageEvent + damageAdd);
        if (this.hasVisualEffects()) {
            UniParticle.of(Particle.SMOKE_NORMAL).play(victim.getEyeLocation(), 0.25, 0.1, 30);
        }
        return true;
    }

    @Override
    public boolean onProtect(@NotNull EntityDamageByEntityEvent event, @NotNull LivingEntity damager, @NotNull LivingEntity victim, @NotNull ItemStack weapon, int level) {
        return false;
    }
}
