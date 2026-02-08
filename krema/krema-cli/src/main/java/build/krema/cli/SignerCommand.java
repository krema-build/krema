package build.krema.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import build.krema.core.updater.UpdateSigner;

/**
 * CLI command for Ed25519 key generation and artifact signing.
 *
 * <p>Usage:</p>
 * <pre>
 * krema signer generate [--output krema-private.key]
 * krema signer sign &lt;file&gt; [--key krema-private.key]
 * </pre>
 */
@Command(
    name = "signer",
    description = "Ed25519 key generation and artifact signing",
    subcommands = {
        SignerCommand.GenerateCommand.class,
        SignerCommand.SignCommand.class
    }
)
public class SignerCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: krema signer <generate|sign>");
        System.out.println("  generate  Generate a new Ed25519 keypair");
        System.out.println("  sign      Sign a file with an Ed25519 private key");
        return 0;
    }

    @Command(name = "generate", description = "Generate a new Ed25519 keypair for update signing")
    public static class GenerateCommand implements Callable<Integer> {

        @Option(names = {"-o", "--output"}, description = "Output file for private key",
            defaultValue = "krema-private.key")
        private String output;

        @Override
        public Integer call() {
            try {
                UpdateSigner.KeyPairResult keyPair = UpdateSigner.generateKeyPair();

                Path outputPath = Path.of(output);
                Files.writeString(outputPath, keyPair.privateKeyBase64());

                System.out.println("[Krema Signer] Ed25519 keypair generated successfully!");
                System.out.println();
                System.out.println("Private key written to: " + outputPath.toAbsolutePath());
                System.out.println();
                System.out.println("Add this to your krema.toml:");
                System.out.println();
                System.out.println("[updater]");
                System.out.println("pubkey = \"" + keyPair.publicKeyBase64() + "\"");
                System.out.println();
                System.out.println("WARNING: Keep your private key secure!");
                System.out.println("  - Do NOT commit it to version control");
                System.out.println("  - Store it in a secure location or CI secret");
                System.out.println("  - Set KREMA_SIGNING_PRIVATE_KEY env var for CI builds");

                return 0;
            } catch (Exception e) {
                System.err.println("[Krema Signer] Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "sign", description = "Sign a file with an Ed25519 private key")
    public static class SignCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "File to sign")
        private Path file;

        @Option(names = {"-k", "--key"}, description = "Path to private key file")
        private String keyFile;

        @Override
        public Integer call() {
            try {
                if (!Files.exists(file)) {
                    System.err.println("[Krema Signer] Error: File not found: " + file);
                    return 1;
                }

                String privateKey = resolvePrivateKey();
                if (privateKey == null) {
                    System.err.println("[Krema Signer] Error: No private key provided.");
                    System.err.println("  Use --key <file> or set KREMA_SIGNING_PRIVATE_KEY env var");
                    return 1;
                }

                Path sigFile = UpdateSigner.writeSignatureFile(file, privateKey);
                System.out.println("[Krema Signer] Signed: " + file);
                System.out.println("[Krema Signer] Signature: " + sigFile);

                return 0;
            } catch (Exception e) {
                System.err.println("[Krema Signer] Error: " + e.getMessage());
                return 1;
            }
        }

        private String resolvePrivateKey() throws Exception {
            // Try --key file first
            if (keyFile != null) {
                Path keyPath = Path.of(keyFile);
                if (!Files.exists(keyPath)) {
                    throw new Exception("Key file not found: " + keyPath);
                }
                return Files.readString(keyPath).trim();
            }

            // Try environment variable
            String envKey = System.getenv("KREMA_SIGNING_PRIVATE_KEY");
            if (envKey != null && !envKey.isBlank()) {
                return envKey.trim();
            }

            // Try default key file
            Path defaultKey = Path.of("krema-private.key");
            if (Files.exists(defaultKey)) {
                return Files.readString(defaultKey).trim();
            }

            return null;
        }
    }
}
