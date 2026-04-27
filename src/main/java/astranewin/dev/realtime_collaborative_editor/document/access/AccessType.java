package astranewin.dev.realtime_collaborative_editor.document.access;

// Ordinal usage!!!
public enum AccessType {
    NONE,
    READ,
    WRITE,
    MANAGE,
    OWNER;

    public boolean isManageAccess() {
        return this.ordinal() >= MANAGE.ordinal();
    }
}
