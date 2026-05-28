package com.example.admin.common.mapper;

import org.mapstruct.MappingTarget;

public interface BaseMapper<D, E> {

    E toEntity(D dto);

    void updateEntity(@MappingTarget E entity, D dto);
}
