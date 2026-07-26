package com.anti069.mod.entity;

import com.anti069.mod.ai.GroqClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * [역할] anti069 — 공포 컨셉 핵심 엔티티. (Mojang 매핑 버전)
 * PEACEFUL → WARNING → LEFT → AWAKENED 4단계로 진행됩니다.
 */
public class Anti069Entity extends Monster {

    private enum Phase { PEACEFUL, WARNING, LEFT, AWAKENED }

    // 각성 여부(클라 동기화). Mojang 매핑에선 SynchedEntityData 를 씁니다.
    private static final EntityDataAccessor<Boolean> AWAKENED =
            SynchedEntityData.defineId(Anti069Entity.class, EntityDataSerializers.BOOLEAN);

    private Phase phase = Phase.PEACEFUL;
    private int provokeCount = 0;
    private int hitCount = 0;
    private int leftTimer = 0;
    private int growlTimer = 0;
    private int talkCooldown = 0; // 대사 쿨타임(틱). 20틱=1초

    public Anti069Entity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AWAKENED, false);
    }

    public boolean isAwakened() {
        return this.entityData.get(AWAKENED);
    }

    private void setAwakened(boolean v) {
        this.entityData.set(AWAKENED, v);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // [각성 후에만] 근접 공격
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true) {
            @Override public boolean canUse() { return isAwakened() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isAwakened() && super.canContinueToUse(); }
        });

        // [평소에만] 배회
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return !isAwakened() && super.canUse(); }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // [각성 후에만] 항상 가장 가까운 플레이어를 적으로
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override public boolean canUse() { return isAwakened() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isAwakened() && super.canContinueToUse(); }
        });
    }

    /** 우클릭 = 시비 */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide() && phase != Phase.LEFT && phase != Phase.AWAKENED) {
            onProvoked(player, false);
        }
        return InteractionResult.SUCCESS;
    }

    /** 공격당함. 각성 후엔 맞아도 즉시 회복(안 죽음). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean result = super.hurtServer(level, source, amount);
        if (source.getEntity() instanceof Player player) {
            if (isAwakened()) {
                this.setHealth(this.getMaxHealth());
            } else {
                onProvoked(player, true);
            }
        }
        return result;
    }

    private void onProvoked(Player player, boolean wasHit) {
        if (phase == Phase.LEFT || phase == Phase.AWAKENED) return;
        provokeCount++;
        if (wasHit) hitCount++;
        phase = Phase.WARNING;

        speakAnnoyed(wasHit);

        if ((provokeCount >= 3 || hitCount >= 2) && this.random.nextFloat() < 0.30f) {
            fakeLeave();
        }
    }

    private void speakAnnoyed(boolean wasHit) {
        if (talkCooldown > 0) return;   // 1초 쿨타임
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;

        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. "
                + "지금 다른 플레이어가 너에게 시비를 걸거나 때리고 있다. "
                + "짧고 자연스러운 한국어 반말 한 문장으로 점점 불편하고 짜증나는 감정을 드러내라. 대사만.";
        String situation = (wasHit ? "플레이어가 나를 때렸다." : "플레이어가 나에게 시비를 건다.")
                + " (누적 도발 " + provokeCount + "회)";

        GroqClient.ask("groq_key_hostile.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : defaultAnnoyed();
            server.execute(() ->
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("<anti069> " + line), false));
        });
    }

    private String defaultAnnoyed() {
        String[] f = {"야 그만해...", "왜 자꾸 이래?", "하지 마.", "진짜 그만하라고."};
        return f[this.random.nextInt(f.length)];
    }

    /** 근처에서 플레이어가 채팅치면 호출됨. 평소엔 태연, 도발 쌓이면 짜증. 1초 쿨타임. */
    public void heardChat(String playerText) {
        if (phase == Phase.LEFT || phase == Phase.AWAKENED) return;
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;

        String mood = (provokeCount == 0)
                ? "너는 평범하고 태연한 척한다."
                : "너는 이미 좀 짜증이 나 있다 (누적 도발 " + provokeCount + "회).";
        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. " + mood
                + " 짧고 자연스러운 한국어 반말 한 문장으로 대답하라. 대사만.";
        String situation = "플레이어가 너에게 말했다: \"" + playerText + "\"";

        GroqClient.ask("groq_key_hostile.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : "...왜?";
            server.execute(() ->
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("<anti069> " + line), false));
        });
    }

    /** 서버 인스턴스를 얻는 도우미. 26.2에선 엔티티에 getServer()가 없어서 ServerLevel 경유. */
    private MinecraftServer server() {
        return this.level() instanceof ServerLevel sl ? sl.getServer() : null;
    }

    /** "나간 척": 바닐라와 동일한 노란 퇴장 메시지 + 사라짐. 60초 뒤 각성. */
    private void fakeLeave() {
        phase = Phase.LEFT;
        leftTimer = 20 * 25; // 25초
        MinecraftServer server = server();
        if (server != null) {
            Component msg = Component.translatable("multiplayer.player.left",
                    Component.literal("anti069")).withStyle(ChatFormatting.YELLOW);
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
        this.setInvisible(true);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.noPhysics = true;
        this.setSilent(true);
    }

    private void awaken() {
        phase = Phase.AWAKENED;
        setAwakened(true);

        this.setInvisible(false);
        this.setNoAi(false);
        this.setInvulnerable(false);
        this.noPhysics = false;
        this.setSilent(false);

        AttributeInstance spd = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.45);
        AttributeInstance range = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) range.setBaseValue(256.0);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.AWAKEN_ROAR, SoundSource.HOSTILE, 0.8f, 1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        if (talkCooldown > 0) talkCooldown--; // 대사 쿨타임 감소

        if (phase == Phase.LEFT) {
            if (--leftTimer <= 0) awaken();
        } else if (phase == Phase.AWAKENED) {
            // 추격 중 주기적으로 그르렁 (볼륨 낮춤)
            if (--growlTimer <= 0) {
                growlTimer = 60 + this.random.nextInt(80);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.HUNT_GROWL, SoundSource.HOSTILE, 0.5f, 1.0f);
            }
            // 각성 상태에서 30블록 이내 플레이어에게 어둠 효과 (10틱마다 갱신)
            if (this.tickCount % 10 == 0) {
                for (Player p : this.level().players()) {
                    if (p.distanceToSqr(this) <= 30.0 * 30.0) {
                        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
                    }
                }
            }
        }
    }
}
