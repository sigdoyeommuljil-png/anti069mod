package com.anti069.mod.client;

import com.anti069.mod.Anti069Mod;
import com.anti069.mod.entity.Anti069Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;

/**
 * [역할] anti069 렌더러. 각성 시 피칠갑 스킨으로 교체 + 크게(1.8배). (Mojang 매핑)
 * (팔다리 늘리는 모델 변형은 뼈대 빌드 성공 후 다음 단계에서 추가)
 */
public class Anti069Renderer
        extends LivingEntityRenderer<Anti069Entity, Anti069RenderState, Anti069Model> {

    private static final Identifier NORMAL =
            Identifier.fromNamespaceAndPath(Anti069Mod.MOD_ID, "textures/entity/anti069_normal.png");
    private static final Identifier AWAKENED =
            Identifier.fromNamespaceAndPath(Anti069Mod.MOD_ID, "textures/entity/anti069_awakened.png");

    public Anti069Renderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Anti069Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Anti069RenderState createRenderState() {
        return new Anti069RenderState();
    }

    @Override
    public void extractRenderState(Anti069Entity entity, Anti069RenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.awakened = entity.isAwakened();
    }

    @Override
    public Identifier getTextureLocation(Anti069RenderState state) {
        return state.awakened ? AWAKENED : NORMAL;
    }

    @Override
    protected void scale(Anti069RenderState state, PoseStack poseStack) {
        if (state.awakened) {
            poseStack.scale(1.8f, 1.8f, 1.8f);
        }
    }
}
