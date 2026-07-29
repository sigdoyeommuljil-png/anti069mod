package com.anti069.mod.ai;

import com.anti069.mod.entity.Anti069Entity;
import com.anti069.mod.entity.Neutral06936Entity;
import com.anti069.mod.entity.Seojune5Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * [역할] 두 NPC(anti069 / 069_36)가 서로의 말·죽음을 보고 대꾸하게 만드는 중앙 관리자.
 *
 * 무한루프 방지가 핵심:
 *  - 한 번 대화가 시작되면 최대 MAX_TURNS(3)번만 주고받고 멈춘다.
 *  - 대화가 끝나면 COOLDOWN 틱(30초) 동안은 새 대화를 시작하지 않는다.
 *  - 상대 NPC가 RANGE(16블록) 안에 있어야만 반응한다.
 *
 * 이 클래스의 상태(turns/lastStart)는 static 이라 월드 전체에서 한 쌍 기준으로 동작한다.
 * (이 모드는 보통 각 NPC가 1마리씩이라 이 단순한 방식으로 충분하다.)
 */
public class NpcTalk {

    private static final int MAX_TURNS = 3;      // 최대 핑퐁 횟수
    private static final double RANGE = 16.0;    // 서로 반응하는 거리
    private static final long COOLDOWN = 600;    // 대화 끝난 뒤 새 대화까지 쿨타임(틱). 20틱=1초

    private static int turns = 0;                // 지금 대화에서 주고받은 횟수
    private static long lastStart = -100000;     // 마지막 대화 시작 시각(getGameTime 기준)

    /**
     * NPC가 방금 '평범한 말'을 했을 때 호출. 근처 다른 NPC가 대꾸할 수 있으면 시킨다(핑퐁).
     *
     * @param speaker    말한 NPC
     * @param speakerTag 말한 NPC 표시 이름(예: "anti069")
     * @param line       말한 내용
     */
    public static void lineSpoken(PathfinderMob speaker, String speakerTag, String line) {
        PathfinderMob other = findOther(speaker);
        if (other == null) return; // 근처에 상대 NPC 없음 → 그냥 혼잣말로 끝

        long now = speaker.level().getGameTime();

        if (turns == 0) {
            // 새 대화 시작 조건: 지난 대화 끝난 지 충분히 지났나?
            if (now - lastStart < COOLDOWN) return;
            lastStart = now;
        }

        if (turns >= MAX_TURNS) {
            // 이미 3번 주고받음 → 여기서 종료하고 카운터 리셋(다음 기회를 위해)
            turns = 0;
            return;
        }

        turns++;
        react(other, speakerTag, line, false);
    }

    /**
     * NPC가 죽었을 때 호출. 근처 다른 NPC가 그 죽음에 한 번 반응한다(핑퐁 아님).
     */
    public static void deathSeen(PathfinderMob deceased, String deadTag) {
        PathfinderMob other = findOther(deceased);
        if (other == null) return;
        // 죽음 반응은 단발성. 이 반응이 또 다른 핑퐁을 낳지 않도록 카운터를 MAX 로 막아둔다.
        turns = MAX_TURNS;
        react(other, deadTag, null, true);
    }

    /** 실제 반응을 각 엔티티에 위임. */
    private static void react(PathfinderMob reactor, String otherTag, String otherLine, boolean death) {
        if (reactor instanceof Anti069Entity a) {
            a.reactToNpc(otherTag, otherLine, death);
        } else if (reactor instanceof Neutral06936Entity n) {
            n.reactToNpc(otherTag, otherLine, death);
        } else if (reactor instanceof Seojune5Entity s) {
            s.reactToNpc(otherTag, otherLine, death);
        }
    }

    /** speaker 주변 RANGE 안에서 '다른 종류의 우리 NPC' 중 가장 가까운 하나를 찾는다. */
    private static PathfinderMob findOther(PathfinderMob speaker) {
        AABB box = speaker.getBoundingBox().inflate(RANGE);
        List<PathfinderMob> list = speaker.level().getEntitiesOfClass(PathfinderMob.class, box,
                e -> e != speaker && e.isAlive()
                        && (e instanceof Anti069Entity || e instanceof Neutral06936Entity
                            || e instanceof Seojune5Entity));
        PathfinderMob best = null;
        double bestD = Double.MAX_VALUE;
        for (PathfinderMob e : list) {
            double d = e.distanceToSqr(speaker);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }
}
