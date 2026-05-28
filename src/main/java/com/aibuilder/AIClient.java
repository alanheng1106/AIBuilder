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

    public AIClient(Logger logger, String provider, String apiKey, String model, String apiUrl, int maxBlocks, int maxDimension) {
        this.logger = logger;
        this.provider = provider.toLowerCase();
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
        this.maxBlocks = maxBlocks;
        this.maxDimension = maxDimension;
    }

    /**
     * Sends a prompt to the configured AI model asynchronously and returns the raw JSON list of blocks.
     */
    public CompletableFuture<String> generateStructure(String userPrompt, String scale) {
        int maxElements = 20;
        if ("small".equalsIgnoreCase(scale)) {
            maxElements = 10;
        } else if ("large".equalsIgnoreCase(scale)) {
            maxElements = 40;
        }

        String systemInstruction = "You are a professional Minecraft building assistant. Your task is to design a build based on the user's prompt. " +
                "You must output ONLY a valid JSON array of block placements. Do not include any explanation, markdown codeblocks (like ```json), or extra text outside the JSON. " +
                "To optimize generation speed and support large builds, you can define individual blocks OR cuboid fill regions. " +
                "Each object in the JSON array must match one of these structures:\n" +
                "- For a single block: {\"x\": x, \"y\": y, \"z\": z, \"type\": \"MATERIAL\"}\n" +
                "- For a cuboid region fill (highly recommended for floors, walls, columns, roofs): {\"x1\": x1, \"y1\": y1, \"z1\": z1, \"x2\": x2, \"y2\": y2, \"z2\": z2, \"type\": \"MATERIAL\"}\n" +
                "Coordinates are relative to the player's position (0,0,0 is the player's foot level, positive X is East, positive Y is Up, positive Z is South). " +
                "Guidelines:\n" +
                "1. Scale and Area: The structure should fit within an area of up to " + maxDimension + "x" + maxDimension + "x" + maxDimension + " blocks. Design it beautifully, realistically, and logically.\n" +
                "2. Interior and Furniture: For habitable or hollow structures (like houses, cabins, towers), do not leave the inside completely empty. Design functional and matching furniture inside (e.g. beds, chests, tables using slabs/stairs, bookshelves, lighting like torches/glowstone). Keep the layout clean, open, and logical.\n" +
                "3. Max Blocks Limit: Max blocks allowed: " + maxBlocks + ". Ensure the total count of blocks in the cuboids does not exceed this.\n" +
                "4. CRITICAL SPEED OPTIMIZATION: Keep the total number of JSON elements in the array under " + maxElements + " (use large cuboids for floors, walls, and ceiling; and single blocks or small cuboids for furniture). Every extra JSON element directly increases API response times. Use simple block compromises for furniture (e.g. OAK_STAIRS for a chair, OAK_SLAB for a table) rather than trying to build complex multi-block furniture. Fewer elements generate significantly faster (under 3 seconds).\n" +
                "5. Raw Output: Output ONLY the raw JSON array. Example: [{\"x1\":-2,\"y1\":0,\"z1\":-2,\"x2\":2,\"y2\":0,\"z2\":2,\"type\":\"STONE\"},{\"x\":0,\"y\":1,\"z\":0,\"type\":\"CHEST\"}]";

        if ("openai".equals(provider)) {
            return callOpenAI(systemInstruction, userPrompt);
        } else if ("ollama".equals(provider)) {
            return callOpenAI(systemInstruction, userPrompt); // Ollama uses OpenAI-compatible API
        } else {
            return callGemini(systemInstruction, userPrompt);
        }
    }

    private CompletableFuture<String> callGemini(String systemInstruction, String userPrompt) {
        // Base API URL
        String baseUrl = (apiUrl == null || apiUrl.isBlank()) ? "https://generativelanguage.googleapis.com" : apiUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        String url = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        // Build Gemini Request Payload
        JsonObject requestJson = new JsonObject();

        // Contents
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject partObj = new JsonObject();
        partObj.addProperty("text", userPrompt);
        parts.add(partObj);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        requestJson.add("contents", contents);

        // System Instruction
        JsonObject systemInstructionObj = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPartObj = new JsonObject();
        sysPartObj.addProperty("text", systemInstruction);
        sysParts.add(sysPartObj);
        systemInstructionObj.add("parts", sysParts);
        requestJson.add("systemInstruction", systemInstructionObj);

        // Generation Config to force JSON mode and lower temperature for speed/determinism
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.addProperty("temperature", 0.2);
        requestJson.add("generationConfig", generationConfig);

        String requestBody = gson.toJson(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("Gemini API error: Status Code " + response.statusCode() + " | Body: " + response.body());
                        String errorMessage = "Status " + response.statusCode();
                        try {
                            JsonObject errorJson = gson.fromJson(response.body(), JsonObject.class);
                            if (errorJson.has("error")) {
                                errorMessage = errorJson.getAsJsonObject("error").get("message").getAsString();
                            }
                        } catch (Exception ignored) {}
                        throw new RuntimeException(errorMessage);
                    }
                    
                    try {
                        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                        if (responseJson.has("error")) {
                            throw new RuntimeException(responseJson.getAsJsonObject("error").get("message").getAsString());
                        }
                        
                        return cleanJsonString(responseJson.getAsJsonArray("candidates")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString());
                    } catch (Exception e) {
                        logger.severe("Failed to parse Gemini response: " + response.body());
                        throw new RuntimeException("Invalid response format from Gemini API: " + e.getMessage(), e);
                    }
                });
    }

    private CompletableFuture<String> callOpenAI(String systemInstruction, String userPrompt) {
        // Determine default base URL: Ollama runs locally, OpenAI uses the cloud
        String defaultBaseUrl = "ollama".equals(provider)
                ? "http://localhost:11434"
                : "https://api.openai.com";
        String baseUrl = (apiUrl == null || apiUrl.isBlank()) ? defaultBaseUrl : apiUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String url = baseUrl + "/v1/chat/completions";

        // Build OpenAI Request Payload
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);

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

        requestJson.addProperty("temperature", 0.2);

        // Response Format to force JSON
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        requestJson.add("response_format", responseFormat);

        String requestBody = gson.toJson(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("OpenAI API error: Status Code " + response.statusCode() + " | Body: " + response.body());
                        String errorMessage = "Status " + response.statusCode();
                        try {
                            JsonObject errorJson = gson.fromJson(response.body(), JsonObject.class);
                            if (errorJson.has("error")) {
                                errorMessage = errorJson.getAsJsonObject("error").get("message").getAsString();
                            }
                        } catch (Exception ignored) {}
                        throw new RuntimeException(errorMessage);
                    }
                    
                    try {
                        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                        if (responseJson.has("error")) {
                            throw new RuntimeException(responseJson.getAsJsonObject("error").get("message").getAsString());
                        }
                        
                        return cleanJsonString(responseJson.getAsJsonArray("choices")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content").getAsString());
                    } catch (Exception e) {
                        logger.severe("Failed to parse OpenAI response: " + response.body());
                        throw new RuntimeException("Invalid response format from OpenAI API: " + e.getMessage(), e);
                    }
                });
    }

    static String cleanJsonString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            if (firstNewLine != -1) {
                trimmed = trimmed.substring(firstNewLine + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }
}
