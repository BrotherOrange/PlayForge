package com.game.playforge.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side SPA routes to index.html while keeping /api out of the way.
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/{path:^(?!api$)[^\\.]+}",
            "/{path:^(?!api$)[^\\.]+}/{subpath:[^\\.]+}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
