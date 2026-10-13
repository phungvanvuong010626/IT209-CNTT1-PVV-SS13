import { useCallback, useEffect, useState } from 'react'
import { api, clearCredentials, hasCredentials } from './api.js'
import AuthDialog from './components/AuthDialog.jsx'
import ModerationPage from './components/ModerationPage.jsx'
import NotificationsMenu from './components/NotificationsMenu.jsx'
import PostCard from './components/PostCard.jsx'
import PostDetail from './components/PostDetail.jsx'
import PostDialog from './components/PostDialog.jsx'

const POLL_INTERVAL = 30000

function App() {
  const [user, setUser] = useState(null)
  const [categories, setCategories] = useState([])
  const [posts, setPosts] = useState([])
  const [selectedPost, setSelectedPost] = useState(null)
  const [filters, setFilters] = useState({ keyword: '', categoryId: '' })
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  const [view, setView] = useState('home')
  const [moderationPostId, setModerationPostId] = useState(null)
  const [unreadCount, setUnreadCount] = useState(0)
  const [notificationsOpen, setNotificationsOpen] = useState(false)

  const loadPosts = useCallback(async () => {
    const query = new URLSearchParams()
    if (filters.keyword) query.set('keyword', filters.keyword)
    if (filters.categoryId) query.set('categoryId', filters.categoryId)
    try {
      const page = await api(`/posts?${query}`)
      setPosts(page.content)
    } catch (error) {
      setNotice(error.message)
    }
  }, [filters])

  const refreshUnread = useCallback(async () => {
    try {
      const result = await api('/notifications/unread-count')
      setUnreadCount(result?.count ?? 0)
    } catch {
      // Lỗi khi cập nhật định kỳ không cần làm phiền người dùng
    }
  }, [])

  useEffect(() => {
    api('/categories').then(setCategories).catch((error) => setNotice(error.message))
    // Chỉ hỏi thông tin người dùng khi phiên đã lưu thông tin đăng nhập,
    // tránh gọi API được bảo vệ lúc chưa đăng nhập
    if (hasCredentials()) {
      api('/auth/me').then(setUser).catch(() => clearCredentials())
    }
  }, [])

  useEffect(() => {
    const query = new URLSearchParams()
    if (filters.keyword) query.set('keyword', filters.keyword)
    if (filters.categoryId) query.set('categoryId', filters.categoryId)
    let active = true
    api(`/posts?${query}`)
      .then((page) => { if (active) setPosts(page.content) })
      .catch((error) => { if (active) setNotice(error.message) })
    return () => { active = false }
  }, [filters])

  useEffect(() => {
    if (!user) return undefined
    let active = true
    const poll = () => api('/notifications/unread-count')
      .then((result) => { if (active) setUnreadCount(result?.count ?? 0) })
      .catch(() => {}) // Lỗi khi cập nhật định kỳ không cần làm phiền người dùng
    poll()
    const timer = setInterval(poll, POLL_INTERVAL)
    return () => { active = false; clearInterval(timer) }
  }, [user])

  async function openPost(id) {
    try {
      const post = await api(`/posts/${id}`)
      setSelectedPost(post)
      setView('home')
    } catch (error) {
      setNotice(error.message)
    }
  }

  function goHome() {
    setView('home')
    setSelectedPost(null)
    loadPosts()
  }

  function goModeration(postId = null) {
    setSelectedPost(null)
    setModerationPostId(postId)
    setNotificationsOpen(false)
    setView('moderation')
  }

  function logout() {
    clearCredentials()
    setUser(null)
    setSelectedPost(null)
    setView('home')
    setUnreadCount(0)
    setNotificationsOpen(false)
    setNotice('Đã đăng xuất')
    loadPosts()
  }

  async function refreshSelected() {
    if (selectedPost) await openPost(selectedPost.id)
    await loadPosts()
    if (user) await refreshUnread()
  }

  const isAdmin = user?.role === 'ADMIN'

  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={goHome}>
          <span className="brand-mark">C</span>
          <span>Campus Hub</span>
        </button>
        <nav>
          {user ? (
            <>
              <span className="user-name">Xin chào, {user.displayName}</span>
              {isAdmin && (view === 'moderation'
                ? <button className="button ghost" onClick={goHome}>Trang chủ</button>
                : <button className="button ghost" onClick={() => goModeration()}>Kiểm duyệt</button>)}
              <div className="notif-wrap">
                <button className="button secondary" aria-haspopup="true" aria-expanded={notificationsOpen}
                  onClick={() => setNotificationsOpen((open) => !open)}>
                  Thông báo ({unreadCount})
                </button>
                {notificationsOpen && <NotificationsMenu onClose={() => setNotificationsOpen(false)}
                  onOpenPost={openPost} onChanged={refreshUnread} />}
              </div>
              <button className="button ghost" onClick={() => setDialog('post')}>Đăng bài</button>
              <button className="button secondary" onClick={logout}>Đăng xuất</button>
            </>
          ) : (
            <button className="button primary" onClick={() => setDialog('login')}>Đăng nhập</button>
          )}
        </nav>
      </header>

      <main>
        {notice && <div className="notice" onClick={() => setNotice('')}>{notice}</div>}
        {view === 'moderation' ? (
          <ModerationPage key={moderationPostId ?? 'none'} user={user} initialPostId={moderationPostId}
            setNotice={setNotice} />
        ) : selectedPost ? (
          <PostDetail post={selectedPost} user={user} onBack={() => setSelectedPost(null)}
            onChanged={refreshSelected} onDeleted={() => { setSelectedPost(null); loadPosts() }}
            onModerate={goModeration} setNotice={setNotice} />
        ) : (
          <>
            <section className="hero">
              <div>
                <p className="eyebrow">Không gian chung của sinh viên</p>
                <h1>Chia sẻ điều đang diễn ra trong trường.</h1>
                <p>Tìm sự kiện, hỏi đáp, trao đổi đồ dùng và cùng giải quyết những vấn đề gần gũi.</p>
              </div>
              <div className="hero-stat"><strong>{posts.length}</strong><span>bài đăng đang hiển thị</span></div>
            </section>

            <section className="filters" aria-label="Tìm kiếm bài đăng">
              <input value={filters.keyword} placeholder="Tìm theo tiêu đề hoặc nội dung..."
                onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} />
              <select value={filters.categoryId}
                onChange={(event) => setFilters({ ...filters, categoryId: event.target.value })}>
                <option value="">Tất cả chuyên mục</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
            </section>

            <section className="post-grid">
              {posts.map((post) => <PostCard key={post.id} post={post} user={user} onOpen={openPost} />)}
              {!posts.length && <div className="empty">Chưa có bài đăng phù hợp.</div>}
            </section>
          </>
        )}
      </main>

      <footer>Campus Hub · Dự án nền cho Mini Project Session 13</footer>

      {dialog === 'login' && <AuthDialog onClose={() => setDialog(null)} onLogin={(loggedInUser) => {
        setUser(loggedInUser); setDialog(null); setNotice('Đăng nhập thành công'); loadPosts()
      }} />}
      {dialog === 'post' && <PostDialog categories={categories} onClose={() => setDialog(null)}
        onSaved={() => { setDialog(null); setNotice('Đã đăng bài'); loadPosts() }} />}
    </div>
  )
}

export default App
