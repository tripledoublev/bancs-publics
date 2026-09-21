package com.machine.benchmap;

import java.util.Random;

/**
 * MeetupKey: Obfuscated, local-first, privacy-preserving location key generator and decoder.
 * 
 * Enables friends to exchange location keys outside the app (via SMS, Signal, WhatsApp)
 * with zero cloud servers or third-party tracking.
 * 
 * Features:
 * - Obfuscated coordinate packing (XOR scrambling + bit packing).
 * - Optional Privacy Blur (Exact, ~150m street blur, ~300m neighborhood blur).
 * - Crockford Base32 encoding with typo correction (maps O->0, I/L->1).
 * - 8-bit checksum verification.
 * - Deep-link URI support (benchmap://meet?k=...).
 */
public class MeetupKey {

    private static final String CROCKFORD_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int XOR_LAT_MASK = 0x5A3C96;
    private static final int XOR_LON_MASK = 0x69C3A5;
    private static final int CHECKSUM_SALT = 0x55;

    public static final int BLUR_EXACT = 0;       // Exact GPS fix (<10m)
    public static final int BLUR_DISCREET = 150;  // ~150m blur (protects exact home address)
    public static final int BLUR_DISTRICT = 300;  // ~300m blur (general district)

    public static class DecodedLocation {
        public final double lat;
        public final double lon;
        public final int blurMeters;

        public DecodedLocation(double lat, double lon, int blurMeters) {
            this.lat = lat;
            this.lon = lon;
            this.blurMeters = blurMeters;
        }
    }

