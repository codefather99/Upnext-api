package com.upnext.web.api;

import com.upnext.domain.*;
import com.upnext.domain.Views.*;
import com.upnext.web.dto.Dto;
import com.upnext.web.error.ApiValidationException;
import com.upnext.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {
    private final RequestService requests;
    private final VoteService votes;
    private final BoardService board;

    public RequestController(RequestService requests, VoteService votes, BoardService board) {
        this.requests = requests;
        this.votes = votes;
        this.board = board;
    }

    /** Safe, idempotent. Visitors allowed; a signed-in user also gets viewerHasVoted. Filters combine; omit one to clear it. */
    @GetMapping
    public PageView<RequestView> list(@RequestParam(required = false) String tag,
                                      @RequestParam(required = false) Status status,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "votes") String sort,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new ApiValidationException("page", "page must be 0 or more");
        if (size < 1 || size > 50) throw new ApiValidationException("size", "size must be between 1 and 50");
        RequestService.Sort order;
        if (sort.equalsIgnoreCase("votes")) order = RequestService.Sort.VOTES;
        else if (sort.equalsIgnoreCase("newest")) order = RequestService.Sort.NEWEST;
        else throw new ApiValidationException("sort", "sort must be votes or newest");
        return requests.search(new RequestService.Browse(tag, status, q, order, page, size, CurrentUser.viewerId()));
    }

    @GetMapping("/{id}")
    public RequestView get(@PathVariable UUID id) { return requests.get(id, CurrentUser.viewerId()); }

    /** Unsafe, not idempotent. Starts OPEN. */
    @PostMapping
    public ResponseEntity<RequestView> create(@Valid @RequestBody Dto.CreateRequest body) {
        return ResponseEntity.status(201).body(requests.create(body.title(), body.description(), body.tags(), CurrentUser.actor()));
    }

    /** Unsafe, idempotent: "I vote". Two taps or two tabs at once leave one vote (the database primary key). */
    @PutMapping("/{id}/vote")
    public VoteState vote(@PathVariable UUID id) { return votes.cast(id, CurrentUser.required().id()); }

    /** Unsafe, idempotent: "I do not vote". Removing a vote that is not there is a success. */
    @DeleteMapping("/{id}/vote")
    public VoteState unvote(@PathVariable UUID id) { return votes.withdraw(id, CurrentUser.required().id()); }

    /** ADMIN only (SecurityConfig). Unsafe, idempotent. expectedVersion guards against two admins moving one card. */
    @PutMapping("/{id}/status")
    public RequestView move(@PathVariable UUID id, @Valid @RequestBody Dto.MoveRequest body) {
        return board.move(id, body.status(), body.expectedVersion(), CurrentUser.actor());
    }

    /** ADMIN only (SecurityConfig). Who moved this card, and when. */
    @GetMapping("/{id}/status-history")
    public List<HistoryEntry> history(@PathVariable UUID id) { return board.history(id); }
}
