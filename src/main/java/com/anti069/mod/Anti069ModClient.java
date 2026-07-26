package com.anti069.mod;

import com.anti069.mod.client.Anti069Renderer;
import com.anti069.mod.client.Neutral06936Renderer;
import com.anti069.mod.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * [역할] 클라이언트 전용 진입점. 두 NPC의 렌더러를 등록합니다.
 */
public class Anti069ModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ANTI069, Anti069Renderer::new);
        EntityRendererRegistry.register(ModEntities.NEUTRAL06936, Neutral06936Renderer::new);
    }
}
