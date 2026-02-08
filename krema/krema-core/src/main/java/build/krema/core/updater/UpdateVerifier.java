package build.krema.core.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 signature verification for update artifacts.
 * Uses the JDK built-in EdDSA provider (available since JDK 15).
 */
public final class UpdateVerifier {

    private UpdateVerifier() {}

    /**
     * Verifies the Ed25519 signature of a file.
     *
     * @param file the file to verify
     * @param signatureBase64 the base64-encoded signature
     * @param publicKeyBase64 the base64-encoded Ed25519 public key
     * @return true if the signature is valid
     */
    public static boolean verify(Path file, String signatureBase64, String publicKeyBase64)
            throws GeneralSecurityException, IOException {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey publicKey = kf.generatePublic(keySpec);

        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(publicKey);
        sig.update(Files.readAllBytes(file));

        return sig.verify(signatureBytes);
    }
}
