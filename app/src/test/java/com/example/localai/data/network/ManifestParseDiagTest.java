package com.example.localai.data.network;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时诊断：真实 fixture manifest 用 App 侧 Gson + ModelManifest 解析（P4 问题排查）。 */
public class ManifestParseDiagTest {

    private static final String ROOT = System.getProperty("user.dir").replace('\\', '/');

    private static ModelManifest parse(String rel) throws Exception {
        byte[] json = Files.readAllBytes(Paths.get(ROOT + "/" + rel));
        String s = new String(json, StandardCharsets.UTF_8);
        return new Gson().fromJson(s, ModelManifest.class);
    }

    @Test
    public void parseQwenManifest() throws Exception {
        ModelManifest m = parse("../backend/fixtures/models/qwen3-4b/2026.08.1/manifest.json");
        assertNotNull(m);
        assertNotNull(m.tasks);
        assertTrue(m.tasks.contains("text"));
        assertNotNull(m.languages);
    }

    @Test
    public void parseApprovedManifest() throws Exception {
        ModelManifest m = parse("../backend/fixtures/models/smollm-135m-instruct/2026.08.1/manifest.json");
        assertNotNull(m);
        assertTrue(m.isApproved());
        assertTrue(m.primaryFile().sizeBytes > 0);
    }
}
