package astranewin.dev.realtime_collaborative_editor.document;

import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;

public enum DocumentAccessPolicy {
    PUBLIC_READ,
    PUBLIC_EDIT,
    RESTRICTED;

    public AccessType toAccessType() {
        return switch (this) {
            case RESTRICTED -> AccessType.NONE;
            case PUBLIC_EDIT -> AccessType.WRITE;
            case PUBLIC_READ -> AccessType.READ;
        };
    }
}
