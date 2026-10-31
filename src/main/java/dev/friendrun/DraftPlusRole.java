package dev.friendrun;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Approved passive roles available in Draft+. */
public enum DraftPlusRole {
    SPEEDSTER("speedster", "Скороход", "+10% к скорости передвижения",
        Attributes.MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    MINER("miner", "Шахтёр", "+15% к скорости добычи блоков",
        Attributes.BLOCK_BREAK_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    GUARD("guard", "Страж", "+4 к максимальному здоровью",
        Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE),
    FIGHTER("fighter", "Боец", "+1 к силе атаки",
        Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE),
    ACROBAT("acrobat", "Акробат", "+15% к силе прыжка",
        Attributes.JUMP_STRENGTH, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    SWIMMER("swimmer", "Пловец", "+20% эффективности движения в воде",
        Attributes.WATER_MOVEMENT_EFFICIENCY, 0.20, AttributeModifier.Operation.ADD_VALUE);

    private final String id;
    private final String title;
    private final String bonus;
    private final Holder<Attribute> attribute;
    private final double amount;
    private final AttributeModifier.Operation operation;

    DraftPlusRole(String id, String title, String bonus, Holder<Attribute> attribute,
                  double amount, AttributeModifier.Operation operation) {
        this.id = id;
        this.title = title;
        this.bonus = bonus;
        this.attribute = attribute;
        this.amount = amount;
        this.operation = operation;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String bonus() { return bonus; }
    public Holder<Attribute> attribute() { return attribute; }
    public double amount() { return amount; }
    public AttributeModifier.Operation operation() { return operation; }

    public Identifier modifierId() {
        return Identifier.fromNamespaceAndPath("friendrun", "draft_plus_role_" + id);
    }

    public static DraftPlusRole byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (DraftPlusRole role : values()) if (role.id.equals(id)) return role;
        return null;
    }

    public static String label(String id) {
        DraftPlusRole role = byId(id);
        return role == null ? "без роли" : role.title;
    }
}
