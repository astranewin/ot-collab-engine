package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentSyncing {
    private static final Logger log = LoggerFactory.getLogger(DocumentSyncing.class);

    public SyncMessage sync(List<Operation> history, String content, int clientVersion, int serverVersion, int historyOffset) {
        boolean outDated = clientVersion < serverVersion;
        if (!outDated) return null;

        log.info("Client version is outdated. Syncing...");

        SyncMessage sync = new SyncMessage();
        sync.setVersion(serverVersion);

        boolean outOfBounds = clientVersion < historyOffset;
        SyncType type = outOfBounds ? SyncType.FULL : SyncType.SOFT;
        sync.setType(type);

        if (type.equals(SyncType.SOFT)) {
            sync.setOperations(
                    history.subList(
                            clientVersion,
                            serverVersion
                    )
            );
        } else {
            sync.setContent(content);
        }

        return sync;
    }

    public SyncMessage forceSync(String content, int serverVersion) {
        log.info("Force sync...");
        return SyncMessage.builder()
                .version(serverVersion)
                .type(SyncType.FULL)
                .content(content)
                .build();
    }
}
