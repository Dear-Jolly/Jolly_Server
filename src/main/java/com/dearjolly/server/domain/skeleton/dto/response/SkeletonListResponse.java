package com.dearjolly.server.domain.skeleton.dto.response;

import java.util.List;

public record SkeletonListResponse(
        int count,
        List<SkeletonGetResponse> skeletons
) {
    public static SkeletonListResponse of(List<SkeletonGetResponse> skeletonList) {
        return new SkeletonListResponse(
                skeletonList.size(),
                skeletonList
        );
    }
}
