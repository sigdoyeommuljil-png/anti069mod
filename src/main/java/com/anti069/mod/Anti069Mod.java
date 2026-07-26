package com.anti069.mod;

import com.anti069.mod.ai.GroqClient;
import com.anti069.mod.entity.Anti069Entity;
import com.anti069.mod.entity.ModEntities;
import com.anti069.mod.entity.ModSounds;
import com.anti069.mod.entity.Neutral06936Entity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * [역할] 공통 진입점. 엔티티/사운드 등록 + 069_36 대화 리스너. (Mojang 매핑)
 */
public class Anti069Mod implements ModInitializer {

    public static final String MOD_ID = "anti069mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double TALK_RANGE = 12.0;

    @Override
    public void onInitialize() {
        ModEntities.registerModEntities();
        ModSounds.registerModSounds();
        registerNeutralChatListener();
        LOGGER.info("[{}] 모드 초기화 완료", MOD_ID);
    }

    /** 플레이어 채팅 시 근처 069_36 이 Groq로 답합니다. */
    private void registerNeutralChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            ServerLevel level = (ServerLevel) sender.level();
            AABB area = sender.getBoundingBox().inflate(TALK_RANGE);
            String playerText = message.decoratedContent().getString();

            // 069_36 (중립 클론) 반응 — 쿨타임/성격은 엔티티가 처리
            List<Neutral06936Entity> neutrals =
                    level.getEntitiesOfClass(Neutral06936Entity.class, area, e -> true);
            if (!neutrals.isEmpty()) {
                neutrals.get(0).heardChat(playerText);
            }

            // anti069 반응 (근처에 있으면 첫 번째가 대답)
            List<Anti069Entity> antis =
                    level.getEntitiesOfClass(Anti069Entity.class, area, e -> true);
            if (!antis.isEmpty()) {
                antis.get(0).heardChat(playerText);
            }
        });
    }


}
