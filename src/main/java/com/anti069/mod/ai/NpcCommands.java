package com.anti069.mod.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [역할] NPC 명령 처리 공용 시스템. anti069/069_36 둘 다 사용.
 * 플레이어 채팅을 Groq로 9개 명령 중 하나로 분류 → 실제 행동 실행.
 * 명령이 아니면(none) 대화 콜백으로 넘김.
 *
 * 지속 명령(follow/sprint)은 엔티티 id별 상태로 저장하고 매 틱 유지한다.
 */
public class NpcCommands {

    private enum Mode { NONE, FOLLOW }

    private static final class State {
        Mode mode = Mode.NONE;
        ServerPlayer target;
        boolean sprint = false;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private static State state(PathfinderMob npc) {
        return STATES.computeIfAbsent(npc.getId(), k -> new State());
    }

    /** 채팅 명령 처리. 명령이 아니면 onNotCommand 실행(=대화). */
    public static void handle(PathfinderMob npc, String keyFile, ServerPlayer player,
                              String text, Runnable onNotCommand) {
        final MinecraftServer server = ((ServerLevel) player.level()).getServer();
        String persona = "너는 플레이어의 말을 마인크래프트 NPC 명령으로 분류하는 분류기다. "
                + "가능한 명령: come(오기), follow(따라오기), sprint(뛰기), jump(점프), stop(멈추기), "
                + "attack_nearest(근처 대상 공격), break_block(앞 블록 부수기), open_door(문 열기), "
                + "use_redstone(레버/버튼 작동), give_item(가진 아이템 달라고 함), none(명령 아님). "
                + "give_item 이면 item 필드에 마인크래프트 영문 아이템 id를 넣어라(예: 철 곡괭이→iron_pickaxe, "
                + "철괴/철 주괴→iron_ingot, 다이아몬드→diamond, 빵→bread, 돌→stone). "
                + "설명 없이 오직 JSON 으로만 답하라. 예: {\"action\":\"give_item\",\"item\":\"iron_pickaxe\"} "
                + "또는 {\"action\":\"follow\"}.";
        GroqClient.ask(keyFile, persona, "플레이어 말: \"" + text + "\"", reply -> {
            String action = parseAction(reply);
            String item = parseItem(reply);
            if (server == null) return;
            server.execute(() -> {
                if (action.equals("none")) {
                    if (onNotCommand != null) onNotCommand.run();
                } else {
                    execute(npc, player, action, item);
                }
            });
        });
    }

    private static String parseAction(String reply) {
        if (reply == null) return "none";
        String r = reply.replace("```json", "").replace("```", "").trim();
        try {
            JsonObject o = JsonParser.parseString(r).getAsJsonObject();
            return o.get("action").getAsString().trim().toLowerCase();
        } catch (Exception e) {
            String low = reply.toLowerCase();
            for (String a : new String[]{"come", "follow", "sprint", "jump", "stop",
                    "attack_nearest", "break_block", "open_door", "use_redstone", "give_item"}) {
                if (low.contains(a)) return a;
            }
            return "none";
        }
    }

