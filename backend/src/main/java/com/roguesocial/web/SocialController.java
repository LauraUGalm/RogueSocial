package com.roguesocial.web;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.roguesocial.social.BlockService;
import com.roguesocial.social.FollowService;
import com.roguesocial.social.FriendService;
import com.roguesocial.social.NotEnoughGoldException;
import com.roguesocial.social.NotYourPostException;
import com.roguesocial.social.Post;
import com.roguesocial.social.Post.Audience;
import com.roguesocial.social.PostService;
import com.roguesocial.social.Purse;
import com.roguesocial.user.SignedIn;

/** Posts, replies, likes, follows, friends, blocks and the feed, for whoever is signed in. */
@RestController
@RequestMapping("/api")
public class SocialController {

    /** {@code audience} is ignored for a reply, which goes to its post's audience. */
    public record NewPost(String text, String replyTo, Audience audience) {
    }

    public record EditedPost(String text) {
    }

    /** A post as one player sees it: the stored fields, plus whether they have liked it. */
    public record PostView(@JsonUnwrapped Post post, boolean liked) {
    }

    public record Posted(PostView post, int goldSpent) {
    }

    public record TakenBack(int goldRefunded) {
    }

    /** Who is signed in, and the gold they have to post with. */
    public record Me(String username, long bankGold) {
    }

    private final PostService posts;
    private final FollowService follows;
    private final FriendService friends;
    private final BlockService blocks;
    private final Purse purse;

    public SocialController(PostService posts, FollowService follows, FriendService friends,
            BlockService blocks, Purse purse) {
        this.posts = posts;
        this.follows = follows;
        this.friends = friends;
        this.blocks = blocks;
        this.purse = purse;
    }

    @GetMapping("/me")
    public Me me(SignedIn me) {
        return new Me(me.username(), purse.balance(me.username()));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Posted post(SignedIn me,
            @RequestBody NewPost body) {
        PostService.Posted posted = posts.post(me.username(), body.text(), body.replyTo(), body.audience());
        return new Posted(new PostView(posted.post(), false), posted.goldSpent());
    }

    @PatchMapping("/posts/{id}")
    public Posted edit(SignedIn me,
            @PathVariable String id, @RequestBody EditedPost body) {
        PostService.Posted posted = posts.edit(me.username(), id, body.text());
        return new Posted(views(me.username(), List.of(posted.post())).getFirst(), posted.goldSpent());
    }

    /** 200 with the tombstone when the post had replies, 204 when it is gone. */
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<PostView> delete(SignedIn me,
            @PathVariable String id) {
        return posts.delete(me.username(), id)
                .map(tombstone -> ResponseEntity.ok(new PostView(tombstone, false)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Takes back your post in its first minute, refunding all its gold. 409 once it's live. */
    @PostMapping("/posts/{id}/take-back")
    public TakenBack takeBack(SignedIn me,
            @PathVariable String id) {
        return new TakenBack(posts.takeBack(me.username(), id));
    }

    @GetMapping("/posts/{id}")
    public PostView find(SignedIn me,
            @PathVariable String id) {
        return views(me.username(), List.of(posts.find(me.username(), id))).getFirst();
    }

    @GetMapping("/posts/{id}/replies")
    public List<PostView> replies(SignedIn me,
            @PathVariable String id) {
        return views(me.username(), posts.replies(me.username(), id));
    }

    @PutMapping("/posts/{id}/like")
    public PostView like(SignedIn me,
            @PathVariable String id) {
        return new PostView(posts.like(me.username(), id), true);
    }

    @DeleteMapping("/posts/{id}/like")
    public PostView unlike(SignedIn me,
            @PathVariable String id) {
        return new PostView(posts.unlike(me.username(), id), false);
    }

    @PutMapping("/follows/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(SignedIn me,
            @PathVariable String username) {
        follows.follow(me.username(), username);
    }

    @DeleteMapping("/follows/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(SignedIn me,
            @PathVariable String username) {
        follows.unfollow(me.username(), username);
    }

    @GetMapping("/friends")
    public FriendService.Friends friends(SignedIn me) {
        return friends.friends(me.username());
    }

    /** Sends a friend request, or accepts theirs. Returns your lists as they are now. */
    @PutMapping("/friends/{username}")
    public FriendService.Friends befriend(SignedIn me,
            @PathVariable String username) {
        friends.request(me.username(), username);
        return friends.friends(me.username());
    }

    /** Unfriends, declines their request, or takes back yours. */
    @DeleteMapping("/friends/{username}")
    public FriendService.Friends unfriend(SignedIn me,
            @PathVariable String username) {
        friends.end(me.username(), username);
        return friends.friends(me.username());
    }

    /** Who you have blocked. Nobody else can see this. */
    @GetMapping("/blocks")
    public List<String> blocked(SignedIn me) {
        return blocks.blocked(me.username());
    }

    /** Blocks them, ending any friendship, request and follows between you. Returns your list. */
    @PutMapping("/blocks/{username}")
    public List<String> block(SignedIn me,
            @PathVariable String username) {
        blocks.block(me.username(), username);
        return blocks.blocked(me.username());
    }

    /** Unblocks them. What the block ended stays ended. Returns your list. */
    @DeleteMapping("/blocks/{username}")
    public List<String> unblock(SignedIn me,
            @PathVariable String username) {
        blocks.unblock(me.username(), username);
        return blocks.blocked(me.username());
    }

    @GetMapping("/feed")
    public List<PostView> feed(SignedIn me,
            @RequestParam(required = false) String before) {
        return views(me.username(), posts.feed(me.username(), before));
    }

    /** Marks the ones this player has liked, asking Mongo once for the whole list. */
    private List<PostView> views(String username, List<Post> page) {
        Set<String> liked = posts.likedAmong(username, page.stream().map(Post::id).toList());
        return page.stream().map(p -> new PostView(p, liked.contains(p.id()))).toList();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    /** 402 is HTTP's "Payment Required", which is exactly this. */
    @ExceptionHandler(NotEnoughGoldException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public Map<String, String> notEnoughGold(NotEnoughGoldException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(NotYourPostException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> notYours(NotYourPostException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(IllegalStateException e) {
        return Map.of("error", e.getMessage());
    }

    /** Mongo or Postgres is down. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> databaseDown(DataAccessResourceFailureException e) {
        return Map.of("error", "The database isn't reachable right now");
    }
}
