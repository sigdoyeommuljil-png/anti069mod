package com.anti069.mod.entity;

import com.anti069.mod.ai.GroqClient;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
    private int preLeaveTimer = -1; // 나간 척 하기까지 남은 틱(-1=대기 안함)
    private int growlTimer = 0;
    private int talkCooldown = 0; // 대사 쿨타임(틱). 20틱=1초
    private int idleTimer = 100;  // 혼잣말 타이머

    // 간단한 아이템 목록 인벤토리 (8칸, 처음엔 비어있음).
    // 나중에 "누가 던져주거나 / 제작" 기능에서 여기에 아이템을 채우게 됩니다.
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(8, ItemStack.EMPTY);

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
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
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

        // [평소에만, 체력 30% 이하] 플레이어에게서 도망
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 12.0f, 1.4, 1.7) {
            @Override public boolean canUse() { return !isAwakened() && healthRatio() <= 0.3f && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isAwakened() && healthRatio() <= 0.3f && super.canContinueToUse(); }
        });

        // [평소에만] 배회
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0) {
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

    /**
     * 데미지 처리.
     * - 평범 상태: 플레이어에게 죽을 만큼 맞으면 죽지 않고 '각성'. 그 외엔 정상 피해 + 도발.
     * - 각성 상태: 기본 무적. 단 '불 붙은 채로 눈덩이 맞음'이면 15뎀 관통(죽으면 리스폰 시 정상화).
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isAwakened()) {
            boolean weakness = this.isOnFire()
                    && source.getDirectEntity() != null
                    && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(source.getDirectEntity().getType()).toString().endsWith("snowball");
            if (weakness) {
                float nh = this.getHealth() - 15.0f;
                if (nh <= 0.0f) {
                    // 확실히 죽임(각성 무적 무시) → die()에서 리스폰 예약 = 정상화
                    return super.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
                }
                this.setHealth(nh);
                return true;
            }
            return false; // 그 외 데미지 무시(무적)
        }

        // 평범 상태
        if (source.getEntity() instanceof Player player) {
            // 플레이어에게 죽을 만큼 맞으면 → 죽지 않고 각성
            if (this.getHealth() - amount <= 0.0f) {
                this.setHealth(this.getMaxHealth());
                awaken();
                return false;
            }
            boolean result = super.hurtServer(level, source, amount);
            onProvoked(player, true);
            return result;
        }
        return super.hurtServer(level, source, amount);
    }

    private void onProvoked(Player player, boolean wasHit) {
        if (phase == Phase.LEFT || phase == Phase.AWAKENED) return;
        provokeCount++;
        if (wasHit) hitCount++;
        phase = Phase.WARNING;

        speakAnnoyed(wasHit);

        if (preLeaveTimer < 0 && (provokeCount >= 3 || hitCount >= 2) && this.random.nextFloat() < 0.30f) {
            startLeaving();
        }
    }

    private void speakAnnoyed(boolean wasHit) {
        if (talkCooldown > 0) return;   // 1초 쿨타임
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;

        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. 성격이 까칠하고 예민하다. "
                + "지금 다른 플레이어가 너에게 시비를 걸거나 때리고 있다. "
                + "반드시 한국어(한글)로만 말하고 영어는 절대 섞지 마라. 짧은 반말 한 문장으로 점점 불편하고 짜증나는 감정을 드러내라. 대사만.";
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
        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. 성격이 까칠하다. " + mood
                + " 반드시 한국어(한글)로만 말하고 영어는 절대 섞지 마라. 짧은 반말 한 문장으로 대답하라. 대사만.";
        String situation = "플레이어가 너에게 말했다: \"" + playerText + "\"";

        GroqClient.ask("groq_key_hostile.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : "...왜?";
            server.execute(() ->
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("<anti069> " + line), false));
        });
    }

    /** 빈 칸에 아이템 넣기. 성공 시 true. (나중에 던져주기/제작에서 사용) */
    public boolean addItem(ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, stack);
                return true;
            }
        }
        return false; // 인벤 가득 참
    }

    /** 죽을 때 호출. 각성 전(평범)일 때만 반응 + 인벤 떨구기. */
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide() && phase != Phase.AWAKENED) {
            dropInventory();
            announceDeath();
            speakDying();
        }
        if (this.level() instanceof ServerLevel sl) {
            com.anti069.mod.Anti069Mod.scheduleRespawn(sl, this.getX(), this.getY(), this.getZ(), ModEntities.ANTI069);
        }
        super.die(source);
    }

    /** 가진 아이템을 바닥에 떨굼. */
    private void dropInventory() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                ItemEntity ie = new ItemEntity(this.level(),
                        this.getX(), this.getY() + 0.5, this.getZ(), stack.copy());
                this.level().addFreshEntity(ie);
            }
        }
    }

    /** 바닐라와 동일한 형식의 사망 메시지를 채팅에 띄움. */
    private void announceDeath() {
        MinecraftServer server = server();
        if (server == null) return;
        Component deathMsg = this.getCombatTracker().getDeathMessage();
        server.getPlayerList().broadcastSystemMessage(deathMsg, false);
    }

    /** 죽는 순간 Groq로 마지막 대사. */
    private void speakDying() {
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. 성격이 까칠하다. 방금 죽었다. "
                + "반드시 한국어(한글)로만, 영어 섞지 말고 짧은 반말 한마디로 죽는 순간의 반응(억울함/놀람/원망 등)을 뱉어라. 대사만.";
        String situation = "나는 방금 죽었다.";
        GroqClient.ask("groq_key_hostile.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : "크윽... 두고 봐...";
            server.execute(() ->
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("<anti069> " + line), false));
        });
    }

    /** 자아 있는 느낌의 혼잣말 (까칠하게). */
    private void idleTalk() {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. 성격이 까칠하다. "
                + "지금 월드를 둘러보며 혼잣말을 한다. 자아가 있는 것처럼, 방금 든 생각이나 관찰을 자연스럽게. "
                + "반드시 한국어(한글)로만, 영어 절대 금지. 짧은 반말 한마디. 대사만.";
        GroqClient.ask("groq_key_hostile.txt", persona, "혼잣말을 한다.", reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : "...심심하네.";
            server.execute(() ->
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("<anti069> " + line), false));
        });
    }

    private float healthRatio() {
        return this.getHealth() / this.getMaxHealth();
    }

    /** 서버 인스턴스를 얻는 도우미. 26.2에선 엔티티에 getServer()가 없어서 ServerLevel 경유. */
    private MinecraftServer server() {
        return this.level() instanceof ServerLevel sl ? sl.getServer() : null;
    }

    /** 4초 뜸들인 뒤 나간 척 하도록 예약 + 나가기 전 경고 대사. */
    private void startLeaving() {
        preLeaveTimer = 20 * 4; // 4초
        speakPreLeave();
    }

    /** 나가기 직전 의미심장한 경고 대사. */
    private void speakPreLeave() {
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = "너는 마인크래프트 서버의 평범한 플레이어 'anti069'인 척하는 존재다. 성격이 까칠하다. "
                + "지금 막 나가버리기 직전, 의미심장하게 경고 한마디 던진다(어떻게 될지 두고 보자는 식). "
                + "반드시 한국어(한글)로만, 영어 금지. 짧은 반말 한마디. 대사만.";
        GroqClient.ask("groq_key_hostile.txt", persona, "곧 나갈 것이다.", reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : "흥, 어떻게 될지 두고 보자고.";
            server.execute(() -> server.getPlayerList().broadcastSystemMessage(
                    Component.literal("<anti069> " + line), false));
        });
    }

    /** 실제 "나간 척": 노란 퇴장 메시지 + 사라짐. 25초 뒤 각성. */
    private void doFakeLeave() {
        preLeaveTimer = -1;
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

        // 나간 척 예약 카운트다운
        if (preLeaveTimer > 0) {
            preLeaveTimer--;
            if (preLeaveTimer == 0) doFakeLeave();
        }

        if (phase == Phase.PEACEFUL || phase == Phase.WARNING) {
            // 근처에 플레이어 있으면 5초쯤마다 혼잣말
            if (--idleTimer <= 0) {
                idleTimer = 100 + this.random.nextInt(60); // 5~8초
                if (this.level().getNearestPlayer(this, 24.0) != null) {
                    idleTalk();
                }
            }
        }

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
                    if (p.distanceToSqr(this) <= 15.0 * 15.0) {
                        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
                    }
                }
            }
        }
    }
}
