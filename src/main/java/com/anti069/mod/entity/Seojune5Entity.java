package com.anti069.mod.entity;

import com.anti069.mod.ai.GroqClient;
import com.anti069.mod.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * [역할] 5seojune — 겁이 엄청 많고 늘 허둥대는 NPC.
 * 싸우지 않는다. 맞으면 놀라서 '폭발 장난'(무해한 뻥 + 주변 밀치기)을 터뜨리고 냅다 도망친다.
 * 대사는 069_36 과 같은 중립 키(groq_key_neutral.txt)를 재활용한다(키 파일 추가 불필요).
 */
public class Seojune5Entity extends PathfinderMob implements NpcInventoryHolder {

    private int talkCooldown = 0;   // 20틱=1초
    private int idleTimer = 100;    // 혼잣말 타이머
    private int panicTimer = 0;     // 0보다 크면 '겁먹은 상태' → 계속 도망
    private int prankCooldown = 0;  // 폭발 장난 재사용 대기
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(8, ItemStack.EMPTY);

    // ---- 각성 단계 ----
    private enum SPhase { NORMAL, SOILED, AWAKENED }
    private SPhase sphase = SPhase.NORMAL;
    private boolean awakened = false;   // 공격 goal 판정용(서버 전용이라 동기화 불필요)
    private int soilTimer = -1;         // 설사 후 각성까지 남은 틱
    private int mrbeastDelay = -1;      // 각성곡(yt1s) 끝나고 미스터비스트 시작까지
    private int mrbeastTimer = 0;       // 미스터비스트 반복 카운트
    private int weebYellTimer = 0;      // 씹덕 함성 도배 카운트
    private int chickenTimer = 0;       // 설사 단계 치킨 스크림 도배 카운트
    private int poopAtkCooldown = 0;    // 각성 후 똥 공격 재사용 대기

    private static final int SEOJUNE_AWAKEN_TICKS = 26;   // yt1s 길이(약 1.3초)
    private static final int SEOJUNE_MRBEAST_TICKS = 443; // 미스터비스트 길이(약 22초)

    /** 각성 후 씹덕 함성(캔드, Groq 안 씀 → API 제한 무관). */
    private static final String[] WEEB_LINES = {
            "히야얍!",
            "냥냥 펀치!",
            "우효~!",
            "받아라, 이 몸의 필살기냥!",
            "냐하하! 전설이 강림했다!",
            "큿... 각성한 나를 막을 순 없어!",
            "이 몸의 진정한 힘을 봐라 냥!"
    };

