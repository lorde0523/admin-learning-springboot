export const StatusBadge = ({ active, children }) => (
  <span className={`badge badge--${active ? 'success' : 'muted'}`}>{children}</span>
);

