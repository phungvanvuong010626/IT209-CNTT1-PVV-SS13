package vn.edu.ptit.campushub.moderation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);

    Optional<Report> findByPostIdAndReporterId(Long postId, Long reporterId);

    long countByPostIdAndStatus(Long postId, ReportStatus status);

    long countByStatus(ReportStatus status);

    List<Report> findByPostIdAndStatus(Long postId, ReportStatus status);

    @EntityGraph(attributePaths = {"post", "reporter"})
    List<Report> findByPostIdOrderByCreatedAtDescIdDesc(Long postId);

    @EntityGraph(attributePaths = {"post", "reporter"})
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "reporter"})
    @Query("select r from Report r")
    Page<Report> findAllReports(Pageable pageable);

    void deleteByPostId(Long postId);

    /** Số báo cáo PENDING theo từng bài: mỗi phần tử là [postId, count]. */
    @Query("""
            select r.post.id, count(r) from Report r
            where r.post.id in :postIds
              and r.status = vn.edu.ptit.campushub.moderation.ReportStatus.PENDING
            group by r.post.id
            """)
    List<Object[]> countPendingByPostIds(@Param("postIds") Collection<Long> postIds);

    /** Danh sách bài (trong tập cho trước) mà người dùng đã báo cáo. */
    @Query("select r.post.id from Report r where r.reporter.id = :userId and r.post.id in :postIds")
    List<Long> findReportedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    /** Thống kê theo bài: [postId, tổng báo cáo, báo cáo PENDING, thời điểm báo cáo gần nhất]. */
    @Query("""
            select r.post.id, count(r),
                   sum(case when r.status = vn.edu.ptit.campushub.moderation.ReportStatus.PENDING then 1 else 0 end),
                   max(r.createdAt)
            from Report r
            where r.post.id in :postIds
            group by r.post.id
            """)
    List<Object[]> aggregateByPostIds(@Param("postIds") Collection<Long> postIds);
}
