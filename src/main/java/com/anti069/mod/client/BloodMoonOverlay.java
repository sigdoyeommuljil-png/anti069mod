package com.anti069.mod.client;

import com.anti069.mod.entity.Anti069Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * [역할] '블러드문' 화면 효과 계산기. 각성한 anti069 가 근처에 있으면 화면을 붉게 물들일
 * '색(ARGB)'을 알려준다. 실제 그리기(fill)는 클라이언트 진입점 람다에서 한다.
 *
 * 왜 여기선 그리기 도구(GuiGraphics 류) 타입을 안 쓰는가:
 *  - 그 클래스 이름이 26.2에서 개명됐고(GuiGraphicsExtractor 등) 정확한 이름을 확정할 수 없다.
 *  - 그리기는 HUD 람다 안에서 하면 컴파일러가 타입을 자동 추론하므로, 이 파일에선 색만 계산해
 *    이름 추측을 원천 차단한다.
 *
 * 왜 하늘이 아니라 화면 오버레이인가:
 *  - 실제 하늘/안개 색 변경은 렌더링 내부를 Mixin 으로 찍어야 하는데 26.2 이름을 확인할 수 없다.
 *  - 화면에 반투명 사각형 하나 그리는 이 방식은 지하에서도 효과가 유지되고 훨씬 안정적이다.
 */
public final class BloodMoonOverlay {

    private BloodMoonOverlay() {}

    /** 이 거리(블록) 안에 각성 anti069 가 있으면 붉어지고, 가까울수록 진해진다. */
    private static final double RANGE = 30.0;
    /** 가장 진할 때의 불투명도(0~255). 너무 진하면 안 보이니 절반 정도로 제한. */
    private static final int MAX_ALPHA = 130;

    /**
     * 지금 화면에 덮을 붉은색을 ARGB int 로 돌려준다.
     * 각성 anti069 가 없거나 멀면 0(=덮지 않음).
     */
    public static int currentColorArgb() {
        float t = intensity();
        if (t <= 0.0f) return 0;
        int alpha = (int) (t * MAX_ALPHA);
        if (alpha <= 0) return 0;
        // 알파(투명도) + 붉은색(0xAA0000)
        return (alpha << 24) | 0x00AA0000;
    }

    /** 화면을 얼마나 붉게 할지 0.0~1.0 으로 계산. */
    private static float intensity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0.0f;

        // 플레이어 주변 RANGE 안에서 '각성한' anti069 만 찾는다.
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        List<Anti069Entity> list = mc.level.getEntitiesOfClass(
                Anti069Entity.class, box, a -> a.isAwakened());
        if (list.isEmpty()) return 0.0f;

        double bestSq = Double.MAX_VALUE;
        for (Anti069Entity a : list) {
            double d = a.distanceToSqr(mc.player);
            if (d < bestSq) bestSq = d;
        }
        double dist = Math.sqrt(bestSq);
        if (dist >= RANGE) return 0.0f;
        return (float) (1.0 - dist / RANGE); // 가까울수록 1.0
    }
}
