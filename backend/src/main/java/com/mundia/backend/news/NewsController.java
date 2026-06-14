package com.mundia.backend.news;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/{poolId}")
    public NewsService.NewsResponse getFeed(
            @PathVariable long poolId,
            @RequestParam(defaultValue = "false") boolean refresh,
            @AuthenticationPrincipal Jwt jwt) {
        return newsService.getNews(poolId, refresh);
    }
}
