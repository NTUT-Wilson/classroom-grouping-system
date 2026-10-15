package com.classroomgrouping.platform;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** Manual measurement, not a timing-sensitive test or a runtime endpoint. */
public final class PasswordHashBenchmark {
    public static void main(String[] args) {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String password = "synthetic-benchmark-password";
        String hash = encoder.encode(password);
        for (int i = 0; i < 5; i++) encoder.matches(password, hash);
        double[] milliseconds = new double[20];
        for (int i = 0; i < milliseconds.length; i++) {
            long start = System.nanoTime();
            if (!encoder.matches(password, hash)) throw new IllegalStateException("Verification failed");
            milliseconds[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(milliseconds);
        System.out.printf(Locale.ROOT,
                "bcrypt strength=%s warmups=5 samples=20 medianMs=%.2f p95Ms=%.2f maxMs=%.2f java=%s processors=%d%n",
                hash.split("\\$")[2], (milliseconds[9] + milliseconds[10]) / 2,
                milliseconds[18], milliseconds[19], System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors());
    }
}
