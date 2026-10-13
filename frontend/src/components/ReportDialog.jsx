import { useEffect, useState } from 'react'
import { api } from '../api.js'
import Dialog from './Dialog.jsx'

const MAX_DESCRIPTION = 500

function ReportDialog({ post, onClose, onReported }) {
  const [reasons, setReasons] = useState([])
  const [loadingReasons, setLoadingReasons] = useState(true)
  const [reason, setReason] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true
    api('/report-reasons')
      .then((list) => { if (active) { setReasons(list); setLoadingReasons(false) } })
      .catch((requestError) => { if (active) { setError(requestError.message); setLoadingReasons(false) } })
    return () => { active = false }
  }, [])

  async function submit(event) {
    event.preventDefault()
    if (!reason) {
      setError('Vui lòng chọn lý do báo cáo')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await api(`/posts/${post.id}/reports`, {
        method: 'POST',
        body: JSON.stringify({ reason, description: description.trim() || null }),
      })
      onReported()
    } catch (requestError) {
      setError(requestError.message)
      setSubmitting(false)
    }
  }

  return <Dialog title="Báo cáo vi phạm" onClose={onClose}>
    <form className="form" onSubmit={submit}>
      <p className="muted report-target">Bài đăng: {post.title}</p>
      {error && <p className="form-error">{error}</p>}
      <label>Lý do báo cáo
        <select required value={reason} disabled={loadingReasons} onChange={(event) => setReason(event.target.value)}>
          <option value="">{loadingReasons ? 'Đang tải danh sách lý do...' : 'Chọn lý do'}</option>
          {reasons.map((item) => <option key={item.code} value={item.code}>{item.label}</option>)}
        </select>
      </label>
      <label>Mô tả thêm (không bắt buộc)
        <textarea rows="5" maxLength={MAX_DESCRIPTION} value={description}
          placeholder="Mô tả ngắn gọn vấn đề bạn gặp phải..."
          onChange={(event) => setDescription(event.target.value)} />
        <span className={`char-count ${description.length >= MAX_DESCRIPTION ? 'at-limit' : ''}`}>
          {description.length}/{MAX_DESCRIPTION}
        </span>
      </label>
      <button className="button primary" disabled={submitting || loadingReasons}>
        {submitting ? 'Đang gửi...' : 'Gửi báo cáo'}
      </button>
    </form>
  </Dialog>
}

export default ReportDialog
