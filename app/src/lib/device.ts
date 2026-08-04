const KEY = 'drl.deviceFp'

/**
 * Định danh thiết bị, sinh ngẫu nhiên một lần rồi lưu localStorage.
 *
 * HẠN CHẾ PHẢI GHI VÀO BÁO CÁO — đây không phải fingerprint thật:
 *  - Xoá dữ liệu trình duyệt là mất, sinh viên phải đăng ký lại thiết bị.
 *  - Sao chép được sang máy khác nếu ai đó biết cách đọc localStorage.
 *  - Trình duyệt ẩn danh sinh giá trị mới mỗi phiên.
 *
 * Fingerprint dựa trên canvas/WebGL/font thì ổn định hơn chút nhưng vẫn giả mạo
 * được, và bị các trình duyệt chống fingerprinting chặn dần. Giải pháp đúng là
 * chứng thực nền tảng (Play Integrity, App Attest) — chỉ có ở app native, nằm
 * ngoài phạm vi PWA của đề tài.
 *
 * Vì vậy device binding trong đề tài này làm TĂNG CHI PHÍ gian lận chứ không
 * làm nó bất khả thi. Đó là phát biểu trung thực cần đưa vào bảng threat model,
 * thay cho "chặn hoàn toàn".
 */
export function getDeviceFp(): string {
  let fp = localStorage.getItem(KEY)
  if (!fp) {
    fp = crypto.randomUUID()
    localStorage.setItem(KEY, fp)
  }
  return fp
}

export function resetDeviceFp(): string {
  localStorage.removeItem(KEY)
  return getDeviceFp()
}
