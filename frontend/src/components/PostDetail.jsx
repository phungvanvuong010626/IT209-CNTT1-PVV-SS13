import { useState } from 'react'
import { api } from '../api.js'
import { formatDate } from '../labels.js'
import ModerationBanner from './ModerationBanner.jsx'
import ReportDialog from './ReportDialog.jsx'
import StatusBadge from './StatusBadge.jsx'

function PostDetail({ post, user, onBack, onChanged, onDeleted, onModerate, setNotice }) {
  const [comment, setComment] = useState('')
  const [reporting, setReporting] = useState(false)
  const isOwner = Boolean(user) && user.id === post.author.id
  const isAdmin = user?.role === 'ADMIN'
  const canManage = isOwner || isAdmin
  const locked = Boolean(post.locked)
  const moderated = post.moderationStatus && post.moderationStatus !== 'NORMAL'
  const canReport = Boolean(user) && !isOwner && !locked

  async function act(action, success) {
    try {
      await action()
      setNotice(success)
      await onChanged()
      return true
    } catch (error) {
      setNotice(error.message)
      return false
    }
  }

  async function reported() {
    setReporting(false)
    setNotice('Đã gửi báo cáo. Cảm ơn bạn đã giúp giữ cộng đồng an toàn')
    await onChanged()
  }

  return (
    <section className="detail">
      <button className="back" onClick={onBack}>← Quay lại danh sách</button>
      <div className="detail-card">
        <ModerationBanner post={post} user={user} />
        <div className="card-top">
          <span className="card-top-left"><span className="tag">{post.category.name}</span>
            {moderated && <StatusBadge value={post.moderationStatus} />}</span>
          <span>{formatDate(post.createdAt)}</span>
        </div>
        <h1>{post.title}</h1>
        <p className="author">Đăng bởi {post.author.displayName} · Trạng thái: {post.status}</p>
        <div className="content">{post.content}</div>
        {locked && <p className="locked-note">Bài đăng đã bị khóa bởi quản trị viên</p>}
        <div className="actions">
          {!locked && <button className={`button ${post.reactedByCurrentUser ? 'primary' : 'secondary'}`} disabled={!user}
            onClick={() => act(() => api(`/posts/${post.id}/reaction`, { method: 'POST' }), 'Đã cập nhật quan tâm')}>
            ♥ {post.reactionCount} quan tâm
          </button>}
          {!locked && canManage && post.status !== 'RESOLVED' && <button className="button secondary"
            onClick={() => act(() => api(`/posts/${post.id}/status`, {
              method: 'PATCH', body: JSON.stringify({ status: 'RESOLVED' }),
            }), 'Đã đánh dấu hoàn tất')}>Đánh dấu hoàn tất</button>}
          {isAdmin && onModerate && <button className="button secondary" onClick={() => onModerate(post.id)}>
            Xử lý trong Kiểm duyệt</button>}
          {canReport && (post.reportedByMe
            ? <button className="button secondary" disabled>Bạn đã báo cáo</button>
            : <button className="button danger" onClick={() => setReporting(true)}>Báo cáo vi phạm</button>)}
          {canManage && <button className="button danger" onClick={async () => {
            if (!window.confirm('Xóa bài đăng này?')) return
            try {
              await api(`/posts/${post.id}`, { method: 'DELETE' })
              setNotice('Đã xóa bài đăng')
              onDeleted()
            } catch (error) {
              setNotice(error.message)
            }
          }}>Xóa bài</button>}
        </div>
      </div>

      <div className="comments">
        <h2>Bình luận ({post.commentCount})</h2>
        {locked
          ? <p className="muted">Bài đăng đã bị khóa bởi quản trị viên. Không thể bình luận hoặc bày tỏ quan tâm.</p>
          : user ? <form onSubmit={async (event) => {
            event.preventDefault()
            const ok = await act(() => api(`/posts/${post.id}/comments`, {
              method: 'POST', body: JSON.stringify({ content: comment }),
            }), 'Đã thêm bình luận')
            if (ok) setComment('')
          }}>
            <textarea value={comment} onChange={(event) => setComment(event.target.value)}
              placeholder="Viết bình luận..." required maxLength="1000" />
            <button className="button primary">Gửi bình luận</button>
          </form> : <p className="muted">Đăng nhập để bình luận hoặc bày tỏ quan tâm.</p>}
        <div className="comment-list">
          {post.comments.map((item) => <div className="comment" key={item.id}>
            <strong>{item.author.displayName}</strong><span>{formatDate(item.createdAt)}</span><p>{item.content}</p>
          </div>)}
        </div>
      </div>

      {reporting && <ReportDialog post={post} onClose={() => setReporting(false)} onReported={reported} />}
    </section>
  )
}

export default PostDetail
