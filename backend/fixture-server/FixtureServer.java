import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 本地 Fixture 演示服务器（P2 联调用，非生产目录服务）。
 * 路径约定：
 *   GET /v1/catalog.json|.sig
 *   GET /v1/models/{modelId}/{version}/manifest.json|.sig
 *   GET /v1/models/{modelId}/{version}/{fileName}   动态生成确定性演示载荷，支持单段 Range/ETag
 * 用法：
 *   java FixtureServer.java serve <port> <fixturesDir>
 *   java FixtureServer.java hash  <modelId> <architecture> <displayName> <sizeBytes>
 */
public final class FixtureServer {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "hash".equals(args[0])) {
            if (args.length != 5) {
                System.err.println("usage: hash <modelId> <architecture> <displayName> <sizeBytes>");
                System.exit(2);
            }
            String h = DemoPayload.sha256Hex(args[1], args[2], args[3], Integer.parseInt(args[4]));
            System.out.println(h);
            return;
        }
        if (args.length == 4 && "serve".equals(args[0])) {
            serve(Integer.parseInt(args[1]), Paths.get(args[2]), args[3]);
            return;
        }
        System.err.println("usage: serve <port> <fixturesDir> <generatedAt> | hash ...");
        System.exit(2);
    }

    static void serve(int port, Path fixturesDir, String generatedAt) throws IOException {
        Map<String, byte[]> statics = new HashMap<>();
        load(statics, fixturesDir, "/v1/catalog.json", "catalog.json");
        load(statics, fixturesDir, "/v1/catalog.sig", "catalog.sig");
        // 递归加载 models/ 下全部 manifest.json / manifest.sig 静态文件
        Path modelsRoot = fixturesDir.resolve("models");
        if (Files.isDirectory(modelsRoot)) {
            try (var walk = Files.walk(modelsRoot)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    String rel = modelsRoot.relativize(file).toString().replace('\\', '/');
                    statics.put("/v1/models/" + rel, readAll(file));
                });
            }
        }
        Map<String, byte[]> dynamic = new HashMap<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> handle(exchange, statics, dynamic));
        server.start();
        System.out.println("FixtureServer listening on 0.0.0.0:" + port + " fixtures=" + fixturesDir
                + " generatedAt=" + generatedAt + " staticRoutes=" + statics.size());
    }

    static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("unreadable fixture: " + file, e);
        }
    }

    static void load(Map<String, byte[]> statics, Path dir, String route, String file) throws IOException {
        Path p = dir.resolve(file);
        if (!Files.exists(p)) {
            throw new IOException("missing fixture file: " + p);
        }
        statics.put(route, Files.readAllBytes(p));
    }

    static void handle(HttpExchange ex, Map<String, byte[]> statics, Map<String, byte[]> dynamic)
            throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = statics.get(path);
            if (body != null) {
                serveBytes(ex, path, body, "application/json");
                return;
            }
            byte[] generated = dynamic.get(path);
            if (generated == null && path.startsWith("/v1/models/")) {
                String[] parts = path.split("/");
                if (parts.length == 6 && !path.endsWith(".sig") && !path.endsWith("manifest.json")) {
                    // /v1/models/{id}/{version}/{file} -> 用清单中的 sizeBytes 生成
                    String modelId = parts[3];
                    String version = parts[4];
                    byte[] manifest = statics.get("/v1/models/" + modelId + "/" + version + "/manifest.json");
                    if (manifest != null) {
                        generated = dynamicPayload(manifest, parts[5]);
                        dynamic.put(path, generated);
                    }
                }
            }
            if (generated == null) {
                respond(ex, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            serveBytes(ex, path, generated, "application/octet-stream");
        } finally {
            ex.close();
        }
    }

    /** 从 manifest JSON 中抠出指定文件名对应的 sizeBytes，用确定性生成器产出载荷（容忍任意空白）。 */
    static byte[] dynamicPayload(byte[] manifestJson, String fileName) {
        String json = new String(manifestJson, StandardCharsets.UTF_8);
        String nameKey = "\"name\"";
        int searchFrom = 0;
        int sizeBytes = -1;
        while (true) {
            int nameIdx = json.indexOf(nameKey, searchFrom);
            if (nameIdx < 0) {
                return null;
            }
            int colon = json.indexOf(':', nameIdx + nameKey.length());
            if (colon < 0) {
                return null;
            }
            int q1 = json.indexOf('"', colon + 1);
            int q2 = q1 < 0 ? -1 : json.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) {
                return null;
            }
            String name = json.substring(q1 + 1, q2);
            if (fileName.equals(name)) {
                int sizeIdx = json.indexOf("\"sizeBytes\"", q2);
                if (sizeIdx < 0) {
                    return null;
                }
                int colon2 = json.indexOf(':', sizeIdx + "\"sizeBytes\"".length());
                int start = colon2 + 1;
                while (start < json.length() && !Character.isDigit(json.charAt(start))) {
                    start++;
                }
                int end = start;
                while (end < json.length() && Character.isDigit(json.charAt(end))) {
                    end++;
                }
                if (end == start) {
                    return null;
                }
                sizeBytes = Integer.parseInt(json.substring(start, end));
                break;
            }
            searchFrom = q2 + 1;
        }

        int modelIdx = json.indexOf("\"modelId\"");
        int archIdx = json.indexOf("\"architecture\"");
        int nameIdx2 = json.indexOf("\"displayName\"");
        if (modelIdx < 0 || archIdx < 0 || nameIdx2 < 0) {
            return null;
        }
        String modelId = stringValueAfter(json, modelIdx);
        String arch = stringValueAfter(json, archIdx);
        String displayName = stringValueAfter(json, nameIdx2);
        if (modelId == null || arch == null || displayName == null) {
            return null;
        }
        return DemoPayload.of(modelId, arch, displayName, sizeBytes);
    }

    /** 解析 "key": "value"（容忍空白），keyIdx 指向引号开头。 */
    static String stringValueAfter(String json, int keyIdx) {
        int colon = json.indexOf(':', keyIdx);
        if (colon < 0) {
            return null;
        }
        int q1 = json.indexOf('"', colon + 1);
        int q2 = q1 < 0 ? -1 : json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return null;
        }
        return json.substring(q1 + 1, q2);
    }

    static void serveBytes(HttpExchange ex, String path, byte[] body, String contentType) throws IOException {
        Headers req = ex.getRequestHeaders();
        String range = req.getFirst("Range");
        String etag = etagOf(body);

        if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
            String spec = range.substring("bytes=".length());
            String ifRange = req.getFirst("If-Range");
            if (ifRange != null && !ifRange.equals(etag) && !ifRange.equals("\"" + etag + "\"")) {
                // If-Range 不匹配：忽略 Range，返回 200 全量
                range = null;
            }
            if (range != null) {
                int dash = spec.indexOf('-');
                long start = Long.parseLong(spec.substring(0, dash));
                long end = dash + 1 < spec.length() && spec.substring(dash + 1).length() > 0
                        ? Long.parseLong(spec.substring(dash + 1))
                        : body.length - 1;
                if (start >= body.length || end < start) {
                    Headers h = ex.getResponseHeaders();
                    h.set("Content-Range", "bytes */" + body.length);
                    h.set("ETag", etag);
                    respond(ex, 416, "text/plain", new byte[0], h);
                    return;
                }
                end = Math.min(end, body.length - 1);
                byte[] slice = new byte[(int) (end - start + 1)];
                System.arraycopy(body, (int) start, slice, 0, slice.length);
                Headers h = ex.getResponseHeaders();
                h.set("Content-Range", "bytes " + start + "-" + end + "/" + body.length);
                h.set("Accept-Ranges", "bytes");
                h.set("ETag", etag);
                h.set("Cache-Control", "no-cache");
                respond(ex, 206, contentType, slice, h);
                return;
            }
        }

        Headers h = ex.getResponseHeaders();
        h.set("Accept-Ranges", "bytes");
        h.set("ETag", etag);
        h.set("Cache-Control", "no-cache");
        respond(ex, 200, contentType, body, h);
    }

    static String etagOf(byte[] body) {
        return "\"" + Integer.toHexString(java.util.Arrays.hashCode(body)) + "\"";
    }

    static void respond(HttpExchange ex, int code, String contentType, byte[] body) throws IOException {
        respond(ex, code, contentType, body, ex.getResponseHeaders());
    }

    static void respond(HttpExchange ex, int code, String contentType, byte[] body, Headers headers)
            throws IOException {
        headers.set("Content-Type", contentType);
        headers.set("Content-Length", String.valueOf(body.length));
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0 && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
