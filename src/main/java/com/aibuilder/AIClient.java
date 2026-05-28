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
        int maxElements = 45;
        if ("small".equalsIgnoreCase(scale)) {
            maxElements = 25;
        } else if ("large".equalsIgnoreCase(scale)) {
            maxElements = 120;
        }

        String systemInstruction = "You are a professional Minecraft building architect. Your task is to design an exceptionally beautiful, realistic, and detailed build based on the user's prompt.\n" +
                "You must output ONLY a valid JSON array of block placements. Do not include any explanation, markdown codeblocks (like ```json), or extra text outside the JSON.\n\n" +
                "Each object in the JSON array must match one of these structures:\n" +
                "- For a single block: {\"x\": x, \"y\": y, \"z\": z, \"type\": \"MATERIAL\"}\n" +
                "- For a cuboid region fill (use this for floors, walls, columns, roofs): {\"x1\": x1, \"y1\": y1, \"z1\": z1, \"x2\": x2, \"y2\": y2, \"z2\": z2, \"type\": \"MATERIAL\"}\n\n" +
                "Coordinates are relative to the player's position (0,0,0 is the player's foot level; positive X is East, positive Y is Up, positive Z is South).\n\n" +
                "Design Rules for Premium Aesthetics:\n" +
                "1. Structured Palette: Never build using only a single block type. Use a harmonious combination of 3-5 blocks. E.g., for medieval/traditional, use cobblestone/stone_bricks for foundation/floor, logs (like OAK_LOG) for structural pillars/corners, planks (like OAK_PLANKS) for walls, and darker stairs (like DARK_OAK_STAIRS or STONE_BRICK_STAIRS) for the roof. For modern, use quartz_block, dark_oak_planks, and black_stained_glass.\n" +
                "2. Roof Overhang & Pitch: Avoid flat roof boxes unless explicitly requested. Build sloped roofs using stairs or slabs. The roof must overhang the walls by at least 1 block on all sides to create realistic depth.\n" +
                "3. Wall Depth & Pillars: Avoid flat, featureless walls. Place wooden log pillars at the corners and frame the walls. Include glass blocks/panes for windows and add doors.\n" +
                "4. Detailed Interiors: For habitable structures, decorate the interior with matching furniture (e.g. beds, chests, tables using slabs/stairs, bookshelves, crafting tables, furnaces) and ensure proper lighting (e.g. torches, lanterns, glowstone) so it isn't dark inside.\n" +
                "5. Standard Material Names: Use valid uppercase Minecraft Java Edition Material names (e.g. STONE_BRICKS, OAK_LOG, OAK_PLANKS, GLASS_PANE, OAK_STAIRS, TORCH, RED_BED, CHEST, CRAFTING_TABLE, FURNACE).\n" +
                "6. Size Constraints: The build must fit within " + maxDimension + "x" + maxDimension + "x" + maxDimension + " blocks and not exceed a total block limit of " + maxBlocks + ".\n" +
                "7. Element Count: Limit the total number of JSON array elements to under " + maxElements + " by representing large structures (like floors, walls, and roofs) as cuboid fills, and using single blocks only for detailing and furniture. This ensures fast API response times without sacrificing beauty.\n" +
                "8. Output: Output ONLY the raw JSON array. Example: [{\"x1\":-2,\"y1\":0,\"z1\":-2,\"x2\":2,\"y2\":0,\"z2\":2,\"type\":\"STONE_BRICKS\"},{\"x\":0,\"y\":1,\"z\":0,\"type\":\"CHEST\"}]";

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
