package vn.edu.ptit.campushub.moderation;

/** Hành động kiểm duyệt kèm nhãn tiếng Việt. AUTO_REVIEW do hệ thống tự thực hiện. */
public enum ModerationAction {
    HIDE("Ẩn bài"),
    LOCK("Khóa bài"),
    RESTORE("Khôi phục"),
    DISMISS("Bỏ qua báo cáo"),
    AUTO_REVIEW("Tự động chuyển kiểm duyệt");

    private final String label;

    ModerationAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
