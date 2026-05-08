import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  confirmPendingProfile,
  createConversation,
  deleteKnowledge,
  discardPendingProfile,
  extractProfileSuggestion,
  getPendingProfile,
  getProfile,
  listKnowledge,
  listFeedback,
  login,
  resetProfile,
  streamChat,
  submitFeedback,
  uploadKnowledge,
} from './api'
import AgentTracePanel from './components/AgentTracePanel'
import ChatWindow from './components/ChatWindow'
import FeedbackPanel from './components/FeedbackPanel'
import KnowledgeList from './components/KnowledgeList'
import KnowledgeUpload from './components/KnowledgeUpload'
import LoginPanel from './components/LoginPanel'
import ProfilePanel from './components/ProfilePanel'
import SourcesPanel from './components/SourcesPanel'
import './App.css'

const DEFAULT_QUERY = '请基于我上传的资料，给我一份成都两天一夜旅行规划，包含行程、交通建议和注意事项。'

function safeJson(data) {
  try {
    return JSON.parse(data)
  } catch {
    return null
  }
}

function normalizeEventPayload(event, data) {
  const parsed = safeJson(data)
  return {
    event,
    raw: data,
    data: parsed,
    ...(parsed && typeof parsed === 'object' ? parsed : {}),
  }
}

function isCitationText(text) {
  return text.includes('【引用片段】') || text.includes('引用片段') || text.includes('銆愬紩鐢ㄧ墖娈')
}

function parseSourcesFromText(text) {
  if (!text) return []
  const body = text
    .replace(/[-─━]{4,}/g, '')
    .replace(/^.*(?:引用片段|銆愬紩鐢ㄧ墖娈).*$/m, '')
    .trim()
  if (!body || body.includes('未命中知识库') || body.includes('鏈懡涓')) return []

  const blocks = body.split(/\n\s*\n/).map((item) => item.trim()).filter(Boolean)
  return blocks.map((block, index) => {
    const firstLine = block.split('\n')[0] || ''
    const idMatch = firstLine.match(/id=([^\s]+)/)
    const sourceMatch = firstLine.match(/(?:来源|source|source_name|鏉ユ簮)=([^\s]+)/i)
    const scoreMatch = firstLine.match(/score=([^\s]+)/i)
    const snippet = block
      .split('\n')
      .slice(firstLine.includes('id=') ? 1 : 0)
      .join('\n')
      .trim()
    return {
      title: sourceMatch?.[1] || idMatch?.[1] || `引用 ${index + 1}`,
      snippet: snippet || block,
      score: scoreMatch?.[1] ? `score ${scoreMatch[1]}` : '',
      hit: idMatch?.[1] ? `id=${idMatch[1]}` : '',
    }
  })
}

function parseSourcesFromStructured(payload) {
  if (!payload || typeof payload !== 'object') return []
  const list = payload.sources || payload.citations || payload.citation || payload.retrieval_hits || []
  const items = Array.isArray(list) ? list : [list]
  return items
    .filter(Boolean)
    .map((item, index) => ({
      title: item.title || item.file_name || item.fileName || item.source || item.source_name || item.id || `来源 ${index + 1}`,
      snippet: item.snippet || item.text || item.content || item.preview || '',
      score: item.score != null ? `score ${item.score}` : '',
      hit: item.hit || item.rank || item.metadata ? JSON.stringify(item.metadata || item.hit || item.rank) : '',
    }))
}

