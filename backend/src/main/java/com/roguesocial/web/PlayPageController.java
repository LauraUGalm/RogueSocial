package com.roguesocial.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the React app (built into static/play/) at /play. */
@Controller
public class PlayPageController {

    @GetMapping({"/", "/play", "/play/"})
    public String play() {
        return "forward:/play/index.html";
    }
}
