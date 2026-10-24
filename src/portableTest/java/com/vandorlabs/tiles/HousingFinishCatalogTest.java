package com.vandorlabs.tiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Freeze original saved choice identities and their authored texture mapping. */
public final class HousingFinishCatalogTest {
    public static void main(String[] args) throws Exception {
        if (HousingFinishCatalog.legacyCount() != 78) throw new AssertionError("legacy saved choice count");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < HousingFinishCatalog.legacyCount(); i++) {
            String id = HousingFinishCatalog.id(i);
            if (HousingFinishCatalog.index(id) != i) throw new AssertionError("duplicate or remapped saved finish " + id);
            digest.update((id + "=" + HousingFinishCatalog.texture(i) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) actual.append(String.format("%02x", value & 255));
        if (!"4ecba7b96ae8b998802083f829f465e14dc342448db8d2995277152170f826a6".equals(actual.toString())) {
            throw new AssertionError("original saved IDs/order or texture paths changed: " + actual);
        }
        if (HousingFinishCatalog.index(null) != -1 || HousingFinishCatalog.index("unknown") != -1) {
            throw new AssertionError("unknown finish lookup");
        }
        System.out.println("Housing finish catalog PASS: 78 original saved choices and texture mappings");
    }
}
