package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.PlazaPostLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlazaPostLikeRepository extends JpaRepository<PlazaPostLike, Long> {

    Optional<PlazaPostLike> findByPostIdAndUserId(Long postId, String userId);

    long countByPostId(Long postId);

    @Query("SELECT l.postId, COUNT(l) FROM PlazaPostLike l WHERE l.postId IN :postIds GROUP BY l.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    @Query("SELECT l.postId FROM PlazaPostLike l WHERE l.userId = :userId AND l.postId IN :postIds")
    List<Long> findLikedPostIds(@Param("userId") String userId, @Param("postIds") Collection<Long> postIds);
}
