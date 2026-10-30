package com.upnext.web.api;

import com.upnext.domain.BoardService;
import com.upnext.domain.RequestService;
import com.upnext.domain.Views.*;
import com.upnext.web.dto.Dto;
import com.upnext.web.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BoardController {
    private final BoardService board;
    private final RequestService requests;
    private final String version;

    public BoardController(BoardService board, RequestService requests, @Value("${upnext.version:0.1.0}") String version) {
        this.board = board;
        this.requests = requests;
        this.version = version;
    }

    /** Safe, idempotent. Also a cheap health probe for the host. */
    @GetMapping("/meta")
    public Dto.Meta meta() { return new Dto.Meta("up-next (demonstration)", true, Instant.now(), version); }

    /** ADMIN only (SecurityConfig). Four columns, most upvoted first. */
    @GetMapping("/board")
    public Board board() { return board.board(CurrentUser.viewerId()); }

    /** Public. A query over request status, so it always agrees with the board. */
    @GetMapping("/roadmap")
    public Roadmap roadmap() { return board.roadmap(CurrentUser.viewerId()); }

    @GetMapping("/tags")
    public List<TagCount> tags() { return requests.tags(); }
}
