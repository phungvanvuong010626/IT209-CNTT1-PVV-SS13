export const moderationStatusLabels = {
  NORMAL: 'Bình thường',
  UNDER_REVIEW: 'Đang kiểm duyệt',
  LOCKED: 'Đã khóa',
  HIDDEN: 'Đã ẩn',
}

export const reportStatusLabels = {
  PENDING: 'Đang chờ',
  RESOLVED: 'Đã xử lý',
  DISMISSED: 'Đã bỏ qua',
}

export const actionLabels = {
  HIDE: 'Ẩn bài',
  LOCK: 'Khóa bài',
  RESTORE: 'Khôi phục',
  DISMISS: 'Bỏ qua báo cáo',
  AUTO_REVIEW: 'Tự động chuyển kiểm duyệt',
}

export const reasonLabels = {
  SPAM: 'Tin rác, quảng cáo',
  INAPPROPRIATE: 'Nội dung không phù hợp',
  SCAM: 'Lừa đảo, thông tin sai',
  HARASSMENT: 'Quấy rối, xúc phạm',
  OTHER: 'Lý do khác',
}

export function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
