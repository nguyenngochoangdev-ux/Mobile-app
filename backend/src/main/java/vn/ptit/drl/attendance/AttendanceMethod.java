package vn.ptit.drl.attendance;

/** Phải khớp CHECK constraint ck_att_method trong V1__init.sql. */
public enum AttendanceMethod {
    /** Sinh viên quét QR trên màn hình presenter. Luồng chính. */
    QR_SCAN,
    /** Cán bộ quét QR do sinh viên hiển thị. Dự phòng khi camera máy sinh viên hỏng. */
    QR_SHOW,
    /** Cán bộ nhập tay. verified = false — đây chính là vấn đề oracle. */
    MANUAL,
    /** Quét offline, đồng bộ muộn khi có mạng. */
    OFFLINE_SYNC
}
