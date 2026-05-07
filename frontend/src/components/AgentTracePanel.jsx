const STAGES = ['PLAN', 'RETRIEVE', 'TOOL', 'GUARD', 'WRITE']

export default function AgentTracePanel({ events, planParse, requestId, parseErrors }) {
  const grouped = STAGES.map((stage) => ({
    stage,
    events: events.filter((item) => item.stage === stage || item.data?.stage === stage),
  }))

  return (
    <section className="panel">
      <div className="panel-title">
        <h2>Agent 过程</h2>
        <span className="badge">{requestId ? requestId.slice(0, 8) : '等待'}</span>
      </div>
      {planParse ? (
        <details className="trace-details" open>
          <summary>PLAN 解析</summary>
          <JsonBlock value={planParse} />
        </details>
      ) : null}
      <div className="timeline">
        {grouped.map(({ stage, events: stageEvents }) => (
          <details className="trace-details" key={stage} open={stageEvents.length > 0}>
            <summary>
              <span>{stage}</span>
              <span className={stageEvents.length ? 'dot active' : 'dot'} />
            </summary>
            {stageEvents.length ? (
              stageEvents.map((event, index) => (
                <div className="trace-row" key={`${stage}-${index}`}>
                  <strong>{event.kind || event.event}</strong>
                  {event.elapsed_ms != null ? <span>{event.elapsed_ms} ms</span> : null}
                  <JsonBlock value={event.data || event} />
                </div>
              ))
            ) : (
              <p className="empty compact">暂无事件</p>
            )}
          </details>
        ))}
      </div>
      {parseErrors.length ? (
        <details className="trace-details">
          <summary>SSE 解析提示</summary>
          <pre>{parseErrors.slice(-5).join('\n')}</pre>
        </details>
      ) : null}
    </section>
  )
}

function JsonBlock({ value }) {
  return <pre className="json-block">{JSON.stringify(value, null, 2)}</pre>
}
