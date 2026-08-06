package vn.ptit.drl.credential;

/**
 * Loại credential được cấp.
 *
 * <p><b>Đúng một giá trị là có chủ ý, không phải thiếu sót.</b> Mỗi loại có một lược đồ
 * {@code claims} riêng trong payload miền {@code CRED}, và mỗi lược đồ là một hợp đồng
 * backend↔verifier phải có test vector riêng ({@code docs/canonicalization.md}). Khai sẵn
 * loại chưa cấp được nghĩa là verifier phải mang mã cho một nhánh không bao giờ chạy tới, và
 * nhánh đó sẽ không có gì kiểm chứng nó.
 *
 * <p>{@code DIEM_REN_LUYEN} (xác nhận điểm rèn luyện một học kỳ) là việc của tuần 5, cùng với
 * rule engine sinh ra con số đó. Thêm nó bây giờ là chốt sớm lược đồ {@code claims} trong khi
 * chưa biết rule engine trả về những trường nào.
 */
public enum CredentialType {

  /**
   * Xác nhận tham gia hoạt động ngoại khóa trong một học kỳ.
   *
   * <p>{@code claims}: {@code semester} · {@code activityCount} · {@code totalPoints}.
   * Đây là bản tổng kết <b>chốt tại thời điểm cấp</b> — sinh viên tham gia thêm hoạt động sau
   * đó thì cấp credential mới, không sửa credential cũ. Sửa là làm hỏng leaf đã neo.
   */
  HOAT_DONG
}
