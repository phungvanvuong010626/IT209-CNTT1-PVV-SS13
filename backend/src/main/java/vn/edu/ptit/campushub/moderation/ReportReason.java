package vn.edu.ptit.campushub.moderation;

/** Lý do báo cáo kèm nhãn tiếng Việt hiển thị cho người dùng. */
public enum ReportReason {
    SPAM("Tin rác, quảng cáo"),
    INAPPROPRIATE("Nội dung không phù hợp"),
    SCAM("Lừa đảo, thông tin sai"),
    HARASSMENT("Quấy rối, xúc phạm"),
    OTHER("Lý do khác");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
