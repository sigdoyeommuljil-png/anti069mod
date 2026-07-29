package com.anti069.mod;

import com.anti069.mod.client.Anti069Renderer;
import com.anti069.mod.client.BloodMoonOverlay;
import com.anti069.mod.client.Neutral06936Renderer;
import com.anti069.mod.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * [역할] 클라이언트 전용 진입점. 두 NPC의 렌더러 등록 + 블러드문 화면 오버레이 등록.
 */
public class Anti069ModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ANTI069, Anti069Renderer::new);
        EntityRendererRegistry.register(ModEntities.NEUTRAL06936, Neutral06936Renderer::new);

        // 블러드문: 각성 anti069 가 근처면 화면을 붉게. 매 프레임 HUD 위에 그린다.
        // 두 번째 인자(틱델타/DeltaTracker)는 안 쓰므로 타입을 명시하지 않아 버전 차이에 안 걸린다.
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) ->
                BloodMoonOverlay.render(guiGraphics));
    }
}
