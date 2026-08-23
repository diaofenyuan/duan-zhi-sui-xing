package com.example.localai.data.network;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 目录/Manifest 拉取客户端。
 * 协议：
 *   GET {base}/v1/catalog.json (+catalog.sig)
 *   GET {base}/v1/models/{modelId}/{version}/manifest.json (+manifest.sig)
 * 目录与 Manifest 均需签名验证通过；Manifest 还需通过结构校验。
 */
public final class CatalogClient {

    private static final Gson GSON = new Gson();

    private final String baseUrl;
    private final OkHttpClient client;
    private final TrustStore trustStore;

    public CatalogClient(String baseUrl, OkHttpClient client, TrustStore trustStore) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = client;
        this.trustStore = trustStore;
    }

    public CatalogClient(String baseUrl, TrustStore trustStore) {
        this(baseUrl, new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build(), trustStore);
    }

    /** 拉取并验证目录；签名失败抛 CatalogException(BAD_SIGNATURE)。 */
    public Catalog fetchCatalog() throws CatalogException {
        byte[] json = fetch("/v1/catalog.json");
        byte[] sig = fetch("/v1/catalog.sig");
        ManifestVerifier.Code code = ManifestVerifier.verify(json, sig, trustStore);
        if (code != ManifestVerifier.Code.OK) {
            throw new CatalogException(CatalogException.Code.BAD_SIGNATURE,
                    "目录签名验证失败：" + code);
        }
        try {
            Catalog catalog = GSON.fromJson(new String(json, java.nio.charset.StandardCharsets.UTF_8), Catalog.class);
            if (catalog == null || catalog.models == null) {
                throw new CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录结构非法");
            }
            return catalog;
        } catch (CatalogException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录解析失败", e);
        }
    }

    /**
     * 拉取并验证 Manifest；返回已验证的 ModelManifest。
     * 校验顺序：HTTP -> 签名 -> 结构校验。
     */
    public ModelManifest fetchManifest(String modelId, String version) throws CatalogException {
        return fetchManifestBundle(modelId, version).manifest;
    }

    /** 拉取 Manifest 及其签名原始字节（用于安装时随模型落盘）。 */
    public ManifestBundle fetchManifestBundle(String modelId, String version) throws CatalogException {
        String base = "/v1/models/" + modelId + "/" + version;
        byte[] json = fetch(base + "/manifest.json");
        byte[] sig = fetch(base + "/manifest.sig");
        ManifestVerifier.Code code = ManifestVerifier.verify(json, sig, trustStore);
        if (code != ManifestVerifier.Code.OK) {
            throw new CatalogException(CatalogException.Code.BAD_SIGNATURE,
                    "模型 " + modelId + " Manifest 签名验证失败：" + code);
        }
        ModelManifest manifest;
        try {
            manifest = GSON.fromJson(new String(json, java.nio.charset.StandardCharsets.UTF_8), ModelManifest.class);
        } catch (RuntimeException e) {
            throw new CatalogException(CatalogException.Code.SCHEMA_INVALID, "Manifest 解析失败", e);
        }
        if (manifest == null) {
            throw new CatalogException(CatalogException.Code.SCHEMA_INVALID, "Manifest 为空");
        }
        String problem = manifest.validate();
        if (problem != null) {
            throw new CatalogException(CatalogException.Code.SCHEMA_INVALID, problem);
        }
        return new ManifestBundle(manifest, json, sig);
    }

    /** 已验证 Manifest 及其原始字节。 */
    public static final class ManifestBundle {
        public final ModelManifest manifest;
        public final byte[] json;
        public final byte[] sig;

        ManifestBundle(ModelManifest manifest, byte[] json, byte[] sig) {
            this.manifest = manifest;
            this.json = json;
            this.sig = sig;
        }
    }

    /** Manifest 中相对路径 URL 解析为绝对地址。 */
    public String resolveUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return baseUrl + (url.startsWith("/") ? url : "/" + url);
    }

    public String baseUrl() {
        return baseUrl;
    }

    private byte[] fetch(String path) throws CatalogException {
        Request request = new Request.Builder().url(baseUrl + path).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new CatalogException(CatalogException.Code.NOT_FOUND,
                        "资源不存在：" + path);
            }
            if (!response.isSuccessful()) {
                throw new CatalogException(CatalogException.Code.HTTP,
                        "HTTP " + response.code() + "：" + path);
            }
            byte[] body = response.body() == null ? new byte[0] : response.body().bytes();
            if (body.length == 0) {
                throw new CatalogException(CatalogException.Code.HTTP, "空响应：" + path);
            }
            return body;
        } catch (IOException e) {
            throw new CatalogException(CatalogException.Code.NETWORK, "网络错误：" + path, e);
        }
    }

    private static String stripTrailingSlash(String url) {
        String s = url;
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
