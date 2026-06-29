import { useMemo, useState } from 'react';

import { Button } from '../components/atoms/Button';
import { Select, TextInput } from '../components/atoms/FormControls';
import { EntityGrid } from '../components/organisms/EntityGrid';
import { WorkbenchTemplate } from '../components/templates/WorkbenchTemplate';
import { createMenuChangeSet, deleteMenuRow, hasMenuChanges, updateMenuRow } from '../features/menu/menuChanges';
import { useMenus, useSaveMenuGrid } from '../queries/adminQueries';

export const MenusPage = () => {
  const [implementation, setImplementation] = useState('jpa');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [changes, setChanges] = useState(createMenuChangeSet);
  const [newRows, setNewRows] = useState([]);
  const criteria = useMemo(() => ({ nameKeyword: keyword, page, size: 20, sort: 'sortOrder,asc' }), [keyword, page]);
  const menus = useMenus(implementation, criteria);
  const save = useSaveMenuGrid();
  const rows = [...newRows, ...(menus.data?.content ?? []).filter((row) => !changes.deletedIds.includes(row.id))];
  const addRow = () => setNewRows((current) => [{ id: Date.now(), menuCode: '', menuName: '', parentMenuId: null, sortOrder: 0, enabled: true, _new: true }, ...current]);
  return (
    <WorkbenchTemplate eyebrow="JPA vs MYBATIS" title="메뉴 워크벤치" description="동일 조건의 조회 SQL과 일괄 저장 delta를 비교합니다."
      toolbar={<div className="toolbar"><Select aria-label="조회 구현" value={implementation} onChange={(event) => setImplementation(event.target.value)}><option value="jpa">JPA</option><option value="mybatis">MyBatis</option></Select><TextInput aria-label="메뉴명 검색" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="메뉴명" /><Button onClick={addRow}>행 추가</Button><Button disabled={!hasMenuChanges(changes)} onClick={() => save.mutate(changes, { onSuccess: () => { setChanges(createMenuChangeSet()); setNewRows([]); } })}>변경 저장</Button></div>}
    >
      <EntityGrid rows={rows} columns={[{ field: 'id', headerName: 'ID', editable: (p) => p.data._new, maxWidth: 100 }, { field: 'menuCode', headerName: '메뉴 코드', editable: (p) => p.data._new }, { field: 'menuName', headerName: '메뉴명', editable: true }, { field: 'parentMenuId', headerName: '상위 ID', editable: true }, { field: 'sortOrder', headerName: '순서', editable: true }, { field: 'enabled', headerName: '사용', editable: true }, { headerName: '작업', maxWidth: 100, cellRenderer: ({ data }) => <button className="link-button" onClick={() => { setChanges((current) => deleteMenuRow(current, data.id, data._new)); if (data._new) setNewRows((current) => current.filter(({ id }) => id !== data.id)); }}>삭제</button> }]} onCellValueChanged={({ data }) => setChanges((current) => updateMenuRow(current, { id: Number(data.id), menuCode: data.menuCode, menuName: data.menuName, parentMenuId: data.parentMenuId ? Number(data.parentMenuId) : null, sortOrder: Number(data.sortOrder), enabled: Boolean(data.enabled) }, data._new))} />
      <div className="pagination"><Button variant="secondary" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>이전</Button><span>{page + 1} / {menus.data?.totalPages || 1}</span><Button variant="secondary" disabled={menus.data?.last ?? true} onClick={() => setPage((value) => value + 1)}>다음</Button></div>
      {save.data && <p className="save-result">생성 {save.data.createdCount}, 수정 {save.data.updatedCount}, 삭제 {save.data.deletedCount}</p>}
    </WorkbenchTemplate>
  );
};

