export const WorkbenchTemplate = ({ eyebrow, title, description, toolbar, children, side }) => (
  <div className="workbench">
    <header className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {toolbar}
    </header>
    <div className={side ? 'workbench__split' : ''}>
      <section className="panel">{children}</section>
      {side && <aside className="panel">{side}</aside>}
    </div>
  </div>
);

