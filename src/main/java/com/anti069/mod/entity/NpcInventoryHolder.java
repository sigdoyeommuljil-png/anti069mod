package com.anti069.mod.entity;

import net.minecraft.world.item.ItemStack;

/**
 * [역할] 두 NPC(anti069 / 069_36)가 공통으로 구현하는 인벤토리 접근용 인터페이스.
 * NpcCommands 가 엔티티 종류를 몰라도 "인벤에서 이 아이템 꺼내줘"를 시킬 수 있게 한다.
 */
public interface NpcInventoryHolder {

    /**
     * 인벤에서 지정한 아이템 1칸(스택)을 꺼내 돌려준다. 없으면 빈 스택.
     *
     * @param itemId "iron_pickaxe" 같은 마인크래프트 영문 아이템 id(네임스페이스 없이).
     *               내부에서 레지스트리 '이름 문자열'로 비교하므로 26.2 API 이름 변경에 안 걸린다.
     */
    ItemStack takeItemByName(String itemId);
}
