import { useState } from 'react'

export default function FeedbackPanel({ disabled, conversationId, requestId, onSubmit, recentItems }) {
  const [thumb, setThumb] = useState('up')
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')

  const submit = async () => {
    await onSubmit({
      conversation_id: conversationId,
      request_id: requestId || undefined,
      thumb,
      rating: Number(rating),
      comment: comment.trim() || undefined,
    })
    setComment('')
  }

  return (
    <section className="panel">
      <div className="panel-title">
        <h2>反馈闭环</h2>
        <span className="badge">{requestId ? '可关联' : '等待回答'}</span>
      </div>
      <div className="segmented">
        <button type="button" className={thumb === 'up' ? 'selected' : ''} onClick={() => setThumb('up')}>赞</button>
        <button type="button" className={thumb === 'down' ? 'selected' : ''} onClick={() => setThumb('down')}>踩</button>
      </div>
      <label>
        评分
        <input
          type="range"
          min="1"
          max="5"
          value={rating}
          onChange={(event) => setRating(event.target.value)}
        />
        <span className="muted">{rating} / 5</span>
      </label>
      <label>
        备注
        <textarea
          rows={3}
          value={comment}
          placeholder="可选"
          onChange={(event) => setComment(event.target.value)}
        />
      </label>
      <button type="button" disabled={disabled} onClick={submit}>提交反馈</button>
      {recentItems?.length ? (
        <details className="trace-details">
          <summary>最近反馈</summary>
          {recentItems.map((item) => (
            <p className="muted" key={item.id}>#{item.id} {item.thumb || '-'} {item.rating || '-'} 分</p>
          ))}
        </details>
      ) : null}
    </section>
  )
}
