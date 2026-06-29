import { Button } from '../atoms/Button';
import { Checkbox, TextInput } from '../atoms/FormControls';

export const EntityEditor = ({ kind, selected, fields, onSave, onDelete, children }) => {
  const initial = Object.fromEntries(fields.map((field) => [field.name, selected?.[field.name] ?? '']));

  return (
    <form
      key={`${kind}-${selected?.id ?? 'new'}`}
      className="editor"
      onSubmit={(event) => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(event.currentTarget));
        values.enabled = event.currentTarget.elements.enabled.checked;
        onSave(values);
      }}
    >
      <h2>{selected ? `${kind} 수정` : `${kind} 등록`}</h2>
      {fields.map((field) => (
        <label key={field.name} className="field">
          <span>{field.label}</span>
          <TextInput
            name={field.name}
            defaultValue={initial[field.name]}
            readOnly={Boolean(selected && field.immutable)}
            required
          />
        </label>
      ))}
      <Checkbox name="enabled" label="사용" defaultChecked={selected?.enabled ?? true} />
      <div className="actions">
        <Button type="submit">저장</Button>
        {selected && (
          <Button type="button" variant="danger" onClick={onDelete}>
            삭제
          </Button>
        )}
      </div>
      {children}
    </form>
  );
};

