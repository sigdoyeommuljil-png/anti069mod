package com.anti069.mod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * [역할] anti069 의 사람 모델. 각성하면 흉측하게 변형. (Mojang 매핑)
 *  - 머리 없애기
 *  - 오른팔 / 왼다리 길게 늘리기 (비대칭 뒤틀림)
 *  - 걷기 애니메이션 제거 (팔다리 각도 고정 → 뻣뻣하게 미끄러지듯)
 * 평범 상태에선 원래대로 복구합니다(모델은 재사용되므로 매 프레임 초기화 필요).
 */
public class Anti069Model extends HumanoidModel<Anti069RenderState> {

    public Anti069Model(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(Anti069RenderState state) {
        super.setupAnim(state); // 기본 사람 자세/걷기 먼저 적용

        if (state.awakened) {
            // 머리 없애기
            this.head.visible = false;
            this.hat.visible = false;

            // 오른팔 늘리기
            this.rightArm.yScale = 2.4f;
            this.rightArm.xScale = 0.7f;
            this.rightArm.zScale = 0.7f;

            // 왼다리 늘리기
            this.leftLeg.yScale = 2.4f;
            this.leftLeg.xScale = 0.8f;
            this.leftLeg.zScale = 0.8f;

            // 걷기 애니메이션 제거: 팔다리 각도 고정
            this.rightArm.xRot = -0.4f;
            this.leftArm.xRot = -0.1f;
            this.rightLeg.xRot = 0.0f;
            this.leftLeg.xRot = 0.0f;
            this.rightArm.yRot = 0.0f;
            this.leftArm.yRot = 0.0f;
        } else {
            // 평범 상태: 원래대로 복구
            this.head.visible = true;
            this.hat.visible = true;
            this.rightArm.xScale = this.rightArm.yScale = this.rightArm.zScale = 1.0f;
            this.leftLeg.xScale = this.leftLeg.yScale = this.leftLeg.zScale = 1.0f;
        }
    }
}
