package com.anti069.mod.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * [역할] 렌더 정보 그릇. 각성 여부 하나 추가. (Mojang 매핑)
 */
public class Anti069RenderState extends HumanoidRenderState {
    public boolean awakened = false;
}
