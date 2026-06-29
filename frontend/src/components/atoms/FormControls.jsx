export const TextInput = (props) => <input className="input" {...props} />;

export const Select = (props) => <select className="input" {...props} />;

export const Checkbox = ({ label, ...props }) => (
  <label className="checkbox">
    <input type="checkbox" {...props} />
    <span>{label}</span>
  </label>
);

