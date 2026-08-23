package com.example.localai.data.network;

/** 可信公钥源：keyId -> 原始 32 字节 Ed25519 公钥；同时支持撤销列表。 */
public interface TrustStore {

    /** 返回 keyId 对应的公钥（32 字节），未知 keyId 返回 null。 */
    byte[] publicKey(String keyId);

    /** 该 keyId 是否已撤销（签名无效）。 */
    boolean isRevoked(String keyId);
}
