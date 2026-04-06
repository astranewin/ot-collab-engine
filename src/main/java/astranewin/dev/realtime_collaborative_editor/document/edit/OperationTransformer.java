package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OperationTransformer {
    private static final Logger log = LoggerFactory.getLogger(OperationTransformer.class);

    public Operation transformAgainst(List<Operation> history, int version, Operation op, int historyOffset) {
        log.info("History size: {}, op.version: {}, version: {}", history.size(), op.getVersion(), version);

        if (history.isEmpty()) return op;
        List<Operation> missed = history.subList(
                op.getVersion() - historyOffset,
                history.size()
        );

        for (Operation prevOp : missed) {
            op = transform(op, prevOp);
        }

        return op;
    }

    private Operation transform(Operation incoming, Operation applied) {
        if (incoming.getType().equals(OperationType.INSERT) &&
                applied.getType().equals(OperationType.INSERT)) {
            // Insert –> Insert
            return transformInsertInsert(incoming, applied);
        }

        if (incoming.getType().equals(OperationType.INSERT) &&
                applied.getType().equals(OperationType.DELETE)) {
            // Insert -> Delete
            return transformInsertDelete(incoming, applied);
        }

        if (incoming.getType().equals(OperationType.DELETE) &&
                applied.getType().equals(OperationType.INSERT)) {
            // Delete -> Insert
            return transformDeleteInsert(incoming, applied);
        }

        // Delete -> Delete
        return transformDeleteDelete(incoming, applied);
    }

    private Operation transformInsertInsert(Operation a, Operation b) {
        if (a.getPosition() < b.getPosition()) return a;
        if (a.getPosition() > b.getPosition()) {
            a.setPosition(a.getPosition() + b.getText().length());
            return a;
        }

        if (a.getSenderId().compareTo(b.getSenderId()) > 0) {
            a.setPosition(a.getPosition() + b.getText().length());
        }

        a.setPosition(a.getPosition() + b.getText().length());
        return a;
    }

    private Operation transformInsertDelete(Operation insert, Operation delete) {
        int delStart = delete.getPosition();
        int delEnd = delStart + delete.getLength();

        if (insert.getPosition() <= delStart) return insert;
        if (insert.getPosition() >= delEnd) {
            insert.setPosition(insert.getPosition() - delete.getLength());
            return insert;
        }

        insert.setPosition(delStart);
        return insert;
    }

    private Operation transformDeleteInsert(Operation delete, Operation insert) {
        if (insert.getPosition() <= delete.getPosition()) {
            delete.setPosition(delete.getPosition() + insert.getText().length());
            return delete;
        }

        if (insert.getPosition() >= delete.getPosition() + delete.getLength()) return delete;

        delete.setLength(delete.getLength() + insert.getText().length());
        return delete;
    }

    private Operation transformDeleteDelete(Operation a, Operation b) {
        int aStart = a.getPosition();
        int aEnd = aStart + a.getLength();

        int bStart = b.getPosition();
        int bEnd = bStart + b.getLength();

        if (aEnd <= bStart) return a;
        if (aStart >= bEnd) {
            a.setPosition(aStart - b.getLength());
            return a;
        }

        int overlapStart = Math.max(aStart, bStart);
        int overlapEnd = Math.min(aEnd, bEnd);
        int overlap = overlapEnd - overlapStart;

        a.setLength(a.getLength() - overlap);

        if (bStart < aStart) {
            a.setPosition(aStart - (overlapStart - bStart));
        }

        return a;
    }
}
