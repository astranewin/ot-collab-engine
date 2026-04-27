package astranewin.dev.realtime_collaborative_editor.document.snapshot;

import astranewin.dev.realtime_collaborative_editor.document.snapshot.domain.DocumentSnapshotEntity;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotListResponse;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.dto.SnapshotResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SnapshotMapping {
    @Mapping(target = "beginDate", source = "beginSnapshot")
    @Mapping(target = "endDate", source = "endSnapshot")
    SnapshotListResponse toSnapshotList(DocumentSnapshotEntity entity);

    @Mapping(target = "beginDate", source = "beginSnapshot")
    @Mapping(target = "endDate", source = "endSnapshot")
    SnapshotResponse toSnapshot(DocumentSnapshotEntity entity);
}
