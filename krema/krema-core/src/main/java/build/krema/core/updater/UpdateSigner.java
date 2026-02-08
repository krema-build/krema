package build.krema.core.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 keypair generation and file signing for update artifacts.
 * Uses the JDK built-in EdDSA provider (available since JDK 15).
 */
public final class UpdateSigner {

    private UpdateSigner() {}

    /**
     * Generates a new Ed25519 keypair.
     *
     * @return a result containing the base64-encoded public and private keys
     */
    public static KeyPairResult generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        String publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

        return new KeyPairResult(publicKeyBase64, privateKeyBase64);
    }

    /**
     * Signs a file using an Ed25519 private key.
     *
     * @param file the file to sign
     * @param privateKeyBase64 the base64-encoded Ed25519 private key
     * @return the base64-encoded signature
     */
    public static String signFile(Path file, String privateKeyBase64) throws GeneralSecurityException, IOException {
        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = kf.generatePrivate(keySpec);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(Files.readAllBytes(file));

        return Base64.getEncoder().encodeToString(sig.sign());
    }

    /**
     * Signs a file and writes the signature to a .sig file alongside the original.
     *
     * @param file the file to sign
     * @param privateKeyBase64 the base64-encoded Ed25519 private key
     * @return the path to the written .sig file
     */
    public static Path writeSignatureFile(Path file, String privateKeyBase64) throws GeneralSecurityException, IOException {
        String signature = signFile(file, privateKeyBase64);
        Path sigFile = file.resolveSibling(file.getFileName() + ".sig");
        Files.writeString(sigFile, signature);
        return sigFile;
    }

    /**
     * Result of keypair generation containing base64-encoded keys.
     */
    public record KeyPairResult(String publicKeyBase64, String privateKeyBase64) {}
}
