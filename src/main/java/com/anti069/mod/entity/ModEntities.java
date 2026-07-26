package com.anti069.mod.entity;

import com.anti069.mod.Anti069Mod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * [역할] 두 NPC(anti069, 069_36)를 게임 엔티티 목록에 등록.
 */
public class ModEntities {

    // --- anti069 (공포) ---
    public static final RegistryKey<EntityType<?>> ANTI069_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Anti069Mod.MOD_ID, "anti069"));

    public static final EntityType<Anti069Entity> ANTI069 = register(
            ANTI069_KEY,
            EntityType.Builder.create(Anti069Entity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)   // 플레이어와 동일한 히트박스
                    .maxTrackingRange(80)
                    .build(ANTI069_KEY)
    );

    // --- 069_36 (중립) ---
    public static final RegistryKey<EntityType<?>> NEUTRAL06936_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Anti069Mod.MOD_ID, "069_36"));

    public static final EntityType<Neutral06936Entity> NEUTRAL06936 = register(
            NEUTRAL06936_KEY,
            EntityType.Builder.create(Neutral06936Entity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.95f)
                    .maxTrackingRange(48)
                    .build(NEUTRAL06936_KEY)
    );

    private static <T extends Entity> EntityType<T> register(
            RegistryKey<EntityType<?>> key, EntityType<T> type) {
        return Registry.register(Registries.ENTITY_TYPE, key, type);
    }

    public static void registerModEntities() {
        FabricDefaultAttributeRegistry.register(ANTI069, Anti069Entity.createAttributes());
        FabricDefaultAttributeRegistry.register(NEUTRAL06936, Neutral06936Entity.createAttributes());
        Anti069Mod.LOGGER.info("[{}] 엔티티 2종 등록 완료", Anti069Mod.MOD_ID);
    }
}
