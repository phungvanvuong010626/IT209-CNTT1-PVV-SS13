package vn.edu.ptit.campushub.moderation;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModeratedPostView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationDetailView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationRequest;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationResult;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ModerationSummary;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReportView;
import vn.edu.ptit.campushub.post.PostService.PageView;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {
    private final ModerationService service;

    public AdminModerationController(ModerationService service) {
        this.service = service;
    }

    @GetMapping("/moderation/summary")
    ModerationSummary summary() {
        return service.summary();
    }

    @GetMapping("/moderation/posts")
    PageView<ModeratedPostView> posts(@RequestParam(required = false) ModerationStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return service.listPosts(status, page, size);
    }

    @GetMapping("/reports")
    PageView<ReportView> reports(@RequestParam(required = false) ReportStatus status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return service.listReports(status, page, size);
    }

    @GetMapping("/posts/{id}/moderation")
    ModerationDetailView detail(@PathVariable Long id, Principal principal) {
        return service.detail(id, principal);
    }

    @PostMapping("/posts/{id}/moderation")
    ModerationResult moderate(@PathVariable Long id, @Valid @RequestBody ModerationRequest request,
                              Principal principal) {
        return service.moderate(id, request, principal);
    }
}
