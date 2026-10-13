import { formatDate } from '../labels.js'
import StatusBadge from './StatusBadge.jsx'

function PostCard({ post, user, onOpen }) {
  const moderated = post.moderationStatus && post.moderationStatus !== 'NORMAL'
  const pending = post.pendingReportCount ?? 0
  const isAdmin = user?.role === 'ADMIN'

  return (
    <article className="post-card" onClick={() => onOpen(post.id)}>
      <div className="card-top"><span className="tag">{post.category.name}</span><span>{formatDate(post.createdAt)}</span></div>
      {(moderated || (isAdmin && pending > 0)) && <div className="card-badges">
        {moderated && <StatusBadge value={post.moderationStatus} />}
        {isAdmin && pending > 0 && <span className="badge badge-pending">{pending} báo cáo chờ</span>}
      </div>}
      <h2>{post.title}</h2>
      <p>{post.content}</p>
      <div className="card-footer">
        <span>{post.author.displayName}</span>
        <span>♥ {post.reactionCount} · {post.commentCount} bình luận</span>
      </div>
    </article>
  )
}

export default PostCard
