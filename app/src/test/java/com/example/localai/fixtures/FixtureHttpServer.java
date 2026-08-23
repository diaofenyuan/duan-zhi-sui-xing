package com.example.localai.fixtures;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地 HTTP Fixture 服务器（JVM 单测用，纯 java.base 的 ServerSocket 实现，
 * 避免 com.sun.net.httpserver 的 JDK 模块依赖）。
 * 能力：静态资产、动态文件载荷（可变内容/ETag）、单段 Range/If-Range 语义、
 * 故障注入（连接截断 dropAfter、分块限速 throttleMs）与请求记录（供断言）。
 */
public final class FixtureHttpServer implements Closeable {

    public static final class RequestRecord {
        public final String method;
        public final String path;
        public final String range;
        public final String ifRange;
        public final int responseCode;

        RequestRecord(String method, String path, String range, String ifRange, int responseCode) {
            this.method = method;
            this.path = path;
            this.range = range;
            this.ifRange = ifRange;
            this.responseCode = responseCode;
        }
    }

    private static final class Entry {
        byte[] content;
        String etag;
        int throttleMs;

        Entry(byte[] content, String etag) {
            this.content = content;
            this.etag = etag;
        }
    }

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private volatile boolean closed;

    private final Map<String, byte[]> assets = new ConcurrentHashMap<>();
    private final Map<String, Entry> files = new ConcurrentHashMap<>();
    private final Map<String, Integer> dropAfter = new ConcurrentHashMap<>();
    private final List<RequestRecord> requests = new CopyOnWriteArrayList<>();

    public FixtureHttpServer() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        acceptThread = new Thread(this::acceptLoop, "fixture-http");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public void asset(String path, byte[] bytes) {
        assets.put(path, bytes);
    }

    /** 读取当前资产字节（供篡改负例使用），返回副本。 */
    public byte[] assetBytes(String path) {
        byte[] bytes = assets.get(path);
        return bytes == null ? null : bytes.clone();
    }

    public void file(String path, byte[] bytes) {
        setFile(path, bytes, "\"e-" + Integer.toHexString(java.util.Arrays.hashCode(bytes)) + "\"");
    }

    public void setFile(String path, byte[] bytes, String etag) {
        files.put(path, new Entry(bytes, etag));
    }

    /** 响应体写满 bodyBytes 字节后强制断开连接（模拟网络中断）。 */
    public void dropAfter(String path, int bodyBytes) {
        dropAfter.put(path, bodyBytes);
    }

    public void clearDrop(String path) {
        dropAfter.remove(path);
    }

    /** 每 chunk 写入后延迟 N 毫秒（拖慢下载以便测试暂停/恢复）。 */
    public void throttleMs(String path, int ms) {
        Entry entry = files.get(path);
        if (entry != null) {
            entry.throttleMs = ms;
        }
    }

    public List<RequestRecord> requests() {
        return new ArrayList<>(requests);
    }

