package com.johnnyblabs.openspec.ai.backend;

import com.google.gson.*;
import com.johnnyblabs.openspec.ai.AiApiException;
import java.util.Set;

/** Codecs for the consumed 0.160.0 generated schemas; optional additions are ignored. */
public final class CodexProtocol {
    private CodexProtocol() {}
    public sealed interface Notification permits Delta, ItemCompleted, TurnCompleted, AccountUpdated, TurnError, TokenUsage, Unknown {}
    public record Delta(String threadId, String turnId, String itemId, String text) implements Notification {}
    public record ItemCompleted(String threadId, String turnId, String itemId, String text, String phase, boolean assistant) implements Notification {}
    public record TurnCompleted(String threadId, String turnId, TurnStatus status) implements Notification {}
    public record TokenUsage(String threadId,String turnId,long totalTokens,long reasoningTokens,Long contextWindow) implements Notification {}
    public record AccountUpdated() implements Notification {}
    public record TurnError(String threadId, String turnId) implements Notification {}
    public record Unknown(String method) implements Notification {}
    public enum TurnStatus { IN_PROGRESS, COMPLETED, FAILED, INTERRUPTED }
    public enum Authentication { CHATGPT, API_KEY, SIGNED_OUT, UNSUPPORTED }
    public enum UsageStatus { ALLOWED, UNAVAILABLE, UNKNOWN }
    public record WorkspaceRouting(String accountId,String backendOrigin,String routingOverride) {}
    public record AccountRead(Authentication authentication,String reportedMode,String email,String planType,WorkspaceRouting routing) {}
    public record Initialized(String userAgent, String platformOs) {}
    public record ThreadStarted(String threadId, String model) {}
    public record TurnStarted(String turnId, TurnStatus status) {}
    public sealed interface Frame permits NotificationFrame, RequestFrame, SuccessFrame, FailureFrame {}
    public record NotificationFrame(JsonObject value) implements Frame { public NotificationFrame { value=value.deepCopy(); } public JsonObject value() { return value.deepCopy(); } }
    public record RequestFrame(JsonObject value) implements Frame { public RequestFrame { value=value.deepCopy(); } public JsonObject value() { return value.deepCopy(); } }
    public record SuccessFrame(int id, JsonObject result) implements Frame { public SuccessFrame { result=result.deepCopy(); } public JsonObject result() { return result.deepCopy(); } }
    public record FailureFrame(int id) implements Frame {}

