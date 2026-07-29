package com.anti069.mod;

import com.anti069.mod.ai.GroqClient;
import com.anti069.mod.entity.Anti069Entity;
import com.anti069.mod.entity.ModEntities;
import com.anti069.mod.entity.ModSounds;
import com.anti069.mod.entity.Neutral06936Entity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * [역할] 공통 진입점. 엔티티/사운드 등록 + 069_36 대화 리스너. (Mojang 매핑)
 */
public class Anti069Mod implements ModInitializer {

    public static final String MOD_ID = "anti069mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double TALK_RANGE = 12.0;

    // 리스폰 on/off (기본 켜짐) + 대기 목록
    private static boolean respawnEnabled = true;
    private static final List<Pending> pendingRespawns = new ArrayList<>();

    private static final class Pending {
        final ServerLevel level; final double x, y, z; final EntityType<?> type; int ticks;
        Pending(ServerLevel level, double x, double y, double z, EntityType<?> type, int ticks) {
            this.level = level; this.x = x; this.y = y; this.z = z; this.type = type; this.ticks = ticks;
        }
    }

    /** 엔티티가 죽을 때 호출 → 5초 뒤 같은 자리에 리스폰 예약 (리스폰 꺼져 있으면 무시). */
    public static void scheduleRespawn(ServerLevel level, double x, double y, double z, EntityType<?> type) {
        if (!respawnEnabled) return;
        pendingRespawns.add(new Pending(level, x, y, z, type, 20 * 5)); // 5초
    }

    @Override
    public void onInitialize() {
        ModEntities.registerModEntities();
        ModSounds.registerModSounds();
        registerNeutralChatListener();
        registerJoinNotice();
        registerRespawnTick();
        registerCommands();
        LOGGER.info("[{}] 모드 초기화 완료", MOD_ID);
    }

    /** 매 틱 리스폰 대기 목록을 처리. */
    private void registerRespawnTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (pendingRespawns.isEmpty()) return;
            Iterator<Pending> it = pendingRespawns.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (--p.ticks <= 0) {
                    Entity e = p.type.create(p.level, EntitySpawnReason.MOB_SUMMONED);
                    if (e != null) {
                        e.setPos(p.x, p.y, p.z);
                        p.level.addFreshEntity(e);
                    }
                    it.remove();
                }
            }
        });
    }

    /** /anti069 respawn on|off 커맨드 등록. */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        Commands.literal("anti069")
                                .then(Commands.literal("respawn")
                                        .then(Commands.literal("on").executes(ctx -> {
                                            respawnEnabled = true;
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("[anti069mod] 리스폰 켜짐"), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("off").executes(ctx -> {
                                            respawnEnabled = false;
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("[anti069mod] 리스폰 꺼짐"), false);
                                            return 1;
                                        })))));
    }

    /** 월드 접속 시 모드가 켜져 있음을 담백하게 안내 (야생 서버에서 깜빡 방지용). */
    private void registerJoinNotice() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                handler.player.sendSystemMessage(
                        Component.literal("[anti069mod] 모드가 켜져 있습니다.")));
    }

    /** 플레이어 채팅 시 근처 069_36 이 Groq로 답합니다. */
    private void registerNeutralChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            ServerLevel level = (ServerLevel) sender.level();
            AABB area = sender.getBoundingBox().inflate(TALK_RANGE);
            String playerText = message.decoratedContent().getString();

            // 069_36 (중립 클론): 명령 분류 → 명령이면 실행, 아니면 대화
            List<Neutral06936Entity> neutrals =
                    level.getEntitiesOfClass(Neutral06936Entity.class, area, e -> true);
            if (!neutrals.isEmpty()) {
                Neutral06936Entity n = neutrals.get(0);
                com.anti069.mod.ai.NpcCommands.handle(
                        n, "groq_key_neutral.txt", sender, playerText, () -> n.heardChat(playerText));
            }

            // anti069: 명령 분류 → 명령이면 실행, 아니면 대화
            List<Anti069Entity> antis =
                    level.getEntitiesOfClass(Anti069Entity.class, area, e -> true);
            if (!antis.isEmpty()) {
                Anti069Entity a = antis.get(0);
                com.anti069.mod.ai.NpcCommands.handle(
                        a, "groq_key_hostile.txt", sender, playerText, () -> a.heardChat(playerText));
            }

            // 5seojune (겁쟁이): 명령 분류 → 명령이면 실행, 아니면 대화 (중립 키 재활용)
            List<com.anti069.mod.entity.Seojune5Entity> seojunes =
                    level.getEntitiesOfClass(com.anti069.mod.entity.Seojune5Entity.class, area, e -> true);
            if (!seojunes.isEmpty()) {
                com.anti069.mod.entity.Seojune5Entity s = seojunes.get(0);
                com.anti069.mod.ai.NpcCommands.handle(
                        s, "groq_key_neutral.txt", sender, playerText, () -> s.heardChat(playerText));
            }
        });
    }


}
