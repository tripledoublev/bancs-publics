package map.bench.bancs_publics;

/**
 * Represents a public bench on the Island of Montreal.
 * Precomputed Mercator coordinates ensure zero-allocation transformations during rendering.
 */
public class Bench {
    public final double lat;
    public final double lon;
    public final double mercX;
    public final double mercY;
    public final String park;
    public final String street;
    public final String borough;
    public final int addressNum; // 0 = no civic number
    public final String material;
    public final int backrest; // 1 = yes, 0 = no, -1 = unspecified
    public final int seats;
    public final boolean isCustom;

    public Bench(double lat, double lon, String park, String street, String borough, int addressNum,
                 String material, int backrest, int seats) {
        this(lat, lon, toMercatorX(lon), toMercatorY(lat), park, street, borough, addressNum, material, backrest, seats, false);
    }

    public Bench(double lat, double lon, double mercX, double mercY, String park, String street,
                 String borough, int addressNum, String material, int backrest, int seats) {
        this(lat, lon, mercX, mercY, park, street, borough, addressNum, material, backrest, seats, false);
    }

    public Bench(double lat, double lon, String park, String street, String borough, int addressNum,
                 String material, int backrest, int seats, boolean isCustom) {
        this(lat, lon, toMercatorX(lon), toMercatorY(lat), park, street, borough, addressNum, material, backrest, seats, isCustom);
    }

    public Bench(double lat, double lon, double mercX, double mercY, String park, String street,
                 String borough, int addressNum, String material, int backrest, int seats, boolean isCustom) {
        this.lat = lat;
        this.lon = lon;
        this.mercX = mercX;
        this.mercY = mercY;
        this.park = park != null ? park : "";
        this.street = street != null ? street : "";
        this.borough = borough != null ? borough : "";
        this.addressNum = addressNum;
        this.material = material != null ? material : "";
        this.backrest = backrest;
        this.seats = seats;
        this.isCustom = isCustom;
    }

    public boolean isInPark() {
        return !park.trim().isEmpty();
    }

    public boolean hasBackrest() {
        return backrest == 1;
    }

    public boolean isWood() {
        if (material.isEmpty()) return false;
        String m = material.toLowerCase();
        return m.contains("bois") || m.contains("wood");
    }

    /**
     * Returns the formatted civic street address (e.g. "2118 Rue du Centre" or "Rue du Centre").
     */
    public String getAddress() {
        if (street.trim().isEmpty()) {
            return "";
        }
        if (addressNum > 0) {
            return addressNum + " " + street;
        }
        return street;
    }

    public String getDisplayName() {
        if (isCustom) {
            if (isInPark()) {
                return park;
            }
            String addr = getAddress();
            if (!addr.isEmpty()) {
                return addr;
            }
            return "Mon banc";
        }
        if (isInPark()) {
            return park;
        }
        String addr = getAddress();
        if (!addr.isEmpty()) {
            return addr;
        }
        return "Banc Public de Rue";
    }

    public String getDisplaySubtitle() {
        if (isCustom) {
            String loc = isInPark() ? park : (!street.isEmpty() ? street : "Ajouté manuellement");
            return "Mes bancs • " + loc;
        }
        if (isInPark()) {
            String addr = getAddress();
            if (!addr.isEmpty() && !borough.isEmpty()) {
                return borough + " • Près du " + addr;
            } else if (!borough.isEmpty()) {
                return borough + " • Parc public";
            } else if (!addr.isEmpty()) {
                return "Parc public • Près de " + addr;
            }
            return "Banc de parc public";
        }
        if (!borough.isEmpty()) {
            return borough + " • Montréal";
        }
        return "Banc public • Montréal";
    }

    public String getFormattedMaterial() {
        if (material.isEmpty() || material.equalsIgnoreCase("non spécifié")) {
            return "Standard";
        }
        return material.substring(0, 1).toUpperCase() + material.substring(1);
    }

    public String getFormattedBackrest() {
        if (backrest == 1) return "Oui";
        if (backrest == 0) return "Non";
        return "Non spécifié";
    }

    public String getId() {
        return isCustom ? "custom_" + toBenchId(lat, lon) : toBenchId(lat, lon);
    }

    public static String toBenchId(double lat, double lon) {
        return String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon);
    }

    public static double toMercatorX(double lon) {
        return Math.toRadians(lon);
    }

    public static double toMercatorY(double lat) {
        double rad = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat)));
        return Math.log(Math.tan(Math.PI / 4.0 + rad / 2.0));
    }

    public static double toDegreesLon(double mercX) {
        return Math.toDegrees(mercX);
    }

    public static double toDegreesLat(double mercY) {
        return Math.toDegrees(2.0 * Math.atan(Math.exp(mercY)) - Math.PI / 2.0);
    }
}
