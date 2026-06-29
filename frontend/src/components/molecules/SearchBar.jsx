import { Button } from '../atoms/Button';
import { TextInput } from '../atoms/FormControls';

export const SearchBar = ({ value, onChange, onSearch, placeholder }) => (
  <form
    className="search-bar"
    onSubmit={(event) => {
      event.preventDefault();
      onSearch();
    }}
  >
    <TextInput
      aria-label="검색어"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
    />
    <Button type="submit">조회</Button>
  </form>
);

