package com.example.localai.data.network;

/** 目录/Manifest 拉取失败（结构化错误码，可直接展示给 UI）。 */
public class CatalogException extends Exception {

    public enum Code {
        NETWORK,
        HTTP,
        NOT_FOUND,
        BAD_SIGNATURE,
        SCHEMA_INVALID
    }

    private final Code code;

    public CatalogException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CatalogException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
