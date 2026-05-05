package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.OperationType;
import org.springframework.stereotype.Component;

import java.util.List;


// That class contains a ton of comments, since it was a headache to debug and write tests.
// *it also can help you with understanding the process :)
@Component
public class OperationTransformer {
    public Operation transformAgainst(List<Operation> history, Operation op, int historyOffset) {
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
        // Applied operation is non-interfering with incoming
        if (a.getPosition() < b.getPosition()) return a;
        // Shifting incoming position by applied position(collides)
        if (a.getPosition() > b.getPosition()) {
            a.setPosition(a.getPosition() + b.getText().length());
            return a;
        }

        // If senderId (a) is greater than senderId (b), then we shift incoming against applied.
        // Basically, user with less ID preempt.
        if (a.getSenderId().compareTo(b.getSenderId()) > 0) {
            a.setPosition(a.getPosition() + b.getText().length());
        }

        return a;
    }

    private Operation transformInsertDelete(Operation insert, Operation delete) {
        int delStart = delete.getPosition();
        int delEnd = delStart + delete.getLength();

        // Applied operation is non-interfering with incoming. No changes
        if (insert.getPosition() <= delStart) return insert;
        // Incoming text is after a delete range. Shift left by delete length.
        if (insert.getPosition() >= delEnd) {
            insert.setPosition(insert.getPosition() - delete.getLength());
            return insert;
        }

        // Incoming position is somewhere in a delete range
        insert.setPosition(delStart);
        return insert;
    }

    private Operation transformDeleteInsert(Operation delete, Operation insert) {
        // Delete(incoming) range is after an insert(applied) position. Shift position right by insert text length
        if (insert.getPosition() <= delete.getPosition()) {
            delete.setPosition(delete.getPosition() + insert.getText().length());
            return delete;
        }

        // Insert(applied) position is not in the range of deletion. No changes
        if (insert.getPosition() >= delete.getPosition() + delete.getLength()) return delete;

        // Insert(applied) position is in the range of deletion. Increase size by insert text length
        delete.setLength(delete.getLength() + insert.getText().length());
        return delete;
    }

    private Operation transformDeleteDelete(Operation a, Operation b) {
        int aStart = a.getPosition();
        int aEnd = aStart + a.getLength();

        int bStart = b.getPosition();
        int bEnd = bStart + b.getLength();

        // Incoming is non-interfering. No changes
        if (aEnd <= bStart) return a;
        // Incoming is after applied. Shifts position left by delete length of applied
        if (aStart >= bEnd) {
            a.setPosition(aStart - b.getLength());
            return a;
        }

        // Overlap calculation in case if operations are colliding.
        // Example: Op(A) deletes [pos 2, len 4], Op(B) deletes [pos 4, len 4]
        // Op(A) changes to [pos 2, len 2].
        int overlapStart = Math.max(aStart, bStart); // 4
        int overlapEnd = Math.min(aEnd, bEnd); // 6
        int overlap = overlapEnd - overlapStart; // 2

        a.setLength(a.getLength() - overlap); // Op(A) became [pos 2, len 2]

        // If incoming operation starts inside applied, updates position
        if (bStart < aStart) {
            a.setPosition(aStart - (overlapStart - bStart));
        }

        return a;
    }
}
