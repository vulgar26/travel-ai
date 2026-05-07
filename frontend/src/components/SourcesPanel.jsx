export default function SourcesPanel({ sources }) {
  return (
    <section className="panel">
      <div className="panel-title">
        <h2>引用来源</h2>
        <span className="badge">{sources.length}</span>
      </div>
      {sources.length === 0 ? (
        <p className="empty">等待后端返回引用片段、sources 或 citation 信息。</p>
      ) : (
        <div className="source-list">
          {sources.map((source, index) => (
            <article className="source-item" key={`${source.title}-${index}`}>
              <div className="source-head">
                <strong>{source.title || `来源 ${index + 1}`}</strong>
                {source.score ? <span>{source.score}</span> : null}
              </div>
              <p>{source.snippet || '无片段内容'}</p>
              {source.hit ? <small>{source.hit}</small> : null}
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
