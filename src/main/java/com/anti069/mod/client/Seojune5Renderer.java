package com.anti069.mod.client;

import com.anti069.mod.Anti069Mod;
import com.anti069.mod.entity.Seojune5Entity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * [역할] 5seojune 렌더러. 사람(플레이어) 모델 + 5seojune.png 스킨. (Mojang 매핑)
 * 텍스처는 지금 069_36 스킨을 임시로 복사해둔 것. 원하는 스킨 png 로 교체하면 된다.
 */
public class Seojune5Renderer
        extends LivingEntityRenderer<Seojune5Entity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Anti069Mod.MOD_ID, "textures/entity/5seojune.png");

    public Seojune5Renderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
