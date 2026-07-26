package com.anti069.mod.entity;

import com.anti069.mod.Anti069Mod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * [역할] 두 NPC를 엔티티 목록에 등록. (Mojang 매핑)
 */
public class ModEntities {

    public static final ResourceKey<EntityType<?>> ANTI069_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Anti069Mod.MOD_ID, "anti069"));

    public static final EntityType<Anti069Entity> ANTI069 = register(
            ANTI069_KEY,
            EntityType.Builder.of(Anti069Entity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(ANTI069_KEY)
    );

    public static final ResourceKey<EntityType<?>> NEUTRAL06936_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Anti069Mod.MOD_ID, "069_36"));

    public static final EntityType<Neutral06936Entity> NEUTRAL06936 = register(
            NEUTRAL06936_KEY,
            EntityType.Builder.of(Neutral06936Entity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(NEUTRAL06936_KEY)
    );

    private static <T extends Entity> EntityType<T> register(
            ResourceKey<EntityType<?>> key, EntityType<T> type) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    public static void registerModEntities() {
        FabricDefaultAttributeRegistry.register(ANTI069, Anti069Entity.createAttributes());
        FabricDefaultAttributeRegistry.register(NEUTRAL06936, Neutral06936Entity.createAttributes());
        Anti069Mod.LOGGER.info("[{}] 엔티티 2종 등록 완료", Anti069Mod.MOD_ID);
    }
}
