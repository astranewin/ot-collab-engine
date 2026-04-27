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
        if (clientVersion >= serverVersion) return null;

        log.info("Client version is outdated. Syncing...");

        int fromIndex = clientVersion - historyOffset;
        int toIndex = serverVersion - historyOffset;

        if (fromIndex < 0 || toIndex > history.size() || fromIndex > toIndex) {
            log.info("Falling back to FULL sync");
            return forceSync(content, serverVersion);
        }

        return SyncMessage.builder()
                .operations(history.subList(fromIndex, toIndex))
                .version(serverVersion)
                .type(SyncType.SOFT)
                .build();
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
