package vn.edu.ptit.campushub.moderation;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionLogRepository extends JpaRepository<ModerationActionLog, Long> {
    @EntityGraph(attributePaths = {"admin"})
    List<ModerationActionLog> findByPostIdOrderByCreatedAtDescIdDesc(Long postId);

    void deleteByPostId(Long postId);
}
