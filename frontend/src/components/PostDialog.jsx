import { useState } from 'react'
import { api } from '../api.js'
import Dialog from './Dialog.jsx'

const emptyPost = { title: '', content: '', categoryId: '' }

function PostDialog({ categories, onClose, onSaved }) {
  const [form, setForm] = useState(emptyPost)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    try {
      await api('/posts', { method: 'POST', body: JSON.stringify({ ...form, categoryId: Number(form.categoryId) }) })
      onSaved()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  return <Dialog title="Tạo bài đăng" onClose={onClose}>
    <form className="form" onSubmit={submit}>
      {error && <p className="form-error">{error}</p>}
      <label>Tiêu đề<input required maxLength="160" value={form.title}
        onChange={(event) => setForm({ ...form, title: event.target.value })} /></label>
      <label>Chuyên mục<select required value={form.categoryId}
        onChange={(event) => setForm({ ...form, categoryId: event.target.value })}>
        <option value="">Chọn chuyên mục</option>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </select></label>
      <label>Nội dung<textarea required maxLength="10000" rows="7" value={form.content}
        onChange={(event) => setForm({ ...form, content: event.target.value })} /></label>
      <button className="button primary">Đăng bài</button>
    </form>
  </Dialog>
}

export default PostDialog
