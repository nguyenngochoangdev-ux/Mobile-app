package vn.ptit.drl.common.web;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Object id) {
        super("Không tìm thấy " + entity + " với id = " + id);
    }

    public NotFoundException(String message) {
        super(message);
    }
}