    public Seojune5Entity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)       // 피 엄청 약함(2칸)
                .add(Attributes.MOVEMENT_SPEED, 0.32)  // 도망가야 하니 좀 빠름
                .add(Attributes.FOLLOW_RANGE, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)    // 각성 후 공격용
                // 로케이터바 표시(다른 NPC와 동일)
                .add(Attributes.WAYPOINT_TRANSMIT_RANGE, 512.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // [각성 후에만] 근접 공격
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.5, true) {
            @Override public boolean canUse() { return awakened && super.canUse(); }
            @Override public boolean canContinueToUse() { return awakened && super.canContinueToUse(); }
        });

        // [평소에만] 돌아다니기
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override public boolean canUse() { return !awakened && super.canUse(); }
        });
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // [각성 후에만] 가장 가까운 플레이어를 적으로
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override public boolean canUse() { return awakened && super.canUse(); }
            @Override public boolean canContinueToUse() { return awakened && super.canContinueToUse(); }
        });
    }

    /** 가장 가까운 플레이어 반대 방향으로 도망. */
    private void fleeFromNearestPlayer() {
        Player near = this.level().getNearestPlayer(this, 16.0);
        if (near == null) return;
        Vec3 away = this.position().subtract(near.position());
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        Vec3 dest = this.position().add(away.normalize().scale(12.0));
        this.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.8); // 겁나서 빠르게
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (talkCooldown > 0) talkCooldown--;
        if (prankCooldown > 0) prankCooldown--;

        com.anti069.mod.ai.NpcCommands.tick(this); // 명령(따라오기 등) 유지

        // '히든 캐릭터' 떡칠 오라: 각성 여부와 상관없이 항상 화려하게 유지
        if (this.tickCount % 40 == 0) applyHiddenAura();

        // 설사(각성 전) 단계: 안 보이게 + 사방으로 똥 분출 + 치킨 스크림 도배, 잠시 뒤 각성
        if (sphase == SPhase.SOILED) {
            wrapInPoop(20);               // 매 틱 오서준을 공처럼 촘촘히 감싼다(랙 감수)
            if (--chickenTimer <= 0) {    // 치킨 스크림 도배(약 1.2초마다)
                chickenTimer = 24;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.SEOJUNE_CHICKEN, SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
            if (soilTimer > 0 && --soilTimer <= 0) awaken5();
            return;
        }

        // 각성 단계: 미스터비스트 배경음 반복 + 씹덕 함성 도배
        if (sphase == SPhase.AWAKENED) {
            tickAwakened();
            return;
        }

        // ---- 평소(겁쟁이) ----
        if (panicTimer > 0) {
            panicTimer--;
            if (this.tickCount % 10 == 0) fleeFromNearestPlayer();
        }
        if (--idleTimer <= 0) {
            idleTimer = 100 + this.random.nextInt(60);
            if (this.level().getNearestPlayer(this, 24.0) != null) {
                idleTalk();
            }
        }
    }

    /** 각성 중 매 틱: yt1s 끝나면 미스터비스트 무한 반복 + 씹덕 함성 도배. */
    private void tickAwakened() {
        // 미스터비스트 배경음: 각성곡(yt1s)이 끝나는 시점부터 시작해 22초마다 반복
        if (mrbeastDelay > 0) {
            if (--mrbeastDelay <= 0) {
                playMrbeast();
                mrbeastTimer = SEOJUNE_MRBEAST_TICKS;
            }
        } else if (--mrbeastTimer <= 0) {
            playMrbeast();
            mrbeastTimer = SEOJUNE_MRBEAST_TICKS;
        }

        // 씹덕 함성 도배(캔드 대사라 Groq 안 씀 → API 제한 무관)
        if (--weebYellTimer <= 0) {
            weebYellTimer = 45; // 약 2.25초마다
            MinecraftServer sv = server();
            if (sv != null) {
                String line = WEEB_LINES[this.random.nextInt(WEEB_LINES.length)];
                sv.getPlayerList().broadcastSystemMessage(
                        Component.literal("<오서준> " + line), false);
            }
        }

        // 플레이어가 가까우면 → 치킨 스크림 + 똥(갈색 염료) 공격
        if (poopAtkCooldown > 0) poopAtkCooldown--;
        Player near = this.level().getNearestPlayer(this, 4.0);
        if (near != null && poopAtkCooldown <= 0) {
            poopAtkCooldown = 25; // 약 1.25초 쿨타임
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEOJUNE_CHICKEN, SoundSource.NEUTRAL, 1.0f, 1.0f);
            throwPoopAt(near);
        }
    }

    private void playMrbeast() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SEOJUNE_MRBEAST, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }

    /** 죽을 만큼 맞으면 → 죽지 않고 '설사' 후 각성 시퀀스 시작. 웅장하게 안 보이며 똥을 뿜는다. */
    private void startSoiling() {
        sphase = SPhase.SOILED;
        soilTimer = 300; // 15초 뒤 각성
        panicTimer = 0;
        this.getNavigation().stop();
        this.setNoAi(true);        // 그 자리에 멈춰서 연출
        this.setInvisible(true);   // 똥 오라에 가려 오서준은 안 보이게
        MinecraftServer server = server();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("오서준이(가) 바지에 설사를 지리고 말았습니다.")
                            .withStyle(ChatFormatting.YELLOW), false);
        }
        // 디스코드 콜링 소리
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SEOJUNE_DISCORD, SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    /** 오서준을 중심으로 '똥' 아이템을 구체 껍질처럼 촘촘히 뿌려 완전히 감싼다(안 보이게). */
    private void wrapInPoop(int count) {
        ItemStack poop = poopStack();
        if (poop.isEmpty()) return;
        double r = 1.3; // 감싸는 반경
        for (int i = 0; i < count; i++) {
            // 구 표면의 무작위 방향
            double u = this.random.nextDouble() * 2.0 - 1.0;      // z축 성분(-1~1)
            double t = this.random.nextDouble() * Math.PI * 2.0;  // 방위각
            double s = Math.sqrt(1.0 - u * u);
            double dx = s * Math.cos(t), dy = u, dz = s * Math.sin(t);
            ItemEntity ie = new ItemEntity(this.level(),
                    this.getX() + dx * r, this.getY() + 1.0 + dy * r, this.getZ() + dz * r,
                    poop.copy());
            // 중력을 잠깐 상쇄하도록 살짝 띄우고 바깥으로 약하게(공 모양 유지)
            ie.setDeltaMovement(dx * 0.04, 0.12 + dy * 0.04, dz * 0.04);
            this.level().addFreshEntity(ie);
        }
    }

    /** 커스텀 '똥' 아이템 1개. 진짜 염료가 아니라 주워도 쓸모없는 전용 아이템. */
    private ItemStack poopStack() {
        return new ItemStack(ModItems.TTONG);
    }

    /** 각성 후: 대상 플레이어 쪽으로 똥을 여러 개 던진다. */
    private void throwPoopAt(Player target) {
        ItemStack poop = poopStack();
        if (poop.isEmpty()) return;
        Vec3 dir = target.position().add(0, 0.5, 0).subtract(this.position().add(0, 1.2, 0));
        if (dir.lengthSqr() < 1.0e-4) return;
        dir = dir.normalize();
        for (int i = 0; i < 5; i++) {
            ItemEntity ie = new ItemEntity(this.level(),
                    this.getX(), this.getY() + 1.2, this.getZ(), poop.copy());
            Vec3 v = dir.scale(0.8).add(
                    (this.random.nextDouble() - 0.5) * 0.2, 0.15,
                    (this.random.nextDouble() - 0.5) * 0.2);
            ie.setDeltaMovement(v.x, v.y, v.z);
            this.level().addFreshEntity(ie);
        }
    }

    /** 각성: 다시 나타나며, 각성곡(yt1s) 1회 + 공격 모드 돌입. 몸은 그대로. */
    private void awaken5() {
        sphase = SPhase.AWAKENED;
        awakened = true;
        soilTimer = -1;
        this.setInvisible(false);  // 똥 오라 걷히고 등장
        this.setNoAi(false);

        // 각성곡(yt1s) 1회. 끝나면(mrbeastDelay 후) 미스터비스트 시작.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SEOJUNE_AWAKEN, SoundSource.NEUTRAL, 1.0f, 1.0f);
        mrbeastDelay = SEOJUNE_AWAKEN_TICKS;

        // 공격하러 오도록 좀 빨라짐
        AttributeInstance spd = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.42);
    }

    /** 맞으면 → (평소) 놀라서 폭발 장난+도망 / (죽을 만큼) 설사 후 각성 / (각성 후) 그냥 피해받음. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // 각성 후엔 겁쟁이 행동 없음. 평범하게 피해만 받는다.
        if (awakened) return super.hurtServer(level, source, amount);
        // 설사(각성 대기) 중엔 무적처럼 버틴다.
        if (sphase == SPhase.SOILED) return false;

        // 플레이어에게 죽을 만큼 맞으면 → 죽지 않고 '설사' 후 각성
        if (source.getEntity() instanceof Player && this.getHealth() - amount <= 0.0f) {
            this.setHealth(this.getMaxHealth());
            startSoiling();
            return false;
        }

        boolean result = super.hurtServer(level, source, amount);
        if (result) {
            panicTimer = 20 * 6;      // 6초간 겁먹고 도망
            this.setTarget(null);
            prankPop();               // 폭발 장난
            if (source.getEntity() instanceof Player) speakScared();
        }
        return result;
    }

    /**
     * '폭발 장난': 무해한 뻥 소리 + 주변 엔티티를 밖으로 살짝 밀친다.
     * 블록도 안 부수고 데미지도 없다. 겁먹어서 얼떨결에 터뜨리는 컨셉.
     */
    private void prankPop() {
        if (prankCooldown > 0) return;
        prankCooldown = 40; // 2초

        // 뻥 소리(box_crash 재사용, 음정 높여서 개그 느낌). 서버에서만 재생.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.HURT_BOX_CRASH, SoundSource.NEUTRAL, 1.0f, 1.4f);

        // 주변 4블록 안 엔티티를 5seojune 바깥쪽으로 밀친다(데미지 없음).
        AABB box = this.getBoundingBox().inflate(4.0);
        for (Entity e : this.level().getEntitiesOfClass(Entity.class, box, x -> x != this)) {
            Vec3 dir = e.position().subtract(this.position());
            if (dir.lengthSqr() < 1.0e-4) dir = new Vec3(1, 0, 0);
            dir = dir.normalize().scale(0.6);
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x, 0.35, dir.z));
            e.hurtMarked = true; // 속도 변화를 클라이언트에 반영
        }
    }

    /**
     * '히든 캐릭터' 떡칠 오라: 온몸에 화려한 효과를 잔뜩 발라 색색깔 파티클이 소용돌이치게 한다.
     * 전부 시각/버프 효과라 대사(Groq)를 안 써서 API 제한과 무관하다.
     * 26.x 에서 이름이 안 바뀐 효과들만 골라 씀(발광·재생·화염저항·수중호흡·흡수·야간투시·체력증가·행운).
     * 마지막 인자 true = 파티클 보이기.
     */
    private void applyHiddenAura() {
        int d = 100; // 5초 지속(2초마다 갱신하므로 안 끊김)
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, d, 0, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, d, 0, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, d, 0, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, d, 0, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, d, 3, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, d, 0, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, d, 3, false, true));
        this.addEffect(new MobEffectInstance(MobEffects.LUCK, d, 0, false, true));
    }

    /** 자아 있는 느낌의 혼잣말 (허둥대며). */
    private void idleTalk() {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase()
                + "지금 월드를 둘러보며 혼잣말을 한다. 겁이 많아 사소한 것에도 놀라고 걱정한다. "
                + "반드시 한국어(한글)로만, 영어 절대 금지. 짧은 반말 한마디. 대사만.";
        askAndSay(server, persona,
                com.anti069.mod.ai.Perception.describe(this) + " 이 상황에서 혼잣말을 한다.",
                "으, 뭔가 무서운데...", true);
    }

    /** 근처 채팅에 반응. 겁먹은 듯 허둥대며. */
    public void heardChat(String playerText) {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase()
                + "반드시 한국어(한글)로만, 영어 절대 섞지 말고 허둥대며 짧게 답하라. 대사만.";
        String situation = com.anti069.mod.ai.Perception.describe(this)
                + " 플레이어가 너에게 말했다: \"" + playerText + "\"";
        askAndSay(server, persona, situation, "헉, 왜, 왜 그래?", false);
    }

    /** 맞았을 때 겁먹은 비명 같은 한마디. */
    private void speakScared() {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase() + "방금 맞아서 완전 겁에 질렸다. 비명 지르듯 허둥대며 한마디. "
                + "반드시 한국어(한글)로만, 영어 섞지 말고 짧은 반말. 대사만.";
        askAndSay(server, persona, "플레이어가 나를 때렸다! 무서워서 도망친다!", "으아악! 살려줘!", false);
    }

    /** 5seojune(오서준) 공통 성격 설명. */
    private String personaBase() {
        return "너는 '오서준'이다. 원래 069_36의 진짜 친구로, 그저 그런 평범한 친구였다. "
                + "그런데 어느 날 갑자기 '어떤 빛'을 보게 되어 완전히 달라졌다. 이제 자기가 진짜로 "
                + "엄청난 전설의 숨겨진 존재라고 굳게 믿는 중2병에 걸려, 거창하고 오글거리게 자신의 "
                + "전설과 그 '빛'에 대해 진지하게 떠벌린다. 그런데 사실은 겁이 아주 많아서, 조금만 "
                + "무섭거나 놀라면 그 믿음이 순식간에 와르르 무너지고 호들갑 떨며 도망친다. ";
    }

    // ---- 인벤토리 ----
    public boolean addItem(ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack takeItemByName(String itemId) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.get(i);
            if (s.isEmpty()) continue;
            String key = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            if (key.equals("minecraft:" + itemId) || key.endsWith(":" + itemId)) {
                inventory.set(i, ItemStack.EMPTY);
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** [NPC끼리 대화] 다른 NPC의 말/죽음에 겁먹은 듯 반응. NpcTalk 가 호출. */
    public void reactToNpc(String otherTag, String otherLine, boolean death) {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase()
                + (death
                    ? ("다른 NPC '" + otherTag + "'가 방금 죽었다! 겁먹고 놀라며 한마디 하라. ")
                    : ("다른 NPC '" + otherTag + "'가 \"" + otherLine + "\"라고 말했다. 허둥대며 대꾸하라. "))
                + "반드시 한국어(한글)로만, 영어 금지, 짧은 반말 한마디. 대사만.";
        String situation = death
                ? ("'" + otherTag + "'가 죽었다.")
                : ("'" + otherTag + "'의 말: \"" + otherLine + "\"");
        askAndSay(server, persona, situation, death ? "히익! 주, 죽었어?!" : "어, 어어...", true);
    }

    // ---- 죽음 ----
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            dropInventory();
            announceDeath();
            speakDying();
            com.anti069.mod.ai.NpcTalk.deathSeen(this, "오서준");
        }
        if (this.level() instanceof ServerLevel sl) {
            com.anti069.mod.Anti069Mod.scheduleRespawn(sl, this.getX(), this.getY(), this.getZ(), ModEntities.SEOJUNE5);
        }
        super.die(source);
    }

    private void dropInventory() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                ItemEntity ie = new ItemEntity(this.level(),
                        this.getX(), this.getY() + 0.5, this.getZ(), stack.copy());
                this.level().addFreshEntity(ie);
            }
        }
    }

    private void announceDeath() {
        MinecraftServer server = server();
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(
                this.getCombatTracker().getDeathMessage(), false);
    }

    private void speakDying() {
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase() + "방금 죽었다. "
                + "반드시 한국어(한글)로만, 영어 섞지 말고 겁먹은 채 짧은 반말 한마디. 대사만.";
        askAndSay(server, persona, "나는 방금 죽었다.", "으아... 이럴 줄 알았어...", false);
    }

    /** Groq 호출 + 실패 시 기본 대사, 서버 스레드에서 채팅 출력. chainNpc=true 면 NPC끼리 대화로 이어짐. */
    private void askAndSay(MinecraftServer server, String persona, String situation,
                           String fallback, boolean chainNpc) {
        GroqClient.ask("groq_key_neutral.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : fallback;
            server.execute(() -> {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("<오서준> " + line), false);
                if (chainNpc) com.anti069.mod.ai.NpcTalk.lineSpoken(this, "오서준", line);
            });
        });
    }

    private MinecraftServer server() {
        return this.level() instanceof ServerLevel sl ? sl.getServer() : null;
    }
}