    /**
     * Encodes latitude and longitude into an obfuscated Swiss meetup key.
     */
    public static String encode(double lat, double lon, int blurMeters) {
        double outLat = lat;
        double outLon = lon;

        if (blurMeters > 0) {
            // Apply reproducible pseudo-random spatial offset within blur radius
            Random rng = new Random((long) (lat * 10000) ^ (long) (lon * 10000));
            double angle = rng.nextDouble() * 2.0 * Math.PI;
            double dist = (blurMeters * 0.7) + (rng.nextDouble() * (blurMeters * 0.3));
            // 1 deg lat ~ 111,139 meters
            // 1 deg lon ~ 111,139 * cos(lat) meters
            double dLat = (dist * Math.cos(angle)) / 111139.0;
            double dLon = (dist * Math.sin(angle)) / (111139.0 * Math.cos(Math.toRadians(lat)));
            outLat += dLat;
            outLon += dLon;
        }

        // Clamp to Greater Montreal bounds
        outLat = Math.max(45.2, Math.min(45.8, outLat));
        outLon = Math.max(-74.1, Math.min(-73.3, outLon));

        long latVal = Math.round((outLat - 45.0) * 1000000.0) & 0xFFFFFF;
        long lonVal = Math.round((-outLon - 73.0) * 1000000.0) & 0xFFFFFF;

        long latMasked = (latVal ^ XOR_LAT_MASK) & 0xFFFFFF;
        long lonMasked = (lonVal ^ XOR_LON_MASK) & 0xFFFFFF;

        int flags = Math.min(15, blurMeters / 50);

        // Pack into 8 bytes (64 bits)
        int b0 = (flags << 4) | (int) ((latMasked >> 20) & 0x0F);
        int b1 = (int) ((latMasked >> 12) & 0xFF);
        int b2 = (int) ((latMasked >> 4) & 0xFF);
        int b3 = (int) (((latMasked & 0x0F) << 4) | ((lonMasked >> 20) & 0x0F));
        int b4 = (int) ((lonMasked >> 12) & 0xFF);
        int b5 = (int) ((lonMasked >> 4) & 0xFF);
        int b6 = (int) ((lonMasked & 0x0F) << 4);

        int chk = (b0 ^ b1 ^ b2 ^ b3 ^ b4 ^ b5 ^ b6 ^ CHECKSUM_SALT) & 0xFF;
        b6 |= (chk >> 4) & 0x0F;
        int b7 = (chk & 0x0F) << 4;

        long data = (((long) b0 & 0xFF) << 56)
                | (((long) b1 & 0xFF) << 48)
                | (((long) b2 & 0xFF) << 40)
                | (((long) b3 & 0xFF) << 32)
                | (((long) b4 & 0xFF) << 24)
                | (((long) b5 & 0xFF) << 16)
                | (((long) b6 & 0xFF) << 8)
                | ((long) b7 & 0xFF);

        // Encode 64-bit value using 13 Crockford Base32 characters (13 * 5 = 65 bits)
        char[] chars = new char[13];
        long val = data << 1;
        for (int i = 0; i < 13; i++) {
            int shift = 60 - (i * 5);
            int idx = (shift >= 0) ? (int) ((val >>> shift) & 0x1F) : 0;
            chars[i] = CROCKFORD_CHARS.charAt(idx);
        }

        String raw = new String(chars);
        return "BM1-" + raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 13);
    }

    /**
     * Decodes an input string or deep link into latitude, longitude, and blur radius.
     * Returns null if key is corrupted, has invalid checksum, or is unparseable.
     */
    public static DecodedLocation decode(String input) {
        if (input == null) return null;

        String str = input.trim();

        // Extract query param 'k=' if a URL or URI was pasted
        if (str.contains("k=")) {
            int idx = str.indexOf("k=");
            str = str.substring(idx + 2);
            int amp = str.indexOf("&");
            if (amp >= 0) {
                str = str.substring(0, amp);
            }
        }

        // Clean formatting: strip prefix, hyphens, spaces
        str = str.toUpperCase()
                .replace("BM1-", "")
                .replace("BM1", "")
                .replace("-", "")
                .replace(" ", "")
                .replace("O", "0")   // Crockford typo correction: O -> 0
                .replace("I", "1")   // Crockford typo correction: I -> 1
                .replace("L", "1");  // Crockford typo correction: L -> 1

        if (str.length() != 13) {
            return null;
        }

        long val = 0;
        for (int i = 0; i < 13; i++) {
            char c = str.charAt(i);
            int idx = CROCKFORD_CHARS.indexOf(c);
            if (idx < 0) {
                return null;
            }
            val = (val << 5) | idx;
        }

        long data = val >>> 1;
        int b0 = (int) ((data >>> 56) & 0xFF);
        int b1 = (int) ((data >>> 48) & 0xFF);
        int b2 = (int) ((data >>> 40) & 0xFF);
        int b3 = (int) ((data >>> 32) & 0xFF);
        int b4 = (int) ((data >>> 24) & 0xFF);
        int b5 = (int) ((data >>> 16) & 0xFF);
        int b6 = (int) ((data >>> 8) & 0xFF);
        int b7 = (int) (data & 0xFF);

        int chk = ((b6 & 0x0F) << 4) | ((b7 >>> 4) & 0x0F);
        int calcChk = (b0 ^ b1 ^ b2 ^ b3 ^ b4 ^ b5 ^ (b6 & 0xF0) ^ CHECKSUM_SALT) & 0xFF;
        if (chk != calcChk) {
            return null; // Corrupted or mistyped key
        }

        int flags = (b0 >>> 4) & 0x0F;
        long latMasked = (((long) (b0 & 0x0F)) << 20)
                | (((long) b1) << 12)
                | (((long) b2) << 4)
                | (((long) (b3 >>> 4)) & 0x0F);

        long lonMasked = (((long) (b3 & 0x0F)) << 20)
                | (((long) b4) << 12)
                | (((long) b5) << 4)
                | (((long) (b6 >>> 4)) & 0x0F);

        long latVal = latMasked ^ XOR_LAT_MASK;
        long lonVal = lonMasked ^ XOR_LON_MASK;

        double lat = 45.0 + (latVal / 1000000.0);
        double lon = -(73.0 + (lonVal / 1000000.0));

        // Sanity check coordinates
        if (lat < 45.2 || lat > 45.8 || lon < -74.1 || lon > -73.3) {
            return null;
        }

        return new DecodedLocation(lat, lon, flags * 50);
    }

    /**
     * Formats an invitation message for messaging apps.
     */
    public static String formatShareText(String key, String optionalLocationName) {
        StringBuilder sb = new StringBuilder();
        sb.append("🤝 Retrouvons-nous sur un banc à mi-chemin !\n\n");
        if (optionalLocationName != null && !optionalLocationName.isEmpty()) {
            sb.append("Je suis vers : ").append(optionalLocationName).append("\n\n");
        }
        sb.append("Voici ma clé de rencontre (100% sécurisée & anonyme) :\n");
        sb.append(key).append("\n\n");
        sb.append("Ou ouvre directement BenchMap :\n");
        sb.append("benchmap://meet?k=").append(key);
        return sb.toString();
    }
}
