package com.anti069.mod.ai;

import com.anti069.mod.Anti069Mod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * [역할] Groq(무료 AI API)에 말을 보내고 답을 받아오는 담당.
 *
 * 중요: API 키는 코드/깃허브에 절대 넣지 않습니다.
 * 게임 폴더(.minecraft)에 있는 텍스트 파일에서 읽어옵니다.
 *   - anti069  -> groq_key_hostile.txt
 *   - 069_36   -> groq_key_neutral.txt
 * 게임 켜기 전에 그 파일에 키를 붙여넣어 두기만 하면 됩니다.
 *
 * 또 중요: AI 응답은 인터넷을 기다려야 해서 느립니다. 게임 본체(메인 스레드)에서
 * 기다리면 게임이 멈추므로, 별도 스레드에서 요청하고(CompletableFuture) 답이 오면
 * 콜백으로 넘겨줍니다. 실제 채팅 출력은 호출한 쪽에서 서버 스레드에 다시 얹어야 합니다.
 */
public class GroqClient {

    // 사용할 모델 이름. 나중에 바꾸고 싶으면 이 한 줄만 고치면 됩니다.
    private static final String MODEL = "llama-3.3-70b-versatile";

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 키 파일을 게임 폴더에서 읽어옵니다. 없거나 비어 있으면 null. */
    private static String readKey(String fileName) {
        try {
            Path p = FabricLoader.getInstance().getGameDir().resolve(fileName);
            if (!Files.exists(p)) {
                Anti069Mod.LOGGER.warn("[groq] 키 파일이 없습니다: {}", p);
                return null;
            }
            String key = Files.readString(p).trim();
            return key.isEmpty() ? null : key;
        } catch (Exception e) {
            Anti069Mod.LOGGER.error("[groq] 키 파일 읽기 실패", e);
            return null;
        }
    }

    /**
     * [역할] system(성격 설정) + user(플레이어가 한 말/상황)를 보내고,
     * AI의 한 줄 답을 onReply 콜백으로 돌려줍니다.
     *
     * @param keyFileName 어떤 키 파일을 쓸지 (hostile/neutral)
     * @param persona     AI 성격 설정 (system 프롬프트)
     * @param situation   지금 상황/플레이어 발화 (user 프롬프트)
     * @param onReply     답이 도착하면 실행할 것 (실패 시 null 전달)
     */
    public static void ask(String keyFileName, String persona, String situation, Consumer<String> onReply) {
        String key = readKey(keyFileName);
        if (key == null) {
            onReply.accept(null); // 키 없으면 조용히 실패 → 호출 측에서 기본 대사 처리
            return;
        }

        // 요청 본문(JSON) 만들기
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", persona);

        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", situation);

        JsonArray messages = new JsonArray();
        messages.add(sys);
        messages.add(usr);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("messages", messages);
        body.addProperty("max_tokens", 60);      // 짧게 (게임 채팅용)
        body.addProperty("temperature", 0.9);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        // 별도 스레드에서 요청 (게임 안 멈추게)
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() != 200) {
                            Anti069Mod.LOGGER.warn("[groq] 응답 코드 {}: {}", res.statusCode(), res.body());
                            return null;
                        }
                        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                        return json.getAsJsonArray("choices")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content").getAsString().trim();
                    } catch (Exception e) {
                        Anti069Mod.LOGGER.error("[groq] 요청 실패", e);
                        return null;
                    }
                })
                .thenAccept(onReply); // 답(또는 null)을 콜백으로
    }
}
