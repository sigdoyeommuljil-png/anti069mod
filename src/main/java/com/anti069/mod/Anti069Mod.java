package com.anti069.mod;

import com.anti069.mod.ai.GroqClient;
import com.anti069.mod.entity.ModEntities;
import com.anti069.mod.entity.ModSounds;
import com.anti069.mod.entity.Neutral06936Entity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
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
            ServerLevel level = sender.serverLevel();
            AABB area = sender.getBoundingBox().inflate(TALK_RANGE);

            List<Neutral06936Entity> nearby =
                    level.getEntitiesOfClass(Neutral06936Entity.class, area, e -> true);
            if (nearby.isEmpty()) return;

            String playerText = message.decoratedContent().getString();
            respondAsNeutral(sender, playerText);
        });
    }

    private void respondAsNeutral(ServerPlayer sender, String playerText) {
        String persona = "너는 마인크래프트 서버의 평화롭고 친근한 안내자 NPC '069_36'이다. "
                + "짧고 자연스러운 한국어 한두 문장으로 친근하게 답하라. 대사만.";
        String situation = "플레이어가 말했다: \"" + playerText + "\"";

        GroqClient.ask("groq_key_neutral.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty())
                    ? reply : "음... 잘 못 들었어. 다시 말해줄래?";
            sender.getServer().execute(() ->
                    sender.getServer().getPlayerList()
                            .broadcastSystemMessage(Component.literal("<069_36> " + line), false));
        });
    }
}
