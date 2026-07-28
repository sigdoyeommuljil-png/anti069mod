package com.anti069.mod.ai;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * [역할] NPC가 "지 눈으로 보는" 주변 상황을 한 줄 텍스트로 뽑는다.
 * 이걸 Groq 프롬프트에 같이 넣으면 상황을 아는 것처럼 말/판단하게 된다.
 */
public class Perception {

    public static String describe(PathfinderMob npc) {
        Level level = npc.level();
        StringBuilder sb = new StringBuilder("[내 주변] ");

        // 낮/밤
        long t = level.getLevelData().getDayTime() % 24000L;
        boolean night = (t >= 13000 && t < 23000);
        sb.append(night ? "밤" : "낮");
        if (level.isRaining()) sb.append(", 비 옴");

        // 근처 생물 (최대 3종)
        AABB box = npc.getBoundingBox().inflate(10.0);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != npc && e.isAlive());
        if (!list.isEmpty()) {
            sb.append(", 근처에 ");
            int n = 0;
            for (LivingEntity e : list) {
                if (n >= 3) break;
                if (n > 0) sb.append("/");
                sb.append(shortName(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()));
                n++;
            }
        } else {
            sb.append(", 근처 아무도 없음");
        }

        // 발밑 블록
        BlockState below = level.getBlockState(npc.blockPosition().below());
        sb.append(", 발밑 ").append(shortName(BuiltInRegistries.BLOCK.getKey(below.getBlock()).toString()));

        // 내 체력
        sb.append(", 체력 ").append((int) npc.getHealth()).append("/").append((int) npc.getMaxHealth());

        return sb.toString();
    }

    /** "minecraft:zombie" → "zombie" */
    private static String shortName(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}
