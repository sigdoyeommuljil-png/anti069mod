package com.anti069.mod.client;

import com.anti069.mod.entity.Anti069Entity;
import com.anti069.mod.entity.Neutral06936Entity;
import com.anti069.mod.entity.Seojune5Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * [역할] Tab 목록 '눈속임'용 데이터 제공자.
 *
 * 진짜로 Tab 플레이어 목록에 넣으려면 서버가 가짜 플레이어 정보 패킷을 보내야 하는데,
 * 그건 26.2 패킷 구조에 크게 의존해 불안정하다. 그래서 서버는 건드리지 않고,
 * 클라이언트에서 근처 NPC 이름을 '직접 화면에 그려' 마치 접속자처럼 보이게 하는 눈속임을 쓴다.
 *
 * 이 파일은 그릴 '이름 목록'만 만든다. 실제 글자 그리기는 클라이언트 진입점 람다에서 한다
 * (그리기 도구 타입 이름이 26.2에서 개명됐을 수 있어, 람다 추론으로 그 이름을 피하려는 것).
 */
public final class FakeTab {

    private FakeTab() {}

    /** Tab 목록에 보여줄 이름 폭(블록). 이 안에 로드된 NPC만 잡힌다(클라이언트 한계). */
    private static final double RANGE = 64.0;

    /** 지금 근처에 있는 우리 NPC들의 '가짜 플레이어 이름' 목록. */
    public static List<String> npcNames() {
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;

        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        List<Entity> ents = mc.level.getEntitiesOfClass(Entity.class, box,
                e -> e instanceof Anti069Entity || e instanceof Neutral06936Entity
                        || e instanceof Seojune5Entity);
        for (Entity e : ents) {
            // anti069 는 '평범한 플레이어인 척'하는 컨셉이라 이 이름이 그대로 뜨는 게 속임수가 된다.
            if (e instanceof Anti069Entity) out.add("anti069");
            else if (e instanceof Seojune5Entity) out.add("5seojune");
            else out.add("069_36");
        }
        return out;
    }
}
