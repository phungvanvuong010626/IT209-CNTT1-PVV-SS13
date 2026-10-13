const messages = {
  UNDER_REVIEW: {
    title: 'Bài đăng đang được kiểm duyệt',
    text: 'Bài đăng đã nhận nhiều báo cáo và đang chờ quản trị viên xem xét. Bài vẫn hiển thị công khai trong thời gian này.',
  },
  HIDDEN: {
    title: 'Bài đăng đã bị ẩn',
    text: 'Chỉ chủ bài và quản trị viên nhìn thấy bài đăng này. Bình luận và tương tác đã bị tắt.',
  },
  LOCKED: {
    title: 'Bài đăng đã bị khóa',
    text: 'Bài đăng vẫn hiển thị nhưng không thể bình luận, bày tỏ quan tâm hoặc chỉnh sửa.',
  },
}

function ModerationBanner({ post, user }) {
  const status = post.moderationStatus
  if (!status || status === 'NORMAL' || !messages[status]) return null
  const isOwner = user && user.id === post.author.id
  const isAdmin = user?.role === 'ADMIN'
  if (!isOwner && !isAdmin) return null

  const pending = post.pendingReportCount ?? 0
  return (
    <div className={`mod-banner mod-banner-${status.toLowerCase().replace('_', '-')}`} role="status">
      <strong>{messages[status].title}</strong>
      <p>{messages[status].text}</p>
      {isAdmin && <p className="mod-banner-meta">Báo cáo đang chờ: {pending}</p>}
    </div>
  )
}

export default ModerationBanner