    public RequestRecord lastRequest(String path) {
        List<RequestRecord> all = requests();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).path.equals(path)) {
                return all.get(i);
            }
        }
        return null;
    }

    public void reset() {
        requests.clear();
        dropAfter.clear();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                Thread thread = new Thread(() -> handle(socket), "fixture-conn");
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                if (closed) {
                    return;
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30_000);
            InputStream in = s.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(),
                            line.substring(colon + 1).trim());
                }
            }

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts.length > 1 ? parts[1] : "/";
            String range = headers.get("range");
            String ifRange = headers.get("if-range");

            OutputStream out = s.getOutputStream();
            int code;
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                code = respond(out, method, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
            } else if (assets.containsKey(path)) {
                code = serveStatic(out, method, path);
            } else if (files.containsKey(path)) {
                code = serveFileWithRange(out, method, path, range, ifRange);
            } else {
                code = respond(out, method, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            }
            requests.add(new RequestRecord(method, path, range, ifRange, code));
        } catch (IOException ignored) {
            // 连接被客户端中断（断网模拟）属预期
        }
    }

    private int serveStatic(OutputStream out, String method, String path) throws IOException {
        byte[] body = assets.get(path);
        String etag = "\"s-" + Integer.toHexString(java.util.Arrays.hashCode(body)) + "\"";
        return respond(out, method, 200, "application/json", body,
                new String[][]{{"ETag", etag}});
    }

    /** 文件服务：Range/If-Range 语义（由 handle 传入解析结果）。 */
    private int serveFileWithRange(OutputStream out, String method, String path,
                                   String range, String ifRange) throws IOException {
        Entry entry = files.get(path);
        byte[] body = entry.content;
        boolean useRange = false;
        long start = 0;
        long end = body.length - 1;
        if (range != null && range.startsWith("bytes=") && !range.contains(",")) {
            if (ifRange != null && !ifRange.equals(entry.etag) && !ifRange.equals("\"" + entry.etag + "\"")) {
                useRange = false; // If-Range 不匹配：按 RFC 返回 200 全量
            } else {
                String spec = range.substring("bytes=".length());
                int dash = spec.indexOf('-');
                start = Long.parseLong(spec.substring(0, dash));
                if (dash + 1 < spec.length()) {
                    end = Long.parseLong(spec.substring(dash + 1));
                }
                if (start >= body.length || end < start) {
                    return respond(out, method, 416, "text/plain", new byte[0],
                            new String[][]{{"Content-Range", "bytes */" + body.length},
                                    {"ETag", entry.etag}});
                }
                end = Math.min(end, body.length - 1);
                useRange = true;
            }
        }

        byte[] payload;
        long contentLength;
        int prefixLength = 0;
        int code;
        if (useRange) {
            payload = new byte[(int) (end - start + 1)];
            System.arraycopy(body, (int) start, payload, 0, payload.length);
            contentLength = payload.length;
            prefixLength = (int) start;
            code = 206;
        } else {
            payload = body;
            contentLength = body.length;
            code = 200;
        }

        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(' ')
                .append(code == 206 ? "Partial Content" : "OK").append("\r\n");
        head.append("Content-Type: application/octet-stream\r\n");
        head.append("Content-Length: ").append(contentLength).append("\r\n");
        head.append("Accept-Ranges: bytes\r\n");
        head.append("ETag: ").append(entry.etag).append("\r\n");
        if (useRange) {
            head.append("Content-Range: bytes ").append(start).append('-').append(end)
                    .append('/').append(body.length).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        if ("HEAD".equalsIgnoreCase(method)) {
            return code;
        }
        Integer drop = dropAfter.get(path);
        int chunk = 4096;
        int written = 0;
        while (written < payload.length) {
            int n = Math.min(chunk, payload.length - written);
            out.write(payload, written, n);
            out.flush();
            written += n;
            if (drop != null && prefixLength + written >= drop) {
                // 模拟网络中断：不写剩余字节，直接断开
                return code;
            }
            if (entry.throttleMs > 0) {
                try {
                    Thread.sleep(entry.throttleMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return code;
                }
            }
        }
        return code;
    }

    private int respond(OutputStream out, String method, int code, String contentType, byte[] body)
            throws IOException {
        return respond(out, method, code, contentType, body, null);
    }

    private int respond(OutputStream out, String method, int code, String contentType, byte[] body,
                        String[][] extraHeaders) throws IOException {
        StringBuilder head = new StringBuilder();
        String reason = code == 404 ? "Not Found" : code == 416 ? "Range Not Satisfiable" : "OK";
        head.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        if (extraHeaders != null) {
            for (String[] h : extraHeaders) {
                head.append(h[0]).append(": ").append(h[1]).append("\r\n");
            }
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body.length > 0 && !"HEAD".equalsIgnoreCase(method)) {
            out.write(body);
        }
        out.flush();
        return code;
    }

    @Override
    public void close() {
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
