package vn.ptit.drl.common;

import java.math.BigDecimal;

public final class GeoUtil {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoUtil() {}

    /** Khoảng cách haversine, mét. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * @return {@code null} khi thiếu toạ độ của sự kiện hoặc của lần quét — không có
     *         dữ liệu thì không kết luận, chứ không mặc định là sai.
     */
    public static Boolean withinRadius(BigDecimal eventLat, BigDecimal eventLng, Integer radiusM,
                                       BigDecimal scanLat, BigDecimal scanLng) {
        if (eventLat == null || eventLng == null || scanLat == null || scanLng == null
                || radiusM == null) {
            return null;
        }
        double d = distanceMeters(eventLat.doubleValue(), eventLng.doubleValue(),
                                  scanLat.doubleValue(), scanLng.doubleValue());
        return d <= radiusM;
    }
}