    /** 응답 JSON 에서 item 필드(영문 아이템 id)를 뽑는다. 없으면 null.
     *  공백은 밑줄로 바꿔 "iron pickaxe" 같은 표기도 "iron_pickaxe"로 맞춘다. */
    private static String parseItem(String reply) {
        if (reply == null) return null;
        String r = reply.replace("```json", "").replace("```", "").trim();
        try {
            JsonObject o = JsonParser.parseString(r).getAsJsonObject();
            if (o.has("item") && !o.get("item").isJsonNull()) {
                return o.get("item").getAsString().trim().toLowerCase().replace(" ", "_");
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** 실제 행동 실행 (서버 스레드에서 호출됨). */
    private static void execute(PathfinderMob npc, ServerPlayer player, String action, String item) {
        State st = state(npc);
        Level level = npc.level();
        switch (action) {
            case "come" -> {
                st.mode = Mode.NONE;
                npc.getNavigation().moveTo(player, 1.3);
            }
            case "follow" -> {
                st.mode = Mode.FOLLOW;
                st.target = player;
            }
            case "sprint" -> {
                st.sprint = true;
                npc.setSprinting(true);
            }
            case "jump" -> {
                Vec3 v = npc.getDeltaMovement();
                npc.setDeltaMovement(v.x, 0.5, v.z);
            }
            case "stop" -> {
                st.mode = Mode.NONE;
                st.sprint = false;
                npc.setSprinting(false);
                npc.getNavigation().stop();
                npc.setTarget(null);
            }
            case "attack_nearest" -> {
                LivingEntity t = nearestLiving(npc, level, 12.0);
                if (t != null) npc.setTarget(t);
            }
            case "break_block" -> breakInFront(npc, level);
            case "open_door" -> toggleNearbyBlock(npc, level, "door", BlockStateProperties.OPEN);
            case "use_redstone" -> toggleNearbyRedstone(npc, level);
            case "give_item" -> giveItemFromInventory(npc, player, item);
            default -> { /* none */ }
        }
    }

    /** 매 틱 호출: follow 유지 등. */
    public static void tick(PathfinderMob npc) {
        State st = STATES.get(npc.getId());
        if (st == null) return;
        if (st.mode == Mode.FOLLOW && st.target != null && st.target.isAlive()) {
            if (npc.tickCount % 10 == 0) {
                npc.getNavigation().moveTo(st.target, st.sprint ? 1.6 : 1.2);
            }
        }
    }

    /** 인벤에 해당 아이템이 있으면 꺼내서 플레이어 쪽으로 던져주고, 없으면 없다고 말한다. */
    private static void giveItemFromInventory(PathfinderMob npc, ServerPlayer player, String itemId) {
        MinecraftServer server = (npc.level() instanceof ServerLevel sl) ? sl.getServer() : null;
        String tag = npcTag(npc);

        if (itemId == null || itemId.isEmpty()) {
            say(server, tag, "뭘 달라는 건데?");
            return;
        }
        if (!(npc instanceof com.anti069.mod.entity.NpcInventoryHolder holder)) return;

        ItemStack got = holder.takeItemByName(itemId);
        if (got.isEmpty()) {
            say(server, tag, "그런 건 없는데.");
            return;
        }
        // NPC 위치에서 아이템을 떨궈 플레이어 쪽으로 살짝 던진다(건네주는 느낌).
        ItemEntity ie = new ItemEntity(npc.level(), npc.getX(), npc.getY() + 0.5, npc.getZ(), got);
        Vec3 dir = player.position().subtract(npc.position());
        if (dir.lengthSqr() > 1.0e-4) {
            dir = dir.normalize().scale(0.3);
            ie.setDeltaMovement(dir.x, 0.2, dir.z);
        }
        npc.level().addFreshEntity(ie);
        say(server, tag, "여기, 받아.");
    }

    /** NPC 종류에 맞는 채팅 표시 이름. */
    private static String npcTag(PathfinderMob npc) {
        return (npc instanceof com.anti069.mod.entity.Anti069Entity) ? "<anti069>" : "<069_36>";
    }

    /** 전체 채팅에 NPC 한마디 출력(간단 대사용). */
    private static void say(MinecraftServer server, String tag, String line) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(Component.literal(tag + " " + line), false);
    }

    private static LivingEntity nearestLiving(PathfinderMob npc, Level level, double range) {
        AABB box = npc.getBoundingBox().inflate(range);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != npc && !(e instanceof Player) && e.isAlive());
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.distanceToSqr(npc);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** npc 정면(몸 방향) 발/머리 높이 블록 부수기. */
    private static void breakInFront(PathfinderMob npc, Level level) {
        double rad = Math.toRadians(npc.getYRot());
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        BlockPos feet = BlockPos.containing(npc.getX() + dx, npc.getY(), npc.getZ() + dz);
        if (!level.getBlockState(feet).isAir()) level.destroyBlock(feet, true);
        BlockPos head = feet.above();
        if (!level.getBlockState(head).isAir()) level.destroyBlock(head, true);
    }

    /** 반경 3칸 내 이름에 keyword 포함된 블록의 boolean 속성 토글 (문 열기 등). */
    private static void toggleNearbyBlock(PathfinderMob npc, Level level, String keyword,
                                          net.minecraft.world.level.block.state.properties.BooleanProperty prop) {
        BlockPos origin = npc.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(3, 2, 3))) {
            BlockState st = level.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            if (id.contains(keyword) && st.hasProperty(prop)) {
                level.setBlock(pos.immutable(), st.setValue(prop, !st.getValue(prop)), 3);
                return;
            }
        }
    }

    /** 반경 3칸 내 레버/버튼 작동. */
    private static void toggleNearbyRedstone(PathfinderMob npc, Level level) {
        BlockPos origin = npc.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(3, 2, 3))) {
            BlockState st = level.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            if ((id.contains("lever") || id.contains("button")) && st.hasProperty(BlockStateProperties.POWERED)) {
                level.setBlock(pos.immutable(),
                        st.setValue(BlockStateProperties.POWERED, !st.getValue(BlockStateProperties.POWERED)), 3);
                return;
            }
        }
    }
}
