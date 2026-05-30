package com.aibuilder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Type;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JSONParsingTest {

    @Test
    public void testExtractJsonArray_markdownFence() {
        String input = "```json\n[\n  {\"x\": 0, \"y\": 1, \"z\": 2, \"type\": \"STONE\"}\n]\n```";
        String result = AIClient.extractJsonArray(input);
        assertEquals("[{\"x\":0,\"y\":1,\"z\":2,\"type\":\"STONE\"}]", result.replaceAll("\\s+", ""));
    }

    @Test
    public void testExtractJsonArray_wrappedObject() {
        // Model sometimes returns {"blocks": [...]} instead of a bare array
        String input = "{\"blocks\": [{\"x\": 0, \"y\": 1, \"z\": 2, \"type\": \"STONE\"}]}";
        String result = AIClient.extractJsonArray(input);
        assertEquals("[{\"x\":0,\"y\":1,\"z\":2,\"type\":\"STONE\"}]", result.replaceAll("\\s+", ""));
    }

    @Test
    public void testExtractJsonArray_bareArray() {
        String input = "[{\"x\": 0, \"y\": 1, \"z\": 2, \"type\": \"STONE\"}]";
        String result = AIClient.extractJsonArray(input);
        assertEquals("[{\"x\":0,\"y\":1,\"z\":2,\"type\":\"STONE\"}]", result.replaceAll("\\s+", ""));
    }

    @Test
    public void testExtractJsonArray_trailingBrackets() {
        String input = "[{\"x\": 0, \"y\": 1, \"z\": 2, \"type\": \"STONE\"}]]";
        String result = AIClient.extractJsonArray(input);
        assertEquals("[{\"x\":0,\"y\":1,\"z\":2,\"type\":\"STONE\"}]", result.replaceAll("\\s+", ""));
    }

    @Test
    public void testExtractJsonArray_truncatedArray() {
        String input = "[\n" +
                "  {\"x\": 0, \"y\": 1, \"z\": 2, \"type\": \"STONE\"},\n" +
                "  {\"x\": 3, \"y\": 4, \"z\":";
        String result = AIClient.extractJsonArray(input);
        assertEquals("[{\"x\":0,\"y\":1,\"z\":2,\"type\":\"STONE\"}]", result.replaceAll("\\s+", ""));
    }

    @Test
    public void testPlacementParsing() {
        String json = "[{\"x\":0,\"y\":1,\"z\":2,\"type\":\"OAK_PLANKS\"}]";
        Gson gson = new Gson();
        Type type = new TypeToken<List<BuildTask.BlockPlacement>>() {
        }.getType();
        List<BuildTask.BlockPlacement> placements = gson.fromJson(json, type);
        assertNotNull(placements);
        assertEquals(1, placements.size());
        assertEquals(0, placements.get(0).x);
        assertEquals(1, placements.get(0).y);
        assertEquals(2, placements.get(0).z);
        assertEquals("OAK_PLANKS", placements.get(0).type);
    }

    @Test
    public void testCuboidParsing() {
        String json = "[{\"x1\":-2,\"y1\":0,\"z1\":-2,\"x2\":2,\"y2\":0,\"z2\":2,\"type\":\"STONE\"}]";
        Gson gson = new Gson();
        Type type = new TypeToken<List<BuildTask.BlockPlacement>>() {
        }.getType();
        List<BuildTask.BlockPlacement> placements = gson.fromJson(json, type);
        assertNotNull(placements);
        assertEquals(1, placements.size());
        assertEquals(-2, placements.get(0).x1);
        assertEquals(0, placements.get(0).y1);
        assertEquals(2, placements.get(0).z2);
        assertEquals("STONE", placements.get(0).type);
    }
}