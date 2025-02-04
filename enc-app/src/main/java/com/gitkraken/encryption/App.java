package com.gitkraken.encryption;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Objects;

/**
 * Based on https://github.com/bcgit/bc-java/blob/main/pg/src/main/java/org/bouncycastle/openpgp/examples/PBEFileProcessor.java
 */
public class App {

    public static final String INPUT_FILE_NAME = "sensitive-data.txt";
    public static final String OUTPUT_FILE_NAME = "encrypted-data.bpg";
    public static final String PUBLIC_KEY = "/public.key";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        try (OutputStream out = Files.newOutputStream(Paths.get(OUTPUT_FILE_NAME))) {
            encryptToStream(loadPublicKey(PUBLIC_KEY), new File(INPUT_FILE_NAME), out);
        }
    }

    private static void encryptToStream(PGPPublicKey publicKey, File file, OutputStream outputStream) {
        try(OutputStream out = new ArmoredOutputStream(outputStream)) {
            // Encryption
            PGPDataEncryptorBuilder dataEncBuilder = new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .setSecureRandom(new SecureRandom())
                    .setWithIntegrityPacket(true);
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(dataEncBuilder);
            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(publicKey)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME));
            try(OutputStream encOut = encGen.open(out, new byte[8192])) {
                // Compression
                PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
                try (OutputStream compOut = compGen.open(encOut)) {
                    // Literal Data
                    PGPUtil.writeFileToLiteralData(compOut, PGPLiteralData.BINARY, file);
                }
            }
        } catch (PGPException e) {
            System.err.println(e);
            if (e.getUnderlyingException() != null) {
                e.getUnderlyingException().printStackTrace();
            }
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private static PGPPublicKey loadPublicKey(String publicKeyFileResource) throws Exception {
        PGPPublicKeyRingCollection keyRingCollection = new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(Objects.requireNonNull(
                        App.class.getResourceAsStream(publicKeyFileResource)
                )),
                new JcaKeyFingerprintCalculator()
        );

        for (PGPPublicKeyRing keyRing : keyRingCollection) {
            for (PGPPublicKey publicKey : keyRing) {
                if (publicKey.isEncryptionKey()) {
                    return publicKey;
                }
            }
        }
        throw new IllegalArgumentException("No encryption key found in the provided public key file.");
    }

}
