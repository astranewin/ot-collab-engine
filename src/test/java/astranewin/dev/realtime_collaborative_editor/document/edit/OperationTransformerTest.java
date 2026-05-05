package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
@ExtendWith(MockitoExtension.class)
class OperationTransformerTest {

    private OperationTransformer underTest;

    @BeforeEach
    void setUp() {
        underTest = new OperationTransformer();
    }

    private Operation createOp(OperationType type, int position, String text, int length, int version, String senderId) {
        Operation op = new Operation();
        op.setType(type);
        op.setPosition(position);
        op.setText(text);
        op.setLength(length);
        op.setVersion(version);
        op.setSenderId(senderId);
        return op;
    }

    @Test
    void transformAgainst_emptyHistory_returnsOriginalOperation() {
        Operation incoming = createOp(OperationType.INSERT, 5, "Hello", 5, 1, "user1");

        Operation result = underTest.transformAgainst(Collections.emptyList(), incoming, 0);

        assertEquals(5, result.getPosition());
        assertEquals("Hello", result.getText());
    }

    @Test
    void transformAgainst_withHistoryOffset_slicesCorrectly() {
        Operation historyOp1 = createOp(OperationType.INSERT, 0, "A", 1, 10, "user2");
        Operation historyOp2 = createOp(OperationType.INSERT, 1, "B", 1, 11, "user2");

        Operation incoming = createOp(OperationType.INSERT, 1, "C", 1, 11, "user1");

        Operation result = underTest.transformAgainst(List.of(historyOp1, historyOp2), incoming, 10);

        assertEquals(1, result.getPosition());
    }

    @Nested
    class insertInsert {
        @Test
        void samePosition_incomingIdBigger_tieBreakerShiftsIncoming() {
            Operation incoming = createOp(OperationType.INSERT, 5, "A", 1, 0, "user2");
            Operation applied = createOp(OperationType.INSERT, 5, "BBB", 3, 0, "user1");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(8, result.getPosition());
        }

        @Test
        void samePosition_incomingIdLess_tieBreakerShiftsIncoming() {
            Operation incoming = createOp(OperationType.INSERT, 5, "A", 1, 0, "user1");
            Operation applied = createOp(OperationType.INSERT, 5, "BBB", 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(5, result.getPosition());
        }

        @Test
        void incomingBeforeApplied_noChange() {
            Operation incoming = createOp(OperationType.INSERT, 3, "A", 1, 0, "user2");
            Operation applied = createOp(OperationType.INSERT, 5, "BBB", 3, 0, "user1");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(3, result.getPosition());
        }

        @Test
        void incomingAfterApplied_shiftRight() {
            Operation incoming = createOp(OperationType.INSERT, 5, "A", 1, 0, "user2");
            Operation applied = createOp(OperationType.INSERT, 2, "BBB", 3, 0, "user1");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(8, result.getPosition());
        }
    }

    @Nested
    class insertDelete {
        @Test
        void incomingBeforeDeletedRange_noChange() {
            Operation incoming = createOp(OperationType.INSERT, 2, "A", 1, 0, "user1");
            Operation applied = createOp(OperationType.DELETE, 5, null, 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(2, result.getPosition());
        }

        @Test
        void incomingAfterDeletedRange_shiftsLeft() {
            Operation incoming = createOp(OperationType.INSERT, 7, "A", 1, 0, "user1");
            Operation applied = createOp(OperationType.DELETE, 3, null, 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(4, result.getPosition());
        }

        @Test
        void incomingInDeletedRange_snapsToStart() {
            Operation incoming = createOp(OperationType.INSERT, 5, "A", 1, 0, "user1");
            Operation applied = createOp(OperationType.DELETE, 3, null, 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(3, result.getPosition());
        }
    }

    @Nested
    class deleteInsert {
        @Test
        void incomingBeforeInsertedRange_noChange() {
            Operation incoming = createOp(OperationType.DELETE, 2, null, 2, 0, "user1");
            Operation applied = createOp(OperationType.INSERT, 5, "BBB", 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(2, result.getPosition());
            assertEquals(2, result.getLength());
        }

        @Test
        void incomingAfterInsertedRange_shiftsRight() {
            Operation incoming = createOp(OperationType.DELETE, 5, null, 2, 0, "user1");
            Operation applied = createOp(OperationType.INSERT, 2, "BBB", 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(8, result.getPosition());
            assertEquals(2, result.getLength());
        }

        @Test
        void incomingSpansInsertedRange_expandsLength() {
            Operation incoming = createOp(OperationType.DELETE, 2, null, 4, 0, "user1");
            Operation applied = createOp(OperationType.INSERT, 4, "BBB", 3, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(2, result.getPosition());
            assertEquals(7, result.getLength());
        }
    }

    @Nested
    class deleteDelete {
        @Test
        void independentBefore_noChange() {
            Operation incoming = createOp(OperationType.DELETE, 2, null, 2, 0, "user1");
            Operation applied = createOp(OperationType.DELETE, 6, null, 2, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(2, result.getPosition());
            assertEquals(2, result.getLength());
        }

        @Test
        void independentAfter_shiftsLeft() {
            Operation incoming = createOp(OperationType.DELETE, 5, null, 2, 0, "user1");
            Operation applied = createOp(OperationType.DELETE, 2, null, 2, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(3, result.getPosition());
            assertEquals(2, result.getLength());
        }

        @Test
        void partialOverlap_shrinksLengthAndShifts() {
            Operation incoming = createOp(OperationType.DELETE, 5, null, 3, 0, "user1");
            Operation applied = createOp(OperationType.DELETE, 4, null, 2, 0, "user2");

            Operation result = underTest.transformAgainst(List.of(applied), incoming, 0);

            assertEquals(4, result.getPosition());
            assertEquals(2, result.getLength());
        }
    }
}