import { useState } from 'react'
import { api } from '../api.js'
import { useApi } from '../useApi.js'
import { actionLabels, formatDate, reasonLabels } from '../labels.js'
import StatusBadge from './StatusBadge.jsx'

const PAGE_SIZE = 10

const POST_FILTERS = [
  { value: '', label: 'Cần xử lý (mặc định)' },
  { value: 'UNDER_REVIEW', label: 'Đang kiểm duyệt' },
  { value: 'HIDDEN', label: 'Đã ẩn' },
  { value: 'LOCKED', label: 'Đã khóa' },
]

const REPORT_FILTERS = [
  { value: 'PENDING', label: 'Đang chờ' },
  { value: 'RESOLVED', label: 'Đã xử lý' },
  { value: 'DISMISSED', label: 'Đã bỏ qua' },
  { value: '', label: 'Tất cả' },
]

const CONFIRMS = {
  HIDE: 'Ẩn bài đăng này? Chỉ chủ bài và quản trị viên còn nhìn thấy bài.',
  LOCK: 'Khóa bài đăng này? Bài vẫn hiển thị nhưng không thể bình luận hay tương tác.',
  RESTORE: 'Khôi phục bài đăng về trạng thái bình thường?',
  DISMISS: 'Bỏ qua toàn bộ báo cáo đang chờ của bài đăng này?',
}

const SUCCESS = {
  HIDE: 'Đã ẩn bài đăng',
  LOCK: 'Đã khóa bài đăng',
  RESTORE: 'Đã khôi phục bài đăng',
  DISMISS: 'Đã bỏ qua báo cáo',
}

function listPath(base, params) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) query.set(key, value)
  })
  return `${base}?${query}`
}

function SummaryCards({ summary, loading, error }) {
  const cards = [
    { key: 'pendingReports', label: 'Báo cáo đang chờ', tone: 'pending' },
    { key: 'underReviewPosts', label: 'Bài đang kiểm duyệt', tone: 'under-review' },
    { key: 'hiddenPosts', label: 'Bài đã ẩn', tone: 'hidden' },
    { key: 'lockedPosts', label: 'Bài đã khóa', tone: 'locked' },
  ]
  return <>
    {error && <p className="form-error">{error}</p>}
    <div className="summary-grid">
      {cards.map((card) => <div key={card.key} className={`summary-card summary-${card.tone}`}>
        <strong>{summary ? (summary[card.key] ?? 0) : (loading ? '...' : '-')}</strong>
        <span>{card.label}</span>
      </div>)}
    </div>
  </>
}

function Pager({ page, data, onChange }) {
  const totalPages = Math.max(data?.totalPages ?? 1, 1)
  const total = data?.totalElements
  return <div className="pager">
    <button className="button secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>Trước</button>
    <span>Trang {page + 1}/{totalPages}{total !== undefined ? ` · ${total} mục` : ''}</span>
    <button className="button secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Sau</button>
  </div>
}

