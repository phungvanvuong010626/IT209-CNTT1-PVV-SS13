package vn.edu.ptit.campushub.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.ptit.campushub.moderation.ModerationStatus;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("""
            select p from Post p
            where p.status <> vn.edu.ptit.campushub.post.PostStatus.HIDDEN
              and (:includeModerationHidden = true
                   or p.moderationStatus <> vn.edu.ptit.campushub.moderation.ModerationStatus.HIDDEN)
              and (:categoryId is null or p.category.id = :categoryId)
              and (:keyword = '' or lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.content) like lower(concat('%', :keyword, '%')))
            """)
    Page<Post> searchPublished(@Param("keyword") String keyword,
                               @Param("categoryId") Long categoryId,
                               @Param("includeModerationHidden") boolean includeModerationHidden,
                               Pageable pageable);

    Page<Post> findByModerationStatus(ModerationStatus moderationStatus, Pageable pageable);

    /** Bài cần quản trị viên chú ý: không ở trạng thái NORMAL hoặc đang có báo cáo chờ xử lý. */
    @Query("""
            select p from Post p
            where p.moderationStatus <> vn.edu.ptit.campushub.moderation.ModerationStatus.NORMAL
               or exists (select r.id from Report r
                          where r.post = p
                            and r.status = vn.edu.ptit.campushub.moderation.ReportStatus.PENDING)
            """)
    Page<Post> findNeedingModeration(Pageable pageable);

    long countByModerationStatus(ModerationStatus moderationStatus);
}
