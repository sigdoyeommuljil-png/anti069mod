package com.anti069.mod;

import com.anti069.mod.ai.GroqClient;
import com.anti069.mod.entity.ModEntities;
import com.anti069.mod.entity.ModSounds;
import com.anti069.mod.entity.Neutral06936Entity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * [역할] 공통 진입점. 엔티티/사운드 등록 + 069_36 대화 리스너 설정.
 */
public class Anti069Mod implements ModInitializer {

    public static final String MOD_ID = "anti069mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 069_36 이 반응하는 거리(칸). 이 안에서 플레이어가 채팅 치면 대답.
    private static final double TALK_RANGE = 12.0;

    @Override
    public void onInitialize() {
        ModEntities.registerModEntities();
        ModSounds.registerModSounds();
        registerNeutralChatListener();
        LOGGER.info("[{}] 모드 초기화 완료", MOD_ID);
    }

    /**
     * [역할] 플레이어가 채팅을 칠 때마다, 근처에 069_36 이 있으면
     * 그 발화를 Groq에 보내 답을 받아 채팅으로 출력합니다.
     */
    private void registerNeutralChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            ServerWorld world = sender.getServerWorld();
            Box area = sender.getBoundingBox().expand(TALK_RANGE);

            List<Neutral06936Entity> nearby =
                    world.getEntitiesByClass(Neutral06936Entity.class, area, e -> true);
            if (nearby.isEmpty()) return; // 근처에 069_36 없으면 무시

            String playerText = message.getContent().getString();
            respondAsNeutral(sender, playerText);
        });
    }

    private void respondAsNeutral(ServerPlayerEntity sender, String playerText) {
        String persona = "너는 마인크래프트 서버의 평화롭고 친근한 안내자 NPC '069_36'이다. "
                + "플레이어에게 도움을 주는 중립적 존재다. "
                + "짧고 자연스러운 한국어 한두 문장으로 친근하게 답하라. 이모지나 설명 없이 대사만.";
        String situation = "플레이어가 말했다: \"" + playerText + "\"";

        GroqClient.ask("groq_key_neutral.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty())
                    ? reply : "음... 잘 못 들었어. 다시 말해줄래?";
            // 딴 스레드 응답 → 서버 스레드에 얹어서 출력
            sender.getServer().execute(() ->
                    sender.getServer().getPlayerManager()
                            .broadcast(Text.literal("<069_36> " + line), false));
        });
    }
}
