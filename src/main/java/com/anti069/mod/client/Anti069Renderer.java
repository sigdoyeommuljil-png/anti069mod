package com.anti069.mod.client;

import com.anti069.mod.Anti069Mod;
import com.anti069.mod.entity.Anti069Entity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * [역할] anti069 를 화면에 그리는 렌더러.
 *  - 평소: 반전 스킨(anti069_normal), 사람 크기
 *  - 각성: 피칠갑 스킨(anti069_awakened)으로 교체 + 크게(1.8배) + 모델 변형은 Anti069Model 담당
 */
public class Anti069Renderer
        extends LivingEntityRenderer<Anti069Entity, Anti069RenderState, Anti069Model> {

    private static final Identifier NORMAL =
            Identifier.of(Anti069Mod.MOD_ID, "textures/entity/anti069_normal.png");
    private static final Identifier AWAKENED =
            Identifier.of(Anti069Mod.MOD_ID, "textures/entity/anti069_awakened.png");

    public Anti069Renderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Anti069Model(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Anti069RenderState createRenderState() {
        return new Anti069RenderState();
    }

    /** 매 프레임 엔티티의 각성 여부를 렌더 상태에 복사 → 모델/텍스처가 이걸 봄. */
    @Override
    public void updateRenderState(Anti069Entity entity, Anti069RenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.awakened = entity.isAwakened();
    }

    @Override
    public Identifier getTexture(Anti069RenderState state) {
        return state.awakened ? AWAKENED : NORMAL;
    }

    /** 각성 시 전체를 크게 그림(위압감). */
    @Override
    protected void scale(Anti069RenderState state, MatrixStack matrices) {
        if (state.awakened) {
            matrices.scale(1.8f, 1.8f, 1.8f);
        }
    }
}
