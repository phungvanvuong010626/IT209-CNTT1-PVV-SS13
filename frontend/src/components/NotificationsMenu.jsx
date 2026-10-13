import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { formatDate } from '../labels.js'

function NotificationsMenu({ onClose, onOpenPost, onChanged }) {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api('/notifications')
      .then((list) => { if (active) setItems(list) })
      .catch((requestError) => { if (active) { setError(requestError.message); setItems([]) } })
    return () => { active = false }
  }, [])

  async function openItem(item) {
    try {
      if (!item.read) {
        await api(`/notifications/${item.id}/read`, { method: 'PATCH' })
        setItems((current) => current.map((entry) => (entry.id === item.id ? { ...entry, read: true } : entry)))
        onChanged()
      }
      if (item.postId) {
        onOpenPost(item.postId)
        onClose()
      }
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  async function markAll() {
    try {
      await api('/notifications/read-all', { method: 'PATCH' })
      setItems((current) => current.map((entry) => ({ ...entry, read: true })))
      setError('')
      onChanged()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const hasUnread = items?.some((item) => !item.read)

  return <>
    <div className="menu-backdrop" onClick={onClose} />
    <section className="notif-menu" aria-label="Thông báo">
      <div className="notif-head">
        <h2>Thông báo</h2>
        <button className="text-button" disabled={!hasUnread} onClick={markAll}>Đánh dấu tất cả đã đọc</button>
      </div>
      {error && <p className="form-error">{error}</p>}
      {items === null && <p className="muted notif-empty">Đang tải thông báo...</p>}
      {items?.length === 0 && !error && <p className="muted notif-empty">Bạn chưa có thông báo nào.</p>}
      <ul className="notif-list">
        {items?.map((item) => <li key={item.id}>
          <button className={`notif-item ${item.read ? '' : 'unread'}`} onClick={() => openItem(item)}>
            <strong>{item.title}</strong>
            <span className="notif-message">{item.message}</span>
            <span className="notif-time">{formatDate(item.createdAt)}</span>
          </button>
        </li>)}
      </ul>
    </section>
  </>
}

export default NotificationsMenu
