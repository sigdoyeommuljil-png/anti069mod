package com.anti069.mod.entity;

import com.anti069.mod.Anti069Mod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * [역할] 변신/추격 사운드를 게임에 등록.
 * 실제 소리 파일은 assets/anti069mod/sounds/*.ogg 이고,
 * assets/anti069mod/sounds.json 이 이 이름과 파일을 연결합니다.
 */
public class ModSounds {

    // 각성(변신) 순간 나는 포효
    public static final SoundEvent AWAKEN_ROAR = register("awaken_roar");
    // 추격 중 주기적으로 나는 그르렁
    public static final SoundEvent HUNT_GROWL = register("hunt_growl");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(Anti069Mod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerModSounds() {
        Anti069Mod.LOGGER.info("[{}] 사운드 등록 완료", Anti069Mod.MOD_ID);
    }
}
