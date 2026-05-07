export default function ProfilePanel({
  profile,
  pending,
  status,
  disabled,
  onRefresh,
  onExtract,
  onConfirm,
  onDiscard,
  onReset,
}) {
  return (
    <section className="panel">
      <div className="panel-title">
        <h2>用户画像确认</h2>
        <span className="badge">{pending ? '待确认' : '当前画像'}</span>
      </div>
      <div className="actions tight">
        <button type="button" className="secondary" disabled={disabled} onClick={onRefresh}>刷新</button>
        <button type="button" className="secondary" disabled={disabled} onClick={onExtract}>抽取建议</button>
        <button type="button" className="danger" disabled={disabled} onClick={onReset}>重置画像</button>
      </div>
      {pending ? (
        <article className="profile-card">
          <strong>待确认画像</strong>
          <JsonPreview value={pending.mergedPreview || pending.merged_preview || pending} />
          <div className="actions tight">
            <button type="button" disabled={disabled} onClick={onConfirm}>确认写入</button>
            <button type="button" className="secondary" disabled={disabled} onClick={onDiscard}>忽略</button>
          </div>
        </article>
      ) : (
        <article className="profile-card">
          <strong>当前画像</strong>
          <JsonPreview value={profile?.profile || {}} />
        </article>
      )}
      {status ? <p className="muted">{status}</p> : null}
    </section>
  )
}

function JsonPreview({ value }) {
  const text = JSON.stringify(value ?? {}, null, 2)
  return <pre className="json-block compact-json">{text === '{}' ? '暂无画像数据' : text}</pre>
}
