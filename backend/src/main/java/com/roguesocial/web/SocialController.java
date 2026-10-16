package com.roguesocial.web;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.roguesocial.social.FollowService;
import com.roguesocial.social.NotEnoughGoldException;
import com.roguesocial.social.NotYourPostException;
import com.roguesocial.social.Post;
import com.roguesocial.social.PostService;
import com.roguesocial.social.Purse;

/**
 * Posts, replies, likes, follows and the feed.
 *
 * <p>There is no sign-in yet. Requests act as {@code rogue.player}, or as whoever the
 * {@code X-Rogue-User} header names, so the feed can be tried with more than one player.
 * This is for development, like {@code POST /api/users}.
 */
@RestController
@RequestMapping("/api")
public class SocialController {

    static final String USER_HEADER = "X-Rogue-User";

    public record NewPost(String text, String replyTo) {
    }

    public record EditedPost(String text) {
    }

    /** A post as one player sees it: the stored fields, plus whether they have liked it. */
    public record PostView(@JsonUnwrapped Post post, boolean liked) {
    }

    public record Posted(PostView post, int goldSpent) {
    }

    /** Who the request acts as, and the gold they have to post with. */
    public record Me(String username, long bankGold) {
    }

    private final PostService posts;
    private final FollowService follows;
    private final Purse purse;
    private final String player;

    public SocialController(PostService posts, FollowService follows, Purse purse,
            @Value("${rogue.player}") String player) {
        this.posts = posts;
        this.follows = follows;
        this.purse = purse;
        this.player = player;
    }

    @GetMapping("/me")
    public Me me(@RequestHeader(value = USER_HEADER, required = false) String as) {
        return new Me(actor(as), purse.balance(actor(as)));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Posted post(@RequestHeader(value = USER_HEADER, required = false) String as,
            @RequestBody NewPost body) {
        PostService.Posted posted = posts.post(actor(as), body.text(), body.replyTo());
        return new Posted(new PostView(posted.post(), false), posted.goldSpent());
    }

    @PatchMapping("/posts/{id}")
    public Posted edit(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id, @RequestBody EditedPost body) {
        PostService.Posted posted = posts.edit(actor(as), id, body.text());
        return new Posted(views(actor(as), List.of(posted.post())).getFirst(), posted.goldSpent());
    }

    @GetMapping("/posts/{id}")
    public PostView find(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id) {
        return views(actor(as), List.of(posts.find(id))).getFirst();
    }

    @GetMapping("/posts/{id}/replies")
    public List<PostView> replies(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id) {
        return views(actor(as), posts.replies(id));
    }

    @PutMapping("/posts/{id}/like")
    public PostView like(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id) {
        return new PostView(posts.like(actor(as), id), true);
    }

    @DeleteMapping("/posts/{id}/like")
    public PostView unlike(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id) {
        return new PostView(posts.unlike(actor(as), id), false);
    }

    @PutMapping("/follows/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String username) {
        follows.follow(actor(as), username);
    }

    @DeleteMapping("/follows/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String username) {
        follows.unfollow(actor(as), username);
    }

    @GetMapping("/feed")
    public List<PostView> feed(@RequestHeader(value = USER_HEADER, required = false) String as,
            @RequestParam(required = false) String before) {
        return views(actor(as), posts.feed(actor(as), before));
    }

    /** Marks the ones this player has liked, asking Mongo once for the whole list. */
    private List<PostView> views(String username, List<Post> page) {
        Set<String> liked = posts.likedAmong(username, page.stream().map(Post::id).toList());
        return page.stream().map(p -> new PostView(p, liked.contains(p.id()))).toList();
    }

    private String actor(String as) {
        return as == null || as.isBlank() ? player : as;
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
