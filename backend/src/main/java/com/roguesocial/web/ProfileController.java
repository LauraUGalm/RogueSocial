package com.roguesocial.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import com.roguesocial.social.Post.Audience;
import com.roguesocial.social.ProfileService;
import com.roguesocial.social.ProfileService.GalleryItem;
import com.roguesocial.social.ProfileService.ProfileView;
import com.roguesocial.user.SignedIn;

/** Profiles, profile pictures and galleries, for whoever is signed in. */
@RestController
@RequestMapping("/api")
public class ProfileController {

    /** A null field is left as it is; a blank one is cleared. */
    public record ProfileEdit(String about, String location, String status) {
    }

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profiles/{username}")
    public ProfileView profile(SignedIn me, @PathVariable String username) {
        return profiles.view(me.username(), username);
    }

    @PatchMapping("/profile")
    public ProfileView edit(SignedIn me, @RequestBody ProfileEdit body) {
        return profiles.update(me.username(), body.about(), body.location(), body.status());
    }

    @PutMapping("/profile/picture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPicture(SignedIn me, @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            profiles.setPicture(me.username(), in);
        }
    }

    @DeleteMapping("/profile/picture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePicture(SignedIn me) {
        profiles.removePicture(me.username());
    }

    @GetMapping("/profiles/{username}/picture")
    public ResponseEntity<byte[]> picture(SignedIn me, @PathVariable String username, WebRequest request) {
        return image(profiles.picture(me.username(), username), request);
    }

    @PostMapping("/profile/gallery")
    @ResponseStatus(HttpStatus.CREATED)
    public GalleryItem addToGallery(SignedIn me, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Audience audience) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return profiles.addToGallery(me.username(), in, audience);
        }
    }

    @DeleteMapping("/profile/gallery/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromGallery(SignedIn me, @PathVariable String id) {
        profiles.removeFromGallery(me.username(), id);
    }

    @GetMapping("/gallery/{id}")
    public ResponseEntity<byte[]> galleryImage(SignedIn me, @PathVariable String id, WebRequest request) {
        return image(profiles.galleryImage(me.username(), id), request);
    }

    /**
     * {@code no-cache} doesn't mean "don't keep it": it means "ask before reusing it". The browser
     * sends back the ETag it has, and if the picture is unchanged the answer is a short 304
     * instead of the bytes. Either way the server has checked who is asking first, so an
     * unfriended or blocked player's browser can't keep showing a picture. {@code private} keeps
     * shared caches (a proxy, say) from storing it for everyone.
     */
    private static ResponseEntity<byte[]> image(ProfileService.Image image, WebRequest request) {
        String etag = "\"" + image.id() + "\"";
        CacheControl cache = CacheControl.noCache().cachePrivate();
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag(etag).cacheControl(cache)
                .body(image.bytes());
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

    /** Mongo or Postgres is down. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> databaseDown(DataAccessResourceFailureException e) {
        return Map.of("error", "The database isn't reachable right now");
    }
}
