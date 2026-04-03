import { useCallback, useRef, useState } from 'react'
import './App.css'

/** 解析 text/event-stream：按行处理 data: 与注释行 */
async function readSseStream(response, onDataLine, onComment) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const parts = buf.split('\n')
    buf = parts.pop() ?? ''
    for (const raw of parts) {
      const line = raw.replace(/\r$/, '')
      if (line.startsWith('data:')) {
        onDataLine(line.slice(5).trimStart())
      } else if (line.startsWith(':')) {
        onComment?.(line)
      }
    }
  }
  if (buf.trim()) {
    const line = buf.replace(/\r$/, '')
    if (line.startsWith('data:')) onDataLine(line.slice(5).trimStart())
  }
}

export default function App() {
  const [username, setUsername] = useState('demo')
  const [password, setPassword] = useState('demo123')
  const [token, setToken] = useState(() => localStorage.getItem('token') ?? '')

  const [conversationId, setConversationId] = useState('demo-conv')
  const [query, setQuery] = useState('给我一份成都两天一夜行程')
  const [output, setOutput] = useState('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')

  const abortRef = useRef(null)

  const login = useCallback(async () => {
    setError('')
    setStatus('登录中…')
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) {
        setStatus('')
        setError(data.message ?? `登录失败 HTTP ${res.status}`)
        return
      }
      const t = data.token
      if (!t) {
        setError('响应无 token')
        setStatus('')
        return
      }
      setToken(t)
      localStorage.setItem('token', t)
      setStatus('已登录（token 已保存到浏览器 localStorage）')
    } catch (e) {
      setStatus('')
      setError(e.message ?? String(e))
    }
  }, [username, password])

  const logout = useCallback(() => {
      setToken('')
      localStorage.removeItem('token')
      setStatus('已退出')
  }, [])

  const stopStream = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setStatus((s) => (s.includes('流式') ? '已停止' : s))
  }, [])

  const startChat = useCallback(async () => {
    setError('')
    setOutput('')
    if (!token) {
      setError('请先登录')
      return
    }
    stopStream()
    const ac = new AbortController()
    abortRef.current = ac

    const url =
      `/api/travel/chat/${encodeURIComponent(conversationId)}` +
      `?query=${encodeURIComponent(query)}`

    setStatus('连接 SSE…')
    try {
      const res = await fetch(url, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        signal: ac.signal,
      })
      if (!res.ok) {
        const txt = await res.text().catch(() => '')
        setError(`请求失败 ${res.status} ${txt}`)
        setStatus('')
        return
      }
      setStatus('流式输出中…（可随时停止）')
      await readSseStream(
        res,
        (data) => setOutput((prev) => prev + data),
        () => {},
      )
      setStatus('流结束')
    } catch (e) {
      if (e.name === 'AbortError') {
        setStatus('已取消')
      } else {
        setError(e.message ?? String(e))
        setStatus('')
      }
    } finally {
      abortRef.current = null
    }
  }, [conversationId, query, token, stopStream])

  return (
    <div className="app">
      <header className="header">
        <h1>Travel AI Planner</h1>
        <p className="hint">
          本页通过 Vite 代理访问后端{' '}
          <code>/api → http://127.0.0.1:8081</code>。请先启动 Spring Boot（默认 8081），再{' '}
          <code>npm run dev</code>。
        </p>
      </header>

      <section className="card">
        <h2>登录</h2>
        <div className="row">
          <label>
            用户名
            <input value={username} onChange={(e) => setUsername(e.target.value)} />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
        </div>
        <div className="actions">
          <button type="button" onClick={login}>
            登录并保存 Token
          </button>
          <button type="button" className="secondary" onClick={logout}>
            退出
          </button>
        </div>
        {token ? (
          <p className="mono small">Token 已就绪（前 24 字符）：{token.slice(0, 24)}…</p>
        ) : null}
      </section>

      <section className="card">
        <h2>SSE 对话</h2>
        <div className="row">
          <label>
            conversationId
            <input
              value={conversationId}
              onChange={(e) => setConversationId(e.target.value)}
            />
          </label>
        </div>
        <label className="block">
          问题
          <textarea
            rows={3}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <div className="actions">
          <button type="button" onClick={startChat} disabled={!token}>
            开始流式输出
          </button>
          <button type="button" className="secondary" onClick={stopStream}>
            停止
          </button>
        </div>
      </section>

      {status ? <p className="status">{status}</p> : null}
      {error ? <p className="error">{error}</p> : null}

      <section className="card output">
        <h2>输出</h2>
        <pre className="stream">{output || '（等待流式文本…）'}</pre>
      </section>
    </div>
  )
}
