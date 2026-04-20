import { useCallback, useRef, useState } from 'react'
import './App.css'

/**
 * 解析 text/event-stream：识别 {@code event:} 与 {@code data:}；无 {@code event:} 时视为 {@code message}。
 * @param {(data: string) => void} onDataLine
 * @param {(line: string) => void} [onComment]
 * @param {(eventName: string, data: string) => void} [onNamedData] — 如 {@code plan_parse}
 */
async function readSseStream(response, onDataLine, onComment, onNamedData) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  let pendingEvent = ''
  const dispatchData = (payload) => {
    const name = pendingEvent || 'message'
    if (name === 'message') {
      onDataLine(payload)
    } else if (onNamedData) {
      onNamedData(name, payload)
    }
    pendingEvent = ''
  }
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const parts = buf.split('\n')
    buf = parts.pop() ?? ''
    for (const raw of parts) {
      const line = raw.replace(/\r$/, '')
      if (line.trim() === '') {
        pendingEvent = ''
        continue
      }
      if (line.startsWith('event:')) {
        pendingEvent = line.slice(6).trimStart()
      } else if (line.startsWith('data:')) {
        dispatchData(line.slice(5).trimStart())
      } else if (line.startsWith(':')) {
        onComment?.(line)
      }
    }
  }
  if (buf.trim()) {
    const line = buf.replace(/\r$/, '')
    if (line.startsWith('data:')) dispatchData(line.slice(5).trimStart())
  }
}

export default function App() {
  const [username, setUsername] = useState('demo')
  const [password, setPassword] = useState('demo123')
  const [token, setToken] = useState(() => localStorage.getItem('token') ?? '')

  const [conversationId, setConversationId] = useState(
    () => localStorage.getItem('conversationId') ?? 'demo-conv',
  )
  const [query, setQuery] = useState('给我一份成都两天一夜行程')
  const [output, setOutput] = useState('')
  const [planParseInfo, setPlanParseInfo] = useState('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [uploadHint, setUploadHint] = useState('')
  const [uploading, setUploading] = useState(false)

  const abortRef = useRef(null)
  const fileInputRef = useRef(null)

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
      try {
        const cres = await fetch('/api/travel/conversations', {
          method: 'POST',
          headers: { Authorization: `Bearer ${t}` },
        })
        const cj = await cres.json().catch(() => ({}))
        if (cres.ok && cj.conversationId) {
          setConversationId(cj.conversationId)
          localStorage.setItem('conversationId', cj.conversationId)
        }
      } catch {
        /* 忽略：可继续用手动填写的 conversationId */
      }
      setStatus('已登录（token 已保存到浏览器 localStorage）')
    } catch (e) {
      setStatus('')
      setError(e.message ?? String(e))
    }
  }, [username, password])

  const logout = useCallback(() => {
      setToken('')
      localStorage.removeItem('token')
      localStorage.removeItem('conversationId')
      setStatus('已退出')
  }, [])

  const newConversation = useCallback(async () => {
    setError('')
    if (!token) {
      setError('请先登录')
      return
    }
    setStatus('创建会话…')
    try {
      const res = await fetch('/api/travel/conversations', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) {
        setError(data.message ?? `创建失败 HTTP ${res.status}`)
        setStatus('')
        return
      }
      if (!data.conversationId) {
        setError('响应无 conversationId')
        setStatus('')
        return
      }
      setConversationId(data.conversationId)
      localStorage.setItem('conversationId', data.conversationId)
      setStatus('已新建会话')
    } catch (e) {
      setError(e.message ?? String(e))
      setStatus('')
    }
  }, [token])

  /** 与后端 KnowledgeController 一致：POST /knowledge/upload，表单字段 file，仅 .txt；响应为 JSON（message / error） */
  const uploadKnowledge = useCallback(async () => {
    setError('')
    setUploadHint('')
    if (!token) {
      setError('请先登录再上传')
      return
    }
    const input = fileInputRef.current
    const file = input?.files?.[0]
    if (!file) {
      setError('请选择 .txt 文件')
      return
    }
    if (!file.name.toLowerCase().endsWith('.txt')) {
      setError('仅支持 .txt')
      return
    }
    setUploading(true)
    setStatus('上传中…')
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch('/api/knowledge/upload', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      })
      const raw = await res.text()
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        body = { message: raw }
      }
      if (!res.ok) {
        const msg = body?.message || body?.error || raw
        setError(`上传失败 ${res.status}：${msg}`)
        setStatus('')
        return
      }
      setUploadHint(body?.message || raw)
      setStatus('上传完成')
      if (input) input.value = ''
    } catch (e) {
      setError(e.message ?? String(e))
      setStatus('')
    } finally {
      setUploading(false)
    }
  }, [token])

  const stopStream = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setStatus((s) => (s.includes('流式') ? '已停止' : s))
  }, [])

  const startChat = useCallback(async () => {
    setError('')
    setOutput('')
    setPlanParseInfo('')
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
        (eventName, data) => {
          if (eventName === 'plan_parse') {
            try {
              const j = JSON.parse(data)
              setPlanParseInfo(
                `${j.plan_parse_outcome ?? '?'} · attempts ${j.plan_parse_attempts ?? '?'} · ${j.plan_parse_resolved ?? ''} · draft ${j.plan_draft_source ?? ''}`,
              )
            } catch {
              setPlanParseInfo(data)
            }
          }
        },
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
          <code>npm run dev</code>。检索按当前登录用户隔离：需先<strong>上传 .txt 知识</strong>再提问，否则会显示「未命中知识库」。
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
        <h2>上传知识（.txt）</h2>
        <p className="hint small" style={{ marginTop: 0 }}>
          后端会分块并向量化入库；仅当 metadata 含当前用户的 <code>user_id</code> 时才会被检索命中。若库里是旧数据（空
          metadata），请重新上传一次。
        </p>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="block" style={{ flex: 1, marginBottom: 0 }}>
            选择文件
            <input ref={fileInputRef} type="file" accept=".txt,text/plain" />
          </label>
        </div>
        <div className="actions">
          <button type="button" onClick={uploadKnowledge} disabled={!token || uploading}>
            {uploading ? '上传中…' : '上传到知识库'}
          </button>
        </div>
        {uploadHint ? <p className="status">{uploadHint}</p> : null}
      </section>

      <section className="card">
        <h2>SSE 对话</h2>
        <p className="hint small" style={{ marginTop: 0 }}>
          登录成功后会向服务端申请 <code>conversationId</code>；生产若开启{' '}
          <code>app.conversation.require-registration=true</code>，须使用已登记的 ID。
        </p>
        <div className="row">
          <label>
            conversationId
            <input
              value={conversationId}
              onChange={(e) => setConversationId(e.target.value)}
            />
          </label>
        </div>
        <div className="actions">
          <button type="button" className="secondary" onClick={newConversation} disabled={!token}>
            新建会话（POST /travel/conversations）
          </button>
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

      {planParseInfo ? (
        <p className="mono small status" style={{ margin: '0 1rem' }} title="SSE event: plan_parse">
          plan_parse: {planParseInfo}
        </p>
      ) : null}
      {status ? <p className="status">{status}</p> : null}
      {error ? <p className="error">{error}</p> : null}

      <section className="card output">
        <h2>输出</h2>
        <pre className="stream">{output || '（等待流式文本…）'}</pre>
      </section>
    </div>
  )
}
