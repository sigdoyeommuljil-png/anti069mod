package com.anti069.mod.entity;

import com.anti069.mod.ai.GroqClient;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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

    public Seojune5Entity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)      // 겁쟁이라 좀 약함
                .add(Attributes.MOVEMENT_SPEED, 0.32)  // 도망가야 하니 좀 빠름
                .add(Attributes.FOLLOW_RANGE, 20.0)
                // 로케이터바 표시(다른 NPC와 동일)
                .add(Attributes.WAYPOINT_TRANSMIT_RANGE, 512.0);
    }

    @Override
    protected void registerGoals() {
        // 싸우는 목표 없음(겁쟁이). 물 피하고, 돌아다니고, 쳐다보고, 두리번거리기만.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
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

        // 겁먹은 동안은 계속 도망
        if (panicTimer > 0) {
            panicTimer--;
            if (this.tickCount % 10 == 0) fleeFromNearestPlayer();
        }

        // 근처 플레이어 있으면 5초쯤마다 허둥대는 혼잣말
        if (--idleTimer <= 0) {
            idleTimer = 100 + this.random.nextInt(60);
            if (this.level().getNearestPlayer(this, 24.0) != null) {
                idleTalk();
            }
        }
    }

    /** 맞으면 → 놀라서 폭발 장난 + 도망 시작 + 호들갑 대사. 반격은 안 함(겁쟁이). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
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

    /** 5seojune 공통 성격 설명. */
    private String personaBase() {
        return "너는 마인크래프트 서버 NPC '5seojune'이다. 겁이 아주 많고 늘 허둥대며 당황한다. "
                + "조금만 무섭거나 놀라도 호들갑을 떤다. ";
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
            com.anti069.mod.ai.NpcTalk.deathSeen(this, "5seojune");
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
                        Component.literal("<5seojune> " + line), false);
                if (chainNpc) com.anti069.mod.ai.NpcTalk.lineSpoken(this, "5seojune", line);
            });
        });
    }

    private MinecraftServer server() {
        return this.level() instanceof ServerLevel sl ? sl.getServer() : null;
    }
}
