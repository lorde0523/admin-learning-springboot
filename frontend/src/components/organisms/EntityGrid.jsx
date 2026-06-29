import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { AgGridReact } from 'ag-grid-react';

ModuleRegistry.registerModules([AllCommunityModule]);

/** Shared AG Grid surface; domain pages own columns and selection behavior. */
export const EntityGrid = ({ rows, columns, onSelectionChanged, ...props }) => (
  <div className="grid-shell">
    <AgGridReact
      theme="legacy"
      rowData={rows}
      columnDefs={columns}
      defaultColDef={{ resizable: true, sortable: true, filter: true, flex: 1 }}
      rowSelection={{ mode: 'singleRow', enableClickSelection: true }}
      onSelectionChanged={onSelectionChanged}
      {...props}
    />
  </div>
);
