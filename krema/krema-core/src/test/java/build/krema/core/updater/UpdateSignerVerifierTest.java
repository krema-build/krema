package build.krema.core.updater;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import build.krema.core.updater.UpdateSigner;
import build.krema.core.updater.UpdateVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateSigner and UpdateVerifier")
class UpdateSignerVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generateKeyPair produces non-null base64 keys")
    void generateKeyPair() throws Exception {
        UpdateSigner.KeyPairResult kp = UpdateSigner.generateKeyPair();

        assertNotNull(kp.publicKeyBase64());
        assertNotNull(kp.privateKeyBase64());
        assertFalse(kp.publicKeyBase64().isBlank());
        assertFalse(kp.privateKeyBase64().isBlank());
    }

    @Test
    @DisplayName("sign and verify round-trip succeeds")
    void signVerifyRoundTrip() throws Exception {
        UpdateSigner.KeyPairResult kp = UpdateSigner.generateKeyPair();

        Path file = tempDir.resolve("test-file.bin");
        Files.writeString(file, "Hello, this is test content for signing.");

        String signature = UpdateSigner.signFile(file, kp.privateKeyBase64());
        boolean valid = UpdateVerifier.verify(file, signature, kp.publicKeyBase64());

        assertTrue(valid);
    }

    @Test
    @DisplayName("wrong public key fails verification")
    void wrongPublicKey() throws Exception {
        UpdateSigner.KeyPairResult kp1 = UpdateSigner.generateKeyPair();
        UpdateSigner.KeyPairResult kp2 = UpdateSigner.generateKeyPair();

        Path file = tempDir.resolve("test-file.bin");
        Files.writeString(file, "Content signed with key 1");

        String signature = UpdateSigner.signFile(file, kp1.privateKeyBase64());
        boolean valid = UpdateVerifier.verify(file, signature, kp2.publicKeyBase64());

        assertFalse(valid);
    }

    @Test
    @DisplayName("tampered file fails verification")
    void tamperedFile() throws Exception {
        UpdateSigner.KeyPairResult kp = UpdateSigner.generateKeyPair();

        Path file = tempDir.resolve("test-file.bin");
        Files.writeString(file, "Original content");

        String signature = UpdateSigner.signFile(file, kp.privateKeyBase64());

        // Tamper with the file
        Files.writeString(file, "Tampered content");

        boolean valid = UpdateVerifier.verify(file, signature, kp.publicKeyBase64());
        assertFalse(valid);
    }

    @Test
    @DisplayName("writeSignatureFile creates .sig file with correct content")
    void writeSignatureFile() throws Exception {
        UpdateSigner.KeyPairResult kp = UpdateSigner.generateKeyPair();

        Path file = tempDir.resolve("app-1.0.0.dmg");
        Files.writeString(file, "Fake binary content");

        Path sigFile = UpdateSigner.writeSignatureFile(file, kp.privateKeyBase64());

        assertTrue(Files.exists(sigFile));
        assertEquals("app-1.0.0.dmg.sig", sigFile.getFileName().toString());

        String sigContent = Files.readString(sigFile);
        assertFalse(sigContent.isBlank());
    }

    @Test
    @DisplayName("signature is valid base64")
    void signatureIsValidBase64() throws Exception {
        UpdateSigner.KeyPairResult kp = UpdateSigner.generateKeyPair();

        Path file = tempDir.resolve("test.bin");
        Files.writeString(file, "Content");

        String signature = UpdateSigner.signFile(file, kp.privateKeyBase64());

        assertDoesNotThrow(() -> Base64.getDecoder().decode(signature));
    }
}
