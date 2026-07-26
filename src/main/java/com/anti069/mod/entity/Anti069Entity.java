package com.anti069.mod.entity;

import com.anti069.mod.ai.GroqClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * [역할] anti069 — 공포 컨셉의 핵심 엔티티.
 *
 * 4단계 상태로 진행됩니다:
 *   PEACEFUL  : 평범한 플레이어인 척 (배회만, 공격 안 함)
 *   WARNING   : 시비/공격 당하면 Groq로 불편해하는 대사
 *   LEFT      : 도발 누적 시 30% 확률로 "나간 척" (노란 퇴장 메시지 + 사라짐) → 60초 대기
 *   AWAKENED  : 괴물로 각성. 포효 + 무한 추적 + 맞아도 안 죽음 + 주기적 그르렁
 *
 * 트리거: 시비(우클릭) 3회 이상 OR 고의 공격 2회 이상 → 30% 확률로 LEFT 진입.
 */
public class Anti069Entity extends HostileEntity {

    private enum Phase { PEACEFUL, WARNING, LEFT, AWAKENED }

    // 각성 여부는 클라이언트(렌더러)도 알아야 해서 DataTracker로 동기화합니다.
    private static final TrackedData<Boolean> AWAKENED =
            DataTracker.registerData(Anti069Entity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private Phase phase = Phase.PEACEFUL;
    private int provokeCount = 0; // 시비(우클릭+공격) 누적
    private int hitCount = 0;     // 고의 공격 누적
    private int leftTimer = 0;    // "나간 척" 후 각성까지 남은 틱
    private int growlTimer = 0;   // 추격 중 그르렁 쿨타임

    public Anti069Entity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(AWAKENED, false);
    }

    public boolean isAwakened() {
        return this.dataTracker.get(AWAKENED);
    }

    private void setAwakened(boolean v) {
        this.dataTracker.set(AWAKENED, v);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 100.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.30)   // 평소 걸음 속도
                .add(EntityAttributes.ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));

        // [각성 후에만] 목표에게 달려가 계속 근접 공격
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.4, true) {
            @Override public boolean canStart() { return isAwakened() && super.canStart(); }
            @Override public boolean shouldContinue() { return isAwakened() && super.shouldContinue(); }
        });

        // [평소에만] 느긋하게 배회
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.7) {
            @Override public boolean canStart() { return !isAwakened() && super.canStart(); }
        });
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(8, new LookAroundGoal(this));

        // [각성 후에만] 항상 가장 가까운 플레이어를 적으로
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true) {
            @Override public boolean canStart() { return isAwakened() && super.canStart(); }
            @Override public boolean shouldContinue() { return isAwakened() && super.shouldContinue(); }
        });
    }

    /** 우클릭 = 시비. 각성 전에만 도발로 처리. */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient && phase != Phase.LEFT && phase != Phase.AWAKENED) {
            onProvoked(player, false);
        }
        return ActionResult.SUCCESS;
    }

    /** 공격당함. 각성 후엔 맞아도 즉시 회복(안 죽음), 각성 전엔 도발로 처리. */
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean result = super.damage(world, source, amount);
        if (source.getAttacker() instanceof PlayerEntity player) {
            if (isAwakened()) {
                this.setHealth(this.getMaxHealth()); // 데미지 표시는 나지만 안 죽음
            } else {
                onProvoked(player, true);
            }
        }
        return result;
    }

    /** 도발 처리 + 대사 + 페이크 퇴장 판정. */
    private void onProvoked(PlayerEntity player, boolean wasHit) {
        if (phase == Phase.LEFT || phase == Phase.AWAKENED) return;
        provokeCount++;
        if (wasHit) hitCount++;
        phase = Phase.WARNING;

        speakAnnoyed(player, wasHit);

        // 시비 3회 이상 OR 고의 공격 2회 이상 → 30% 확률로 나간 척
        if ((provokeCount >= 3 || hitCount >= 2) && this.random.nextFloat() < 0.30f) {
            fakeLeave();
        }
    }

    /** Groq로 "불편해하는" 대사를 생성해 채팅에 출력. 실패 시 기본 대사. */
    private void speakAnnoyed(PlayerEntity player, boolean wasHit) {
        final MinecraftServer server = this.getServer();
        if (server == null) return;

        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. "
                + "지금 다른 플레이어가 너에게 시비를 걸거나 때리고 있다. "
                + "짧고 자연스러운 한국어 반말 한 문장으로, 점점 불편하고 짜증나는 감정을 드러내라. "
                + "이모지나 설명 없이 대사만.";
        String situation = (wasHit ? "플레이어가 나를 때렸다." : "플레이어가 나에게 시비를 건다.")
                + " (누적 도발 " + provokeCount + "회)";

        GroqClient.ask("groq_key_hostile.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : defaultAnnoyed();
            // 답은 딴 스레드에서 오므로, 서버 스레드에 얹어서 채팅 출력
            server.execute(() ->
                    server.getPlayerManager().broadcast(Text.literal("<anti069> " + line), false));
        });
    }

    private String defaultAnnoyed() {
        String[] f = {"야 그만해...", "왜 자꾸 이래?", "하지 마.", "진짜 그만하라고."};
        return f[this.random.nextInt(f.length)];
    }

    /** "나간 척": 바닐라와 동일한 노란 퇴장 메시지 + 화면에서 사라짐. 60초 뒤 각성. */
    private void fakeLeave() {
        phase = Phase.LEFT;
        leftTimer = 20 * 60; // 60초
        MinecraftServer server = this.getServer();
        if (server != null) {
            // 바닐라 실제 퇴장 메시지와 100% 동일한 번역키+노란색
            Text msg = Text.translatable("multiplayer.player.left", Text.literal("anti069"))
                    .formatted(Formatting.YELLOW);
            server.getPlayerManager().broadcast(msg, false);
        }
        // 진짜 나간 것처럼: 투명 + AI 정지 + 무적 + 통과
        this.setInvisible(true);
        this.setAiDisabled(true);
        this.setInvulnerable(true);
        this.noClip = true;
        this.setSilent(true);
    }

    /** 각성: 괴물로 변신. */
    private void awaken() {
        phase = Phase.AWAKENED;
        setAwakened(true); // 클라 동기화 → 모델 변형 + 텍스처 교체

        this.setInvisible(false);
        this.setAiDisabled(false);
        this.setInvulnerable(false); // 데미지는 받되 위 damage()에서 회복
        this.noClip = false;
        this.setSilent(false);

        // 능력치 강화: 빠르고 멀리서도 추적
        EntityAttributeInstance spd = this.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.45);
        EntityAttributeInstance range = this.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
        if (range != null) range.setBaseValue(256.0);

        // 포효 1회
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.AWAKEN_ROAR, SoundCategory.HOSTILE, 2.0f, 1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        if (phase == Phase.LEFT) {
            if (--leftTimer <= 0) {
                awaken();
            }
        } else if (phase == Phase.AWAKENED) {
            // 추격 중 주기적으로 그르렁
            if (--growlTimer <= 0) {
                growlTimer = 60 + this.random.nextInt(80);
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.HUNT_GROWL, SoundCategory.HOSTILE, 1.4f, 1.0f);
            }
        }
    }
}
