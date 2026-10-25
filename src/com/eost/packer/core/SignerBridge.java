package com.eost.packer.core;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SignerBridge {
    private final Context ctx;
    private final File ksFile;
    private static final String KS_PASS = "etc123";
    private static final String ALIAS = "etc";

    public SignerBridge(Context ctx, File ksFile) {
        this.ctx = ctx;
        this.ksFile = ksFile;
    }

    /** 用内置 apksig 库执行 v1+v2 签名（无需外部 JVM） */
    public void sign(File inputApk, File outputApk) throws Exception {
        File dex = new File(ctx.getFilesDir(), "dex/apksig.dex");
        if (!dex.exists()) {
            throw new IllegalStateException("内置 apksig 库缺失，无法签名");
        }
        ClassLoader cl = new dalvik.system.DexClassLoader(
                dex.getAbsolutePath(),
                new File(ctx.getFilesDir(), "opt").getAbsolutePath(),
                null,
                getClass().getClassLoader());

        // 读取 ETC keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream in = new FileInputStream(ksFile)) {
            ks.load(in, KS_PASS.toCharArray());
        }
        PrivateKey pk = (PrivateKey) ks.getKey(ALIAS, KS_PASS.toCharArray());
        java.security.cert.Certificate[] chain = ks.getCertificateChain(ALIAS);
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : chain) certs.add((X509Certificate) c);

        // new ApkSigner.Signer.Builder(alias, privateKey, certs).build()
        Class<?> signerClass = cl.loadClass("com.android.apksig.ApkSigner$Signer");
        Class<?> signerBuilderClass = cl.loadClass("com.android.apksig.ApkSigner$Signer$Builder");
        Constructor<?> sbCtor = signerBuilderClass.getConstructor(String.class, PrivateKey.class, List.class);
        Object signerBuilder = sbCtor.newInstance(ALIAS, pk, certs);
        Method buildMethod = signerBuilderClass.getMethod("build");
        Object signer = buildMethod.invoke(signerBuilder);

        // new ApkSigner.Builder(Collections.singletonList(signer))
        Class<?> apkSignerClass = cl.loadClass("com.android.apksig.ApkSigner");
        Class<?> apkSignerBuilderClass = cl.loadClass("com.android.apksig.ApkSigner$Builder");
        Constructor<?> abCtor = apkSignerBuilderClass.getConstructor(List.class);
        Object apkSignerBuilder = abCtor.newInstance(Collections.singletonList(signer));

        // setInputApk / setOutputApk / setV1SigningEnabled / setV2SigningEnabled / build / sign
        Method setIn = apkSignerBuilderClass.getMethod("setInputApk", File.class);
        setIn.invoke(apkSignerBuilder, inputApk);
        Method setOut = apkSignerBuilderClass.getMethod("setOutputApk", File.class);
        setOut.invoke(apkSignerBuilder, outputApk);
        Method setV1 = apkSignerBuilderClass.getMethod("setV1SigningEnabled", boolean.class);
        setV1.invoke(apkSignerBuilder, Boolean.TRUE);
        Method setV2 = apkSignerBuilderClass.getMethod("setV2SigningEnabled", boolean.class);
        setV2.invoke(apkSignerBuilder, Boolean.TRUE);
        Method buildApk = apkSignerBuilderClass.getMethod("build");
        Object apkSigner = buildApk.invoke(apkSignerBuilder);
        Method sign = apkSignerClass.getMethod("sign");
        sign.invoke(apkSigner);
    }
}
