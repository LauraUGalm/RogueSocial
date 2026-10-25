package com.roguesocial.social;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.roguesocial.dungeon.PlayerStats;
import com.roguesocial.social.FriendService.Relation;
import com.roguesocial.social.Post.Audience;
import com.roguesocial.user.User;
import com.roguesocial.user.UserRepository;

/**
 * Profiles: what a player wrote about themselves, their game stats, their profile picture and
 * their gallery.
 *
 * <p>Pictures are stored in GridFS, Mongo's way of keeping files. GridFS splits each file into
 * chunks of 255 KB in {@code fs.chunks}, and keeps one document per file in {@code fs.files}
 * with its name, size, type and upload date, plus a {@code metadata} document for our own
 * fields: {@code ownerId}, {@code kind} ({@code "profile"} or {@code "gallery"}) and, for the
 * gallery, {@code audience}. Finding a player's pictures is a query on {@code fs.files} like any
 * other, and the bytes are only read when a picture is asked for.
 *
 * <p>The profile itself (picture included) is for everyone. A gallery picture is for the
 * owner's followers and friends, or only their friends, as chosen when it was uploaded; unlike a
 * post's audience, a Followers picture really does need a follow. Nothing is shown across a
 * block, either way: the profile, pictures and gallery all answer that there's no such player.
 */
@Service
public class ProfileService {

    static final int MAX_ABOUT = 500;
    static final int MAX_LOCATION = 50;
    static final int MAX_STATUS = 100;
    static final int MAX_GALLERY = 30;

    private static final String PROFILE = "profile";
    private static final String GALLERY = "gallery";

    /** One picture in a gallery. Its bytes are at {@code /api/gallery/{id}}. */
    public record GalleryItem(String id, Audience audience, Instant uploadedAt) {
    }

    /**
     * A profile as one player sees it.
     *
     * @param pictureId  the profile picture's id, or null if there is none. A new picture has a
     *                   new id, so the browser knows to fetch it
     * @param relation   where the viewer stands with them; null on your own profile
     * @param following  whether the viewer follows them
     * @param followsYou whether they follow the viewer
     * @param gallery    the pictures the viewer may see, newest first
     */
    public record ProfileView(String username, boolean mine, OffsetDateTime joined, int level, int power,
            int defense, int maxHealth, String about, String location, String status, Instant statusAt,
            String pictureId, Relation relation, boolean following, boolean followsYou,
            List<GalleryItem> gallery) {
    }

    /** A picture's bytes, always a JPEG. {@code id} changes whenever the picture does. */
    public record Image(String id, byte[] bytes) {
    }

    private final MongoTemplate mongo;
    private final GridFsTemplate files;
    private final SocialIndexes indexes;
    private final UserRepository users;
    private final FriendService friends;
    private final FollowService follows;
    private final BlockService blocks;

    public ProfileService(MongoTemplate mongo, GridFsTemplate files, SocialIndexes indexes, UserRepository users,
            FriendService friends, FollowService follows, BlockService blocks) {
        this.mongo = mongo;
        this.files = files;
        this.indexes = indexes;
        this.users = users;
        this.friends = friends;
        this.follows = follows;
        this.blocks = blocks;
    }

    public ProfileView view(String viewerName, String username) {
        indexes.ensure();
        User viewer = user(viewerName);
        User owner = visible(viewer, username);
        boolean mine = viewer.userId() == owner.userId();
        Relation relation = mine ? null : friends.relation(viewer.userId(), owner.userId());
        boolean friendsWith = mine || relation == Relation.FRIENDS;
        boolean following = !mine && follows.isFollowing(viewer.userId(), owner.userId());

        Profile profile = mongo.findById(owner.userId(), Profile.class);
        if (profile == null) {
            profile = new Profile(owner.userId(), null, null, null, null);
        }
        GridFSFile picture = files.findOne(pictureOf(owner.userId()));

        List<GalleryItem> gallery = new ArrayList<>();
        for (GridFSFile f : files.find(galleryOf(owner.userId())
                .with(Sort.by(Direction.DESC, "uploadDate")))) {
            GalleryItem item = galleryItem(f);
            if (mine || mayView(item.audience(), friendsWith, following)) {
                gallery.add(item);
            }
        }

        PlayerStats.Stats stats = owner.stats();
        return new ProfileView(owner.username(), mine, owner.createdAt(), stats.level(), stats.power(),
                stats.defense(), stats.maxHealth(), profile.about(), profile.location(), profile.status(),
                profile.statusAt(), picture == null ? null : picture.getObjectId().toHexString(), relation,
                following,
                !mine && follows.isFollowing(owner.userId(), viewer.userId()), gallery);
    }

