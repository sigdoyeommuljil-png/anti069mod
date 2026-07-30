package com.anti069.mod.item;

import com.anti069.mod.Anti069Mod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * [역할] 커스텀 아이템 등록. 지금은 '똥'(오서준 각성 연출용) 하나.
 * 진짜 갈색 염료를 뿌리면 주워서 염료로 쓸 수 있으니, 아무 기능 없는 전용 아이템으로 만든다.
 */
public class ModItems {

    public static final ResourceKey<Item> TTONG_KEY =
            ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath(Anti069Mod.MOD_ID, "ttong"));

    public static final Item TTONG =
            Registry.register(BuiltInRegistries.ITEM, TTONG_KEY,
                    new Item(new Item.Properties().setId(TTONG_KEY)));

    /** 클래스 로드(=등록)를 확실히 트리거하기 위한 호출용. */
    public static void registerModItems() {
        Anti069Mod.LOGGER.info("[{}] 아이템 등록 완료", Anti069Mod.MOD_ID);
    }
}
