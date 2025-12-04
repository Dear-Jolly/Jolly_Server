package com.dearjolly.server.domain.skeleton.service;

import com.dearjolly.server.domain.skeleton.dto.request.SkeletonCreateRequest;
import com.dearjolly.server.domain.skeleton.dto.response.SkeletonGetResponse;
import com.dearjolly.server.domain.skeleton.dto.response.SkeletonListResponse;
import com.dearjolly.server.domain.skeleton.entity.Skeleton;
import com.dearjolly.server.domain.skeleton.repository.SkeletonRepository;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkeletonService {

    private final SkeletonRepository skeletonRepository;

    /**
     * Skeleton 전체 조회
     */
    public SkeletonListResponse getSkeletons() {
        List<SkeletonGetResponse> skeletonList = skeletonRepository.findAll().stream()
                .map(SkeletonGetResponse::of)
                .toList();

        return SkeletonListResponse.of(skeletonList);
    }

    /**
     * Skeleton 생성
     */
    @Transactional
    public SkeletonGetResponse createSkeleton(SkeletonCreateRequest request) {
        Skeleton skeleton = request.toEntity();
        Skeleton saved = skeletonRepository.save(skeleton);
        return SkeletonGetResponse.of(saved);
    }
}
