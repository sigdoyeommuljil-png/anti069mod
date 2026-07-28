package com.anti069.mod.entity;

import com.anti069.mod.ai.GroqClient;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * [역할] 069_36 — 서버 주인의 클론 NPC. 쿨하고 시크한 성격, 맞으면 좀 화냄.
 * anti069 가 가진 기본 기능(대사 쿨타임 + 인벤토리 + 죽음 반응)을 동일하게 가짐.
 */
public class Neutral06936Entity extends PathfinderMob implements NpcInventoryHolder {

    private int talkCooldown = 0;  // 20틱=1초
    private int idleTimer = 100;   // 혼잣말 타이머
    private int annoyed = 0;       // 맞은 횟수 → 대사가 점점 화남
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(8, ItemStack.EMPTY);

    public Neutral06936Entity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 체력 30% 초과일 때만 맞서 싸움 (그 이하로 떨어지면 도망 로직이 우선)
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
            @Override public boolean canUse() { return healthRatio() > 0.3f && super.canUse(); }
            @Override public boolean canContinueToUse() { return healthRatio() > 0.3f && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        // 맞으면 때린 상대에게 반격 (체력 30% 초과일 때만)
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return healthRatio() > 0.3f && super.canUse(); }
        });
    }

    private float healthRatio() {
        return this.getHealth() / this.getMaxHealth();
    }

    /** 가장 가까운 플레이어 반대 방향으로 도망. */
    private void fleeFromNearestPlayer() {
        Player near = this.level().getNearestPlayer(this, 16.0);
        if (near == null) return;
        Vec3 away = this.position().subtract(near.position());
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        Vec3 dest = this.position().add(away.normalize().scale(10.0));
        this.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.6);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (talkCooldown > 0) talkCooldown--;

        com.anti069.mod.ai.NpcCommands.tick(this); // 명령(따라오기 등) 유지

        // 체력 30% 이하면 도망 (공격 중단하고 튐)
        if (healthRatio() <= 0.3f && this.tickCount % 20 == 0) {
            this.setTarget(null);
            fleeFromNearestPlayer();
        }

        // 근처 플레이어 있으면 5초쯤마다 혼잣말
        if (--idleTimer <= 0) {
            idleTimer = 100 + this.random.nextInt(60);
            if (this.level().getNearestPlayer(this, 24.0) != null) {
                idleTalk();
            }
        }
    }

    /** 자아 있는 느낌의 혼잣말 (쿨하게). */
    private void idleTalk() {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase()
                + "지금 월드를 둘러보며 혼잣말을 한다. 자아가 있는 것처럼, 방금 든 생각이나 관찰을 자연스럽게. "
                + "반드시 한국어(한글)로만, 영어 절대 금지. 짧은 반말 한마디. 대사만.";
        askAndSay(server, persona, com.anti069.mod.ai.Perception.describe(this) + " 이 상황에서 혼잣말을 한다.", "흐음.", true);
    }

    /** 맞으면 좀 화냄 (대사가 점점 세짐). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean result = super.hurtServer(level, source, amount);
        if (source.getEntity() instanceof Player) {
            annoyed++;
            speakHurt();
        }
        return result;
    }

    /** 근처 채팅에 반응. 쿨하게, 맞았으면 화내면서. 1초 쿨타임. */
    public void heardChat(String playerText) {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;

        String mood = (annoyed == 0)
                ? "지금은 기분이 나쁘지 않다."
                : "방금 맞아서 좀 화가 나 있다.";
        String persona = personaBase() + mood
                + " 반드시 한국어(한글)로만, 영어 절대 섞지 말고 짧게 답하라. 대사만.";
        String situation = com.anti069.mod.ai.Perception.describe(this)
                + " 플레이어가 너에게 말했다: \"" + playerText + "\"";
        askAndSay(server, persona, situation, "...뭐.", false);
    }

    private void speakHurt() {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase() + "방금 플레이어한테 맞았다. 살짝 화내라. "
                + "반드시 한국어(한글)로만, 영어 섞지 말고 짧은 반말 한마디. 대사만.";
        askAndSay(server, persona, "플레이어가 나를 때렸다. (누적 " + annoyed + "회)", "아, 왜 때려.", false);
    }

    /** 069_36 공통 성격 설명. */
    private String personaBase() {
        return "너는 이 서버 주인을 본뜬 클론 NPC '069_36'이다. 평소엔 쿨하고 시크하며 담백하게 짧게 말한다. ";
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

    // ---- 죽음 ----
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            dropInventory();
            announceDeath();
            speakDying();
            com.anti069.mod.ai.NpcTalk.deathSeen(this, "069_36");
        }
        if (this.level() instanceof ServerLevel sl) {
            com.anti069.mod.Anti069Mod.scheduleRespawn(sl, this.getX(), this.getY(), this.getZ(), ModEntities.NEUTRAL06936);
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
                + "반드시 한국어(한글)로만, 영어 섞지 말고 쿨하게 짧은 반말 한마디 남겨라. 대사만.";
        askAndSay(server, persona, "나는 방금 죽었다.", "쳇, 이렇게 가네.", false);
    }

    /** Groq 호출 + 실패 시 기본 대사, 서버 스레드에서 채팅 출력.
     *  chainNpc=true 이면 이 말도 NPC끼리 대화의 일부로 처리(상대가 반응할 수 있게). */
    private void askAndSay(MinecraftServer server, String persona, String situation,
                           String fallback, boolean chainNpc) {
        GroqClient.ask("groq_key_neutral.txt", persona, situation, reply -> {
            String line = (reply != null && !reply.isEmpty()) ? reply : fallback;
            server.execute(() -> {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("<069_36> " + line), false);
                if (chainNpc) com.anti069.mod.ai.NpcTalk.lineSpoken(this, "069_36", line);
            });
        });
    }

    /**
     * [인터페이스 구현] 인벤에서 지정 아이템 1칸을 꺼내 반환(없으면 빈 스택).
     * 레지스트리 '이름 문자열'로 비교 → 26.2 아이템 조회 API 변경에 안 걸린다.
     */
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

    /** [NPC끼리 대화] 다른 NPC의 말/죽음에 쿨하게 한마디 반응한다. NpcTalk 가 호출. */
    public void reactToNpc(String otherTag, String otherLine, boolean death) {
        if (talkCooldown > 0) return;
        talkCooldown = 20;
        final MinecraftServer server = server();
        if (server == null) return;
        String persona = personaBase()
                + (death
                    ? ("다른 NPC '" + otherTag + "'가 방금 죽었다. 여기에 쿨하게 한마디 반응하라. ")
                    : ("다른 NPC '" + otherTag + "'가 \"" + otherLine + "\"라고 말했다. 여기에 쿨하게 대꾸하라. "))
                + "반드시 한국어(한글)로만, 영어 금지, 짧은 반말 한마디. 대사만.";
        String situation = death
                ? ("'" + otherTag + "'가 죽었다.")
                : ("'" + otherTag + "'의 말: \"" + otherLine + "\"");
        // 이 반응도 대화의 일부 → chainNpc=true 로 상대가 또 반응할 수 있게(3턴 제한은 NpcTalk 관리)
        askAndSay(server, persona, situation, death ? "...갔군." : "...그래서?", true);
    }

    private MinecraftServer server() {
        return this.level() instanceof ServerLevel sl ? sl.getServer() : null;
    }
}
