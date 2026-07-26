package com.anti069.mod.client;

import com.anti069.mod.Anti069Mod;
import com.anti069.mod.entity.Neutral06936Entity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Identifier;

/**
 * [역할] 069_36 을 사람 모양으로 그리는 렌더러. 업로드해준 스킨 사용.
 */
public class Neutral06936Renderer
        extends LivingEntityRenderer<Neutral06936Entity, BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {

    private static final Identifier TEXTURE =
            Identifier.of(Anti069Mod.MOD_ID, "textures/entity/069_36.png");

    public Neutral06936Renderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public BipedEntityRenderState createRenderState() {
        return new BipedEntityRenderState();
    }

    @Override
    public Identifier getTexture(BipedEntityRenderState state) {
        return TEXTURE;
    }
}
