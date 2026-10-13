package com.roguesocial.web;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

import com.roguesocial.social.FollowService;
import com.roguesocial.social.NotEnoughGoldException;
import com.roguesocial.social.NotYourPostException;
import com.roguesocial.social.Post;
import com.roguesocial.social.PostService;

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

    private final PostService posts;
    private final FollowService follows;
    private final String player;

    public SocialController(PostService posts, FollowService follows,
            @Value("${rogue.player}") String player) {
        this.posts = posts;
        this.follows = follows;
        this.player = player;
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostService.Posted post(@RequestHeader(value = USER_HEADER, required = false) String as,
            @RequestBody NewPost body) {
        return posts.post(actor(as), body.text(), body.replyTo());
    }

    @PatchMapping("/posts/{id}")
    public PostService.Posted edit(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id, @RequestBody EditedPost body) {
        return posts.edit(actor(as), id, body.text());
    }

    @GetMapping("/posts/{id}")
    public Post find(@PathVariable String id) {
        return posts.find(id);
    }

    @GetMapping("/posts/{id}/replies")
    public List<Post> replies(@PathVariable String id) {
        return posts.replies(id);
    }

    @PutMapping("/posts/{id}/like")
    public Post like(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id) {
        return posts.like(actor(as), id);
    }

    @DeleteMapping("/posts/{id}/like")
    public Post unlike(@RequestHeader(value = USER_HEADER, required = false) String as,
            @PathVariable String id) {
        return posts.unlike(actor(as), id);
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
    public List<Post> feed(@RequestHeader(value = USER_HEADER, required = false) String as,
            @RequestParam(required = false) String before) {
        return posts.feed(actor(as), before);
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
