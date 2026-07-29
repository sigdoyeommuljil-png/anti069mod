package com.anti069.mod.client;

import com.anti069.mod.entity.Anti069Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * [역할] '블러드문' 화면 효과. 각성한 anti069 가 근처에 있으면 화면 전체에 붉은 막을 씌운다.
 *
 * 왜 하늘이 아니라 화면 오버레이인가:
 *  - 실제 하늘/안개 색을 바꾸려면 26.2 렌더링 내부(FogRenderer 등)를 Mixin 으로 찍어야 하는데
 *    그 이름을 확인할 방법이 없어 빌드가 불안정하다.
 *  - 화면에 반투명 사각형을 하나 그리는 이 방식은 GuiGraphics.fill() 만 쓰므로 아주 안정적이고,
 *    지하 추격전에서도 효과가 그대로 유지된다.
 *
 * 이 클래스는 클라이언트에서만 호출된다(HUD 렌더 시).
 */
public final class BloodMoonOverlay {

    private BloodMoonOverlay() {}

    /** 이 거리(블록) 안에 각성 anti069 가 있으면 붉어지고, 가까울수록 진해진다. */
    private static final double RANGE = 30.0;
    /** 가장 진할 때의 불투명도(0~255). 너무 진하면 안 보이니 절반 정도로 제한. */
    private static final int MAX_ALPHA = 130;

    /**
     * 지금 화면을 얼마나 붉게 할지 0.0~1.0 으로 계산.
     * 각성 anti069 가 없거나 멀면 0.0.
     */
    public static float intensity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0.0f;

        // 플레이어 주변 RANGE 안에서 '각성한' anti069 만 찾는다(클라이언트에서도 getEntitiesOfClass 사용 가능).
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        List<Anti069Entity> list = mc.level.getEntitiesOfClass(
                Anti069Entity.class, box, a -> a.isAwakened());
        if (list.isEmpty()) return 0.0f;

        // 가장 가까운 놈 기준으로 강도 결정
        double bestSq = Double.MAX_VALUE;
        for (Anti069Entity a : list) {
            double d = a.distanceToSqr(mc.player);
            if (d < bestSq) bestSq = d;
        }
        double dist = Math.sqrt(bestSq);
        if (dist >= RANGE) return 0.0f;
        return (float) (1.0 - dist / RANGE); // 가까울수록 1.0 에 가까움
    }

    /** HUD 위에 붉은 막을 그린다. HudRenderCallback 에서 매 프레임 호출. */
    public static void render(GuiGraphics g) {
        float t = intensity();
        if (t <= 0.0f) return;

        int w = g.guiWidth();
        int h = g.guiHeight();
        int alpha = (int) (t * MAX_ALPHA);
        // ARGB 색상: 알파(투명도) + 붉은색(0xAA0000). fill 은 (x1,y1,x2,y2,ARGB).
        int color = (alpha << 24) | 0x00AA0000;
        g.fill(0, 0, w, h, color);
    }
}
