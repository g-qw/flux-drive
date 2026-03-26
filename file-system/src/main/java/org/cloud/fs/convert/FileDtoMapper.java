package org.cloud.fs.convert;

import org.cloud.api.dto.FileDTO;
import org.cloud.api.dto.FileInputDTO;
import org.cloud.fs.dto.FileInput;
import org.cloud.fs.dto.FileRpcView;
import org.cloud.fs.dto.FileView;
import org.cloud.fs.entity.File;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = UUIDConverter.class
)
public interface FileDtoMapper {
    FileDtoMapper INSTANCE = Mappers.getMapper(FileDtoMapper.class);

    @Mapping(source = "id", target = "id", qualifiedByName = "uuidToString")
    @Mapping(source = "directoryId", target = "directoryId", qualifiedByName = "uuidToString")
    @Mapping(source = "userId", target = "userId", qualifiedByName = "uuidToString")
    FileDTO entityToDto(File file); // fluent getter

    @Mapping(source = "id", target = "id", qualifiedByName = "uuidToString")
    @Mapping(source = "userId", target = "userId", qualifiedByName = "uuidToString")
    FileDTO viewToDto(FileRpcView fileView); // fluent getter

    @Mapping(source = "directoryId", target = "directoryId", qualifiedByName = "stringToUUID")
    FileInput toInput(FileInputDTO dto);
}