    public static Frame frame(JsonObject value) throws AiApiException {
        try {
            if (value.has("method")) {
                text(value, "method");
                return value.has("id") ? new RequestFrame(value) : new NotificationFrame(value);
            }
            JsonElement id = value.get("id");
            if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isNumber()
                    || !id.getAsString().matches("[1-9][0-9]{0,8}")) throw incompatible();
            if (value.has("error") == value.has("result")) throw incompatible();
            return value.has("error") ? new FailureFrame(id.getAsInt()) : new SuccessFrame(id.getAsInt(), object(value, "result"));
        } catch (RuntimeException e) { throw incompatible(); }
    }
    public static AccountRead account(JsonObject response) throws AiApiException {
        JsonElement requires=response.get("requiresOpenaiAuth");
        if(requires==null || !requires.isJsonPrimitive() || !requires.getAsJsonPrimitive().isBoolean())throw incompatible();
        JsonElement value=response.get("account");
        if(value==null || value.isJsonNull())return new AccountRead(Authentication.SIGNED_OUT,"signedOut",null,null,null);
        JsonObject account=object(response,"account");String mode=text(account,"type");
        Authentication kind=switch(mode){case "chatgpt" -> Authentication.CHATGPT;case "apiKey" -> Authentication.API_KEY;default -> Authentication.UNSUPPORTED;};
        String email=optionalText(account,"email"),plan=optionalText(account,"planType");
        if(kind==Authentication.CHATGPT && (!account.has("email") || plan==null))throw incompatible();
        JsonElement route=response.get("workspaceRouting");WorkspaceRouting routing=null;
        if(route!=null && !route.isJsonNull()) {JsonObject fields=object(response,"workspaceRouting");routing=new WorkspaceRouting(text(fields,"chatgptAccountId"),text(fields,"backendOrigin"),text(fields,"accountRoutingOverride"));}
        return new AccountRead(kind,mode,email,plan,routing);
    }
    public static UsageStatus usage(JsonObject value) {
        JsonElement allowed=value.get("ordinaryUsageAllowed");
        if(allowed==null || !allowed.isJsonPrimitive() || !allowed.getAsJsonPrimitive().isBoolean())return UsageStatus.UNKNOWN;
        return allowed.getAsBoolean()?UsageStatus.ALLOWED:UsageStatus.UNAVAILABLE;
    }
    public static Initialized initialized(JsonObject value) throws AiApiException {
        text(value,"codexHome"); text(value,"platformFamily");
        return new Initialized(text(value, "userAgent"), text(value, "platformOs"));
    }
    public static ThreadStarted threadStarted(JsonObject value) throws AiApiException {
        return new ThreadStarted(text(object(value, "thread"), "id"), text(value, "model"));
    }
    public static TurnStarted turnStarted(JsonObject value) throws AiApiException {
        JsonObject turn = object(value, "turn");
        return new TurnStarted(text(turn, "id"), status(turn));
    }
    public static Notification notification(JsonObject event) throws AiApiException {
        String method = text(event, "method");
        // Unknown optional notifications may omit params or use a non-object value.
        if (!Set.of("account/updated", "turn/completed", "item/completed", "item/agentMessage/delta", "error", "thread/tokenUsage/updated").contains(method)) return new Unknown(method);
        if (method.equals("account/updated")) return new AccountUpdated();
        JsonObject params = object(event, "params");
        String thread = text(params, "threadId");
        if (method.equals("turn/completed")) {
            JsonObject turn = object(params, "turn");
            return new TurnCompleted(thread, text(turn, "id"), status(turn));
        }
        String turn = text(params, "turnId");
        if (method.equals("thread/tokenUsage/updated")) {
            JsonObject usage=object(params,"tokenUsage"),total=object(usage,"total"),last=object(usage,"last");
            validateUsage(total); validateUsage(last);
            JsonElement window=usage.get("modelContextWindow");
            Long context=window==null || window.isJsonNull()?null:integer(window);
            if(context!=null && context<=0)throw incompatible();
            return new TokenUsage(thread,turn,integer(total.get("totalTokens")),integer(total.get("reasoningOutputTokens")),context);
        }
        if (method.equals("error")) {
            object(params,"error");
            JsonElement retry=params.get("willRetry");
            if(retry==null || !retry.isJsonPrimitive() || !retry.getAsJsonPrimitive().isBoolean())throw incompatible();
            return new TurnError(thread, turn);
        }
        if (method.equals("item/agentMessage/delta")) return new Delta(thread, turn, text(params, "itemId"), string(params, "delta", true));
        JsonObject item = object(params, "item");
        JsonElement completed = params.get("completedAtMs");
        if (completed == null || !completed.isJsonPrimitive() || !completed.getAsJsonPrimitive().isNumber()) throw incompatible();
        try { completed.getAsBigDecimal().toBigIntegerExact().longValueExact(); } catch (ArithmeticException e) { throw incompatible(); }
        String type = text(item, "type"), id = text(item, "id");
        boolean assistant = type.equals("agentMessage");
        return new ItemCompleted(thread, turn, id, assistant ? string(item, "text", true) : "", assistant ? optionalText(item, "phase") : null, assistant);
    }
    private static void validateUsage(JsonObject value) throws AiApiException {
        for(String key:Set.of("cachedInputTokens","inputTokens","outputTokens","reasoningOutputTokens","totalTokens"))if(integer(value.get(key))<0)throw incompatible();
        if(value.has("cacheWriteInputTokens") && integer(value.get("cacheWriteInputTokens"))<0)throw incompatible();
    }
    private static long integer(JsonElement value) throws AiApiException {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw incompatible();
        try {return value.getAsBigDecimal().toBigIntegerExact().longValueExact();}catch(ArithmeticException e){throw incompatible();}
    }
    private static TurnStatus status(JsonObject turn) throws AiApiException {
        if (!turn.has("items") || !turn.get("items").isJsonArray()) throw incompatible();
        return switch (text(turn, "status")) {
            case "inProgress" -> TurnStatus.IN_PROGRESS;
            case "completed" -> TurnStatus.COMPLETED;
            case "failed" -> TurnStatus.FAILED;
            case "interrupted" -> TurnStatus.INTERRUPTED;
            default -> throw incompatible();
        };
    }
    private static String text(JsonObject value, String key) throws AiApiException { return string(value,key,false); }
    private static String string(JsonObject value, String key, boolean emptyAllowed) throws AiApiException {
        JsonElement field = value == null ? null : value.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) throw incompatible();
        String text = field.getAsString();
        if (!emptyAllowed && text.isBlank()) throw incompatible();
        return text;
    }
    private static String optionalText(JsonObject value, String key) throws AiApiException {
        JsonElement field=value.get(key); return field==null || field.isJsonNull() ? null : string(value,key,true);
    }
    private static JsonObject object(JsonObject value, String key) throws AiApiException {
        JsonElement field = value == null ? null : value.get(key);
        if (field == null || !field.isJsonObject()) throw incompatible();
        return field.getAsJsonObject();
    }
    private static AiApiException incompatible() { return new AiApiException("Codex returned an incompatible required protocol field; no result was applied."); }
}
