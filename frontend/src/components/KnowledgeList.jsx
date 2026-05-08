function shortHash(value) {
  if (!value) return '-'
  return value.length > 12 ? `${value.slice(0, 12)}...` : value
}

function formatCreatedAt(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export default function KnowledgeList({ items, loading, status, disabled, onRefresh, onDelete }) {
  return (
    <section className="panel">
      <div className="panel-title">
        <h2>知识库</h2>
        <button type="button" className="secondary" onClick={onRefresh} disabled={disabled || loading}>
          {loading ? '刷新中' : '刷新'}
        </button>
      </div>

      {status ? <p className="status-line">{status}</p> : null}
      {!items.length ? <p className="empty">暂无知识文件。上传 .txt 后会出现在这里。</p> : null}

      {items.length ? (
        <div className="knowledge-list">
          {items.map((item) => (
            <article className="knowledge-item" key={item.file_id || item.fileId || item.filename}>
              <div className="knowledge-head">
                <strong>{item.filename || '-'}</strong>
                <span className={item.deletable ? 'badge ok' : 'badge'}>{item.deletable ? '可删除' : '只读'}</span>
              </div>
              <dl>
                <div>
                  <dt>chunks</dt>
                  <dd>{item.chunk_count ?? item.chunkCount ?? 0}</dd>
                </div>
                <div>
                  <dt>created</dt>
                  <dd>{formatCreatedAt(item.created_at || item.createdAt)}</dd>
                </div>
                <div>
                  <dt>legacy</dt>
                  <dd>{item.legacy ? 'true' : 'false'}</dd>
                </div>
                <div>
                  <dt>hash</dt>
                  <dd className="mono">{shortHash(item.content_hash || item.contentHash)}</dd>
                </div>
              </dl>
              {item.deletable ? (
                <button
                  type="button"
                  className="danger"
                  onClick={() => onDelete(item.file_id || item.fileId)}
                  disabled={disabled || loading}
                >
                  删除
                </button>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}
