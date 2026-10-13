package vn.edu.ptit.campushub.moderation;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.campushub.moderation.ModerationDtos.MyReportView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReasonView;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReportRequest;
import vn.edu.ptit.campushub.moderation.ModerationDtos.ReportView;

@RestController
public class ReportController {
    private final ModerationService service;

    public ReportController(ModerationService service) {
        this.service = service;
    }

    @GetMapping("/api/report-reasons")
    List<ReasonView> reasons() {
        return service.reasons();
    }

    @PostMapping("/api/posts/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ReportView report(@PathVariable Long id, @Valid @RequestBody ReportRequest request, Principal principal) {
        return service.report(id, request, principal);
    }

    @GetMapping("/api/posts/{id}/reports/me")
    MyReportView myReport(@PathVariable Long id, Principal principal) {
        return service.myReport(id, principal);
    }
}
