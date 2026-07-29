package com.anti069.mod;

import com.anti069.mod.client.Anti069Renderer;
import com.anti069.mod.client.BloodMoonOverlay;
import com.anti069.mod.client.FakeTab;
import com.anti069.mod.client.Neutral06936Renderer;
import com.anti069.mod.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * [역할] 클라이언트 전용 진입점. 두 NPC의 렌더러 등록 + 블러드문 화면 오버레이 등록.
 */
public class Anti069ModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ANTI069, Anti069Renderer::new);
        EntityRendererRegistry.register(ModEntities.NEUTRAL06936, Neutral06936Renderer::new);

        // 블러드문: 각성 anti069 가 근처면 화면을 붉게. HUD 요소로 매 프레임 그린다.
        // context(그리기 도구)의 타입은 컴파일러가 추론하므로 이름을 적지 않는다 → 26.2 개명에 안 걸림.
        // 유일하게 남는 이름은 fill 하나. 화면 크기는 오래 안정적인 Window 에서 가져온다.
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("anti069mod", "blood_moon"),
                (context, tickCounter) -> {
                    int color = BloodMoonOverlay.currentColorArgb();
                    if (color == 0) return; // 각성 몹 없음/멀음 → 안 그림
                    Minecraft mc = Minecraft.getInstance();
                    int w = mc.getWindow().getGuiScaledWidth();
                    int h = mc.getWindow().getGuiScaledHeight();
                    context.fill(0, 0, w, h, color);
                });

        // Tab 눈속임: Tab 을 누르고 있을 때, 근처 NPC 이름을 화면에 접속자처럼 그린다.
        // 서버 패킷 없이 클라이언트에서 글자만 그리는 방식. drawString(폰트, 글자, x, y, 색, 그림자).
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("anti069mod", "fake_tab"),
                (context, tickCounter) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!mc.options.keyPlayerList.isDown()) return; // Tab 안 누르면 안 그림
                    java.util.List<String> names = FakeTab.npcNames();
                    if (names.isEmpty()) return;
                    int y = 30; // 실제 탭 목록 근처 위쪽. 위치/정렬은 나중에 조절 가능.
                    for (String name : names) {
                        context.drawString(mc.font, name, 10, y, 0xFFFFFFFF, false);
                        y += 10;
                    }
                });
    }
}
