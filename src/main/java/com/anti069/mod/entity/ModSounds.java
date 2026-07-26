package com.anti069.mod.entity;

import com.anti069.mod.Anti069Mod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * [역할] 변신/추격 사운드 등록. (Mojang 매핑)
 */
public class ModSounds {

    public static final SoundEvent AWAKEN_ROAR = register("awaken_roar");
    public static final SoundEvent HUNT_GROWL = register("hunt_growl");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Anti069Mod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id));
    }

    public static void registerModSounds() {
        Anti069Mod.LOGGER.info("[{}] 사운드 등록 완료", Anti069Mod.MOD_ID);
    }
}
