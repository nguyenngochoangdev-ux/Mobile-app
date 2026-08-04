package vn.ptit.drl.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình riêng của đề tài, bind từ khối {@code drl:} trong application.yml.
 */
@ConfigurationProperties(prefix = "drl")
public record DrlProperties(Jwt jwt, Attendance attendance, Anchor anchor, Storage storage) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {}

    /**
     * @param qrSlotSeconds    QR đổi mỗi N giây.
     * @param qrSlotTolerance  Số slot lùi được chấp nhận, bù lệch đồng hồ giữa thiết bị.
     * @param offlineWindowHours Cửa sổ chấp nhận bản ghi check-in offline đồng bộ muộn.
     * @param geofenceBlocking Để {@code false}. GPS trong nhà rất kém chính xác — geofence
     *                         là tín hiệu phụ đánh dấu bản ghi cần xem lại, không dùng để
     *                         từ chối check-in.
     */
    public record Attendance(int qrSlotSeconds, int qrSlotTolerance,
                             int offlineWindowHours, boolean geofenceBlocking) {}

    public record Anchor(String cron, boolean enabled, String rpcUrl, String rpcUrlFallback,
                         long chainId, String issuerRegistryAddress,
                         String anchorRegistryAddress, String statusListAddress) {}

    public record Storage(String uploadDir) {}
}
