export default function LoginPanel({
  username,
  password,
  token,
  status,
  onUsernameChange,
  onPasswordChange,
  onLogin,
  onLogout,
}) {
  return (
    <section className="panel login-panel">
      <div className="panel-title">
        <h2>登录状态</h2>
        <span className={token ? 'badge ok' : 'badge'}>{token ? '已登录' : '未登录'}</span>
      </div>
      <div className="form-grid">
        <label>
          用户名
          <input value={username} onChange={(event) => onUsernameChange(event.target.value)} />
        </label>
        <label>
          密码
          <input
            type="password"
            value={password}
            onChange={(event) => onPasswordChange(event.target.value)}
          />
        </label>
      </div>
      <div className="actions">
        <button type="button" onClick={onLogin}>登录并创建会话</button>
        <button type="button" className="secondary" onClick={onLogout}>退出</button>
      </div>
      {token ? <p className="muted mono">Token: {token.slice(0, 24)}...</p> : null}
      {status ? <p className="status-line">{status}</p> : null}
    </section>
  )
}
