package vn.ptit.drl.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình riêng của đề tài, bind từ khối {@code drl:} trong application.yml.
 */
@ConfigurationProperties(prefix = "drl")
public record DrlProperties(Jwt jwt, Attendance attendance, Anchor anchor, Storage storage,
                            Credential credential) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {}

    /**
     * @param qrSlotSeconds    QR đổi mỗi N giây.
     * @param qrSlotTolerance  Số slot lùi được chấp nhận, bù lệch đồng hồ giữa thiết bị.
     * @param offlineWindowHours Cửa sổ chấp nhận bản ghi check-in offline đồng bộ muộn.
     * @param geofenceBlocking Để {@code false}. GPS trong nhà rất kém chính xác — geofence
     *                         là tín hiệu phụ đánh dấu bản ghi cần xem lại, không dùng để
     *                         từ chối check-in.
     * @param autoApproveFirstDevice Thiết bị ĐẦU TIÊN của sinh viên được duyệt tự động;
     *                         mọi thiết bị sau đó phải qua cán bộ duyệt.
     *                         <p>
     *                         Đánh đổi có chủ ý: bắt duyệt cả thiết bị đầu tiên nghĩa là
     *                         cán bộ phải duyệt tay 500 sinh viên trước khi hệ thống dùng
     *                         được. Rủi ro còn lại — sinh viên chưa từng đăng nhập bị người
     *                         khác chiếm tài khoản và đăng ký thiết bị đầu tiên — phải ghi
     *                         vào phần hạn chế của báo cáo.
     *                         <p>
     *                         Luận điểm "device binding chống điểm danh hộ" vẫn đứng vững:
     *                         mượn tài khoản để bạn quét hộ đòi hỏi ĐỔI thiết bị, và việc
     *                         đó luôn cần duyệt.
     */
    public record Attendance(int qrSlotSeconds, int qrSlotTolerance,
                             int offlineWindowHours, boolean geofenceBlocking,
                             boolean autoApproveFirstDevice) {}

    /**
     * @param privateKey Khóa ký giao dịch neo. Phải có {@code ANCHOR_ROLE} trên
     *                   {@code AnchorRegistry}.
     *                   <p>
     *                   Tách khỏi khóa triển khai là có chủ ý — contract cấp
     *                   {@code ANCHOR_ROLE} riêng nên khóa triển khai (đồng thời là admin)
     *                   cất được offline sau khi cấp quyền. Nếu khóa của job neo bị lộ, kẻ
     *                   tấn công neo được root rác nhưng KHÔNG sửa được lô đã neo và không
     *                   cấp/thu quyền được cho ai.
     */
    public record Anchor(String cron, boolean enabled, String rpcUrl, String rpcUrlFallback,
                         long chainId, String privateKey, String issuerRegistryAddress,
                         String anchorRegistryAddress, String statusListAddress) {}

    public record Storage(String uploadDir) {}

    /**
     * @param statusListPoolSize Kích thước không gian {@code status_list_index}.
     *                         <p>
     *                         Chỉ số được cấp NGẪU NHIÊN từ pool này, không tuần tự — cấp
     *                         tuần tự làm sự kiện {@code StatusChanged(index)} trên chuỗi
     *                         công khai lộ thứ tự cấp phát và tương quan được với danh sách
     *                         sinh viên (PROJECT.md §2.3).
     *                         <p>
     *                         Đây là một cái núm có hai đầu đánh đổi. Pool LỚN thì gần như
     *                         không bao giờ bốc trùng, nhưng chỉ số rải đều nên mỗi lần thu
     *                         hồi chạm một ô lưu trữ mới — trường hợp đắt nhất của bitmap
     *                         (đã đo: {@code docs/measurements.md} §11.4). Pool NHỎ thì chỉ
     *                         số dày hơn, gom cụm tốt hơn, nhưng bốc trùng nhiều lên và có
     *                         ngày hết chỗ.
     *                         <p>
     *                         Mặc định 2^20 = 1.048.576: với cỡ 50.000 credential thì độ đầy
     *                         ~5%, số lần bốc kỳ vọng ~1,05. Xem
     *                         {@code docs/canonicalization.md} §10.
     * @param issuerPrivateKey Khóa ký credential của TỔ CHỨC cấp phát. Rỗng thì không cấp
     *                         được credential, nhưng ứng dụng vẫn khởi động.
     *                         <p>
     *                         NÊN KHÁC {@code ANCHOR_PRIVATE_KEY}. Hai khóa hai vai trò: lộ
     *                         khóa neo thì kẻ tấn công neo được root rác nhưng không cấp được
     *                         credential giả; lộ khóa issuer thì ngược lại. Gộp một khóa là
     *                         nhân đôi thiệt hại của một lần lộ mà không tiết kiệm được gì.
     */
    public record Credential(long statusListPoolSize, String issuerPrivateKey) {}
}
