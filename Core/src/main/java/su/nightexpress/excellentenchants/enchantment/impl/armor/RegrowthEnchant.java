package su.nightexpress.excellentenchants.enchantment.impl.armor;

import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.utils.EntityUtil;
import su.nexmedia.engine.utils.NumberUtil;
import su.nexmedia.engine.utils.values.UniParticle;
import su.nightexpress.excellentenchants.ExcellentEnchants;
import su.nightexpress.excellentenchants.Placeholders;
import su.nightexpress.excellentenchants.api.enchantment.meta.Chanced;
import su.nightexpress.excellentenchants.api.enchantment.type.PassiveEnchant;
import su.nightexpress.excellentenchants.enchantment.config.EnchantScaler;
import su.nightexpress.excellentenchants.enchantment.impl.ExcellentEnchant;
import su.nightexpress.excellentenchants.enchantment.impl.meta.ChanceImplementation;
import su.nightexpress.excellentenchants.enchantment.impl.meta.PeriodImplementation;

public class RegrowthEnchant extends ExcellentEnchant implements Chanced, PassiveEnchant {

    public static final String ID = "regrowth";

    private static final String PLACEHOLDER_HEAL_AMOUNT     = "%enchantment_heal_amount%";
    private static final String PLACEHOLDER_HEAL_MIN_HEALTH = "%enchantment_heal_min_health%";
    private static final String PLACEHOLDER_HEAL_MAX_HEALTH = "%enchantment_heal_max_health%";

    private EnchantScaler healMinHealth;
    private EnchantScaler healMaxHealth;
    private EnchantScaler healAmount;

    private ChanceImplementation chanceImplementation;
    private PeriodImplementation periodImplementation;

    public RegrowthEnchant(@NotNull ExcellentEnchants plugin) {
        super(plugin, ID);
        this.getDefaults().setDescription("Restores " + PLACEHOLDER_HEAL_AMOUNT + " hearts every few seconds.");
        this.getDefaults().setLevelMax(5);
        this.getDefaults().setTier(0.7);
    }

    @Override
    public void loadSettings() {
        super.loadSettings();
        this.chanceImplementation = ChanceImplementation.create(this,
            "20.0 + " + Placeholders.ENCHANTMENT_LEVEL + " * 5");

        this.periodImplementation = PeriodImplementation.create(this, "100");

        this.healMinHealth = EnchantScaler.read(this, "Settings.Heal.Min_Health", "0.5",
            "Minimal entity health for the enchantment to have effect.");
        this.healMaxHealth = EnchantScaler.read(this, "Settings.Heal.Max_Health", "20.0",
            "Maximal entity health when the enchantment will not heal anymore.");
        this.healAmount = EnchantScaler.read(this, "Settings.Heal.Amount", "0.25",
            "Amount of hearts to be restored.");

        this.addPlaceholder(PLACEHOLDER_HEAL_AMOUNT, level -> NumberUtil.format(this.getHealAmount(level)));
        this.addPlaceholder(PLACEHOLDER_HEAL_MIN_HEALTH, level -> NumberUtil.format(this.getHealMaxHealth(level)));
        this.addPlaceholder(PLACEHOLDER_HEAL_MAX_HEALTH, level -> NumberUtil.format(this.getHealMaxHealth(level)));
    }

    @NotNull
    @Override
    public ChanceImplementation getChanceImplementation() {
        return chanceImplementation;
    }

    @NotNull
    @Override
    public PeriodImplementation getPeriodImplementation() {
        return periodImplementation;
    }

    @NotNull
    @Override
    public EnchantmentTarget getCategory() {
        return EnchantmentTarget.ARMOR_TORSO;
    }

    public double getHealAmount(int level) {
        return this.healAmount.getValue(level);
    }

    public double getHealMinHealth(int level) {
        return this.healMinHealth.getValue(level);
    }

    public double getHealMaxHealth(int level) {
        return this.healMaxHealth.getValue(level);
    }

    @Override
    public boolean onTrigger(@NotNull LivingEntity entity, @NotNull ItemStack item, int level) {
        if (!this.checkTriggerChance(level)) return false;

        double healthMax = EntityUtil.getAttribute(entity, Attribute.GENERIC_MAX_HEALTH);
        double healthHas = entity.getHealth();
        if (healthHas < this.getHealMinHealth(level) || healthHas > this.getHealMaxHealth(level)) return false;
        if (healthHas >= healthMax) return false;

        double amount = Math.min(healthMax, healthHas + this.getHealAmount(level));
        entity.setHealth(amount);

        if (this.hasVisualEffects()) {
            UniParticle.of(Particle.HEART).play(entity.getEyeLocation(), 0.25, 0.1, 5);
        }
        return true;
    }
}
