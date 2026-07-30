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
    public static final SoundEvent AWAKEN_CHANT = register("awaken_chant");     // 각성 배경음(무한 반복)
    public static final SoundEvent DEATH_CAR_CRASH = register("death_car_crash"); // 사망음
    public static final SoundEvent HURT_BOX_CRASH = register("hurt_box_crash");   // 피격음
    public static final SoundEvent SEOJUNE_DISCORD = register("seojune_discord");  // 5seojune 설사(각성 전) 디스코드 콜링
    public static final SoundEvent SEOJUNE_AWAKEN = register("seojune_awaken");    // 5seojune 각성 순간 1회
    public static final SoundEvent SEOJUNE_MRBEAST = register("seojune_mrbeast");  // 5seojune 각성 배경음(반복)

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Anti069Mod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id));
    }

    public static void registerModSounds() {
        Anti069Mod.LOGGER.info("[{}] 사운드 등록 완료", Anti069Mod.MOD_ID);
    }
}