    /**
     * Changes what you wrote. A null field is left as it is; a blank one is cleared. Saving the
     * first time creates the document ("upsert": update it, or insert it if there's none).
     */
    public ProfileView update(String username, String about, String location, String status) {
        indexes.ensure();
        User me = user(username);
        Profile before = mongo.findById(me.userId(), Profile.class);
        Update update = new Update();
        set(update, "about", about, MAX_ABOUT, "About me");
        set(update, "location", location, MAX_LOCATION, "Location");
        set(update, "status", status, MAX_STATUS, "Status");
        String oldStatus = before == null ? null : before.status();
        if (status != null && !Objects.equals(clean(status), oldStatus)) {
            update.set("statusAt", Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }
        if (!update.getUpdateObject().isEmpty()) {
            mongo.upsert(query(where("_id").is(me.userId())), update, Profile.class);
        }
        return view(username, username);
    }

    /**
     * Replaces your profile picture. The new one is stored before the old one is removed, so
     * there's never a moment with neither.
     */
    public void setPicture(String username, InputStream upload) {
        indexes.ensure();
        User me = user(username);
        byte[] jpeg = Pictures.square(upload, Pictures.PROFILE_SIDE);
        ObjectId id = store(me, PROFILE, jpeg, new Document());
        files.delete(pictureOf(me.userId()).addCriteria(where("_id").ne(id)));
    }

    public void removePicture(String username) {
        indexes.ensure();
        files.delete(pictureOf(user(username).userId()));
    }

    /** For anyone, except across a block. */
    public Image picture(String viewerName, String username) {
        indexes.ensure();
        User viewer = user(viewerName);
        User owner = visible(viewer, username);
        GridFSFile file = files.findOne(pictureOf(owner.userId()));
        if (file == null) {
            throw new NoSuchElementException(username + " has no picture");
        }
        return image(file);
    }

    public GalleryItem addToGallery(String username, InputStream upload, Audience audience) {
        indexes.ensure();
        User me = user(username);
        // Two uploads at once could both pass this and make 31. Not worth a lock for that.
        if (mongo.count(galleryOf(me.userId()), "fs.files") >= MAX_GALLERY) {
            throw new IllegalArgumentException("A gallery holds " + MAX_GALLERY + " pictures; remove one first");
        }
        byte[] jpeg = Pictures.fit(upload, Pictures.GALLERY_SIDE);
        Audience to = audience == null ? Audience.FOLLOWERS : audience;
        ObjectId id = store(me, GALLERY, jpeg, new Document("audience", to.name()));
        return galleryItem(files.findOne(query(where("_id").is(id))));
    }

    public void removeFromGallery(String username, String pictureId) {
        indexes.ensure();
        User me = user(username);
        if (!ObjectId.isValid(pictureId)
                || files.findOne(galleryOf(me.userId()).addCriteria(where("_id").is(new ObjectId(pictureId)))) == null) {
            throw new NoSuchElementException("No picture " + pictureId + " in your gallery");
        }
        files.delete(query(where("_id").is(new ObjectId(pictureId))));
    }

    /** A gallery picture, checked against its audience and blocks like a post. */
    public Image galleryImage(String viewerName, String pictureId) {
        indexes.ensure();
        User viewer = user(viewerName);
        GridFSFile file = ObjectId.isValid(pictureId)
                ? files.findOne(query(where("_id").is(new ObjectId(pictureId)).and("metadata.kind").is(GALLERY)))
                : null;
        if (file == null) {
            throw new NoSuchElementException("No picture " + pictureId);
        }
        long ownerId = file.getMetadata().getLong("ownerId");
        long me = viewer.userId();
        if (ownerId != me && (blocks.between(me, ownerId)
                || !mayView(galleryItem(file).audience(), friends.areFriends(me, ownerId),
                        follows.isFollowing(me, ownerId)))) {
            throw new NoSuchElementException("No picture " + pictureId);
        }
        return image(file);
    }

    /** Whether someone else may see a gallery picture with this audience. */
    private static boolean mayView(Audience audience, boolean friendsWith, boolean following) {
        return friendsWith || (audience == Audience.FOLLOWERS && following);
    }

    private ObjectId store(User owner, String kind, byte[] jpeg, Document metadata) {
        metadata.append("ownerId", owner.userId()).append("kind", kind);
        return files.store(new ByteArrayInputStream(jpeg), owner.username() + "-" + kind + ".jpg", "image/jpeg",
                metadata);
    }

    private Image image(GridFSFile file) {
        try (InputStream in = files.getResource(file).getInputStream()) {
            return new Image(file.getObjectId().toHexString(), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GalleryItem galleryItem(GridFSFile f) {
        return new GalleryItem(f.getObjectId().toHexString(),
                Audience.valueOf(f.getMetadata().getString("audience")), f.getUploadDate().toInstant());
    }

    private static Query pictureOf(long ownerId) {
        return query(where("metadata.ownerId").is(ownerId).and("metadata.kind").is(PROFILE));
    }

    private static Query galleryOf(long ownerId) {
        return query(where("metadata.ownerId").is(ownerId).and("metadata.kind").is(GALLERY));
    }

    /** The player, unless either has blocked the other: then, as far as the viewer can tell, there's none. */
    private User visible(User viewer, String username) {
        User owner = user(username);
        if (blocks.between(viewer.userId(), owner.userId())) {
            throw new NoSuchElementException("No user " + username);
        }
        return owner;
    }

    private static void set(Update update, String field, String value, int max, String label) {
        if (value == null) {
            return;
        }
        String cleaned = clean(value);
        if (cleaned == null) {
            update.unset(field);
            return;
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw new IllegalArgumentException(label + " can be at most " + max + " characters");
        }
        update.set(field, cleaned);
    }

    /** Trimmed, or null if nothing is left. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("No user " + username));
    }
}
