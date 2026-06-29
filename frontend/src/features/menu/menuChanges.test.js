import { describe, expect, it } from 'vitest';

import { createMenuChangeSet, deleteMenuRow, updateMenuRow } from './menuChanges';

describe('menu change set', () => {
  it('tracks new rows as created and edits them in place', () => {
    const initial = createMenuChangeSet();
    const created = updateMenuRow(initial, { id: 99, menuCode: 'NEW', menuName: 'New' }, true);
    const edited = updateMenuRow(created, { id: 99, menuCode: 'NEW', menuName: 'Changed' }, true);

    expect(edited.createdRows).toEqual([
      { id: 99, menuCode: 'NEW', menuName: 'Changed' },
    ]);
    expect(edited.updatedRows).toEqual([]);
  });

  it('removes unsaved rows without adding a deleted ID', () => {
    const created = updateMenuRow(
      createMenuChangeSet(),
      { id: 99, menuCode: 'NEW' },
      true,
    );

    expect(deleteMenuRow(created, 99, true)).toEqual(createMenuChangeSet());
  });

  it('tracks persisted edits and deletes without conflicts', () => {
    const updated = updateMenuRow(
      createMenuChangeSet(),
      { id: 3, menuCode: 'OLD', menuName: 'Changed' },
      false,
    );
    const deleted = deleteMenuRow(updated, 3, false);

    expect(deleted.updatedRows).toEqual([]);
    expect(deleted.deletedIds).toEqual([3]);
  });
});