export default function App() {
  const [username, setUsername] = useState('demo')
  const [password, setPassword] = useState('demo123')
  const [token, setToken] = useState(() => localStorage.getItem('token') || '')
  const [conversationId, setConversationId] = useState(() => localStorage.getItem('conversationId') || '')
  const [query, setQuery] = useState(DEFAULT_QUERY)

  const [loginStatus, setLoginStatus] = useState('')
  const [globalStatus, setGlobalStatus] = useState('')
  const [error, setError] = useState('')
  const [uploadResult, setUploadResult] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [knowledgeItems, setKnowledgeItems] = useState([])
  const [knowledgeLoading, setKnowledgeLoading] = useState(false)
  const [knowledgeStatus, setKnowledgeStatus] = useState('')

  const [messages, setMessages] = useState([])
  const [streaming, setStreaming] = useState(false)
  const [loading, setLoading] = useState(false)
  const [sources, setSources] = useState([])
  const [agentEvents, setAgentEvents] = useState([])
  const [planParse, setPlanParse] = useState(null)
  const [requestId, setRequestId] = useState('')
  const [parseErrors, setParseErrors] = useState([])

  const [profile, setProfile] = useState(null)
  const [pendingProfile, setPendingProfile] = useState(null)
  const [profileStatus, setProfileStatus] = useState('')
  const [feedbackItems, setFeedbackItems] = useState([])
  const abortRef = useRef(null)

  const loggedIn = Boolean(token)
  const lastAssistant = useMemo(
    () => [...messages].reverse().find((message) => message.role === 'assistant' && message.content),
    [messages],
  )

  const showError = useCallback((err) => {
    setError(err?.message || String(err))
  }, [])

  const refreshProfile = useCallback(async () => {
    if (!token) return
    try {
      const current = await getProfile(token)
      setProfile(current)
      setProfileStatus('画像已刷新。')
      if (conversationId) {
        try {
          const pending = await getPendingProfile(token, conversationId)
          setPendingProfile(pending)
        } catch (err) {
          if (err.status !== 404) setProfileStatus(err.message)
          setPendingProfile(null)
        }
      }
    } catch (err) {
      setProfileStatus(err.message)
    }
  }, [conversationId, token])

  const refreshFeedback = useCallback(async () => {
    if (!token) return
    try {
      const data = await listFeedback(token, 5)
      setFeedbackItems(data.items || [])
    } catch {
      setFeedbackItems([])
    }
  }, [token])

  const refreshKnowledge = useCallback(async () => {
    if (!token) return
    setKnowledgeLoading(true)
    try {
      const data = await listKnowledge(token)
      setKnowledgeItems(data.items || [])
      setKnowledgeStatus('知识列表已刷新。')
    } catch (err) {
      setKnowledgeItems([])
      setKnowledgeStatus(err.message)
    } finally {
      setKnowledgeLoading(false)
    }
  }, [token])

  useEffect(() => {
    if (token) {
      refreshProfile()
      refreshFeedback()
      refreshKnowledge()
    }
  }, [refreshFeedback, refreshKnowledge, refreshProfile, token])

  const handleLogin = useCallback(async () => {
    setError('')
    setLoginStatus('登录中...')
    try {
      const auth = await login(username, password)
      if (!auth.token) throw new Error('登录响应中没有 token。')
      setToken(auth.token)
      localStorage.setItem('token', auth.token)
      const conversation = await createConversation(auth.token)
      setConversationId(conversation.conversationId)
      localStorage.setItem('conversationId', conversation.conversationId)
      setLoginStatus('登录成功，已创建新会话。')
    } catch (err) {
      setLoginStatus('')
      showError(err)
    }
  }, [password, showError, username])

  const handleLogout = useCallback(() => {
    abortRef.current?.abort()
    setToken('')
    setConversationId('')
    setMessages([])
    setSources([])
    setAgentEvents([])
    setPlanParse(null)
    setRequestId('')
    setKnowledgeItems([])
    setKnowledgeStatus('')
    localStorage.removeItem('token')
    localStorage.removeItem('conversationId')
    setLoginStatus('已退出。')
  }, [])

  const handleNewConversation = useCallback(async () => {
    setError('')
    try {
      const data = await createConversation(token)
      setConversationId(data.conversationId)
      localStorage.setItem('conversationId', data.conversationId)
      setMessages([])
      setSources([])
      setAgentEvents([])
      setPlanParse(null)
      setRequestId('')
      setGlobalStatus('已创建新会话。')
    } catch (err) {
      showError(err)
    }
  }, [showError, token])

  const handleUpload = useCallback(async (file) => {
    setError('')
    if (!file.name.toLowerCase().endsWith('.txt')) {
      setError('当前上传接口仅支持 .txt 文件。')
      return
    }
    setUploading(true)
    try {
      const result = await uploadKnowledge(token, file)
      setUploadResult(result)
      await refreshKnowledge()
      setGlobalStatus('知识上传完成。')
    } catch (err) {
      const code = err.body?.error
      if (err.status === 409 && code === 'DUPLICATE_KNOWLEDGE') {
        setKnowledgeStatus(`该文件内容已上传：${err.body?.fileName || err.body?.fileId || ''}`)
        await refreshKnowledge()
      } else {
        showError(err)
      }
    } finally {
      setUploading(false)
    }
  }, [refreshKnowledge, showError, token])

  const handleDeleteKnowledge = useCallback(async (fileId) => {
    if (!fileId) return
    const confirmed = window.confirm('删除只影响后续 RAG 检索，不删除历史对话和已生成回答。确认删除？')
    if (!confirmed) return

    setError('')
    setKnowledgeStatus('正在删除知识文件...')
    try {
      await deleteKnowledge(token, fileId)
      setKnowledgeStatus('知识文件已删除。')
      setGlobalStatus('知识文件已删除。')
      await refreshKnowledge()
    } catch (err) {
      const code = err.body?.error
      if (err.status === 404 && code === 'KNOWLEDGE_FILE_NOT_FOUND') {
        setKnowledgeStatus('知识文件不存在或已被删除，已刷新列表。')
        await refreshKnowledge()
      } else if (err.status === 409 && code === 'LEGACY_KNOWLEDGE_NOT_DELETABLE') {
        setKnowledgeStatus('旧知识数据缺少 file_id，当前仅支持删除新上传的知识文件。')
      } else {
        setKnowledgeStatus(err.message)
      }
    }
  }, [refreshKnowledge, token])

  const appendAssistant = useCallback((assistantId, chunk) => {
    setMessages((current) => current.map((message) => (
      message.id === assistantId ? { ...message, content: message.content + chunk } : message
    )))
  }, [])

  const handleSseEvent = useCallback((assistantId, payload) => {
    const event = payload.event || 'message'
    const data = payload.data
    const parsed = safeJson(data)

    if (event === 'message') {
      if (isCitationText(data)) {
        const parsedSources = parseSourcesFromText(data)
        if (parsedSources.length) setSources(parsedSources)
      } else {
        appendAssistant(assistantId, data)
      }
      return
    }

    const normalized = normalizeEventPayload(event, data)
    if (parsed?.request_id || parsed?.requestId) setRequestId(parsed.request_id || parsed.requestId)

    if (event === 'plan_parse') {
      setPlanParse(parsed || { raw: data })
      return
    }
    if (event === 'stage' || event === 'policy') {
      setAgentEvents((current) => [...current, normalized])
      return
    }
    if (event === 'source' || event === 'sources' || event === 'citation' || event === 'citations') {
      const structuredSources = parseSourcesFromStructured(parsed)
      if (structuredSources.length) setSources(structuredSources)
      return
    }
    if (event === 'done') {
      setStreaming(false)
      setLoading(false)
      setGlobalStatus('本轮回答完成。')
      return
    }
    if (event === 'error') {
      setAgentEvents((current) => [...current, normalized])
      setError(parsed?.message || parsed?.error_code || data)
    }
  }, [appendAssistant])

  const handleSend = useCallback(async () => {
    setError('')
    if (!token) {
      setError('请先登录。')
      return
    }
    if (!conversationId) {
      setError('请先创建 conversationId。')
      return
    }

    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const userId = crypto.randomUUID()
    const assistantId = crypto.randomUUID()

    setMessages((current) => [
      ...current,
      { id: userId, role: 'user', content: query.trim() },
      { id: assistantId, role: 'assistant', content: '' },
    ])
    setSources([])
    setAgentEvents([])
    setPlanParse(null)
    setParseErrors([])
    setRequestId('')
    setStreaming(true)
    setLoading(true)
    setGlobalStatus('正在连接 SSE...')

    try {
      await streamChat(token, conversationId, query.trim(), {
        onEvent: (eventPayload) => handleSseEvent(assistantId, eventPayload),
        onComment: () => {},
        onParseError: (line) => setParseErrors((current) => [...current, line]),
      }, controller.signal)
      setStreaming(false)
      setLoading(false)
      setGlobalStatus('流式输出结束。')
    } catch (err) {
      if (err.name === 'AbortError') {
        setGlobalStatus('已停止本轮输出。')
      } else {
        showError(err)
      }
      setStreaming(false)
      setLoading(false)
    } finally {
      abortRef.current = null
    }
  }, [conversationId, handleSseEvent, query, showError, token])

  const handleStop = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setStreaming(false)
    setLoading(false)
  }, [])

  const handleExtractProfile = useCallback(async () => {
    setProfileStatus('正在抽取画像建议...')
    try {
      const result = await extractProfileSuggestion(token, conversationId, true)
      if (result.pendingSaved || result.pending_saved) {
        const pending = await getPendingProfile(token, conversationId)
        setPendingProfile(pending)
        setProfileStatus('已生成待确认画像。')
      } else {
        setPendingProfile({
          suggestedPatch: result.suggestedPatch || result.suggested_patch,
          mergedPreview: result.mergedPreview || result.merged_preview,
        })
        setProfileStatus('已生成画像建议。若后端配置 require-confirm=false，可能已直接写入。')
      }
    } catch (err) {
      setProfileStatus(err.message)
    }
  }, [conversationId, token])

  const handleConfirmProfile = useCallback(async () => {
    try {
      const result = await confirmPendingProfile(token, conversationId)
      setProfile(result)
      setPendingProfile(null)
      setProfileStatus('待确认画像已写入。')
    } catch (err) {
      setProfileStatus(err.message)
    }
  }, [conversationId, token])

  const handleDiscardProfile = useCallback(async () => {
    try {
      await discardPendingProfile(token, conversationId)
      setPendingProfile(null)
      setProfileStatus('已忽略待确认画像。')
    } catch (err) {
      setProfileStatus(err.message)
    }
  }, [conversationId, token])

  const handleResetProfile = useCallback(async () => {
    try {
      await resetProfile(token, conversationId, false)
      setProfile({ schemaVersion: 1, profile: {} })
      setPendingProfile(null)
      setProfileStatus('画像已重置。')
    } catch (err) {
      setProfileStatus(err.message)
    }
  }, [conversationId, token])

  const handleFeedback = useCallback(async (payload) => {
    setError('')
    try {
      await submitFeedback(token, payload)
      setGlobalStatus('反馈已提交。')
      await refreshFeedback()
    } catch (err) {
      showError(err)
    }
  }, [refreshFeedback, showError, token])

  return (
    <main className="app-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Travel AI Planner</p>
          <h1>AI 旅行规划助手</h1>
          <p>展示后端已具备的登录鉴权、知识上传、RAG 流式聊天、Agent 阶段事件、用户画像和反馈闭环。</p>
        </div>
        <div className="hero-status">
          <span>{globalStatus || '等待操作'}</span>
          {error ? <strong>{error}</strong> : null}
        </div>
      </header>

      <div className="layout">
        <div className="main-column">
          <LoginPanel
            username={username}
            password={password}
            token={token}
            status={loginStatus}
            onUsernameChange={setUsername}
            onPasswordChange={setPassword}
            onLogin={handleLogin}
            onLogout={handleLogout}
          />
          <KnowledgeUpload
            disabled={!loggedIn}
            uploading={uploading}
            result={uploadResult}
            onUpload={handleUpload}
          />
          <KnowledgeList
            disabled={!loggedIn}
            loading={knowledgeLoading}
            items={knowledgeItems}
            status={knowledgeStatus}
            onRefresh={refreshKnowledge}
            onDelete={handleDeleteKnowledge}
          />
          <ChatWindow
            conversationId={conversationId}
            query={query}
            messages={messages}
            streaming={streaming}
            loading={loading}
            onConversationChange={(value) => {
              setConversationId(value)
              localStorage.setItem('conversationId', value)
            }}
            onQueryChange={setQuery}
            onNewConversation={handleNewConversation}
            onSend={handleSend}
            onStop={handleStop}
          />
        </div>
        <aside className="side-column">
          <AgentTracePanel
            events={agentEvents}
            planParse={planParse}
            requestId={requestId}
            parseErrors={parseErrors}
          />
          <SourcesPanel sources={sources} />
          <ProfilePanel
            profile={profile}
            pending={pendingProfile}
            status={profileStatus}
            disabled={!loggedIn || !conversationId}
            onRefresh={refreshProfile}
            onExtract={handleExtractProfile}
            onConfirm={handleConfirmProfile}
            onDiscard={handleDiscardProfile}
            onReset={handleResetProfile}
          />
          <FeedbackPanel
            disabled={!loggedIn || !lastAssistant || streaming}
            conversationId={conversationId}
            requestId={requestId}
            recentItems={feedbackItems}
            onSubmit={handleFeedback}
          />
        </aside>
      </div>
    </main>
  )
}
