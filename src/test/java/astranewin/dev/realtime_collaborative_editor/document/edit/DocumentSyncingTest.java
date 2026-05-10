package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DocumentSyncingTest {
    private DocumentSyncing underTest;
    private List<Operation> mockHistory;
    private final String content = "Hello world";

    @BeforeEach
    void setUp() {
        underTest = new DocumentSyncing();

        mockHistory = Arrays.asList(
                mock(Operation.class),
                mock(Operation.class),
                mock(Operation.class),
                mock(Operation.class),
                mock(Operation.class)
        );
    }

    @Nested
    class sync {
        @Test
        void clientVersionIsUpToDate_ShouldReturnNull() {
            int clientVersion = 10;
            int serverVersion = 10;

            SyncMessage sync = underTest.sync(mockHistory, content, clientVersion, serverVersion, 0);

            assertNull(sync);
        }

        @Test
        void clientVersionIsAhead_ShouldReturnNull() {
            int clientVersion = 11;
            int serverVersion = 10;

            SyncMessage sync = underTest.sync(mockHistory, content, clientVersion, serverVersion, 0);

            assertNull(sync);
        }

        @Test
        void clientWithinHistoryBounds_ShouldReturnSoftSync() {
            int historyOffset = 10;

            int clientVersion = 11;
            int serverVersion = 14;

            SyncMessage sync = underTest.sync(mockHistory, content, clientVersion, serverVersion, historyOffset);

            assertNotNull(sync);
            assertEquals(SyncType.SOFT, sync.getType());
            assertEquals(serverVersion, sync.getVersion());
            assertEquals(3, sync.getOperations().size());
            assertThat(sync.getOperations()).containsExactly(
                    mockHistory.get(1),
                    mockHistory.get(2),
                    mockHistory.get(3)
            );
        }
    }
}