package com.liuitemlib.item;

/** 物品库条目（不可变）。data 为 LiuLib ItemCodec 编码串。 */
public final class LibraryItem {
    private final String id;
    private final long revision;
    private final String displayName;
    private final String category;
    private final String tags;
    private final String material;
    private final String data;
    private final String dataHash;
    private final String createdBy;
    private final long createdAt;
    private final String updatedBy;
    private final long updatedAt;
    private final boolean deleted;

    public LibraryItem(String id, long revision, String displayName, String category, String tags, String material,
                       String data, String dataHash, String createdBy, long createdAt,
                       String updatedBy, long updatedAt, boolean deleted) {
        this.id = id;
        this.revision = revision;
        this.displayName = displayName == null ? "" : displayName;
        this.category = category == null || category.isBlank() ? "default" : category;
        this.tags = tags == null ? "" : tags;
        this.material = material == null ? "" : material;
        this.data = data;
        this.dataHash = dataHash == null ? "" : dataHash;
        this.createdBy = createdBy == null ? "" : createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy == null ? "" : updatedBy;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }

    public String id() { return id; }
    public long revision() { return revision; }
    public String displayName() { return displayName; }
    public String category() { return category; }
    public String tags() { return tags; }
    public String material() { return material; }
    public String data() { return data; }
    public String dataHash() { return dataHash; }
    public String createdBy() { return createdBy; }
    public long createdAt() { return createdAt; }
    public String updatedBy() { return updatedBy; }
    public long updatedAt() { return updatedAt; }
    public boolean deleted() { return deleted; }

    public LibraryItem withLifecycle(long newRevision, String actor, long now, boolean newDeleted) {
        return new LibraryItem(id, newRevision, displayName, category, tags, material, data, dataHash,
                createdBy, createdAt, actor, now, newDeleted);
    }

    public LibraryItem withContent(String newDisplayName, String newCategory, String newMaterial,
                                   String newData, String newHash, long newRevision, String actor, long now) {
        return new LibraryItem(id, newRevision, newDisplayName, newCategory, tags, newMaterial, newData, newHash,
                createdBy, createdAt, actor, now, false);
    }
}
