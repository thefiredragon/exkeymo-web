package ris58h.exkeymo.web;

import ch.cern.test.mdm.utils.SignedJar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AppBuilder {
    private static final Logger log = LoggerFactory.getLogger(AppBuilder.class);

    private byte[] inAppBytes;
    private X509Certificate certificate;
    private PrivateKey privateKey;

    public void init() throws Exception {
        InputStream unsignedAppStream = AppBuilder.class.getResourceAsStream("/app-release-unsigned.apk");
        this.inAppBytes = unsignedAppStream.readAllBytes();

        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] password = "exkeymo".toCharArray();
        keyStore.load(AppBuilder.class.getResourceAsStream("/exkeymo.keystore"), password);
        this.certificate = (X509Certificate) keyStore.getCertificate("app");
        this.privateKey = (PrivateKey) keyStore.getKey("app", password);
    }

    public byte[] buildApp(String layout) throws Exception {
        try (ByteArrayOutputStream outAppBytes = new ByteArrayOutputStream(inAppBytes.length)) {
            log.info("Building app...");
            long start = System.currentTimeMillis();

            buildApp(layout, outAppBytes);

            if (log.isInfoEnabled()) {
                long mills = System.currentTimeMillis() - start;
                log.info(String.format("App is built in %.1f seconds", (((double) mills) / 1000)));
            }

            return outAppBytes.toByteArray();
        }
    }

    private void buildApp(String layout, OutputStream outAppStream) throws Exception {
        ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(inAppBytes));
        SignedJar signedJar = new SignedJar(
                outAppStream,
                Collections.singleton(certificate),
                certificate,
                privateKey
        );
        ZipEntry zipEntry;
        while ((zipEntry = zipStream.getNextEntry()) != null) {
            if (zipEntry.isDirectory()) {
                continue;
            }
            String name = zipEntry.getName();
            if (name.equals("META-INF/MANIFEST.MF")) {
                continue;
            }
            byte[] bytes;
            if (name.equals("res/raw/keyboard_layout.kcm")) {
                bytes = layout.getBytes(StandardCharsets.UTF_8);
            } else {
                bytes = zipStream.readAllBytes();
            }
            signedJar.addFileContents(name, bytes);
        }
        signedJar.close();
    }
}
