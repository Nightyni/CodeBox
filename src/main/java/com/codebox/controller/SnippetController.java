package com.codebox.controller;

import com.codebox.dto.PageResponse;
import com.codebox.dto.SnippetRequest;
import com.codebox.dto.SnippetResponse;
import com.codebox.entity.Snippet;
import com.codebox.entity.User;
import com.codebox.service.AuthService;
import com.codebox.service.SnippetService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snippets")
public class SnippetController extends BaseController {

    private final SnippetService snippetService;

    public SnippetController(AuthService authService, SnippetService snippetService) {
        super(authService);
        this.snippetService = snippetService;
    }

    @GetMapping
    public PageResponse<SnippetResponse> list(HttpSession session,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String language,
                                              @RequestParam(required = false) String tags,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        User user = currentUser(session);
        PageResponse<Snippet> page =
                snippetService.search(user.getId(), keyword, language, tags, pageNum, pageSize);
        List<SnippetResponse> items = page.items().stream().map(SnippetResponse::from).toList();
        return PageResponse.of(items, page.pageNum(), page.pageSize(), page.totalCount());
    }

    @GetMapping("/{id}")
    public SnippetResponse detail(HttpSession session, @PathVariable Long id) {
        User user = currentUser(session);
        Snippet snippet = snippetService.findById(id, user.getId());
        if (snippet == null) {
            throw ApiException.notFound("代码片段不存在");
        }
        return SnippetResponse.from(snippet);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SnippetResponse create(HttpSession session, @Valid @RequestBody SnippetRequest request) {
        User user = currentUser(session);
        return SnippetResponse.from(snippetService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public SnippetResponse update(HttpSession session, @PathVariable Long id,
                                  @Valid @RequestBody SnippetRequest request) {
        User user = currentUser(session);
        Snippet updated = snippetService.update(user.getId(), id, request);
        if (updated == null) {
            throw ApiException.notFound("代码片段不存在");
        }
        return SnippetResponse.from(updated);
    }

    /** DELETE, not GET: a GET delete is triggerable by prefetch, crawlers and CSRF. */
    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> delete(HttpSession session, @PathVariable Long id) {
        User user = currentUser(session);
        if (!snippetService.delete(user.getId(), id)) {
            throw ApiException.notFound("代码片段不存在");
        }
        return java.util.Map.of("success", true, "id", id);
    }
}
