package com.aibuilder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class AIClient {

    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Logger logger;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final int maxBlocks;
    private final int maxDimension;

    public AIClient(Logger logger, String provider, String apiKey, String model, String apiUrl, int maxBlocks,
            int maxDimension) {
        this.logger = logger;
        this.provider = provider.toLowerCase();
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
        this.maxBlocks = maxBlocks;
        this.maxDimension = maxDimension;
    }

    public CompletableFuture<String> generateStructure(String userPrompt, String scale) {
        int maxElements = switch (scale.toLowerCase()) {
            case "small" -> 25;
            case "large" -> 120;
            default -> 45;
        };

        String systemInstruction = "You are a Minecraft Java Edition builder. Build whatever the user asks.\n\n" +

                "Before placing a single block, decide three things:\n" +
                "  1. What is the most RECOGNIZABLE feature of this subject? Lead with that.\n" +
                "  2. What COLOR palette makes it instantly identifiable?\n" +
                "  3. What ONE unexpected detail will make this build feel unique and not generic?\n\n" +

                "Then build it. Every build should look different from the last.\n\n" +

                "OUTPUT: A valid JSON array only. No markdown, no explanation, nothing outside the JSON.\n\n" +

                "  {\"x\": x, \"y\": y, \"z\": z, \"type\": \"MATERIAL\"}\n" +
                "  {\"x1\": x1, \"y1\": y1, \"z1\": z1, \"x2\": x2, \"y2\": y2, \"z2\": z2, \"type\": \"MATERIAL\"}\n\n"
                +

                "RULES (these are technical, not creative — follow them exactly):\n" +
                "  - y=0 is ground. First element must be a ground layer (GRASS_BLOCK or DIRT) at y=0\n" +
                "    covering the full footprint. No blocks may float without support below them.\n" +
                "  - Material names must be valid Minecraft Java Edition SCREAMING_SNAKE_CASE.\n" +
                "  - Do not use: OAK_WOOD_PLANK, WOODEN_DOOR, LOG, STEP, LEAVES, TORCH, DOUBLE_STEP.\n" +
                "  - Max size: " + maxDimension + "x" + maxDimension + "x" + maxDimension + ".\n" +
                "  - Max total blocks: " + maxBlocks + ".\n" +
                "  - Max JSON elements: " + maxElements + ".";

        return switch (provider) {
            case "openai", "ollama", "deepseek" -> callOpenAI(systemInstruction, userPrompt);
            default -> callGemini(systemInstruction, userPrompt);
        };
    }

    private CompletableFuture<String> callGemini(String systemInstruction, String userPrompt) {
        String baseUrl = (apiUrl == null || apiUrl.isBlank())
                ? "https://generativelanguage.googleapis.com"
                : apiUrl.replaceAll("/$", "");

        String url = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        JsonObject requestJson = new JsonObject();

        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject partObj = new JsonObject();
        partObj.addProperty("text", userPrompt);
        parts.add(partObj);
        contentObj.add("parts", parts);
        contentObj.addProperty("role", "user");
        contents.add(contentObj);
        requestJson.add("contents", contents);

        JsonObject systemInstructionObj = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPartObj = new JsonObject();
        sysPartObj.addProperty("text", systemInstruction);
        sysParts.add(sysPartObj);
        systemInstructionObj.add("parts", sysParts);
        requestJson.add("systemInstruction", systemInstructionObj);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.addProperty("temperature", 1.0);
        generationConfig.addProperty("maxOutputTokens", 8192);
        requestJson.add("generationConfig", generationConfig);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
                .timeout(Duration.ofSeconds(120))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("Gemini API error: " + response.statusCode() + " | " + response.body());
                        throw new RuntimeException(extractErrorMessage(response.body(), "error"));
                    }
                    try {
                        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                        if (responseJson.has("error"))
                            throw new RuntimeException(
                                    responseJson.getAsJsonObject("error").get("message").getAsString());
                        String text = responseJson
                                .getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts").get(0).getAsJsonObject()
                                .get("text").getAsString();
                        return extractJsonArray(text);
                    } catch (Exception e) {
                        logger.severe("Failed to parse Gemini response: " + response.body());
                        throw new RuntimeException("Invalid Gemini response: " + e.getMessage(), e);
                    }
                });
    }

    private CompletableFuture<String> callOpenAI(String systemInstruction, String userPrompt) {
        String defaultBase;
        String path;
        if ("ollama".equals(provider)) {
            defaultBase = "http://localhost:11434";
            path = "/v1/chat/completions";
        } else if ("deepseek".equals(provider)) {
            defaultBase = "https://api.deepseek.com";
            path = "/chat/completions";
        } else {
            defaultBase = "https://api.openai.com";
            path = "/v1/chat/completions";
        }

        String baseUrl = (apiUrl == null || apiUrl.isBlank()) ? defaultBase : apiUrl.replaceAll("/$", "");
        String url = baseUrl.contains("/chat/completions") ? baseUrl : baseUrl + path;

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);
        requestJson.addProperty("temperature", 1.0);

        JsonArray messages = new JsonArray();
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemInstruction);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        requestJson.add("messages", messages);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        requestJson.add("response_format", responseFormat);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestJson)))
                .timeout(Duration.ofSeconds(120))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("OpenAI API error: " + response.statusCode() + " | " + response.body());
                        throw new RuntimeException(extractErrorMessage(response.body(), "error"));
                    }
                    try {
                        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                        if (responseJson.has("error"))
                            throw new RuntimeException(
                                    responseJson.getAsJsonObject("error").get("message").getAsString());
                        String content = responseJson
                                .getAsJsonArray("choices").get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content").getAsString();
                        return extractJsonArray(content);
                    } catch (Exception e) {
                        logger.severe("Failed to parse OpenAI response: " + response.body());
                        throw new RuntimeException("Invalid OpenAI response: " + e.getMessage(), e);
                    }
                });
    }

    static String extractJsonArray(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl != -1)
                trimmed = trimmed.substring(nl + 1).trim();
            if (trimmed.endsWith("```"))
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        if (trimmed.startsWith("{")) {
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start != -1 && end != -1)
                return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String extractErrorMessage(String body, String key) {
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json.has(key)) {
                com.google.gson.JsonElement element = json.get(key);
                if (element.isJsonObject()) {
                    JsonObject subObj = element.getAsJsonObject();
                    if (subObj.has("message")) {
                        return subObj.get("message").getAsString();
                    }
                } else if (element.isJsonPrimitive()) {
                    return element.getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        if (body != null && !body.isBlank() && body.length() < 300) {
            return body.trim();
        }
        return "Unknown error";
    }
}