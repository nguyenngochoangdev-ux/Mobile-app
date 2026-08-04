package vn.ptit.drl.identity;

/** Phải khớp CHECK constraint ck_device_status trong V1__init.sql. */
public enum DeviceStatus {
    PENDING, ACTIVE, REVOKED
}