function PendingPostsTable({ version, selectedId, onSelect }) {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const { data, loading, error } = useApi(listPath('/admin/moderation/posts', { status, page, size: PAGE_SIZE }), version)
  const rows = data?.content ?? []

  return <div>
    <div className="mod-toolbar">
      <label>Lọc theo trạng thái
        <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
          {POST_FILTERS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
        </select>
      </label>
    </div>
    {error && <p className="form-error">{error}</p>}
    {loading && !data && <p className="muted mod-empty">Đang tải danh sách bài đăng...</p>}
    {!loading && !error && rows.length === 0 && <p className="muted mod-empty">Không có bài đăng nào cần xử lý.</p>}
    {rows.length > 0 && <div className="table-wrap">
      <table className="mod-table">
        <thead><tr>
          <th>Tiêu đề</th><th>Chuyên mục</th><th>Tác giả</th><th>Trạng thái</th>
          <th>Báo cáo chờ/tổng</th><th>Báo cáo gần nhất</th>
        </tr></thead>
        <tbody>
          {rows.map((row) => <tr key={row.id} className={row.id === selectedId ? 'selected' : ''}
            onClick={() => onSelect(row.id)}>
            <td className="cell-title">{row.title}</td>
            <td>{row.categoryName}</td>
            <td>{row.author?.displayName}</td>
            <td><StatusBadge value={row.moderationStatus} /></td>
            <td>{row.pendingReportCount ?? 0}/{row.totalReportCount ?? 0}</td>
            <td>{row.lastReportedAt ? formatDate(row.lastReportedAt) : '-'}</td>
          </tr>)}
        </tbody>
      </table>
    </div>}
    <Pager page={page} data={data} onChange={setPage} />
  </div>
}

function ReportsTable({ version, selectedId, onSelect }) {
  const [status, setStatus] = useState('PENDING')
  const [page, setPage] = useState(0)
  const { data, loading, error } = useApi(listPath('/admin/reports', { status, page, size: PAGE_SIZE }), version)
  const rows = data?.content ?? []

  return <div>
    <div className="mod-toolbar">
      <label>Lọc theo trạng thái
        <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
          {REPORT_FILTERS.map((item) => <option key={item.label} value={item.value}>{item.label}</option>)}
        </select>
      </label>
    </div>
    {error && <p className="form-error">{error}</p>}
    {loading && !data && <p className="muted mod-empty">Đang tải danh sách báo cáo...</p>}
    {!loading && !error && rows.length === 0 && <p className="muted mod-empty">Không có báo cáo nào.</p>}
    {rows.length > 0 && <div className="table-wrap">
      <table className="mod-table">
        <thead><tr>
          <th>Bài đăng</th><th>Người báo cáo</th><th>Lý do</th><th>Mô tả</th><th>Trạng thái</th><th>Thời gian</th>
        </tr></thead>
        <tbody>
          {rows.map((row) => <tr key={row.id} className={row.postId === selectedId ? 'selected' : ''}
            onClick={() => onSelect(row.postId)}>
            <td className="cell-title">{row.postTitle}</td>
            <td>{row.reporter?.displayName}</td>
            <td>{row.reasonLabel || reasonLabels[row.reason] || row.reason}</td>
            <td className="cell-desc">{row.description || '-'}</td>
            <td><StatusBadge kind="report" value={row.status} /></td>
            <td>{formatDate(row.createdAt)}</td>
          </tr>)}
        </tbody>
      </table>
    </div>}
    <Pager page={page} data={data} onChange={setPage} />
  </div>
}

function ModerationPanel({ postId, version, onClose, onActed, setNotice }) {
  const [note, setNote] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const { data, loading, error } = useApi(`/admin/posts/${postId}/moderation`, version)

  const ready = data && data.post && data.post.id === postId
  const post = ready ? data.post : null
  const reports = ready ? (data.reports ?? []) : []
  const history = ready ? (data.history ?? []) : []
  const pendingCount = reports.filter((item) => item.status === 'PENDING').length
  const status = post?.moderationStatus

  const disabled = {
    HIDE: busy || status === 'HIDDEN',
    LOCK: busy || status === 'LOCKED',
    RESTORE: busy || status === 'NORMAL',
    DISMISS: busy || pendingCount === 0,
  }

  async function act(action) {
    const trimmed = note.trim()
    if ((action === 'HIDE' || action === 'LOCK') && !trimmed) {
      setMessage('Vui lòng nhập ghi chú trước khi ẩn hoặc khóa bài đăng')
      return
    }
    if (!window.confirm(CONFIRMS[action])) return
    setBusy(true)
    setMessage('')
    try {
      const result = await api(`/admin/posts/${postId}/moderation`, {
        method: 'POST',
        body: JSON.stringify({ action, note: trimmed || null }),
      })
      setNote('')
      setNotice(`${SUCCESS[action]} (${result?.resolvedReports ?? 0} báo cáo được xử lý)`)
      onActed()
    } catch (requestError) {
      setMessage(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  return <aside className="mod-panel" aria-label="Chi tiết kiểm duyệt">
    <div className="mod-panel-head">
      <h2>Chi tiết bài đăng</h2>
      <button className="text-button" onClick={onClose}>Đóng</button>
    </div>
    {error && <p className="form-error">{error}</p>}
    {!ready && loading && <p className="muted mod-empty">Đang tải chi tiết...</p>}
    {post && <>
      <div className="mod-post">
        <div className="card-badges">
          <StatusBadge value={status} />
          <span className="tag">{post.category?.name}</span>
        </div>
        <h3>{post.title}</h3>
        <p className="author">Đăng bởi {post.author?.displayName} · {formatDate(post.createdAt)}</p>
        <div className="mod-post-content">{post.content}</div>
      </div>

      <h3>Báo cáo ({reports.length})</h3>
      {reports.length === 0 && <p className="muted">Bài đăng chưa có báo cáo nào.</p>}
      <ul className="mod-list">
        {reports.map((item) => <li key={item.id}>
          <div className="mod-list-top">
            <strong>{item.reasonLabel || reasonLabels[item.reason] || item.reason}</strong>
            <StatusBadge kind="report" value={item.status} />
          </div>
          <p>{item.description || 'Không có mô tả'}</p>
          <span className="muted">{item.reporter?.displayName} · {formatDate(item.createdAt)}</span>
          {item.resolutionNote && <span className="muted"> · Ghi chú xử lý: {item.resolutionNote}</span>}
        </li>)}
      </ul>

      <h3>Lịch sử kiểm duyệt</h3>
      {history.length === 0 && <p className="muted">Chưa có hành động kiểm duyệt nào.</p>}
      <ul className="mod-list">
        {history.map((item) => <li key={item.id}>
          <div className="mod-list-top">
            <strong>{item.actionLabel || actionLabels[item.action] || item.action}</strong>
            <span className="muted">{formatDate(item.createdAt)}</span>
          </div>
          <span className="muted">{item.admin ? item.admin.displayName : 'Hệ thống'}</span>
          {item.note && <p>{item.note}</p>}
        </li>)}
      </ul>

      <div className="mod-actions">
        <h3>Xử lý</h3>
        {message && <p className="form-error">{message}</p>}
        <label>Ghi chú (bắt buộc khi ẩn hoặc khóa bài)
          <textarea rows="3" maxLength="500" value={note} placeholder="Nhập lý do xử lý..."
            onChange={(event) => { setNote(event.target.value); setMessage('') }} />
          <span className={`char-count ${note.length >= 500 ? 'at-limit' : ''}`}>{note.length}/500</span>
        </label>
        <div className="mod-buttons">
          <button className="button danger-solid" disabled={disabled.HIDE} onClick={() => act('HIDE')}>Ẩn bài</button>
          <button className="button danger-solid" disabled={disabled.LOCK} onClick={() => act('LOCK')}>Khóa bài</button>
          <button className="button primary" disabled={disabled.RESTORE} onClick={() => act('RESTORE')}>Khôi phục</button>
          <button className="button secondary" disabled={disabled.DISMISS} onClick={() => act('DISMISS')}>Bỏ qua báo cáo</button>
        </div>
      </div>
    </>}
  </aside>
}

function ModerationPage({ user, initialPostId, setNotice }) {
  const [tab, setTab] = useState('posts')
  const [selectedId, setSelectedId] = useState(initialPostId ?? null)
  const [version, setVersion] = useState(0)
  const isAdmin = user?.role === 'ADMIN'
  const summary = useApi(isAdmin ? '/admin/moderation/summary' : null, version)

  if (!isAdmin) {
    return <section className="moderation"><p className="empty">Chỉ quản trị viên mới truy cập được trang Kiểm duyệt.</p></section>
  }

  return <section className="moderation">
    <h1>Kiểm duyệt nội dung</h1>
    <SummaryCards summary={summary.data} loading={summary.loading} error={summary.error} />

    <div className="tabs" role="tablist">
      <button role="tab" aria-selected={tab === 'posts'} className={`tab ${tab === 'posts' ? 'active' : ''}`}
        onClick={() => setTab('posts')}>Bài chờ xử lý</button>
      <button role="tab" aria-selected={tab === 'reports'} className={`tab ${tab === 'reports' ? 'active' : ''}`}
        onClick={() => setTab('reports')}>Báo cáo</button>
    </div>

    <div className={`mod-layout ${selectedId ? 'with-panel' : ''}`}>
      <div className="mod-main">
        {tab === 'posts'
          ? <PendingPostsTable version={version} selectedId={selectedId} onSelect={setSelectedId} />
          : <ReportsTable version={version} selectedId={selectedId} onSelect={setSelectedId} />}
      </div>
      {selectedId && <ModerationPanel key={selectedId} postId={selectedId} version={version}
        onClose={() => setSelectedId(null)} onActed={() => setVersion((value) => value + 1)}
        setNotice={setNotice} />}
    </div>
  </section>
}

export default ModerationPage
