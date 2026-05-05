package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.common.exceptions.DocumentNotInitializedException;
import astranewin.dev.realtime_collaborative_editor.common.exceptions.NotFoundException;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.*;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import astranewin.dev.realtime_collaborative_editor.document.entity.DocumentRepository;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.DocumentSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentOperationServiceTest {
    @Mock
    private DocumentRepository repository;
    @Spy
    private OperationTransformer operationTransformer = new OperationTransformer();
    @Mock
    private DocumentSyncing documentSyncing;
    @Mock
    private ScheduledExecutorService scheduler;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private DocumentSnapshotService documentSnapshotService;

    private final String DOC_ID = "1";
    private final Long PARSED_DOC_ID = 1L;

    @InjectMocks
    private DocumentOperationService underTest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(underTest, "historyLimitSize", 50);
    }

    @AfterEach
    void tearDown() {
        underTest.shutdown();
    }


    @Nested
    class initDocument {
        @Test
        void documentExists_loadsFromDbAndReturnsState() {
            when(repository.existsById(PARSED_DOC_ID)).thenReturn(true);
            when(repository.getContentByDocId(PARSED_DOC_ID)).thenReturn(Optional.of("Test document"));

            DocumentState documentState = underTest.initDocument(DOC_ID);

            assertNotNull(documentState);
            assertThat(documentState.getContent()).isEqualTo("Test document");
            assertThat(documentState.getVersion()).isEqualTo(0);
            assertThat(documentState.getHistory().size()).isEqualTo(0);
        }

        @Test
        void documentDoesNotExist_throwsNotFoundException() {
            when(repository.existsById(PARSED_DOC_ID)).thenReturn(false);
            assertThrows(NotFoundException.class, () -> underTest.initDocument(DOC_ID));
            verify(repository, never()).getContentByDocId(anyLong());
        }
    }

    @Nested
    class handle {
        @Test
        void documentNotInitialized_throwsException() {
            Operation op = new Operation();
            assertThrows(DocumentNotInitializedException.class, () -> underTest.handle(DOC_ID, op));
        }

        @Test
        void clientRequiresFullSync_returnsSyncMessageWithoutProcessing() {
            initDummyContent("Test document");

            Operation op = new Operation();
            SyncMessage syncMessage = SyncMessage.builder().type(SyncType.FULL).build();
            when(documentSyncing.sync(anyList(), anyString(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(syncMessage);

            DocumentHandleOperationResponse handle = underTest.handle(DOC_ID, op);

            assertEquals(SyncType.FULL, handle.getSyncMessage().getType());
            assertNull(handle.getOp());
            verify(operationTransformer, never()).transformAgainst(anyList(), any(), anyInt());
        }

        @Test
        void validInsertOperation_appliesUpdatesVersion() {
            initDummyContent("Hello");

            Operation incomingOp = createOp(OperationType.INSERT, 5, " world", 0, 0);

            when(documentSyncing.sync(anyList(), anyString(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(operationTransformer.transformAgainst(anyList(), eq(incomingOp), anyInt()))
                    .thenReturn(incomingOp);

            DocumentHandleOperationResponse handle = underTest.handle(DOC_ID, incomingOp);

            DocumentState documentState = underTest.initDocument(DOC_ID);

            assertTrue(documentState.isDirty());
            assertEquals("Hello world", documentState.getContent());
            assertEquals(1, documentState.getVersion());
            assertEquals(1, documentState.getHistory().size());

            assertNotNull(handle.getOp());
            assertEquals(1, handle.getOp().getVersion());
        }

        @Test
        void exceedsHistorySize_prunesHistoryAndTriggersSnapshot() {
            DocumentState state = initDummyContent("Hello world");

            for (int i = 0; i < 50; i++) {
                state.getHistory().add(new Operation());
            }

            Operation incomingOp = createOp(OperationType.INSERT, 0, "Test", 0, 50);

            when(documentSyncing.sync(anyList(), anyString(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(operationTransformer.transformAgainst(anyList(), eq(incomingOp), anyInt()))
                    .thenReturn(incomingOp);

            underTest.handle(DOC_ID, incomingOp);

            assertEquals(50, state.getHistory().size());
            assertEquals(1, state.getHistoryOffset());
            verify(documentSnapshotService).initSnapshot(eq(PARSED_DOC_ID), any(), anyString());
        }

        @Test
        void insertOutOfBounds_snapsToBoundariesSafely() {
            initDummyContent("ABC");

            Operation incomingOp = createOp(OperationType.INSERT, 100, "D", 0, 0);

            when(documentSyncing.sync(anyList(), anyString(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(operationTransformer.transformAgainst(anyList(), eq(incomingOp), anyInt()))
                    .thenReturn(incomingOp);

            underTest.handle(DOC_ID, incomingOp);

            DocumentState state = underTest.initDocument(DOC_ID);
            assertEquals("ABCD", state.getContent());
        }

        @Test
        void deleteOutOfBounds_snapsToBoundariesSafely() {
            initDummyContent("ABCDEF");

            Operation incomingOp = createOp(OperationType.DELETE, 4, null, 60, 0);
            when(documentSyncing.sync(anyList(), anyString(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(null);
            when(operationTransformer.transformAgainst(anyList(), eq(incomingOp), anyInt()))
                    .thenReturn(incomingOp);

            underTest.handle(DOC_ID, incomingOp);

            DocumentState state = underTest.initDocument(DOC_ID);
            assertEquals("ABCD", state.getContent());
        }
    }

    @Nested
    class otHardTests {
        @Test
        void manyOperationsAtOnce_safelyApplyOperations() {
            initDummyContent("ABCDE");

            List<Operation> operationList = new ArrayList<>(List.of(
                    createOp(OperationType.INSERT, 5, "F", 0, "1", 0),
                    createOp(OperationType.INSERT, 0, "STOP", 0, "2", 0),
                    createOp(OperationType.INSERT, 5, "H", 0, "3", 0)
            ));

            when(documentSyncing.sync(anyList(), anyString(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(null);

            for (int i = 0; i < 3; i++) {
                underTest.handle(DOC_ID, operationList.get(i));
            }

            DocumentState state = underTest.initDocument(DOC_ID);
            assertEquals("STOPABCDEFH", state.getContent());
        }

        @Test
        void concurrentDeletes_overlappingRanges() {
            initDummyContent("123456789");

            List<Operation> operationList = List.of(
                    createOp(OperationType.DELETE, 1, null, 3, "user1", 0),
                    createOp(OperationType.DELETE, 3, null, 3, "user2", 0)
            );

            for (Operation op : operationList) {
                underTest.handle(DOC_ID, op);
            }

            DocumentState state = underTest.initDocument(DOC_ID);
            assertEquals("1789", state.getContent());
        }

        @Test
        void insertIntoDeletedRange() {
            initDummyContent("Existing Text");

            List<Operation> operationList = List.of(
                    createOp(OperationType.DELETE, 0, null, 13, "user1", 0),
                    createOp(OperationType.INSERT, 9, "NEW", 0, "user2", 0)
            );

            for (Operation op : operationList) {
                underTest.handle(DOC_ID, op);
            }

            DocumentState state = underTest.initDocument(DOC_ID);
            assertEquals("NEW", state.getContent());
        }

        @Test
        void deletionShiftedByLeadingInsert() {
            initDummyContent("Target");

            List<Operation> operationList = List.of(
                    createOp(OperationType.INSERT, 0, "START_", 0, "user1", 0),
                    createOp(OperationType.DELETE, 0, null, 6, "user2", 0)
            );

            for (Operation op : operationList) {
                underTest.handle(DOC_ID, op);
            }

            DocumentState state = underTest.initDocument(DOC_ID);
            assertEquals("START_", state.getContent());
        }
    }

    private DocumentState initDummyContent(String initialContent) {
        when(repository.existsById(PARSED_DOC_ID)).thenReturn(true);
        when(repository.getContentByDocId(PARSED_DOC_ID)).thenReturn(Optional.of(initialContent));
        return underTest.initDocument(DOC_ID);
    }

    private Operation createOp(OperationType type, int position, String text, int length, int version) {
        Operation op = new Operation();
        op.setType(type);
        op.setPosition(position);
        op.setText(text);
        op.setLength(length);
        op.setVersion(version);
        return op;
    }

    private Operation createOp(OperationType type, int position, String text, int length, String sender, int version) {
        Operation op = new Operation();
        op.setType(type);
        op.setPosition(position);
        op.setText(text);
        op.setSenderId(sender);
        op.setLength(length);
        op.setVersion(version);
        return op;
    }
}