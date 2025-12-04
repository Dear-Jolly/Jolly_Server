package com.dearjolly.server.domain.skeleton.repository;

import com.dearjolly.server.domain.skeleton.entity.Skeleton;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkeletonRepository extends JpaRepository<Skeleton, Integer> {
}
