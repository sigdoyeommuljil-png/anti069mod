package com.anti069.mod.client;

import net.minecraft.client.render.entity.state.BipedEntityRenderState;

/**
 * [역할] 렌더링에 필요한 정보를 담는 그릇.
 * 기본 사람 렌더 상태(BipedEntityRenderState)에 "각성 여부" 한 개를 추가했습니다.
 * 이 값을 모델이 읽어서 형태를 바꿉니다.
 */
public class Anti069RenderState extends BipedEntityRenderState {
    public boolean awakened = false;
}
