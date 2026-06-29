import { useEffect, useState } from 'react';

import { EntityEditor } from '../components/organisms/EntityEditor';
import { EntityGrid } from '../components/organisms/EntityGrid';
import { WorkbenchTemplate } from '../components/templates/WorkbenchTemplate';
import { useMenuOptions, useRoleMenus, useRoleMutations, useRoles } from '../queries/adminQueries';

export const RolesPage = () => {
  const [selected, setSelected] = useState(null);
  const [menuIds, setMenuIds] = useState([]);
  const roles = useRoles();
  const menus = useMenuOptions();
  const assignedMenus = useRoleMenus(selected?.id);
  const mutations = useRoleMutations();
  useEffect(() => {
    setMenuIds(assignedMenus.data?.map(({ id }) => id) ?? []);
  }, [assignedMenus.data]);
  const save = (request) => selected ? mutations.update.mutate({ id: selected.id, request }) : mutations.create.mutate(request);
  return (
    <WorkbenchTemplate eyebrow="JPA RELATION" title="역할 워크벤치" description="역할과 메뉴의 다대다 관계를 검증합니다."
      side={<EntityEditor kind="역할" selected={selected} fields={[{ name: 'roleCode', label: '역할 코드', immutable: true }, { name: 'roleName', label: '역할명' }]} onSave={save} onDelete={() => confirm('역할을 삭제할까요?') && mutations.remove.mutate(selected.id)}>
        {selected && <fieldset><legend>메뉴 배정</legend><div className="choice-list">{menus.data?.map((menu) => <label key={menu.id} className="checkbox"><input type="checkbox" checked={menuIds.includes(menu.id)} onChange={() => setMenuIds((ids) => ids.includes(menu.id) ? ids.filter((id) => id !== menu.id) : [...ids, menu.id])}/>{menu.menuName}</label>)}</div><button type="button" className="button button--secondary" onClick={() => mutations.assignMenus.mutate({ id: selected.id, menuIds })}>메뉴 저장</button></fieldset>}
      </EntityEditor>}
    >
      <EntityGrid rows={roles.data ?? []} columns={[{ field: 'id', headerName: 'ID', maxWidth: 90 }, { field: 'roleCode', headerName: '역할 코드' }, { field: 'roleName', headerName: '역할명' }, { field: 'enabled', headerName: '사용' }]} onSelectionChanged={({ api }) => setSelected(api.getSelectedRows()[0] ?? null)} />
    </WorkbenchTemplate>
  );
};
