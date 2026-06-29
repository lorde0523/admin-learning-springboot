/**
 * Creates the exact delta shape expected by MenuGridSaveRequest.
 */
export const createMenuChangeSet = () => ({
  createdRows: [],
  updatedRows: [],
  deletedIds: [],
});

const upsertById = (rows, row) => [
  ...rows.filter(({ id }) => id !== row.id),
  row,
];

export const updateMenuRow = (changeSet, row, isNew) => ({
  ...changeSet,
  createdRows: isNew
    ? upsertById(changeSet.createdRows, row)
    : changeSet.createdRows,
  updatedRows: isNew
    ? changeSet.updatedRows
    : upsertById(changeSet.updatedRows, row),
  deletedIds: changeSet.deletedIds.filter((id) => id !== row.id),
});

export const deleteMenuRow = (changeSet, id, isNew) => ({
  createdRows: changeSet.createdRows.filter((row) => row.id !== id),
  updatedRows: changeSet.updatedRows.filter((row) => row.id !== id),
  deletedIds: isNew
    ? changeSet.deletedIds
    : [...new Set([...changeSet.deletedIds, id])],
});

export const hasMenuChanges = (changeSet) =>
  changeSet.createdRows.length > 0 ||
  changeSet.updatedRows.length > 0 ||
  changeSet.deletedIds.length > 0;

