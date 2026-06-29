import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { EntityGrid } from './EntityGrid';

const gridSpy = vi.fn(() => <div data-testid="grid" />);

vi.mock('ag-grid-react', () => ({
  AgGridReact: (props) => gridSpy(props),
}));

describe('EntityGrid', () => {
  it('uses the legacy theme contract required by the imported AG Grid CSS', () => {
    render(<EntityGrid rows={[]} columns={[]} />);

    expect(gridSpy).toHaveBeenCalledWith(
      expect.objectContaining({ theme: 'legacy' }),
    );
  });
});
