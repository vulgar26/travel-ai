const API_BASE = '/api'

async function parseJsonSafe(response) {
  const text = await response.text()
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}

function authHeaders(token, extra = {}) {
  return token ? { ...extra, Authorization: `Bearer ${token}` } : extra
}

export function getErrorMessage(status, body) {
  const detail = body?.message || body?.error || ''
  if (status === 401) return detail || '未登录或 Token 已失效，请重新登录。'
  if (status === 400) return detail || '请求参数不正确，请检查输入。'
  if (status === 403) return detail || '当前会话未登记或无权限访问。'
  if (status === 429) return detail || '请求过于频繁，请稍后再试。'
  return detail || `请求失败，HTTP ${status}`
}

async function requestJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options)
  const body = await parseJsonSafe(response)
  if (!response.ok) {
    const error = new Error(getErrorMessage(response.status, body))
    error.status = response.status
    error.body = body
    throw error
  }
  return body
}

export async function login(username, password) {
  return requestJson('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export async function createConversation(token) {
  return requestJson('/travel/conversations', {
    method: 'POST',
    headers: authHeaders(token),
  })
}

export async function uploadKnowledge(token, file) {
  const form = new FormData()
  form.append('file', file)
  return requestJson('/knowledge/upload', {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  })
}

export async function getProfile(token) {
  return requestJson('/travel/profile', {
    headers: authHeaders(token),
  })
}

export async function resetProfile(token, conversationId, clearChatMemory = false) {
  const params = new URLSearchParams()
  params.set('clearChatMemory', String(clearChatMemory))
  if (conversationId) params.set('conversationId', conversationId)
  await fetch(`${API_BASE}/travel/profile?${params.toString()}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  }).then(async (response) => {
    if (!response.ok) {
      const body = await parseJsonSafe(response)
      throw new Error(getErrorMessage(response.status, body))
    }
  })
}

export async function extractProfileSuggestion(token, conversationId, saveAsPending = true) {
  return requestJson('/travel/profile/extract-suggestion', {
    method: 'POST',
    headers: authHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ conversationId, saveAsPending }),
  })
}

export async function getPendingProfile(token, conversationId) {
  return requestJson(`/travel/profile/pending-extraction?conversationId=${encodeURIComponent(conversationId)}`, {
    headers: authHeaders(token),
  })
}

export async function confirmPendingProfile(token, conversationId) {
  return requestJson('/travel/profile/confirm-extraction', {
    method: 'POST',
    headers: authHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ conversationId }),
  })
}

export async function discardPendingProfile(token, conversationId) {
  await fetch(`${API_BASE}/travel/profile/pending-extraction?conversationId=${encodeURIComponent(conversationId)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  }).then(async (response) => {
    if (!response.ok) {
      const body = await parseJsonSafe(response)
      throw new Error(getErrorMessage(response.status, body))
    }
  })
}

export async function submitFeedback(token, payload) {
  return requestJson('/travel/feedback', {
    method: 'POST',
    headers: authHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(payload),
  })
}

export async function listFeedback(token, limit = 5) {
  return requestJson(`/travel/feedback?limit=${limit}&offset=0`, {
    headers: authHeaders(token),
  })
}

export async function streamChat(token, conversationId, query, handlers, signal) {
  const response = await fetch(`${API_BASE}/travel/chat/${encodeURIComponent(conversationId)}`, {
    method: 'POST',
    headers: authHeaders(token, {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ query }),
    signal,
  })

  if (!response.ok) {
    const body = await parseJsonSafe(response)
    const error = new Error(getErrorMessage(response.status, body))
    error.status = response.status
    error.body = body
    throw error
  }
  if (!response.body) {
    throw new Error('浏览器没有返回可读取的 SSE 响应体。')
  }

  await readSseStream(response, handlers)
}

async function readSseStream(response, handlers) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let dataLines = []

  const dispatch = () => {
    if (dataLines.length === 0) {
      eventName = 'message'
      return
    }
    const data = dataLines.join('\n')
    handlers.onEvent?.({ event: eventName || 'message', data })
    eventName = 'message'
    dataLines = []
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const raw of lines) {
      const line = raw.replace(/\r$/, '')
      if (line === '') {
        dispatch()
      } else if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      } else if (line.startsWith(':')) {
        handlers.onComment?.(line.slice(1).trim())
      } else {
        handlers.onParseError?.(line)
      }
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    const line = buffer.replace(/\r$/, '')
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    } else {
      handlers.onParseError?.(line)
    }
  }
  dispatch()
}
