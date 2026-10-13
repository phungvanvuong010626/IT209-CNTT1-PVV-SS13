import { useEffect, useState } from 'react'
import { api } from './api.js'

// Tải dữ liệu GET theo đường dẫn. Truyền path = null để không tải.
// Đổi path hoặc reloadToken sẽ tải lại; data giữ giá trị cũ trong lúc tải lại.
export function useApi(path, reloadToken = 0) {
  const key = path ? `${path}#${reloadToken}` : null
  const [state, setState] = useState({ key: null, data: null, error: '' })

  useEffect(() => {
    if (!path) return undefined
    let active = true
    const currentKey = `${path}#${reloadToken}`
    api(path)
      .then((data) => { if (active) setState({ key: currentKey, data, error: '' }) })
      .catch((error) => { if (active) setState({ key: currentKey, data: null, error: error.message }) })
    return () => { active = false }
  }, [path, reloadToken])

  const settled = key !== null && state.key === key
  return {
    data: state.data,
    error: settled ? state.error : '',
    loading: key !== null && !settled,
  }
}
