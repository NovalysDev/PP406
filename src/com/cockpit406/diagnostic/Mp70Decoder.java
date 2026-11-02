package com.cockpit406.diagnostic;

import java.util.LinkedHashMap;
import java.util.Map;

/** Decodes only the two MP7.0 layouts verified on this car. No transport or commands. */
public final class Mp70Decoder {
    private Mp70Decoder() {}

    public static Map<String, Double> decode(String hex) {
        if (hex == null || (hex.length() & 1) != 0) throw new IllegalArgumentException("Invalid hex");
        int[] data = new int[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hex");
            data[i] = high * 16 + low;
        }
        if (data.length < 2 || data[0] != 0x11) throw new IllegalArgumentException("Not a measurement");
        int group = data[1];
        if ((group != 0 && group != 2) || data.length != (group == 0 ? 20 : 24)) {
            throw new IllegalArgumentException("Unverified group or length");
        }
        Map<String, Double> values = new LinkedHashMap<>();
        put(values, "group", group);
        put(values, "rpm", data[2] * 40);
        put(values, "battery_v", (data[3] * 141 / 200) / 10.0);
        if (group == 2) {
            put(values, "throttle_deg", (data[7] * 79 / 20) / 10.0);
            put(values, "throttle_mv", data[8] * 98 / 5);
            put(values, "coolant_r_c", data[9] * 3 / 4 - 48);
            put(values, "coolant_s_c", data[10] * 3 / 4 - 48);
            put(values, "air_r_c", data[11] * 3 / 4 - 48);
            put(values, "air_s_c", data[12] * 3 / 4 - 48);
            put(values, "idle_duty_pct", data[13]);
            put(values, "map_mbar", data[14] * 71 / 16);
            put(values, "canister_duty_pct", data[16] * 25 / 64);
            put(values, "oxygen_mv", data[18] * 247 / 50);
            put(values, "speed_kmh", data[21] * 39 / 35);
        } else {
            put(values, "load_ms", (data[5] / 2) / 10.0);
            put(values, "injection_ms", ((data[6] << 8 | data[7]) / 75) / 10.0);
            put(values, "throttle_deg", (data[10] * 79 / 20) / 10.0);
            put(values, "throttle_mv", data[11] * 98 / 5);
            put(values, "coolant_r_c", data[12] * 3 / 4 - 48);
            put(values, "coolant_s_c", data[13] * 3 / 4 - 48);
            put(values, "air_r_c", data[14] * 3 / 4 - 48);
            put(values, "air_s_c", data[15] * 3 / 4 - 48);
            put(values, "map_mbar", data[16] * 71 / 16);
            put(values, "oxygen_mv", data[19] * 247 / 50);
        }
        return values;
    }

    private static void put(Map<String, Double> values, String key, double value) {
        values.put(key, value);
    }
}
