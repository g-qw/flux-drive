package org.cloud.fs.convert;

import org.cloud.api.dto.DirectoryCreationInputDTO;
import org.cloud.api.dto.DirectoryDTO;
import org.cloud.fs.dto.DirectoryInput;
import org.cloud.fs.entity.Directory;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = UUIDConverter.class
)
public interface DirectoryDtoMapper {
    DirectoryDtoMapper INSTANCE = Mappers.getMapper(DirectoryDtoMapper.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "uuidToString")
    @Mapping(source = "parentId", target = "parentId", qualifiedByName = "uuidToString")
    @Mapping(source = "userId", target = "userId", qualifiedByName = "uuidToString")
    DirectoryDTO entityToDto(Directory directory);

    @Mapping(source = "parentId", target = "parentId", qualifiedByName = "stringToUUID")
    DirectoryInput toInput(DirectoryCreationInputDTO dto);
}
