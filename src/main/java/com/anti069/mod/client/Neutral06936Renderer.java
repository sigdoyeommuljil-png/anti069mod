package com.anti069.mod.client;

import com.anti069.mod.Anti069Mod;
import com.anti069.mod.entity.Neutral06936Entity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * [역할] 069_36 렌더러. 업로드한 스킨 사용. (Mojang 매핑)
 */
public class Neutral06936Renderer
        extends LivingEntityRenderer<Neutral06936Entity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Anti069Mod.MOD_ID, "textures/entity/069_36.png");

    public Neutral06936Renderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public ResourceLocation getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
