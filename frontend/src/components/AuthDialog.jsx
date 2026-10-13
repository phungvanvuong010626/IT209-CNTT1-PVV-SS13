import { useState } from 'react'
import { api, clearCredentials, setCredentials } from '../api.js'
import Dialog from './Dialog.jsx'

function AuthDialog({ onClose, onLogin }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ username: '', displayName: '', password: '' })
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    try {
      if (mode === 'register') {
        await api('/auth/register', { method: 'POST', body: JSON.stringify(form) })
      }
      setCredentials(form.username, form.password)
      onLogin(await api('/auth/me'))
    } catch (requestError) {
      clearCredentials()
      setError(requestError.message)
    }
  }

  return <Dialog title={mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'} onClose={onClose}>
    <form className="form" onSubmit={submit}>
      {error && <p className="form-error">{error}</p>}
      <label>Tên đăng nhập<input required minLength="3" value={form.username}
        onChange={(event) => setForm({ ...form, username: event.target.value })} /></label>
      {mode === 'register' && <label>Tên hiển thị<input required value={form.displayName}
        onChange={(event) => setForm({ ...form, displayName: event.target.value })} /></label>}
      <label>Mật khẩu<input required minLength="6" type="password" value={form.password}
        onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>
      <button className="button primary">{mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}</button>
      <button type="button" className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>
        {mode === 'login' ? 'Chưa có tài khoản? Đăng ký' : 'Đã có tài khoản? Đăng nhập'}
      </button>
    </form>
  </Dialog>
}

export default AuthDialog
