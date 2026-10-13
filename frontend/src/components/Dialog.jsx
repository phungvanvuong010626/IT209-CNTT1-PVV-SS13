function Dialog({ title, onClose, children }) {
  return <div className="dialog-backdrop" onMouseDown={onClose}>
    <section className="dialog" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
      <div className="dialog-title"><h2>{title}</h2><button onClick={onClose} aria-label="Đóng">×</button></div>
      {children}
    </section>
  </div>
}

export default Dialog
