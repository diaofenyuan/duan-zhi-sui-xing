import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Manifest/Catalog Ed25519 签名工具（backend 控制面用，JDK 17 原生 Ed25519）。
 * 用法：
 *   java ManifestSigner.java keygen <keyId> <keysDir>                生成 .seed/.pub（十六进制 32 字节）
 *   java ManifestSigner.java sign   <keyId> <keysDir> <dataFile> <sigFile>   对原始字节流签名
 *   java ManifestSigner.java verify <keyId> <keysDir> <dataFile> <sigFile>   校验签名
 * 签名载荷 = 数据文件（manifest.json / catalog.json）的原始字节流；
 * sig 文件为 JSON：{"algorithm":"Ed25519","keyId":"...","value":"<base64 R||S>"}。
 * 注意：密钥文件 .seed 为私密材料，仅用于 dev fixture；生产密钥必须在离线环境生成并托管。
 */
public final class ManifestSigner {

    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] PREFIX_PKCS8 = new byte[]{
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20};

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            return;
        }
        String cmd = args[0];
        switch (cmd) {
            case "keygen": {
                require(args.length == 3, "keygen <keyId> <keysDir>");
                keygen(args[1], Paths.get(args[2]));
                break;
            }
            case "sign": {
                require(args.length == 5, "sign <keyId> <keysDir> <dataFile> <sigFile>");
                sign(args[1], Paths.get(args[2]), Paths.get(args[3]), Paths.get(args[4]));
                break;
            }
            case "verify": {
                require(args.length == 5, "verify <keyId> <keysDir> <dataFile> <sigFile>");
                verify(args[1], Paths.get(args[2]), Paths.get(args[3]), Paths.get(args[4]));
                break;
            }
            default:
                usage();
        }
    }

    private static void usage() {
        System.err.println("usage: java ManifestSigner.java keygen|sign|verify ...");
    }

    private static void require(boolean ok, String usage) {
        if (!ok) {
            System.err.println("bad arguments, expected: " + usage);
            System.exit(2);
        }
    }

    private static void keygen(String keyId, Path dir) throws Exception {
        Files.createDirectories(dir);
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = gen.generateKeyPair();

        byte[] seed = extractSeed(pair.getPrivate().getEncoded());
        byte[] pub = extractPub(pair.getPublic().getEncoded());

        Path seedPath = dir.resolve(keyId + ".seed");
        Path pubPath = dir.resolve(keyId + ".pub");
        Files.writeString(seedPath, HEX.formatHex(seed), StandardCharsets.US_ASCII);
        Files.writeString(pubPath, HEX.formatHex(pub), StandardCharsets.US_ASCII);
        System.out.println("keyId=" + keyId);
        System.out.println("seed -> " + seedPath + " (KEEP PRIVATE, dev-only)");
        System.out.println("pub  -> " + pubPath + " hex=" + HEX.formatHex(pub));
    }

    private static void sign(String keyId, Path dir, Path dataFile, Path sigFile) throws Exception {
        PrivateKey key = loadPrivate(keyId, dir);
        byte[] payload = Files.readAllBytes(dataFile);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(key);
        sig.update(payload);
        String b64 = Base64.getEncoder().encodeToString(sig.sign());
        String json = "{\"algorithm\":\"Ed25519\",\"keyId\":\"" + keyId + "\",\"value\":\"" + b64 + "\"}";
        Files.writeString(sigFile, json, StandardCharsets.UTF_8);
        System.out.println("signed " + dataFile + " (" + payload.length + " bytes) -> " + sigFile);
    }

    private static void verify(String keyId, Path dir, Path dataFile, Path sigFile) throws Exception {
        PublicKey key = loadPublic(keyId, dir);
        byte[] payload = Files.readAllBytes(dataFile);
        String json = Files.readString(sigFile, StandardCharsets.UTF_8);
        String b64 = extractJsonValue(json, "value");
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(key);
        sig.update(payload);
        boolean ok = sig.verify(Base64.getDecoder().decode(b64));
        System.out.println(ok ? "VERIFY OK  keyId=" + keyId + " file=" + dataFile
                : "VERIFY FAILED keyId=" + keyId + " file=" + dataFile);
        if (!ok) {
            System.exit(1);
        }
    }

    private static PrivateKey loadPrivate(String keyId, Path dir) throws Exception {
        String hex = Files.readString(dir.resolve(keyId + ".seed"), StandardCharsets.US_ASCII).trim();
        byte[] seed = HEX.parseHex(hex);
        byte[] pkcs8 = new byte[PREFIX_PKCS8.length + seed.length];
        System.arraycopy(PREFIX_PKCS8, 0, pkcs8, 0, PREFIX_PKCS8.length);
        System.arraycopy(seed, 0, pkcs8, PREFIX_PKCS8.length, seed.length);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    private static PublicKey loadPublic(String keyId, Path dir) throws Exception {
        String hex = Files.readString(dir.resolve(keyId + ".pub"), StandardCharsets.US_ASCII).trim();
        byte[] pub = HEX.parseHex(hex);
        byte[] spki = new byte[12 + pub.length];
        byte[] prefix = new byte[]{0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
        System.arraycopy(prefix, 0, spki, 0, prefix.length);
        System.arraycopy(pub, 0, spki, prefix.length, pub.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
    }

    /** JDK 17 Ed25519 PKCS#8: 30 2e 02 01 00 30 05 06 03 2b 65 70 04 22 04 20 <seed32>。 */
    private static byte[] extractSeed(byte[] pkcs8) {
        if (pkcs8.length != 48) {
            throw new IllegalStateException("unexpected PKCS#8 length " + pkcs8.length);
        }
        byte[] seed = new byte[32];
        System.arraycopy(pkcs8, 16, seed, 0, 32);
        return seed;
    }

    /** JDK 17 Ed25519 SPKI: 30 2a 30 05 06 03 2b 65 70 03 21 00 <pub32>。 */
    private static byte[] extractPub(byte[] spki) {
        if (spki.length != 44) {
            throw new IllegalStateException("unexpected SPKI length " + spki.length);
        }
        byte[] pub = new byte[32];
        System.arraycopy(spki, 12, pub, 0, 32);
        return pub;
    }

    private static String extractJsonValue(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int i = json.indexOf(marker);
        if (i < 0) {
            throw new IllegalArgumentException("sig file missing " + key);
        }
        int start = i + marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            throw new IllegalArgumentException("sig file malformed");
        }
        return json.substring(start, end);
    }
}
